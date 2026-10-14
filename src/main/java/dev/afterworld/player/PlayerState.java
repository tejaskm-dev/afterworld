package dev.afterworld.player;

import com.mojang.serialization.Codec;
import dev.afterworld.edict.Edict;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything Afterworld remembers about one player. Mutable and owned by the player entity via a
 * persistent attachment that survives death. Fields marked "runtime" are not saved.
 */
public final class PlayerState {
	public static final Codec<PlayerState> CODEC = CompoundTag.CODEC.xmap(PlayerState::load, PlayerState::save);
	public static final int DATA_VERSION = 1;
	public static final int MAX_LEGEND = 150;

	/** Coarse behaviour categories the adaptive systems observe. */
	public static final int TACTIC_MELEE = 0;
	public static final int TACTIC_RANGED = 1;
	public static final int TACTIC_STEALTH = 2;
	public static final int TACTIC_EDICT_BASE = 3;
	public static final int TACTIC_COUNT = TACTIC_EDICT_BASE + Edict.COUNT;

	public Discipline discipline = Discipline.NONE;
	public int disciplineChanges;
	public final EnumSet<Edict> learned = EnumSet.noneOf(Edict.class);
	public final Edict[] slots = new Edict[4];
	public final long[] cooldownUntil = new long[Edict.COUNT];
	public long signatureReadyAt;

	public final Set<String> codex = new LinkedHashSet<>();
	public final List<RumourEntry> rumours = new ArrayList<>();
	public int nextRumourId = 1;
	public int trackedRumour = -1;

	public final int[] rep = new int[Faction.COUNT];
	public final int[] boonTier = new int[Faction.COUNT];

	public final List<BondState> bonds = new ArrayList<>();
	public final List<LegendEntry> legend = new ArrayList<>();
	public final int[] tactics = new int[TACTIC_COUNT];

	public boolean journalGiven;
	public boolean introShown;
	public Origin origin = Origin.NONE;
	/** The Edict the traveller said lies within them: its cooldown is a fifth shorter. */
	public Edict affinity;
	/** What the traveller called themselves on arrival (empty: their account name). */
	public String travellerName = "";
	/** Set once the traveller has finished (or skipped) the arrival screen. */
	public boolean arrived;
	public long oathbreakerUntil;
	public int favourOwed;
	public long favourDeadline;
	public int lanternTicks;
	public int lastSettlementKey;
	public int pendingBond = -1;
	public long pendingBondUntil;

	// ---- progression
	/** Renown earned in total (the level is derived), unspent Insight and the skills bought with it. */
	public long renown;
	public int insight;
	public final Set<String> skills = new LinkedHashSet<>();

	// ---- the economy
	/** Marks carried (lost in part on death) and banked (safe in the Guild Ledger). */
	public long marks;
	public long banked;
	public long lifetimeEarned;
	public long interestDay = -1;
	/** Today's bargain with each merchant: day * 1000 + (500 + percent change). */
	public final java.util.Map<String, Long> haggles = new java.util.HashMap<>();
	/** Prologue trading lesson: whether the traveller has sold and bought yet. */
	public boolean soldOnce;
	public boolean boughtOnce;

	// ---- powers and movement
	/** The Aspect equipped (its id; empty: none), every Aspect bound, and the Resonance built toward it. */
	public String aspect = "";
	public final Set<String> aspects = new LinkedHashSet<>();
	public float resonance;
	/** Gifts learned: one bit per {@link dev.afterworld.movement.Gifts.Gift}. */
	public int gifts;
	/** Gifts lent for the length of a trial (the practice glide, the walk on water); never saved. */
	public int trialGifts;
	/** Each Edict's rank (1-3) by id; absent: rank 1. */
	public final java.util.Map<String, Integer> edictRank = new java.util.HashMap<>();
	/** Contracts taken from Warden's Boards (at most three at a time). */
	public final java.util.List<dev.afterworld.economy.Contract> contracts = new java.util.ArrayList<>();

