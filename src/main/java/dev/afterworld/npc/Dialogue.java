package dev.afterworld.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.quest.Quests;
import dev.afterworld.quest.SideQuests;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.Waystones;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Conversations. Each NPC's words are a small graph of nodes loaded from
 * {@code data/afterworld/dialogue/<id>.json}: a node is a few spoken lines (each with its voice
 * line and length) and the answers the traveller may give. Entry rules pick the opening node from
 * the traveller's place in the story; conditions hide answers that don't apply yet; actions
 * complete objectives, set flags, hand over gifts or open a shop.
 *
 * <p>Conditions: {@code obj=<id>} (that objective is open now), {@code done:<id>},
 * {@code chapter>=<n>}, {@code chapter=<n>}, {@code finished}, {@code flag:<f>}, {@code !flag:<f>}.
 * Actions: {@code complete:<id>}, {@code flag:<f>}, {@code give:<item>*<n>}, {@code xp:<n>},
 * {@code heal}, {@code mark:<what>}. Special targets: {@code #end}, {@code #shop}, {@code #travel}.
 */
public final class Dialogue {
	private static final Map<Npc, Script> SCRIPTS = new EnumMap<>(Npc.class);
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	record Line(String text, String sound, int ticks, String cue, List<Integer> beats) {
		static Line of(JsonObject lo) {
			List<Integer> beats = new ArrayList<>();
			if (lo.has("beats")) {
				for (JsonElement b : lo.getAsJsonArray("beats")) {
					beats.add(b.getAsInt());
				}
			}
			return new Line(lo.get("text").getAsString(), lo.has("sound") ? lo.get("sound").getAsString() : "",
				lo.has("ticks") ? lo.get("ticks").getAsInt() : 0, lo.has("cue") ? lo.get("cue").getAsString() : "", beats);
		}

		AwPayloads.DialogueShow.Line wire() {
			return new AwPayloads.DialogueShow.Line(this.text, this.sound, this.ticks, this.cue, this.beats);
		}
	}

	record Option(String text, String go, List<String> when, List<String> actions) {
	}

	record Node(List<Line> lines, List<String> actions, List<Option> options) {
	}

	record Rule(List<String> when, String go) {
	}

	record Script(List<Rule> entry, Map<String, Node> nodes) {
	}

	static final class Session {
		final Mannequin npcEntity;
		final Npc npc;
		String node;
		List<Option> shown = List.of();

		Session(Mannequin npcEntity, Npc npc) {
			this.npcEntity = npcEntity;
			this.npc = npc;
		}
	}

	private Dialogue() {
	}

	public static void init() {
		for (Npc npc : Npc.VALUES) {
			Script s = load(npc);
			if (s != null) {
				SCRIPTS.put(npc, s);
			}
		}
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.DialogueChoose.TYPE, (payload, ctx) -> choose(ctx.player(), payload.entity(), payload.option()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Dialogue::tick);
	}

	// ------------------------------------------------------------------ loading

	private static Script load(Npc npc) {
		String path = "/data/afterworld/dialogue/" + npc.id + ".json";
		try (InputStream in = Dialogue.class.getResourceAsStream(path)) {
			if (in == null) {
				Afterworld.LOG.warn("No dialogue for {}", npc.id);
				return null;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Rule> entry = new ArrayList<>();
			for (JsonElement e : root.getAsJsonArray("entry")) {
				JsonObject o = e.getAsJsonObject();
				entry.add(new Rule(strings(o, "if"), o.get("go").getAsString()));
			}
			Map<String, Node> nodes = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("nodes").entrySet()) {
				JsonObject n = e.getValue().getAsJsonObject();
				List<Line> lines = new ArrayList<>();
				for (JsonElement l : n.getAsJsonArray("lines")) {
					JsonObject lo = l.getAsJsonObject();
					lines.add(Line.of(lo));
				}
				List<Option> options = new ArrayList<>();
				if (n.has("options")) {
					for (JsonElement oe : n.getAsJsonArray("options")) {
						JsonObject oo = oe.getAsJsonObject();
						options.add(new Option(oo.get("text").getAsString(), oo.has("go") ? oo.get("go").getAsString() : "#end", strings(oo, "if"), strings(oo, "do")));
					}
				}
				nodes.put(e.getKey(), new Node(lines, strings(n, "do"), options));
			}
			return new Script(entry, nodes);
		} catch (Exception ex) {
			Afterworld.LOG.error("Could not read dialogue for {}", npc.id, ex);
			return null;
		}
	}

	private static List<String> strings(JsonObject o, String key) {
		if (!o.has(key)) {
			return List.of();
		}
		JsonArray a = o.getAsJsonArray(key);
		List<String> out = new ArrayList<>(a.size());
		for (JsonElement e : a) {
			out.add(e.getAsString());
		}
		return out;
	}

	// ------------------------------------------------------------------ talking

	/** Whoever is talking to this NPC right now, if anyone. */
	public static Player partnerOf(Mannequin m) {
		for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
			if (e.getValue().npcEntity == m) {
				return m.level().getPlayerByUUID(e.getKey());
			}
		}
		return null;
	}

	public static void start(ServerPlayer p, Mannequin m, Npc npc) {
		Script script = SCRIPTS.get(npc);
		if (script == null) {
			return;
		}
		String go = null;
		for (Rule r : script.entry()) {
			if (holds(p, r.when())) {
				go = r.go();
				break;
			}
		}
		if (go == null) {
			return;
		}
		Session s = new Session(m, npc);
		SESSIONS.put(p.getUUID(), s);
		Progress.firstMeeting(p, npc);
		show(p, s, go);
	}

	private static void show(ServerPlayer p, Session s, String nodeId) {
		Script script = SCRIPTS.get(s.npc);
		Node node = script.nodes().get(nodeId);
		if (node == null) {
			Afterworld.LOG.warn("Dialogue {} has no node {}", s.npc.id, nodeId);
			close(p, true);
			return;
		}
		s.node = nodeId;
		for (String a : node.actions()) {
			act(p, s, a);
		}
		List<Option> shown = new ArrayList<>();
		for (Option o : node.options()) {
			if (holds(p, o.when())) {
				shown.add(o);
			}
		}
		s.shown = shown;
		List<AwPayloads.DialogueShow.Line> lines = new ArrayList<>();
		for (Line l : node.lines()) {
			lines.add(l.wire());
		}
		List<String> opts = new ArrayList<>();
		for (Option o : shown) {
			opts.add(o.text());
		}
		ServerPlayNetworking.send(p, new AwPayloads.DialogueShow(s.npcEntity.getId(), s.npc.id, s.npc.displayName, s.npc.role, s.npc.color, lines, opts, false));
	}

	private static void choose(ServerPlayer p, int entity, int option) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.npcEntity.getId() != entity) {
			return;
		}
		if (option < 0 || option >= s.shown.size()) {
			SESSIONS.remove(p.getUUID());
			return;
		}
		Option o = s.shown.get(option);
		for (String a : o.actions()) {
			act(p, s, a);
		}
		switch (o.go()) {
			case "#end" -> close(p, true);
			case "#shop" -> {
				close(p, true);
				dev.afterworld.economy.Shop.open(p, s.npcEntity, s.npc);
			}
			case "#travel" -> {
				close(p, true);
				Waystones.openTravel(p, null, false);
			}
			case "#charter" -> {
				close(p, true);
				Waystones.openTravel(p, null, true);
			}
			case "#bank" -> {
				close(p, true);
				dev.afterworld.economy.Bank.open(p);
			}
			default -> show(p, s, o.go());
		}
	}

	private static void close(ServerPlayer p, boolean tellClient) {
		SESSIONS.remove(p.getUUID());
		if (tellClient) {
			ServerPlayNetworking.send(p, new AwPayloads.DialogueShow(-1, "", "", "", 0, List.of(), List.of(), true));
		}
	}

	/** Conversations end when the traveller walks off. */
	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		SESSIONS.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Session s = e.getValue();
			if (p == null || s.npcEntity.isRemoved() || p.level() != s.npcEntity.level() || p.distanceToSqr(s.npcEntity) > Npcs.TALK_RANGE * Npcs.TALK_RANGE * 2.2) {
				if (p != null) {
					ServerPlayNetworking.send(p, new AwPayloads.DialogueShow(-1, "", "", "", 0, List.of(), List.of(), true));
				}
				return true;
			}
			return false;
		});
	}

	// ------------------------------------------------------------------ conditions and actions

	static boolean holds(ServerPlayer p, List<String> when) {
		for (String c : when) {
			if (!holds(p, c)) {
				return false;
			}
		}
		return true;
	}

	private static boolean holds(ServerPlayer p, String c) {
		PlayerState s = PlayerState.of(p);
		if (c.startsWith("!")) {
			return !holds(p, c.substring(1));
		}
		if (c.startsWith("obj=")) {
			return Quests.isActive(s, c.substring(4));
		}
		if (c.startsWith("done:")) {
			return Quests.isDone(s, c.substring(5));
		}
		if (c.startsWith("chapter>=")) {
			return s.chapter >= Integer.parseInt(c.substring(9));
		}
		if (c.startsWith("chapter=")) {
			return s.chapter == Integer.parseInt(c.substring(8));
		}
		if (c.equals("finished")) {
			return s.questFinished;
		}
		if (c.startsWith("flag:")) {
			return s.flags.contains(c.substring(5));
		}
		if (c.startsWith("side:")) {
			return sideHolds(s, c.substring(5));
		}
		if (c.startsWith("has:")) {
			ItemCount ic = ItemCount.parse(c.substring(4));
			return ic != null && dev.afterworld.util.Inv.count(p, ic.item()) >= ic.count();
		}
		if (c.equals("time:night")) {
			return isNight(p);
		}
		if (c.startsWith("clear:")) {
			// No monster within this many blocks of the traveller: "it's safe to leave now".
			double r = Double.parseDouble(c.substring(6));
			return p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(r),
				m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
		}
		if (c.equals("time:day")) {
			return !isNight(p);
		}
		if (c.equals("weapon:ready")) {
			return dev.afterworld.item.WeaponGrowth.ready(p.getMainHandItem());
		}
		Afterworld.LOG.warn("Unknown dialogue condition {}", c);
		return false;
	}

	private static boolean isNight(ServerPlayer p) {
		long t = p.level().getOverworldClockTime() % 24000L;
		return t >= 13000 && t <= 23000;
	}

	/** {@code <q>=<n>}, {@code <q>>=<n>}, {@code <q>:started}, {@code <q>:finished}. */
	private static boolean sideHolds(PlayerState s, String c) {
		String id;
		if (c.endsWith(":started") || c.endsWith(":finished")) {
			id = c.substring(0, c.lastIndexOf(':'));
			SideQuests.Side q = SideQuests.Side.byId(id);
			int st = q == null ? -1 : SideQuests.stage(s, q);
			return c.endsWith(":started") ? st >= 0 : st == SideQuests.FINISHED;
		}
		int ge = c.indexOf(">=");
		int eq = c.indexOf('=');
		id = c.substring(0, ge >= 0 ? ge : eq);
		SideQuests.Side q = SideQuests.Side.byId(id);
		if (q == null) {
			return false;
		}
		int st = SideQuests.stage(s, q);
		int n = Integer.parseInt(c.substring((ge >= 0 ? ge + 2 : eq + 1)));
		return ge >= 0 ? st >= n && st != SideQuests.FINISHED : st == n;
	}

	/** "namespace:item*count" (count defaults to 1). */
	record ItemCount(Item item, int count) {
		static ItemCount parse(String spec) {
			int n = 1;
			int star = spec.indexOf('*');
			if (star > 0) {
				n = Integer.parseInt(spec.substring(star + 1));
				spec = spec.substring(0, star);
			}
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(spec));
			return item == null ? null : new ItemCount(item, n);
		}
	}

	private static void act(ServerPlayer p, Session session, String a) {
		PlayerState s = PlayerState.of(p);
		if (a.startsWith("complete:")) {
			Quests.complete(p, a.substring(9));
		} else if (a.startsWith("flag:")) {
			s.flags.add(a.substring(5));
		} else if (a.startsWith("give:")) {
			ItemCount ic = ItemCount.parse(a.substring(5));
			if (ic != null) {
				Inv.give(p, new ItemStack(ic.item(), ic.count()));
			}
		} else if (a.startsWith("take:")) {
			ItemCount ic = ItemCount.parse(a.substring(5));
			if (ic != null) {
				Inv.take(p, ic.item(), ic.count());
			}
		} else if (a.startsWith("wallet:")) {
			long n = Long.parseLong(a.substring(7));
			dev.afterworld.economy.Wallet.add(p, n);
			dev.afterworld.economy.Wallet.chime(p, n);
		} else if (a.startsWith("side:")) {
			String[] parts = a.substring(5).split(":");
			SideQuests.Side q = SideQuests.Side.byId(parts[0]);
			if (q != null && parts.length > 1) {
				switch (parts[1]) {
					case "start" -> SideQuests.start(p, q);
					case "advance" -> SideQuests.advance(p, q);
					case "finish" -> SideQuests.finish(p, q);
					default -> Afterworld.LOG.warn("Unknown side action {}", a);
				}
			}
		} else if (a.startsWith("minigame:")) {
			dev.afterworld.minigame.Minigames.start(p, a.substring(9), session.npcEntity);
		} else if (a.startsWith("xp:")) {
			ExperienceOrb.award(p.level(), p.position(), Integer.parseInt(a.substring(3)));
		} else if (a.equals("heal")) {
			p.setHealth(p.getMaxHealth());
			p.getFoodData().eat(20, 1.0F);
			p.clearFire();
			p.removeAllEffects();
			Fx.burst(p.level(), ParticleTypes.HEART, p.position().add(0, 1.2, 0), 6, 0.5, 0.02);
			Fx.sound(p.level(), p.position(), AwSounds.UI_DISCOVERY, 0.7F, 1.4F);
		} else if (a.startsWith("mark:")) {
			Quests.mark(p, a.substring(5), session.npc);
		} else {
			Afterworld.LOG.warn("Unknown dialogue action {}", a);
		}
	}

	/** Small bookkeeping the conversations share. */
	static final class Progress {
		private Progress() {
		}

		static void firstMeeting(ServerPlayer p, Npc npc) {
			if (PlayerState.of(p).flags.add("met:" + npc.id)) {
				dev.afterworld.player.Progress.legend(p, "meeting", "Met " + npc.displayName + ", " + npc.role.toLowerCase(java.util.Locale.ROOT) + ".");
				dev.afterworld.player.Progress.codex(p, "people." + npc.id);
			}
		}
	}
}
