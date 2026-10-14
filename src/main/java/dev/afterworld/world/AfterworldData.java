package dev.afterworld.world;

import com.mojang.serialization.Codec;
import dev.afterworld.Afterworld;
import dev.afterworld.site.Site;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Everything the world remembers that is shared by all players: the Chronicle, settlements,
 * factions, sites, bosses and events. Stored once per world (server-wide data storage).
 */
public final class AfterworldData extends SavedData {
	public static final int DATA_VERSION = 1;
	public static final int MAX_CHRONICLE = 400;
	public static final Codec<AfterworldData> CODEC = CompoundTag.CODEC.xmap(AfterworldData::load, AfterworldData::save);
	public static final SavedDataType<AfterworldData> TYPE = new SavedDataType<>(Afterworld.id("world"), AfterworldData::new, CODEC, null);

	public long lastSimDay = -1;
	public int nextChronicleId = 1;
	public int nextSettlementId = 1;
	public int nextSiteId = 1;
	public int nextEventId = 1;
	public final List<ChronicleEntry> chronicle = new ArrayList<>();
	public final Long2ObjectOpenHashMap<Settlement> settlements = new Long2ObjectOpenHashMap<>();
	public final FactionState[] factions = new FactionState[Faction.COUNT];
	public final Int2ObjectLinkedOpenHashMap<Site> sites = new Int2ObjectLinkedOpenHashMap<>();
	public final List<WorldEvent> events = new ArrayList<>();
	public final BossStates.Pilgrim pilgrim = new BossStates.Pilgrim();
	public final BossStates.BellRinger bell = new BossStates.BellRinger();
	public final BossStates.Vault vault = new BossStates.Vault();
	public boolean founded;
	/** Whether the grand structures have been placed in this world. */
	public boolean grandSeeded;
	/** Whether a good spawn has been looked for (once, when the world is new). */
	public boolean spawnChosen;
	/** Where each named NPC lives (block pos as a long). */
	public final java.util.Map<String, Long> npcHomes = new java.util.HashMap<>();
	/** The Atlas's plan: where each one-of-a-kind site starts (structure name to packed chunk). */
	public final java.util.Map<String, Long> atlas = new java.util.HashMap<>();
	/** The Waystone Camp: whether it stands, and its centre (block pos as a long). */
	public boolean campBuilt;
	/** The five Wardens of Chapter III, a bit each once beaten (shogun, lich, colossus, sentinel, warden). */
	public int wardensDefeated;
	/** Phylacteries of the Crimson Lich broken anywhere in the world (three, and he stays down). */
	public int phylacteries;
	public long campCenter = Long.MIN_VALUE;
	public int nextWaystoneId = 1;
	/** Every merchant's stock and memory of what they've been sold. */
	public final java.util.Map<String, dev.afterworld.economy.Market.State> markets = new java.util.HashMap<>();
	public final Int2ObjectLinkedOpenHashMap<Waystone> waystones = new Int2ObjectLinkedOpenHashMap<>();

	/** A waystone the world knows of: found (and named) the first time anyone touches it. */
	public static final class Waystone {
		public int id;
		public String name = "";
		public String dim = "minecraft:overworld";
		public long pos;
		public boolean camp;

		public net.minecraft.core.BlockPos blockPos() {
			return net.minecraft.core.BlockPos.of(pos);
		}

		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("id", id);
			t.putString("name", name);
			t.putString("dim", dim);
			t.putLong("pos", pos);
			t.putBoolean("camp", camp);
			return t;
		}