	// ---- the Path (main questline)
	/** The chapter of the main story the traveller is in (see {@link dev.afterworld.quest.Chapter}). */
	public int chapter;
	/** Objectives finished in the current chapter (bit per objective) and counters for counted ones. */
	public int objDone;
	public final int[] objCount = new int[12];
	public boolean questFinished;
	/** Things said and done in conversations ("met:isolde", "gift:bram"). */
	public final Set<String> flags = new LinkedHashSet<>();
	/**
	 * What the traveller has done, counted whether or not the story had asked for it yet: goals by
	 * name ("CAST", "TRADE") and objectives talked through ahead of time ("obj:meet_bram"). An
	 * objective that opens later finds these and counts at once, so nothing done early is lost.
	 */
	public final java.util.Map<String, Integer> deeds = new java.util.HashMap<>();
	/** The Warden's Ladder: the highest floor cleared, the floor a climb may be taken up again from
	 * (1, then the floor after each landing or echo), and where the traveller stepped in from. */
	public int ladderBest;
	public int ladderResume = 1;
	public String ladderReturnDim = "";
	public long ladderReturnPos;
	/** The keepers' questlines: stage per questline id (absent: not started; 99: done), newest first. */
	public final java.util.Map<String, Integer> side = new java.util.HashMap<>();
	public final java.util.List<String> recentSide = new java.util.ArrayList<>();
	/** Waystones this traveller has touched (ids in the world's waystone list). */
	public final it.unimi.dsi.fastutil.ints.IntArrayList attuned = new it.unimi.dsi.fastutil.ints.IntArrayList();
	public long travelReadyAt;
	/** A charter flight is on offer until this tick (runtime). */
	public long charterUntil;
	/** A place someone marked on the traveller's map: a keeper's tip, followed like an objective. */
	public boolean hasMark;
	public int markX;
	public int markY = Integer.MIN_VALUE;
	public int markZ;
	public String markDim = "minecraft:overworld";
	public String markLabel = "";

	// ---- runtime ----
	public boolean hudDirty = true;
	/** While choosing an origin and garb, the world leaves the traveller alone. */
	public long arrivingUntil;
	public long echoWindowUntil;
	public long pactUntil;
	public long forgottenUntil;
	public long revealedUntil;
	public long hungerUntil;
	// Rank III capstones in force for the current cast of an Edict (see edict.EdictEffects).
	/** Edicts the Echoes window will still repeat (one, or two at Rank III). */
	public int echoEdicts;
	public boolean echoCapstone;
	public boolean hungerCapstone;
	/** Hospitality III: guests fight beside you, so only striking a guest breaks the bargain. */
	public boolean pactAllies;
	public boolean forgottenCapstone;
	public boolean forgottenStrikeUsed;
	public boolean forgottenBonusPending;
	public int suppressedMask;
	public long suppressedUntil;
	public Edict lastCast;
	public long lastCastTime;
	public final long[] castAt = new long[Edict.COUNT];
	/** One snapshot per second for Borrowed Time; index = (second % 6). */
	public final Snapshot[] history = new Snapshot[6];
	public double wrongTurnX;
	public double wrongTurnY;
	public double wrongTurnZ;
	public long wrongTurnUntil;
	public String wrongTurnDim = "";
	public long heartUsedAt = -10000;
	public long thimbleUntil;
	/** The quest tracker needs resending. */
	public boolean questDirty = true;
	/** Where each open objective points, refreshed every few seconds (packed block pos; MIN_VALUE: nowhere). */
	public final long[] objTarget = new long[12];
	public final boolean[] objTargetSurface = new boolean[12];
	public long targetsAt = -1000;
	/** The last landmark search: where it was made from and what it found. */
	public long landmarkFrom = Long.MIN_VALUE;
	public long landmarkFound = Long.MIN_VALUE;
	public long landmarkAt = -100000;
	/** Structure searches by tag: {from, found (MIN_VALUE: none), when}. */
	public final java.util.Map<String, long[]> structureCache = new java.util.HashMap<>();
	public int lastSyncHash;
	/** Stamina (-1: not yet filled), what the client last heard, and when it was last spent or ran dry. */
	public float stamina = -1;
	public float staminaSent = -1;
	public float staminaMaxSent;
	public long staminaSpentAt;
	public long staminaEmptyAt;
	/** When Second Wind last came (game time; not saved). */
	public long secondWindAt = -100_000;
	public long staminaBoostUntil;
	/** Moves in progress, as the client reports them. */
	public boolean gliding;
	public boolean climbing;
	public boolean waterWalking;
	public long slideUntil;
	/** Dodge invulnerability, the parry window and the next parry, and whether this dodge was already perfect. */
	public long dodgeUntil;
	public boolean dodgePerfect;
	public long parryUntil;
	public long parryReadyAt;
	/** No fall damage until (grapples, launches, the skyward bell). */
	public long safeFallUntil;
	/** Art stances: Riposte's counter window and the Aegis beam's guard. */
	public long riposteUntil;
	public long guardUntil;
	/** Aspect effects in progress. */
	public long lifestealUntil;
	public long reflectUntil;
	/** The Ring of Second Breath can save you again after this tick. */
	public long secondBreathAt;
	/** The Vanguard's Last Word may save them again after this tick. */
	public long lastWordAt;
	/** Wearing the whole Starweave set (Edict cooldowns a fifth shorter). */
	public boolean starweave;
	/** The Duskstrider answering this traveller's whistle. */
	public java.util.UUID mount;
	/** Hits landed in a row on the same creature (for flourishes and drum beats). */
	public int comboTarget;
	public int combo;
	public long comboAt;

