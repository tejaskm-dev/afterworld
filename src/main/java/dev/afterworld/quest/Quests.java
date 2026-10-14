package dev.afterworld.quest;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.BossStates;
import dev.afterworld.world.Faction;
import dev.afterworld.world.WorldSim;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Path. Tracks each traveller's place in the main story, notices when an objective is met,
 * works out where every open objective points (and keeps the client's tracker, marker and light
 * pillar fed), and hands out rewards. Checks that need the world (distances, boss states) run
 * once a second per player; target searches every few seconds at most.
 */
public final class Quests {
	public static final TagKey<Structure> LANDMARKS = TagKey.create(Registries.STRUCTURE, Afterworld.id("landmarks"));
	private static final long NOWHERE = Long.MIN_VALUE;

	private Quests() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Quests::tick);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer p && killed instanceof Enemy) {
				progress(p, Chapter.Goal.KILL, 1);
			}
		});
		Sites.RESOLVE_LISTENERS.add((server, site, by) -> {
			if (by != null && site.kind == SiteKind.ANOMALY) {
				progress(by, Chapter.Goal.ANOMALY, 1);
			}
		});
	}

	// ------------------------------------------------------------------ state queries

	public static Chapter chapter(PlayerState s) {
		return Chapter.byOrdinal(s.chapter);
	}

	public static boolean done(PlayerState s, int i) {
		return (s.objDone & (1 << i)) != 0;
	}

	/** Whether objective {@code i} of the current chapter is open right now. */
	public static boolean open(PlayerState s, int i) {
		Chapter c = chapter(s);
		if (c == null || s.questFinished || done(s, i)) {
			return false;
		}
		if (c.guided) {
			for (int j = 0; j < i; j++) {
				if (!done(s, j)) {
					return false;
				}
			}
			return true;
		}
		if (c.objectives.get(i).last()) {
			for (int j = 0; j < c.objectives.size(); j++) {
				if (j != i && !c.objectives.get(j).last() && !done(s, j)) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean isActive(PlayerState s, String objective) {
		Chapter c = chapter(s);
		if (c == null) {
			return false;
		}
		int i = c.indexOf(objective);
		return i >= 0 && open(s, i);
	}

	/** Whether an objective is behind the traveller: finished, or talked through ahead of its time. */
	public static boolean isDone(PlayerState s, String objective) {
		for (int ci = 0; ci < Chapter.VALUES.length; ci++) {
			int i = Chapter.VALUES[ci].indexOf(objective);
			if (i >= 0) {
				return s.questFinished || s.chapter > ci || s.chapter == ci && done(s, i) || s.deeds.containsKey("obj:" + objective);
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ progress

	/**
	 * Completes an objective by name (used by conversations): at once if it is open now, otherwise
	 * as soon as the story reaches it.
	 */
	public static void complete(ServerPlayer p, String objective) {
		PlayerState s = PlayerState.of(p);
		Chapter c = chapter(s);
		if (c == null) {
			return;
		}
		int i = c.indexOf(objective);
		if (i >= 0 && open(s, i)) {
			finish(p, s, c, i);
		} else if (!isDone(s, objective)) {
			s.deeds.put("obj:" + objective, 1);
		}
	}

	/** Counts towards every open objective of this kind (and remembers it, for objectives not open yet). */
	public static void progress(ServerPlayer p, Chapter.Goal goal, int amount) {
		PlayerState s = PlayerState.of(p);
		s.deeds.merge(goal.name(), amount, (a, b) -> (int) Math.min(1_000_000L, (long) a + b));
		Chapter c = chapter(s);
		if (c == null || s.questFinished) {
			return;
		}
		for (int i = 0; i < c.objectives.size(); i++) {
			Chapter.Objective o = c.objectives.get(i);
			if (o.goal() == goal && open(s, i)) {
				s.objCount[i] = Math.min(o.count(), s.objCount[i] + amount);
				s.questDirty = true;
				if (s.objCount[i] >= o.count()) {
					finish(p, s, c, i);
				}
			}
		}
	}

	public static void onDodge(ServerPlayer p) {
		progress(p, Chapter.Goal.DODGE, 1);
	}

	public static void onParry(ServerPlayer p) {
		progress(p, Chapter.Goal.PARRY, 1);
	}

	public static void onContract(ServerPlayer p) {
		progress(p, Chapter.Goal.CONTRACT, 1);
	}

	public static void onCast(ServerPlayer p) {
		progress(p, Chapter.Goal.CAST, 1);
	}

	public static void onJournal(ServerPlayer p) {
		progress(p, Chapter.Goal.JOURNAL, 1);
	}

	public static void onAttune(ServerPlayer p, AfterworldData.Waystone w) {
		progress(p, w.camp ? Chapter.Goal.ATTUNE_CAMP : Chapter.Goal.ATTUNE_LANDMARK, 1);
	}

	/**
	 * Whether the traveller already did what an objective asks before it opened: touched the
	 * waystone, cast, rolled, traded, talked it through with the keeper.
	 */
	private static boolean already(PlayerState s, AfterworldData data, Chapter.Objective o) {
		if (s.deeds.containsKey("obj:" + o.id())) {
			return true;
		}
		return switch (o.goal()) {
			case ATTUNE_CAMP, ATTUNE_LANDMARK -> {
				for (int id : s.attuned) {
					AfterworldData.Waystone w = data.waystones.get(id);
					if (w != null && w.camp == (o.goal() == Chapter.Goal.ATTUNE_CAMP)) {
						yield true;
					}
				}
				yield false;
			}
			case CAST, DODGE, PARRY, JOURNAL, TRADE, CONTRACT, ANOMALY, KILL -> s.deeds.getOrDefault(o.goal().name(), 0) >= o.count();
			default -> false;
		};
	}

	private static void finish(ServerPlayer p, PlayerState s, Chapter c, int i) {
		finish(p, s, c, i, false);
	}

	private static void finish(ServerPlayer p, PlayerState s, Chapter c, int i, boolean earlier) {
		Chapter.Objective o = c.objectives.get(i);
		s.objDone |= 1 << i;
		s.objCount[i] = o.count();
		s.questDirty = true;
		Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable(earlier ? "notice.afterworld.objective_already" : "notice.afterworld.objective_done",
			Component.translatable("quest.afterworld.obj." + o.id())));
		ExperienceOrb.award(p.level(), p.position(), 12 + 8 * s.chapter);
		dev.afterworld.player.Renown.grant(p, 40 + 25 * s.chapter, Component.translatable("quest.afterworld.obj." + o.id()).getString());
		boolean all = true;
		for (int j = 0; j < c.objectives.size(); j++) {
			all &= done(s, j);
		}
		if (all) {
			nextChapter(p, s, c);
		}
	}

	private static void nextChapter(ServerPlayer p, PlayerState s, Chapter finished) {
		reward(p, finished);
		dev.afterworld.player.Skills.grantInsight(p, 1);
		dev.afterworld.player.Renown.grant(p, 300 * (finished.ordinal() + 1), Component.translatable("quest.afterworld.chapter." + finished.id).getString());
		Progress.legend(p, "milestone", "Finished " + Component.translatable("quest.afterworld.chapter." + finished.id).getString() + ".");
		s.chapter++;
		s.objDone = 0;
		java.util.Arrays.fill(s.objCount, 0);
		java.util.Arrays.fill(s.objTarget, NOWHERE);
		s.targetsAt = -1000;
		s.questDirty = true;
		Chapter next = chapter(s);
		if (next == null) {
			s.questFinished = true;
			title(p, Component.translatable("quest.afterworld.finished"), Component.translatable("quest.afterworld.finished.sub"), 0xE8C873);
			narrate(p, s, "epilogue");
			return;
		}
		title(p, Component.translatable("quest.afterworld.chapter.n", Component.translatable("quest.afterworld.numeral." + s.chapter)),
			Component.translatable("quest.afterworld.chapter." + next.id), 0xE8C873);
		narrate(p, s, "chapter" + s.chapter);
	}

	/** Shows the chapter card for whatever chapter the traveller is in (on joining, say). */
	public static void announce(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		Chapter c = chapter(s);
		if (c == null || s.questFinished) {
			return;
		}
		Component head = s.chapter == 0 ? Component.translatable("quest.afterworld.prologue")
			: Component.translatable("quest.afterworld.chapter.n", Component.translatable("quest.afterworld.numeral." + s.chapter));
		title(p, head, Component.translatable("quest.afterworld.chapter." + c.id), 0xE8C873);
	}

	/** The narrator's words for a turn of the story, once per traveller. */
	public static void narrate(ServerPlayer p, PlayerState s, String group) {
		if (s.flags.add("narrated:" + group)) {
			dev.afterworld.util.Scheduler.after(30, () -> dev.afterworld.npc.Barks.narrate(p, group));
		}
	}

	private static void title(ServerPlayer p, Component title, Component sub, int color) {
		if (ServerPlayNetworking.canSend(p, AwPayloads.Title.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.CHAPTER, title, sub, color));
		}
		Fx.privateSound(p, AwSounds.UI_CHRONICLE, 1.0F, 0.8F);
	}

	private static void reward(ServerPlayer p, Chapter c) {
		switch (c) {
			case PROLOGUE -> {
				dev.afterworld.economy.Wallet.add(p, 60);
				Inv.give(p, new ItemStack(Items.COOKED_BEEF, 8));
				ExperienceOrb.award(p.level(), p.position(), 60);
			}
			case LISTENING -> {
				dev.afterworld.economy.Wallet.add(p, 200);
				Inv.give(p, new ItemStack(dev.afterworld.registry.AwBlocks.WAYSTONE));
				Inv.give(p, new ItemStack(Items.GOLDEN_APPLE, 2));
				ExperienceOrb.award(p.level(), p.position(), 160);
				for (Faction f : Faction.VALUES) {
					Progress.rep(p, f, 5);
				}
			}
			case THREE -> {
				dev.afterworld.economy.Wallet.add(p, 600);
				Inv.give(p, new ItemStack(Items.TOTEM_OF_UNDYING));
				ExperienceOrb.award(p.level(), p.position(), 400);
			}
			case WARDENS -> {
				dev.afterworld.economy.Wallet.add(p, 2000);
				Inv.give(p, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
				ExperienceOrb.award(p.level(), p.position(), 1200);
			}
		}
		Fx.burst(p.level(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 40, 0.6, 0.3);
	}

	// ------------------------------------------------------------------ marks

	/** A keeper marks somewhere on the traveller's map. */
	public static void mark(ServerPlayer p, String what, @org.jspecify.annotations.Nullable Npc by) {
		PlayerState s = PlayerState.of(p);
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		BlockPos at = null;
		String label = what;
		switch (what) {
			case "landmark" -> {
				at = level.findNearestMapStructure(LANDMARKS, p.blockPosition(), 48, false);
				label = "A landmark";
			}
			case "village" -> {
				at = level.findNearestMapStructure(StructureTags.VILLAGE, p.blockPosition(), 48, false);
				label = "A village";
			}
			case "anomaly" -> {
				Site site = anomalyFor(p, data);
				at = site == null ? null : site.pos();
				label = site == null ? "A loose Edict" : site.name;
			}
			case "shrine" -> {
				Site site = nearestShrine(data, p.getX(), p.getZ());
				at = site == null ? null : site.pos();
				label = site == null ? "A shrine" : site.name;
			}
			case "tablet" -> {
				at = structure(p, s, "glyph_sites");
				label = "A glyph tablet";
			}
			case "caravan" -> {
				at = structure(p, s, "caravan_wreck");
				label = "The silent caravan's road";
			}
			case "obelisk" -> {
				at = structure(p, s, "buried_obelisk");
				label = "The buried obelisk";
			}
			case "ladder" -> {
				at = structure(p, s, "ladder_gates");
				label = "The Warden's Ladder";
			}
			case "cathedral" -> {
				Site site = data.sites.get(data.bell.site);
				at = site == null ? null : site.pos();
				label = "The Hollow Cathedral";
			}
			case "vault" -> {
				Site site = data.sites.get(data.vault.site);
				at = site == null ? null : site.pos();
				label = "The Vault of Seconds";
			}
			default -> Afterworld.LOG.warn("Unknown mark {}", what);
		}
		if (at == null) {
			p.sendSystemMessage(Component.translatable("message.afterworld.mark_none").withColor(0x9A9488));
			return;
		}
		s.hasMark = true;
		s.markX = at.getX();
		s.markY = what.equals("landmark") || what.equals("village") || what.equals("tablet") || what.equals("caravan") || what.equals("obelisk")
			|| what.equals("ladder")
			? Integer.MIN_VALUE : at.getY();
		s.markZ = at.getZ();
		s.markDim = level.dimension().identifier().toString();
		s.markLabel = label;
		s.questDirty = true;
		Progress.notice(p, AwPayloads.Notice.QUEST, by == null ? Component.translatable("notice.afterworld.surveyed", Component.literal(label))
			: Component.translatable("notice.afterworld.marked", Component.literal(by.displayName), Component.literal(label)));
	}

	// ------------------------------------------------------------------ the tick

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			// Spread players over the second.
			if ((t + p.getId()) % 20 != 0) {
				continue;
			}
			PlayerState s = PlayerState.of(p);
			if (!s.arrived) {
				continue;
			}
			second(p, s);
		}
	}

	private static void second(ServerPlayer p, PlayerState s) {
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		AfterworldData data = AfterworldData.get(server);
		Chapter c = chapter(s);
		long now = level.getGameTime();
		if (c != null && !s.questFinished) {
			// Steps taken before the story asked for them count as soon as it does (one a second, so
			// the notices read in order); partly done counted ones show how far along they are.
			for (int i = 0; i < c.objectives.size(); i++) {
				if (!open(s, i)) {
					continue;
				}
				Chapter.Objective o = c.objectives.get(i);
				if (already(s, data, o)) {
					finish(p, s, c, i, true);
					sync(p, s);
					return;
				}
				int had = o.count() > 1 ? Math.min(o.count() - 1, s.deeds.getOrDefault(o.goal().name(), 0)) : 0;
				if (had > s.objCount[i]) {
					s.objCount[i] = had;
					s.questDirty = true;
				}
			}
			// Things the world decides: shrines reached, great powers dealt with.
			for (int i = 0; i < c.objectives.size(); i++) {
				if (!open(s, i)) {
					continue;
				}
				switch (c.objectives.get(i).goal()) {
					case SHRINE -> {
						Site shrine = nearestShrine(data, p.getX(), p.getZ());
						if (shrine != null && level.dimension() == Level.OVERWORLD && horizontal(p, shrine.x, shrine.z) < 12) {
							finish(p, s, c, i);
						}
					}
					case BOSS_BELL -> {
						if (data.bell.state != BossStates.BellRinger.WAITING) {
							finish(p, s, c, i);
						}
					}
					case BOSS_KING -> {
						if (data.vault.king != BossStates.Vault.KING_WAITING) {
							finish(p, s, c, i);
						}
					}
					case BOSS_PILGRIM -> {
						if (data.pilgrim.state == BossStates.Pilgrim.DEFEATED || data.pilgrim.state == BossStates.Pilgrim.AT_PEACE) {
							finish(p, s, c, i);
						}
					}
					case BOSS_SHOGUN, BOSS_LICH, BOSS_COLOSSUS, BOSS_SENTINEL, BOSS_WARDEN -> {
						int bit = 1 << (c.objectives.get(i).goal().ordinal() - Chapter.Goal.BOSS_SHOGUN.ordinal());
						if ((data.wardensDefeated & bit) != 0) {
							finish(p, s, c, i);
						}
					}
					default -> {
					}
				}
				if (s.chapter != c.ordinal()) {
					return;
				}
			}
			if (now - s.targetsAt >= 60) {
				s.targetsAt = now;
				targets(p, s, c, data);
			}
		}
		// The tracked lead changing (heard, followed, settled) changes the tracker.
		RumourEntry lead = s.rumour(s.trackedRumour);
		int leadHash = lead != null && lead.active() ? lead.id : -1;
		if (leadHash != s.lastSyncHash) {
			s.lastSyncHash = leadHash;
			s.questDirty = true;
		}
		// A mark is reached once you are standing near it.
		if (s.hasMark && s.markDim.equals(level.dimension().identifier().toString()) && horizontal(p, s.markX, s.markZ) < 24) {
			s.hasMark = false;
			s.questDirty = true;
			Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.mark_reached", Component.literal(s.markLabel)));
		}
		sync(p, s);
	}

	private static double horizontal(ServerPlayer p, double x, double z) {
		double dx = p.getX() - (x + 0.5);
		double dz = p.getZ() - (z + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Works out where each open objective points. */
	private static void targets(ServerPlayer p, PlayerState s, Chapter c, AfterworldData data) {
		MinecraftServer server = p.level().getServer();
		for (int i = 0; i < c.objectives.size() && i < s.objTarget.length; i++) {
			long before = s.objTarget[i];
			s.objTarget[i] = NOWHERE;
			s.objTargetSurface[i] = false;
			if (!open(s, i)) {
				continue;
			}
			Chapter.Objective o = c.objectives.get(i);
			BlockPos at = null;
			boolean surface = false;
			switch (o.goal()) {
				case TALK -> {
					Npc npc = Npc.byId(o.arg());
					at = npc == null ? null : Npcs.home(server, npc);
				}
				case ATTUNE_CAMP -> {
					for (AfterworldData.Waystone w : data.waystones.values()) {
						if (w.camp) {
							at = w.blockPos();
						}
					}
					if (at == null && data.campCenter != Long.MIN_VALUE) {
						at = BlockPos.of(data.campCenter);
					}
				}
				case ATTUNE_LANDMARK -> {
					at = landmark(p, s);
					surface = true;
				}
				case ANOMALY -> {
					Site site = anomalyFor(p, data);
					if (site != null) {
						at = site.pos();
						surface = !site.built();
					}
				}
				case SHRINE -> {
					Site site = nearestShrine(data, p.getX(), p.getZ());
					if (site != null) {
						at = site.pos();
						surface = !site.built();
					}
				}
				case BOSS_BELL -> {
					Site site = data.sites.get(data.bell.site);
					if (site != null) {
						at = site.pos();
						surface = !site.built();
					}
				}
				case BOSS_KING -> {
					Site site = data.sites.get(data.vault.site);
					if (site != null) {
						at = site.pos();
						surface = true;
					}
				}
				case BOSS_PILGRIM -> {
					at = BlockPos.containing(data.pilgrim.x, data.pilgrim.y, data.pilgrim.z);
					surface = true;
				}
				case BOSS_SHOGUN, BOSS_LICH, BOSS_COLOSSUS, BOSS_SENTINEL, BOSS_WARDEN -> {
					String lair = o.goal().name().substring(5).toLowerCase(java.util.Locale.ROOT);
					at = dev.afterworld.world.atlas.Atlas.lair(lair);
					if (at == null) {
						at = structure(p, s, "lair/" + lair);
					}
					surface = true;
				}
				default -> {
				}
			}
			if (at != null) {
				s.objTarget[i] = at.asLong();
				s.objTargetSurface[i] = surface;
			}
			if (before != s.objTarget[i]) {
				s.questDirty = true;
			}
		}
	}

	/**
	 * The nearest structure in tag {@code afterworld:<tag>}, searched for at most every two minutes
	 * per tag (or after a long walk), so chasing an objective never stalls the server.
	 */
	public static BlockPos structure(ServerPlayer p, PlayerState s, String tag) {
		long now = p.level().getGameTime();
		BlockPos here = p.blockPosition();
		long[] c = s.structureCache.get(tag);
		boolean moved = c == null || BlockPos.of(c[0]).distSqr(here) > 256 * 256;
		if (p.level().dimension() == Level.OVERWORLD && (moved || now - c[2] > 20 * 120 && c[1] == Long.MIN_VALUE)) {
			BlockPos found = p.level().findNearestMapStructure(TagKey.create(Registries.STRUCTURE, Afterworld.id(tag)), here, 48, false);
			c = new long[] {here.asLong(), found == null ? Long.MIN_VALUE : found.asLong(), now};
			s.structureCache.put(tag, c);
		}
		return c == null || c[1] == Long.MIN_VALUE ? null : BlockPos.of(c[1]);
	}

	/** The nearest landmark, searched for at most every half minute (or after a long walk). */
	private static BlockPos landmark(ServerPlayer p, PlayerState s) {
		long now = p.level().getGameTime();
		BlockPos here = p.blockPosition();
		boolean moved = s.landmarkFrom == Long.MIN_VALUE || BlockPos.of(s.landmarkFrom).distSqr(here) > 256 * 256;
		if (p.level().dimension() == Level.OVERWORLD && (moved || now - s.landmarkAt > 20 * 120 && s.landmarkFound == Long.MIN_VALUE)) {
			s.landmarkAt = now;
			s.landmarkFrom = here.asLong();
			BlockPos found = p.level().findNearestMapStructure(LANDMARKS, here, 40, false);
			s.landmarkFound = found == null ? Long.MIN_VALUE : found.asLong();
		}
		return s.landmarkFound == Long.MIN_VALUE ? null : BlockPos.of(s.landmarkFound);
	}

	/** The loose Edict this traveller should chase: the nearest open anomaly, or a new one not too far off. */
	private static Site anomalyFor(ServerPlayer p, AfterworldData data) {
		String dim = "minecraft:overworld";
		Site best = Sites.nearestOpen(data, SiteKind.ANOMALY, dim, p.getX(), p.getZ());
		if (best != null && horizontal(p, best.x, best.z) < 700) {
			return best;
		}
		if (p.level().dimension() != Level.OVERWORLD) {
			return best;
		}
		Edict e = WorldSim.anomalyEdict(p.getRandom());
		Site made = Sites.createNear(p.level().getServer(), SiteKind.ANOMALY, p.getBlockX(), p.getBlockZ(), 130, 260, p.getRandom(), -1,
			Faction.BUREAU.ordinal(), e.ordinal());
		return made != null ? made : best;
	}

	private static Site nearestShrine(AfterworldData data, double x, double z) {
		Site best = null;
		double bd = Double.MAX_VALUE;
		for (int id : data.pilgrim.shrines) {
			Site site = data.sites.get(id);
			if (site != null) {
				double d = (site.x - x) * (site.x - x) + (site.z - z) * (site.z - z);
				if (d < bd) {
					bd = d;
					best = site;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ the tracker

	/** Sends the tracker if anything in it changed. */
	public static void sync(ServerPlayer p, PlayerState s) {
		if (!s.questDirty || !ServerPlayNetworking.canSend(p, AwPayloads.QuestSync.TYPE)) {
			return;
		}
		s.questDirty = false;
		List<AwPayloads.QuestSync.Entry> entries = new ArrayList<>();
		Chapter c = chapter(s);
		String dim = "minecraft:overworld";
		if (c != null && !s.questFinished) {
			for (int i = 0; i < c.objectives.size(); i++) {
				Chapter.Objective o = c.objectives.get(i);
				boolean open = open(s, i);
				boolean done = done(s, i);
				if (!open && !done) {
					continue;
				}
				long at = i < s.objTarget.length ? s.objTarget[i] : NOWHERE;
				boolean has = open && at != NOWHERE;
				BlockPos bp = has ? BlockPos.of(at) : BlockPos.ZERO;
				entries.add(new AwPayloads.QuestSync.Entry(AwPayloads.QuestSync.MAIN, Component.translatable("quest.afterworld.obj." + o.id()),
					s.objCount[i], o.count(), done, has, bp.getX(), has && s.objTargetSurface[i] ? Integer.MIN_VALUE : bp.getY(), bp.getZ(), dim));
			}
		}
		if (s.hasMark) {
			entries.add(new AwPayloads.QuestSync.Entry(AwPayloads.QuestSync.MARK, Component.literal(s.markLabel), 0, 0, false, true,
				s.markX, s.markY, s.markZ, s.markDim));
		}
		entries.addAll(SideQuests.entries(s));
		entries.addAll(dev.afterworld.economy.Board.entries(s));
		RumourEntry lead = s.rumour(s.trackedRumour);
		if (lead != null && lead.active()) {
			entries.add(new AwPayloads.QuestSync.Entry(AwPayloads.QuestSync.LEAD, Component.literal(lead.title), 0, 0, false, true,
				lead.x, Integer.MIN_VALUE, lead.z, lead.dim));
		}
		Component title = c == null || s.questFinished ? Component.translatable("quest.afterworld.finished")
			: Component.translatable("quest.afterworld.chapter." + c.id);
		Component head = c == null || s.questFinished ? Component.empty()
			: s.chapter == 0 ? Component.translatable("quest.afterworld.prologue")
			: Component.translatable("quest.afterworld.chapter.n", Component.translatable("quest.afterworld.numeral." + s.chapter));
		ServerPlayNetworking.send(p, new AwPayloads.QuestSync(head, title, entries));
	}
}