		static Waystone load(CompoundTag t) {
			Waystone w = new Waystone();
			w.id = t.getIntOr("id", 0);
			w.name = t.getStringOr("name", "");
			w.dim = t.getStringOr("dim", "minecraft:overworld");
			w.pos = t.getLongOr("pos", 0);
			w.camp = t.getBooleanOr("camp", false);
			return w;
		}
	}

	public Waystone waystoneAt(String dim, long pos) {
		for (Waystone w : waystones.values()) {
			if (w.pos == pos && w.dim.equals(dim)) {
				return w;
			}
		}
		return null;
	}

	/** Rewards owed to players who were offline when they were earned ("item id*count" or "bond:id"). */
	public final java.util.Map<java.util.UUID, java.util.List<String>> pending = new java.util.HashMap<>();

	public AfterworldData() {
		for (Faction f : Faction.VALUES) {
			factions[f.ordinal()] = new FactionState(f);
		}
	}

	public static AfterworldData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public void owe(java.util.UUID player, String reward) {
		pending.computeIfAbsent(player, k -> new ArrayList<>()).add(reward);
		setDirty();
	}

	public FactionState faction(Faction f) {
		return factions[f.ordinal()];
	}

	public Settlement settlementById(int id) {
		for (Settlement s : settlements.values()) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	public ChronicleEntry addChronicle(ChronicleEntry e) {
		e.id = nextChronicleId++;
		chronicle.add(e);
		while (chronicle.size() > MAX_CHRONICLE) {
			// Forget the oldest page that is neither missing nor about a great actor.
			int victim = 0;
			for (int i = 0; i < chronicle.size(); i++) {
				ChronicleEntry c = chronicle.get(i);
				if (!c.missing && !c.cause.startsWith("boss")) {
					victim = i;
					break;
				}
			}
			chronicle.remove(victim);
		}
		setDirty();
		return e;
	}

	private CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("v", DATA_VERSION);
		t.putLong("lastSimDay", lastSimDay);
		t.putInt("nextChronicle", nextChronicleId);
		t.putInt("nextSettlement", nextSettlementId);
		t.putInt("nextSite", nextSiteId);
		t.putInt("nextEvent", nextEventId);
		t.putBoolean("founded", founded);
		t.putBoolean("grandSeeded", grandSeeded);
		t.putBoolean("spawnChosen", spawnChosen);
		ListTag c = new ListTag();
		for (ChronicleEntry e : chronicle) {
			c.add(e.save());
		}
		t.put("chronicle", c);
		ListTag s = new ListTag();
		for (Settlement st : settlements.values()) {
			s.add(st.save());
		}
		t.put("settlements", s);
		CompoundTag f = new CompoundTag();
		for (FactionState fs : factions) {
			f.put(fs.faction.id, fs.save());
		}
		t.put("factions", f);
		ListTag sites = new ListTag();
		for (Site site : this.sites.values()) {
			sites.add(site.save());
		}
		t.put("sites", sites);
		ListTag ev = new ListTag();
		for (WorldEvent e : events) {
			ev.add(e.save());
		}
		t.put("events", ev);
		t.put("pilgrim", pilgrim.save());
		t.put("bell", bell.save());
		t.put("vault", vault.save());
		CompoundTag pend = new CompoundTag();
		for (var e : pending.entrySet()) {
			ListTag l = new ListTag();
			for (String v : e.getValue()) {
				l.add(net.minecraft.nbt.StringTag.valueOf(v));
			}
			pend.put(e.getKey().toString(), l);
		}
		t.put("pending", pend);
		CompoundTag homes = new CompoundTag();
		for (var e : npcHomes.entrySet()) {
			homes.putLong(e.getKey(), e.getValue());
		}
		t.put("npcHomes", homes);
		CompoundTag atlasTag = new CompoundTag();
		for (var e : atlas.entrySet()) {
			atlasTag.putLong(e.getKey(), e.getValue());
		}
		t.put("atlas", atlasTag);
		t.putBoolean("campBuilt", campBuilt);
		t.putInt("wardensDefeated", wardensDefeated);
		t.putInt("phylacteries", phylacteries);
		t.putLong("campCenter", campCenter);
		t.putInt("nextWaystone", nextWaystoneId);
		ListTag ws = new ListTag();
		for (Waystone w : waystones.values()) {
			ws.add(w.save());
		}
		t.put("waystones", ws);
		CompoundTag mk = new CompoundTag();
		for (var e : markets.entrySet()) {
			mk.put(e.getKey(), e.getValue().save());
		}
		t.put("markets", mk);
		return t;
	}

	private static AfterworldData load(CompoundTag t) {
		AfterworldData d = new AfterworldData();
		d.lastSimDay = t.getLongOr("lastSimDay", -1);
		d.nextChronicleId = t.getIntOr("nextChronicle", 1);
		d.nextSettlementId = t.getIntOr("nextSettlement", 1);
		d.nextSiteId = t.getIntOr("nextSite", 1);
		d.nextEventId = t.getIntOr("nextEvent", 1);
		d.founded = t.getBooleanOr("founded", false);
		d.grandSeeded = t.getBooleanOr("grandSeeded", false);
		d.spawnChosen = t.getBooleanOr("spawnChosen", false);
		ListTag c = t.getListOrEmpty("chronicle");
		for (int i = 0; i < c.size(); i++) {
			d.chronicle.add(ChronicleEntry.load(c.getCompoundOrEmpty(i)));
		}
		ListTag s = t.getListOrEmpty("settlements");
		for (int i = 0; i < s.size(); i++) {
			Settlement st = Settlement.load(s.getCompoundOrEmpty(i));
			d.settlements.put(st.key, st);
		}
		CompoundTag f = t.getCompoundOrEmpty("factions");
		for (FactionState fs : d.factions) {
			fs.load(f.getCompoundOrEmpty(fs.faction.id));
		}
		ListTag sites = t.getListOrEmpty("sites");
		for (int i = 0; i < sites.size(); i++) {
			Site site = Site.load(sites.getCompoundOrEmpty(i));
			d.sites.put(site.id, site);
		}
		ListTag ev = t.getListOrEmpty("events");
		for (int i = 0; i < ev.size(); i++) {
			d.events.add(WorldEvent.load(ev.getCompoundOrEmpty(i)));
		}
		d.pilgrim.load(t.getCompoundOrEmpty("pilgrim"));
		d.bell.load(t.getCompoundOrEmpty("bell"));
		d.vault.load(t.getCompoundOrEmpty("vault"));
		CompoundTag pend = t.getCompoundOrEmpty("pending");
		for (String key : pend.keySet()) {
			try {
				java.util.UUID id = java.util.UUID.fromString(key);
				ListTag l = pend.getListOrEmpty(key);
				java.util.List<String> list = new ArrayList<>();
				for (int i = 0; i < l.size(); i++) {
					list.add(l.getStringOr(i, ""));
				}
				d.pending.put(id, list);
			} catch (IllegalArgumentException ignored) {
			}
		}
		CompoundTag homes = t.getCompoundOrEmpty("npcHomes");
		for (String key : homes.keySet()) {
			d.npcHomes.put(key, homes.getLongOr(key, 0));
		}
		CompoundTag atlasTag = t.getCompoundOrEmpty("atlas");
		for (String key : atlasTag.keySet()) {
			d.atlas.put(key, atlasTag.getLongOr(key, 0));
		}
		d.campBuilt = t.getBooleanOr("campBuilt", false);
		d.wardensDefeated = t.getIntOr("wardensDefeated", 0);
		d.phylacteries = t.getIntOr("phylacteries", 0);
		d.campCenter = t.getLongOr("campCenter", Long.MIN_VALUE);
		d.nextWaystoneId = t.getIntOr("nextWaystone", 1);
		ListTag ws = t.getListOrEmpty("waystones");
		for (int i = 0; i < ws.size(); i++) {
			Waystone w = Waystone.load(ws.getCompoundOrEmpty(i));
			d.waystones.put(w.id, w);
		}
		CompoundTag mk = t.getCompoundOrEmpty("markets");
		for (String k : mk.keySet()) {
			d.markets.put(k, dev.afterworld.economy.Market.State.load(mk.getCompoundOrEmpty(k)));
		}
		return d;
	}
}