	public record Snapshot(long time, String dim, double x, double y, double z, float yRot, float xRot, float health, int food, float saturation, int fire) {
	}

	public static PlayerState of(ServerPlayer player) {
		return player.getAttachedOrCreate(AwAttachments.PLAYER);
	}

	// ------------------------------------------------------------------ edicts

	public int slotCount() {
		return discipline.slotCount();
	}

	public boolean isEquipped(Edict e) {
		for (int i = 0; i < slotCount(); i++) {
			if (slots[i] == e) {
				return true;
			}
		}
		return false;
	}

	/** Places an edict into the first free slot. Returns false when every slot is taken. */
	public boolean autoEquip(Edict e) {
		if (isEquipped(e)) {
			return true;
		}
		for (int i = 0; i < slotCount(); i++) {
			if (slots[i] == null) {
				slots[i] = e;
				hudDirty = true;
				return true;
			}
		}
		return false;
	}

	public void tactic(int index, int amount) {
		if (index >= 0 && index < TACTIC_COUNT) {
			tactics[index] = Math.min(100000, tactics[index] + amount);
		}
	}

	// ------------------------------------------------------------------ reputation

	public int rep(Faction f) {
		return rep[f.ordinal()];
	}

	/** Returns the old tier if it changed, otherwise {@code Integer.MIN_VALUE}. */
	public int addRep(Faction f, int amount) {
		int before = rep[f.ordinal()];
		int after = Math.max(-100, Math.min(100, before + amount));
		rep[f.ordinal()] = after;
		int t0 = Faction.tier(before);
		return Faction.tier(after) != t0 ? t0 : Integer.MIN_VALUE;
	}

	// ------------------------------------------------------------------ bonds

	public BondState bond(BondType type) {
		for (BondState b : bonds) {
			if (b.type == type) {
				return b;
			}
		}
		return null;
	}

	public boolean hasBond(BondType type) {
		return bond(type) != null;
	}

	// ------------------------------------------------------------------ rumours

	public RumourEntry rumour(int id) {
		for (RumourEntry r : rumours) {
			if (r.id == id) {
				return r;
			}
		}
		return null;
	}

	public int activeRumourCount() {
		int n = 0;
		for (RumourEntry r : rumours) {
			if (r.active()) {
				n++;
			}
		}
		return n;
	}

