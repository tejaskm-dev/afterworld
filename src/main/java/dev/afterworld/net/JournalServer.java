package dev.afterworld.net;

import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.BondState;
import dev.afterworld.player.BondType;
import dev.afterworld.player.Bonds;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Disciplines;
import dev.afterworld.player.LegendEntry;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.rumour.Rumours;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.ChronicleEntry;
import dev.afterworld.world.Faction;
import dev.afterworld.world.FactionState;
import dev.afterworld.world.Names;
import dev.afterworld.world.Settlement;
import dev.afterworld.world.Settlements;
import dev.afterworld.world.WorldEvent;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds journal pages on request and validates every journal action on the server. */
public final class JournalServer {
	public static final int TAB_EDICTS = 0;
	public static final int TAB_RUMOURS = 1;
	public static final int TAB_CHRONICLE = 2;
	public static final int TAB_WORLD = 3;
	public static final int TAB_CODEX = 4;

	/** Quest systems add their own choices (a = choice id). Return true if handled. */
	public static final List<ChoiceHandler> CHOICES = new ArrayList<>();

	@FunctionalInterface
	public interface ChoiceHandler {
		boolean handle(ServerPlayer player, int choice, int arg, String text);
	}

	public static final int CHOICE_CONTRACT = 1;
	public static final int CHOICE_ACCEPT_BOND = 2;
	public static final int CHOICE_DECLINE_BOND = 3;

	private JournalServer() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.JournalAction.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
	}

	private static void handle(ServerPlayer p, AwPayloads.JournalAction a) {
		PlayerState s = PlayerState.of(p);
		int tab = TAB_EDICTS;
		switch (a.action()) {
			case AwPayloads.JournalAction.OPEN -> {
				tab = a.a();
				dev.afterworld.quest.Quests.onJournal(p);
			}
			case AwPayloads.JournalAction.EQUIP -> {
				equip(p, s, a.a(), Edict.byOrdinal(a.b()));
				tab = TAB_EDICTS;
			}
			case AwPayloads.JournalAction.CHOOSE_DISCIPLINE -> {
				Disciplines.choose(p, Discipline.byOrdinal(a.a()));
				tab = TAB_EDICTS;
			}
			case AwPayloads.JournalAction.TRACK_RUMOUR -> {
				RumourEntry r = s.rumour(a.a());
				if (r != null && r.active()) {
					s.trackedRumour = r.id;
					s.questDirty = true;
				}
				tab = TAB_RUMOURS;
			}
			case AwPayloads.JournalAction.ABANDON_RUMOUR -> {
				RumourEntry r = s.rumour(a.a());
				if (r != null && r.active()) {
					r.state = RumourEntry.ABANDONED;
					r.outcome = "Abandoned.";
					if (s.trackedRumour == r.id) {
						s.trackedRumour = -1;
					}
					s.questDirty = true;
				}
				tab = TAB_RUMOURS;
			}
			case AwPayloads.JournalAction.CLAIM_BOON -> {
				claimBoon(p, s, Faction.byOrdinal(a.a()));
				tab = TAB_WORLD;
			}
			case AwPayloads.JournalAction.PAY_BOND -> {
				BondType type = BondType.byOrdinal(a.a());
				BondState b = type == null ? null : s.bond(type);
				if (b != null && !Bonds.pay(p, s, b)) {
					p.sendOverlayMessage(Component.translatable("message.afterworld.bond_cannot_pay." + type.id).withColor(0xC4553B));
				}
				tab = TAB_WORLD;
			}
			case AwPayloads.JournalAction.RELEASE_BOND -> {
				BondType type = BondType.byOrdinal(a.a());
				if (type != null) {
					Bonds.release(p, type);
				}
				tab = TAB_WORLD;
			}
			case AwPayloads.JournalAction.RESTORE_PAGE -> {
				tab = TAB_CHRONICLE;
				restorePage(p, a.s());
			}
			case AwPayloads.JournalAction.CHOICE -> {
				tab = TAB_WORLD;
				switch (a.a()) {
					case CHOICE_CONTRACT -> {
						Faction f = Faction.byOrdinal(a.b());
						if (f != null) {
							Rumours.contract(p, f);
						}
						tab = TAB_RUMOURS;
					}
					case CHOICE_ACCEPT_BOND -> {
						BondType type = BondType.byOrdinal(a.b());
						if (type != null) {
							Bonds.accept(p, type);
						}
					}
					case CHOICE_DECLINE_BOND -> Bonds.decline(p);
					default -> {
						for (ChoiceHandler h : CHOICES) {
							if (h.handle(p, a.a(), a.b(), a.s())) {
								break;
							}
						}
					}
				}
			}
			default -> {
				return;
			}
		}
		send(p, tab);
	}

	private static void equip(ServerPlayer p, PlayerState s, int slot, Edict e) {
		if (slot < 0 || slot >= s.slotCount()) {
			return;
		}
		if (e == null) {
			s.slots[slot] = null;
		} else {
			if (!s.learned.contains(e)) {
				return;
			}
			for (int i = 0; i < 4; i++) {
				if (s.slots[i] == e) {
					s.slots[i] = s.slots[slot];
				}
			}
			s.slots[slot] = e;
		}
		s.hudDirty = true;
		EdictSystem.syncHud(p, s);
	}

	private static void claimBoon(ServerPlayer p, PlayerState s, Faction f) {
		if (f == null) {
			return;
		}
		int tier = Faction.tier(s.rep(f));
		int claimed = s.boonTier[f.ordinal()];
		if (tier <= claimed || tier < 1) {
			return;
		}
		int next = claimed + 1;
		s.boonTier[f.ordinal()] = next;
		List<ItemStack> gifts = new ArrayList<>();
		switch (f) {
			case CROWN -> {
				if (next == 1) {
					gifts.add(new ItemStack(Items.EMERALD, 12));
					gifts.add(new ItemStack(Items.GOLDEN_APPLE, 2));
				} else if (next == 2) {
					gifts.add(new ItemStack(AwItems.APOLOGETIC_BLADE));
				} else {
					gifts.add(new ItemStack(AwItems.fragment(Edict.BORROWED_TIME)));
				}
			}
			case GUILD -> {
				if (next == 1) {
					gifts.add(new ItemStack(AwItems.TORN_MAP, 3));
					gifts.add(new ItemStack(AwItems.CHRONICLE_PAGE, 4));
				} else if (next == 2) {
					gifts.add(new ItemStack(AwItems.UNFINISHED_COMPASS));
				} else {
					gifts.add(new ItemStack(AwItems.fragment(Edict.TRESPASS)));
				}
			}
			case COURT -> {
				if (next == 1) {
					gifts.add(new ItemStack(AwItems.GRAVE_WAX, 4));
					gifts.add(new ItemStack(AwItems.PILGRIM_OFFERING, 2));
				} else if (next == 2) {
					gifts.add(new ItemStack(AwItems.HUNGRY_LANTERN));
				} else {
					gifts.add(new ItemStack(AwItems.fragment(Edict.HUNGER)));
					Bonds.offer(p, s, BondType.LANDLORD);
				}
			}
			case BUREAU -> {
				if (next == 1) {
					gifts.add(new ItemStack(AwItems.ANOMALY_RESIDUE, 8));
					gifts.add(new ItemStack(AwItems.THIMBLE_OF_BORROWED_RAIN));
				} else if (next == 2) {
					gifts.add(new ItemStack(AwItems.CLOCKWORK_HEART));
				} else {
					gifts.add(new ItemStack(AwItems.fragment(Edict.WITNESS)));
				}
			}
		}
		for (ItemStack g : gifts) {
			Inv.give(p, g);
		}
		p.sendSystemMessage(Component.translatable("message.afterworld.boon", f.displayName()).withColor(f.color));
		Progress.legend(p, "reputation", "Received a boon from " + f.displayName().getString() + ".");
	}

	// ------------------------------------------------------------------ pages

	public static void send(ServerPlayer p, int tab) {
		if (!ServerPlayNetworking.canSend(p, AwPayloads.Journal.TYPE)) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		CompoundTag t = new CompoundTag();
		t.putInt("discipline", s.discipline.ordinal());
		t.putString("origin", s.origin.id);
		t.putString("name", s.travellerName);
		t.putInt("affinity", s.affinity != null ? s.affinity.ordinal() : -1);
		t.putInt("slotCount", s.slotCount());
		t.putInt("pendingBond", s.pendingBondUntil > p.level().getGameTime() ? s.pendingBond : -1);
		switch (tab) {
			case TAB_EDICTS -> edicts(p, s, t);
			case TAB_RUMOURS -> rumours(p, s, t);
			case TAB_CHRONICLE -> chronicle(p, s, t);
			case TAB_WORLD -> world(p, s, t);
			case TAB_CODEX -> codex(s, t);
			default -> {
			}
		}
		ServerPlayNetworking.send(p, new AwPayloads.Journal(tab, t));
	}

	private static void edicts(ServerPlayer p, PlayerState s, CompoundTag t) {
		int learned = 0;
		for (Edict e : s.learned) {
			learned |= 1 << e.ordinal();
		}
		t.putInt("learned", learned);
		int[] slots = new int[4];
		for (int i = 0; i < 4; i++) {
			slots[i] = s.slots[i] == null ? -1 : s.slots[i].ordinal();
		}
		t.putIntArray("slots", slots);
		int[] cds = new int[Edict.COUNT];
		for (Edict e : Edict.VALUES) {
			cds[e.ordinal()] = EdictSystem.cooldownFor(s, e);
		}
		t.putIntArray("cooldowns", cds);
		t.putInt("changes", s.disciplineChanges);
		t.putInt("levels", p.experienceLevel);
	}

	private static void rumours(ServerPlayer p, PlayerState s, CompoundTag t) {
		ListTag list = new ListTag();
		List<RumourEntry> ordered = new ArrayList<>();
		for (RumourEntry r : s.rumours) {
			if (r.active()) {
				ordered.add(r);
			}
		}
		for (int i = s.rumours.size() - 1; i >= 0 && ordered.size() < 24; i--) {
			RumourEntry r = s.rumours.get(i);
			if (!r.active()) {
				ordered.add(r);
			}
		}
		String dim = p.level().dimension().identifier().toString();
		for (RumourEntry r : ordered) {
			CompoundTag e = new CompoundTag();
			e.putInt("id", r.id);
			e.putString("kind", r.kind.id);
			e.putString("title", r.title);
			e.putString("text", r.text);
			e.putString("source", r.source);
			e.putInt("confidence", r.confidence());
			e.putInt("state", r.state);
			e.putString("outcome", r.outcome);
			e.putLong("day", r.day);
			e.putInt("faction", r.faction);
			if (!r.active()) {
				e.putInt("reliability", r.reliability);
			}
			ListTag clues = new ListTag();
			for (String c : r.clues) {
				if (!c.equals("arrived")) {
					clues.add(StringTag.valueOf(c));
				}
			}
			e.put("clues", clues);
			e.putBoolean("arrived", r.clues.contains("arrived"));
			if (r.active() && r.dim.equals(dim)) {
				double dx = r.x - p.getX();
				double dz = r.z - p.getZ();
				e.putString("bearing", Names.direction(dx, dz));
				e.putString("distance", Names.distance(Math.sqrt(dx * dx + dz * dz)));
			}
			list.add(e);
		}
		t.put("rumours", list);
		t.putInt("tracked", s.trackedRumour);
		AfterworldData data = AfterworldData.get(p.level().getServer());
		ListTag contracts = new ListTag();
		for (Faction f : Faction.VALUES) {
			CompoundTag c = new CompoundTag();
			c.putInt("faction", f.ordinal());
			c.putString("leader", data.faction(f).leader);
			boolean active = false;
			for (RumourEntry r : s.rumours) {
				if (r.active() && r.faction == f.ordinal()) {
					active = true;
				}
			}
			c.putBoolean("active", active);
			c.putBoolean("refused", s.rep(f) <= -15);
			contracts.add(c);
		}
		t.put("contracts", contracts);
	}

	/** Three Chronicle Pages restore a missing page (and any other page torn by the same cause). */
	private static void restorePage(ServerPlayer p, String cause) {
		int need = 3;
		var inv = p.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(dev.afterworld.registry.AwItems.CHRONICLE_PAGE)) {
				have += inv.getItem(i).getCount();
			}
		}
		if (have < need && !p.isCreative()) {
			p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.afterworld.restore_need", need).withColor(0xB0A8A0));
			return;
		}
		int restored = dev.afterworld.world.Chronicle.reveal(p, cause);
		if (restored == 0) {
			return;
		}
		if (!p.isCreative()) {
			for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
				var stack = inv.getItem(i);
				if (stack.is(dev.afterworld.registry.AwItems.CHRONICLE_PAGE)) {
					int take = Math.min(need, stack.getCount());
					stack.shrink(take);
					need -= take;
				}
			}
		}
		dev.afterworld.util.Fx.sound(p.level(), p.position(), dev.afterworld.registry.AwSounds.UI_CHRONICLE, 1.0F, 1.0F);
	}

	private static void chronicle(ServerPlayer p, PlayerState s, CompoundTag t) {
		AfterworldData data = AfterworldData.get(p.level().getServer());
		ListTag pub = new ListTag();
		ListTag missing = new ListTag();
		for (int i = data.chronicle.size() - 1; i >= 0; i--) {
			ChronicleEntry e = data.chronicle.get(i);
			CompoundTag c = new CompoundTag();
			c.putLong("day", e.day);
			c.putString("title", e.title);
			c.putString("text", e.text);
			c.putString("place", e.place);
			c.putInt("faction", e.faction);
			c.putString("actor", e.actor);
			c.putInt("certainty", e.certainty);
			if (e.missing) {
				c.putString("cause", e.cause);
				if (missing.size() < 40) {
					missing.add(c);
				}
			} else if (pub.size() < 80) {
				pub.add(c);
			}
		}
		t.put("public", pub);
		t.put("missing", missing);
		ListTag legend = new ListTag();
		for (int i = s.legend.size() - 1; i >= 0 && legend.size() < 60; i--) {
			LegendEntry l = s.legend.get(i);
			CompoundTag c = new CompoundTag();
			c.putLong("day", l.day());
			c.putString("kind", l.kind());
			c.putString("text", l.text());
			legend.add(c);
		}
		t.put("legend", legend);
		t.putLong("today", Progress.day(p));
	}

	private static void world(ServerPlayer p, PlayerState s, CompoundTag t) {
		AfterworldData data = AfterworldData.get(p.level().getServer());
		ListTag factions = new ListTag();
		for (FactionState fs : data.factions) {
			CompoundTag c = new CompoundTag();
			int f = fs.faction.ordinal();
			c.putInt("faction", f);
			c.putInt("influence", fs.influence);
			c.putString("leader", fs.leader);
			c.putString("op", fs.operation);
			c.putInt("rep", s.rep[f]);
			c.putInt("claimed", s.boonTier[f]);
			factions.add(c);
		}
		t.put("factions", factions);
		ListTag bonds = new ListTag();
		long day = Progress.day(p);
		for (BondState b : s.bonds) {
			CompoundTag c = new CompoundTag();
			c.putInt("type", b.type.ordinal());
			c.putLong("due", b.dueDay() - day);
			c.putBoolean("complication", b.complication);
			c.putInt("counter", b.counter);
			bonds.add(c);
		}
		t.put("bonds", bonds);
		Settlement here = Settlements.at(p.level(), p.blockPosition());
		if (here == null) {
			here = Settlements.nearest(data, p.level().dimension().identifier().toString(), p.getX(), p.getZ(), 400);
		}
		if (here != null) {
			t.put("settlement", settlement(here));
		}
		ListTag known = new ListTag();
		for (Settlement st : data.settlements.values()) {
			if (known.size() < 24) {
				known.add(settlement(st));
			}
		}
		t.put("known", known);
		ListTag events = new ListTag();
		for (WorldEvent e : data.events) {
			if (e.active) {
				CompoundTag c = new CompoundTag();
				c.putString("kind", e.kind);
				Settlement st = data.settlementById(e.settlement);
				c.putString("place", st == null ? "" : st.name);
				c.putLong("left", e.endDay - day);
				events.add(c);
			}
		}
		t.put("events", events);
	}

	private static CompoundTag settlement(Settlement st) {
		CompoundTag c = new CompoundTag();
		c.putString("name", st.name);
		c.putString("leader", st.leader);
		c.putInt("patron", st.patron);
		c.putInt("safety", st.safety);
		c.putInt("prosperity", st.prosperity);
		c.putInt("legitimacy", st.legitimacy);
		c.putInt("anomaly", st.anomaly);
		c.putInt("state", st.state);
		c.putString("words", st.safetyWord() + " · " + st.prosperityWord() + " · " + st.legitimacyWord() + " · " + st.anomalyWord());
		return c;
	}

	private static void codex(PlayerState s, CompoundTag t) {
		ListTag list = new ListTag();
		for (String c : s.codex) {
			list.add(StringTag.valueOf(c));
		}
		t.put("codex", list);
	}
}