	public boolean hasActiveRumourForSite(int siteId) {
		for (RumourEntry r : rumours) {
			if (r.active() && r.siteId == siteId) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ legend

	public void addLegend(long day, String kind, String text) {
		legend.add(new LegendEntry(day, kind, text));
		while (legend.size() > MAX_LEGEND) {
			legend.remove(0);
		}
	}

	// ------------------------------------------------------------------ persistence

	private CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("v", DATA_VERSION);
		t.putString("discipline", discipline.id);
		t.putInt("disciplineChanges", disciplineChanges);
		ListTag learnedTag = new ListTag();
		for (Edict e : learned) {
			learnedTag.add(StringTag.valueOf(e.id));
		}
		t.put("learned", learnedTag);
		ListTag slotTag = new ListTag();
		for (Edict e : slots) {
			slotTag.add(StringTag.valueOf(e == null ? "" : e.id));
		}
		t.put("slots", slotTag);
		CompoundTag cd = new CompoundTag();
		for (Edict e : Edict.VALUES) {
			if (cooldownUntil[e.ordinal()] > 0) {
				cd.putLong(e.id, cooldownUntil[e.ordinal()]);
			}
		}
		t.put("cooldowns", cd);
		t.putLong("signatureReadyAt", signatureReadyAt);
		ListTag codexTag = new ListTag();
		for (String s : codex) {
			codexTag.add(StringTag.valueOf(s));
		}
		t.put("codex", codexTag);
		ListTag rumourTag = new ListTag();
		for (RumourEntry r : rumours) {
			rumourTag.add(r.save());
		}
		t.put("rumours", rumourTag);
		t.putInt("nextRumourId", nextRumourId);
		t.putInt("trackedRumour", trackedRumour);
		CompoundTag repTag = new CompoundTag();
		CompoundTag boonTag = new CompoundTag();
		for (Faction f : Faction.VALUES) {
			repTag.putInt(f.id, rep[f.ordinal()]);
			boonTag.putInt(f.id, boonTier[f.ordinal()]);
		}
		t.put("rep", repTag);
		t.put("boons", boonTag);
		ListTag bondTag = new ListTag();
		for (BondState b : bonds) {
			bondTag.add(b.save());
		}
		t.put("bonds", bondTag);
		ListTag legendTag = new ListTag();
		for (LegendEntry l : legend) {
			legendTag.add(l.save());
		}
		t.put("legend", legendTag);
		t.putIntArray("tactics", tactics);
		t.putBoolean("journalGiven", journalGiven);
		t.putBoolean("introShown", introShown);
		t.putString("origin", origin.id);
		if (affinity != null) {
			t.putString("affinity", affinity.id);
		}
		t.putString("travellerName", travellerName);
		t.putBoolean("arrived", arrived);
		t.putLong("oathbreakerUntil", oathbreakerUntil);
		t.putInt("favourOwed", favourOwed);
		t.putLong("favourDeadline", favourDeadline);
		t.putInt("lanternTicks", lanternTicks);
		t.putLong("renown", renown);
		t.putInt("insight", insight);
		ListTag skillTag = new ListTag();
		for (String k : skills) {
			skillTag.add(StringTag.valueOf(k));
		}
		t.put("skills", skillTag);
		t.putLong("marks", marks);
		t.putLong("banked", banked);
		t.putLong("lifetimeEarned", lifetimeEarned);
		t.putLong("interestDay", interestDay);
		CompoundTag hag = new CompoundTag();
		for (var e : haggles.entrySet()) {
			hag.putLong(e.getKey(), e.getValue());
		}
		t.put("haggles", hag);
		t.putBoolean("soldOnce", soldOnce);
		t.putBoolean("boughtOnce", boughtOnce);
		t.putString("aspect", aspect);
		ListTag aspectTag = new ListTag();
		for (String a : aspects) {
			aspectTag.add(StringTag.valueOf(a));
		}
		t.put("aspects", aspectTag);
		t.putFloat("resonance", resonance);
		t.putInt("gifts", gifts);
		CompoundTag rankTag = new CompoundTag();
		for (var e : edictRank.entrySet()) {
			rankTag.putInt(e.getKey(), e.getValue());
		}
		t.put("edictRank", rankTag);
		ListTag contractTag = new ListTag();
		for (dev.afterworld.economy.Contract c : contracts) {
			contractTag.add(c.save());
		}
		t.put("contracts", contractTag);
		t.putInt("chapter", chapter);
		t.putInt("objDone", objDone);
		t.putIntArray("objCount", objCount);
		t.putBoolean("questFinished", questFinished);
		ListTag flagTag = new ListTag();
		for (String f : flags) {
			flagTag.add(StringTag.valueOf(f));
		}
		t.put("flags", flagTag);
		CompoundTag deedTag = new CompoundTag();
		for (var e : deeds.entrySet()) {
			deedTag.putInt(e.getKey(), e.getValue());
		}
		t.put("deeds", deedTag);
		t.putInt("ladderBest", ladderBest);
		t.putInt("ladderResume", ladderResume);
		t.putString("ladderReturnDim", ladderReturnDim);
		t.putLong("ladderReturnPos", ladderReturnPos);
		CompoundTag sideTag = new CompoundTag();
		for (var e : side.entrySet()) {
			sideTag.putInt(e.getKey(), e.getValue());
		}
		t.put("side", sideTag);
		ListTag recentTag = new ListTag();
		for (String r : recentSide) {
			recentTag.add(StringTag.valueOf(r));
		}
		t.put("recentSide", recentTag);
		t.putIntArray("attuned", attuned.toIntArray());
		t.putLong("travelReadyAt", travelReadyAt);
		if (hasMark) {
			CompoundTag m = new CompoundTag();
			m.putInt("x", markX);
			m.putInt("y", markY);
			m.putInt("z", markZ);
			m.putString("dim", markDim);
			m.putString("label", markLabel);
			t.put("mark", m);
		}
		return t;
	}

	private static PlayerState load(CompoundTag t) {
		PlayerState s = new PlayerState();
		String d = t.getStringOr("discipline", "none");
		for (Discipline disc : Discipline.VALUES) {
			if (disc.id.equals(d)) {
				s.discipline = disc;
			}
		}
		s.disciplineChanges = t.getIntOr("disciplineChanges", 0);
		ListTag learnedTag = t.getListOrEmpty("learned");
		for (int i = 0; i < learnedTag.size(); i++) {
			Edict e = Edict.byId(learnedTag.getStringOr(i, ""));
			if (e != null) {
				s.learned.add(e);
			}
		}
		ListTag slotTag = t.getListOrEmpty("slots");
		for (int i = 0; i < Math.min(4, slotTag.size()); i++) {
			Edict e = Edict.byId(slotTag.getStringOr(i, ""));
			s.slots[i] = e != null && s.learned.contains(e) ? e : null;
		}
		CompoundTag cd = t.getCompoundOrEmpty("cooldowns");
		for (Edict e : Edict.VALUES) {
			s.cooldownUntil[e.ordinal()] = cd.getLongOr(e.id, 0);
		}
		s.signatureReadyAt = t.getLongOr("signatureReadyAt", 0);
		ListTag codexTag = t.getListOrEmpty("codex");
		for (int i = 0; i < codexTag.size(); i++) {
			s.codex.add(codexTag.getStringOr(i, ""));
		}
		ListTag rumourTag = t.getListOrEmpty("rumours");
		for (int i = 0; i < rumourTag.size(); i++) {
			s.rumours.add(RumourEntry.load(rumourTag.getCompoundOrEmpty(i)));
		}
		s.nextRumourId = t.getIntOr("nextRumourId", 1);
		s.trackedRumour = t.getIntOr("trackedRumour", -1);
		CompoundTag repTag = t.getCompoundOrEmpty("rep");
		CompoundTag boonTag = t.getCompoundOrEmpty("boons");
		for (Faction f : Faction.VALUES) {
			s.rep[f.ordinal()] = repTag.getIntOr(f.id, 0);
			s.boonTier[f.ordinal()] = boonTag.getIntOr(f.id, 0);
		}
		ListTag bondTag = t.getListOrEmpty("bonds");
		for (int i = 0; i < bondTag.size(); i++) {
			BondState b = BondState.load(bondTag.getCompoundOrEmpty(i));
			if (b != null) {
				s.bonds.add(b);
			}
		}
		ListTag legendTag = t.getListOrEmpty("legend");
		for (int i = 0; i < legendTag.size(); i++) {
			s.legend.add(LegendEntry.load(legendTag.getCompoundOrEmpty(i)));
		}
		int[] tac = t.getIntArray("tactics").orElse(new int[0]);
		System.arraycopy(tac, 0, s.tactics, 0, Math.min(tac.length, TACTIC_COUNT));
		s.journalGiven = t.getBooleanOr("journalGiven", false);
		s.introShown = t.getBooleanOr("introShown", false);
		s.origin = Origin.byId(t.getStringOr("origin", "none"));
		s.affinity = Edict.byId(t.getStringOr("affinity", ""));
		s.travellerName = t.getStringOr("travellerName", "");
		s.arrived = t.getBooleanOr("arrived", false);
		s.oathbreakerUntil = t.getLongOr("oathbreakerUntil", 0);
		s.favourOwed = t.getIntOr("favourOwed", 0);
		s.favourDeadline = t.getLongOr("favourDeadline", 0);
		s.lanternTicks = t.getIntOr("lanternTicks", 0);
		s.renown = t.getLongOr("renown", 0);
		s.insight = t.getIntOr("insight", 0);
		ListTag skillTag = t.getListOrEmpty("skills");
		for (int i = 0; i < skillTag.size(); i++) {
			s.skills.add(skillTag.getStringOr(i, ""));
		}
		s.marks = t.getLongOr("marks", 0);
		s.banked = t.getLongOr("banked", 0);
		s.lifetimeEarned = t.getLongOr("lifetimeEarned", 0);
		s.interestDay = t.getLongOr("interestDay", -1);
		CompoundTag hag = t.getCompoundOrEmpty("haggles");
		for (String k : hag.keySet()) {
			s.haggles.put(k, hag.getLongOr(k, 0));
		}
		s.soldOnce = t.getBooleanOr("soldOnce", false);
		s.boughtOnce = t.getBooleanOr("boughtOnce", false);
		s.aspect = t.getStringOr("aspect", "");
		ListTag aspectTag = t.getListOrEmpty("aspects");
		for (int i = 0; i < aspectTag.size(); i++) {
			s.aspects.add(aspectTag.getStringOr(i, ""));
		}
		s.resonance = t.getFloatOr("resonance", 0);
		s.gifts = t.getIntOr("gifts", 0);
		CompoundTag rankTag = t.getCompoundOrEmpty("edictRank");
		for (String k : rankTag.keySet()) {
			s.edictRank.put(k, rankTag.getIntOr(k, 1));
		}
		for (var tag : t.getListOrEmpty("contracts")) {
			if (tag instanceof CompoundTag ct) {
				s.contracts.add(dev.afterworld.economy.Contract.load(ct));
			}
		}
		s.chapter = t.getIntOr("chapter", 0);
		s.objDone = t.getIntOr("objDone", 0);
		int[] oc = t.getIntArray("objCount").orElse(new int[0]);
		System.arraycopy(oc, 0, s.objCount, 0, Math.min(oc.length, s.objCount.length));
		s.questFinished = t.getBooleanOr("questFinished", false);
		ListTag flagTag = t.getListOrEmpty("flags");
		for (int i = 0; i < flagTag.size(); i++) {
			s.flags.add(flagTag.getStringOr(i, ""));
		}
		CompoundTag deedTag = t.getCompoundOrEmpty("deeds");
		for (String k : deedTag.keySet()) {
			s.deeds.put(k, deedTag.getIntOr(k, 0));
		}
		s.ladderBest = t.getIntOr("ladderBest", 0);
		s.ladderResume = Math.max(1, t.getIntOr("ladderResume", 1));
		s.ladderReturnDim = t.getStringOr("ladderReturnDim", "");
		s.ladderReturnPos = t.getLongOr("ladderReturnPos", 0L);
		CompoundTag sideTag = t.getCompoundOrEmpty("side");
		for (String k : sideTag.keySet()) {
			s.side.put(k, sideTag.getIntOr(k, -1));
		}
		ListTag recentTag = t.getListOrEmpty("recentSide");
		for (int i = 0; i < recentTag.size(); i++) {
			s.recentSide.add(recentTag.getStringOr(i, ""));
		}
		for (int id : t.getIntArray("attuned").orElse(new int[0])) {
			s.attuned.add(id);
		}
		s.travelReadyAt = t.getLongOr("travelReadyAt", 0);
		t.getCompound("mark").ifPresent(m -> {
			s.hasMark = true;
			s.markX = m.getIntOr("x", 0);
			s.markY = m.getIntOr("y", Integer.MIN_VALUE);
			s.markZ = m.getIntOr("z", 0);
			s.markDim = m.getStringOr("dim", "minecraft:overworld");
			s.markLabel = m.getStringOr("label", "");
		});
		return s;
	}

	/** Copies persistent state into this instance (used when the attachment is re-created). */
	public PlayerState copy() {
		return load(save());
	}
}
