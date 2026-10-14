package dev.afterworld.world;

import dev.afterworld.AwConfig;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.Progress;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The abstract simulation. Runs once per in-game day over a handful of numbers: settlements drift,
 * factions pick operations, events start and end, and the Chronicle records what mattered.
 */
public final class WorldSim {
	/** Edicts that anomalies can carry, in rough order of how common they are. */
	private static final Edict[] ANOMALY_EDICTS = {Edict.WEIGHTLESSNESS, Edict.ECHOES, Edict.WITNESS, Edict.STILLNESS, Edict.FORGOTTEN,
		Edict.HUNGER, Edict.RETURN, Edict.HOSPITALITY, Edict.WEIGHTLESSNESS, Edict.ECHOES};
	public static final List<DayListener> DAY_LISTENERS = new ArrayList<>();

	@FunctionalInterface
	public interface DayListener {
		void onNewDay(MinecraftServer server, AfterworldData data, long day);
	}

	private WorldSim() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(WorldSim::found);
		ServerLifecycleEvents.SERVER_STARTED.register(WorldSim::seedGrand);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 200 == 37) {
				checkDay(server);
			}
		});
	}

	// ------------------------------------------------------------------ founding

	/**
	 * The grand structures: the seat of each faction and the Archive of Almost, spread around spawn
	 * between 1.9 and 4.4 kilometres out. Runs once per world (worlds from older versions get them too).
	 */
	private static void seedGrand(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		if (data.grandSeeded) {
			return;
		}
		data.grandSeeded = true;
		RandomSource r = RandomSource.create(server.overworld().getSeed() ^ 0x6A2D5EEDL);
		BlockPos spawn = server.overworld().getRespawnData().pos();
		double base = r.nextDouble() * Math.PI * 2;
		Object[][] grand = {
			{SiteKind.BASTION, Faction.CROWN, 1900},
			{SiteKind.OBSERVATORY, Faction.GUILD, 2500},
			{SiteKind.MAUSOLEUM, Faction.COURT, 2200},
			{SiteKind.CONTAINMENT, Faction.BUREAU, 2900},
			{SiteKind.ARCHIVE, null, 3900},
		};
		for (int i = 0; i < grand.length; i++) {
			SiteKind kind = (SiteKind) grand[i][0];
			Faction faction = (Faction) grand[i][1];
			int dist = (Integer) grand[i][2] + r.nextInt(500);
			double a = base + i * Math.PI * 2 / grand.length + (r.nextDouble() - 0.5) * 0.5;
			int x = (int) (spawn.getX() + Math.cos(a) * dist);
			int z = (int) (spawn.getZ() + Math.sin(a) * dist);
			Site site = Sites.create(server, kind, "minecraft:overworld", x, z, -1, faction == null ? -1 : faction.ordinal(), 0, null);
			Chronicle.record(server, site.name, GRAND_LORE[i], site.name, faction == null ? -1 : faction.ordinal(), "", "lore:" + kind.id);
		}
		data.setDirty();
	}

	private static final String[] GRAND_LORE = {
		"The Crown keeps a bastion on the frontier, with a gate that is never quite closed and a throne nobody sits on.",
		"The Cartographers' Guild built an observatory to measure the stars. The stars, they report, have not been cooperating.",
		"The Morrow Court tends a mausoleum where the names of the forgotten are kept, in case anyone comes asking.",
		"The Bureau of Impossible Things keeps its finds in a facility with glass walls. One of the walls is no longer glass.",
		"Somewhere stands an archive of things that almost happened. Its upper floors have not yet decided to fall.",
	};

	/** First start of a world: place the great sites and write the opening pages. */
	private static void found(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		if (data.founded) {
			return;
		}
		data.founded = true;
		long seed = server.overworld().getSeed();
		RandomSource r = RandomSource.create(seed ^ 0xAF7E5D0L);
		BlockPos spawn = server.overworld().getRespawnData().pos();
		double base = r.nextDouble() * Math.PI * 2;

		for (FactionState fs : data.factions) {
			fs.leader = Names.person(r);
			fs.influence = 35 + r.nextInt(20);
		}
		data.faction(Faction.CROWN).influence = 55;
		data.faction(Faction.CROWN).relations[Faction.BUREAU.ordinal()] = -10;
		data.faction(Faction.BUREAU).relations[Faction.CROWN.ordinal()] = -10;
		data.faction(Faction.GUILD).relations[Faction.CROWN.ordinal()] = 10;

		// The story's places, nearest first: the Pilgrim's loop of shrines (a walk of a few minutes),
		// the village, then the cathedral and the vault, each a longer road than the last.
		double pa = base;
		double cx = spawn.getX() + Math.cos(pa) * 1400;
		double cz = spawn.getZ() + Math.sin(pa) * 1400;
		for (int i = 0; i < 4; i++) {
			double a = pa + i * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.4;
			int x = (int) (cx + Math.cos(a) * (300 + r.nextInt(80)));
			int z = (int) (cz + Math.sin(a) * (300 + r.nextInt(80)));
			Site shrine = Sites.create(server, SiteKind.SHRINE, "minecraft:overworld", x, z, -1, -1, i, "Shrine of the " + SHRINE_NAMES[i]);
			data.pilgrim.shrines[i] = shrine.id;
		}
		data.pilgrim.state = BossStates.Pilgrim.WANDERING;
		Site first = data.sites.get(data.pilgrim.shrines[0]);
		data.pilgrim.x = first.x;
		data.pilgrim.z = first.z;
		data.pilgrim.leg = 1;

		Site village = Sites.create(server, SiteKind.UNREMEMBERED, "minecraft:overworld",
			(int) (spawn.getX() + Math.cos(base + 2.3) * 1000), (int) (spawn.getZ() + Math.sin(base + 2.3) * 1000), -1, Faction.COURT.ordinal(), 0, Names.place(r));
		double ca = clearOfLairs(spawn, base + 3.9, 2200);
		Site cathedral = Sites.create(server, SiteKind.CATHEDRAL, "minecraft:overworld",
			(int) (spawn.getX() + Math.cos(ca) * 2200), (int) (spawn.getZ() + Math.sin(ca) * 2200), -1, -1, 0, "The Hollow Cathedral");
		data.bell.site = cathedral.id;
		data.bell.nameA = r.nextInt(Names.BELL_A.length);
		data.bell.nameB = r.nextInt(Names.BELL_B.length);
		double va = clearOfLairs(spawn, base + 5.4, 3300);
		Site vault = Sites.create(server, SiteKind.VAULT, "minecraft:overworld",
			(int) (spawn.getX() + Math.cos(va) * 3300), (int) (spawn.getZ() + Math.sin(va) * 3300), -1, Faction.GUILD.ordinal(), 0, "The Vault of Seconds");
		data.vault.site = vault.id;
		for (Site seat : data.sites.values()) {
			if (dev.afterworld.world.atlas.Atlas.SEATS.contains(seat.kind)) {
				dev.afterworld.world.atlas.Siting.reserve(seat.x >> 4, seat.z >> 4, dev.afterworld.world.atlas.Atlas.LAIR_RESERVE);
			}
		}

		Chronicle.record(server, "The Census of " + village.name,
			"A village's census lists people who were never born. The Morrow Court has asked that it not be read aloud.",
			village.name, Faction.COURT.ordinal(), "", "lore:census");
		Chronicle.record(server, "The Pilgrim's Road",
			"Shepherds speak of a stone figure that walks between old shrines without touching the ground. Where it passes, the grass forgets how to grow.",
			"", -1, "", "lore:pilgrim");
		Chronicle.missing(server, "The Bell of the Hollow Cathedral",
			"No one living remembers hearing its bell.",
			"It rings every night. It takes the memory of the ringing with it.", "The Hollow Cathedral", -1, "boss:bell");
		Chronicle.record(server, "A Clocktower, Charted Twice",
			"The Cartographers' Guild has charted a clocktower that is never in the same state twice. Their surveyors return a few seconds older than they left.",
			"The Vault of Seconds", Faction.GUILD.ordinal(), "", "lore:vault");
		Chronicle.record(server, "An Unnamed Traveller",
			"A traveller has arrived who does not appear in any record. The world has not yet decided what to make of them.",
			"", -1, "", "lore:arrival");
		data.setDirty();
	}

	private static final String[] SHRINE_NAMES = {"First Step", "Long Rest", "Turned Back", "Last Mile"};

	// ------------------------------------------------------------------ daily

	private static void checkDay(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		long day = Progress.day(server);
		if (data.lastSimDay < 0) {
			data.lastSimDay = day;
			data.setDirty();
			return;
		}
		if (day <= data.lastSimDay) {
			return;
		}
		long missed = day - data.lastSimDay;
		long run = Math.min(missed, Math.max(1, AwConfig.get().maxOfflineSimulationDays + 1));
		for (long i = run - 1; i >= 0; i--) {
			simulateDay(server, data, day - i, i > 0);
		}
		data.lastSimDay = day;
		data.setDirty();
	}

	/** Runs one simulated day. {@code catchUp} days (long absences) are gentler. */
	public static void simulateDay(MinecraftServer server, AfterworldData data, long day, boolean catchUp) {
		if (!AwConfig.get().worldSimulation) {
			return;
		}
		RandomSource r = RandomSource.create(server.overworld().getSeed() ^ day * 0x9E3779B97F4A7C15L);
		double freq = AwConfig.get().worldEventFrequency;
		for (Settlement s : data.settlements.values()) {
			updateSettlement(server, data, s, day, catchUp, r, freq);
		}
		if (freq > 0) {
			for (FactionState fs : data.factions) {
				if (day - fs.lastOpDay >= 2 && r.nextFloat() < 0.45F * freq) {
					operate(server, data, fs, day, r);
				}
			}
			ensureContent(server, data, day, r);
		}
		for (WorldEvent e : data.events) {
			if (e.active && day >= e.endDay) {
				endEvent(server, data, e, day, false);
			}
		}
		data.events.removeIf(e -> !e.active && day - e.endDay > 10);
		for (DayListener l : DAY_LISTENERS) {
			l.onNewDay(server, data, day);
		}
		data.setDirty();
	}

	private static void updateSettlement(MinecraftServer server, AfterworldData data, Settlement s, long day, boolean catchUp, RandomSource r, double freq) {
		boolean decline = AwConfig.get().settlementDecline && !catchUp;
		int dSafety = Math.min(8, s.kills / 3) - s.deaths * 4 + s.helped * 4;
		int dProsperity = Math.min(6, s.trades / 2) + (s.safety >= 60 ? 1 : s.safety < 30 ? -3 : 0) + s.helped * 2;
		int dLegit = (s.safety >= 60 && s.prosperity >= 50) ? 1 : s.prosperity < 30 ? -2 : 0;
		int anomalies = Sites.countOpenNear(data, SiteKind.ANOMALY, s.x, s.z, 400);
		int dAnomaly = anomalies > 0 ? 2 * anomalies : -1;
		// Unwatched places slowly return toward the ordinary.
		dSafety += Integer.signum(50 - s.safety);
		dProsperity += Integer.signum(50 - s.prosperity);
		if (!decline) {
			dSafety = Math.max(0, dSafety);
			dProsperity = Math.max(0, dProsperity);
			dLegit = Math.max(0, dLegit);
		}
		s.safety += dSafety;
		s.prosperity += dProsperity;
		s.legitimacy += dLegit;
		s.anomaly += dAnomaly;
		s.clamp();
		s.kills = 0;
		s.trades = 0;
		s.deaths = 0;
		s.helped = 0;

		if (s.state == Settlement.ABANDONED) {
			if (s.safety >= 45 && s.prosperity >= 40) {
				s.state = Settlement.NORMAL;
				s.leader = Names.person(r);
				Chronicle.record(server, s.name + " Resettled", "Families have returned to " + s.name + ". " + s.leader + " has taken up the keys.",
					s.name, s.patron, "", "settlement:" + s.id);
			}
			return;
		}
		if (s.state == Settlement.REMEMBERED || s.state == Settlement.ANEW) {
			return;
		}
		s.lowDays = (s.safety <= 10 && s.prosperity <= 15) ? s.lowDays + 1 : 0;
		if (s.lowDays >= 3 && decline) {
			s.state = Settlement.ABANDONED;
			Chronicle.record(server, "The Abandonment of " + s.name,
				"The last families left " + s.name + " after " + s.lowDays + " lean, frightened days. The bell was left behind.", s.name, s.patron, "", "settlement:" + s.id);
			return;
		}
		s.state = (s.safety < 30 || s.prosperity < 25) ? Settlement.STRUGGLING : Settlement.NORMAL;

		if (freq <= 0 || catchUp) {
			return;
		}
		if (s.safety < 30 && !hasEvent(data, WorldEvent.SILENT_CARAVAN, s.id) && r.nextFloat() < 0.35F * freq) {
			startCaravan(server, data, s, day, r);
		}
		if (s.anomaly > 60 && !hasEvent(data, WorldEvent.ANOMALY_BLOOM, s.id) && r.nextFloat() < 0.3F * freq) {
			WorldEvent e = newEvent(data, WorldEvent.ANOMALY_BLOOM, s.id, day, 6);
			Site site = Sites.createNear(server, SiteKind.ANOMALY, s.x, s.z, 140, 320, r, s.id, Faction.BUREAU.ordinal(), anomalyEdict(r).ordinal());
			if (site != null) {
				e.site = site.id;
			}
			Chronicle.record(server, "Strangeness near " + s.name, "Wells near " + s.name + " have begun to answer questions. The Bureau has been notified.",
				s.name, Faction.BUREAU.ordinal(), "", "event:" + e.id);
		}
		if (s.legitimacy < 25 && AwConfig.get().allowDestructivePolitics && !hasEvent(data, WorldEvent.CROWN_OF_CROWS, s.id) && r.nextFloat() < 0.25F * freq) {
			WorldEvent e = newEvent(data, WorldEvent.CROWN_OF_CROWS, s.id, day, 5);
			String old = s.leader;
			Chronicle.record(server, "A Crown of Crows", old + ", who kept " + s.name + ", has not been seen for three days. Crows gather on the empty posts.",
				s.name, s.patron, "", "event:" + e.id);
			Chronicle.missing(server, "Where " + old + " Went", "Nobody in " + s.name + " will say.",
				old + " made a Bond with something that lives under the well, and paid for the town's luck with their own name.", s.name, Faction.COURT.ordinal(), "crows:" + s.id);
		}
	}

	// ------------------------------------------------------------------ factions

	private static void operate(MinecraftServer server, AfterworldData data, FactionState fs, long day, RandomSource r) {
		List<Settlement> all = new ArrayList<>(data.settlements.values());
		boolean success = r.nextInt(100) < 45 + fs.influence / 2;
		fs.lastOpDay = day;
		int f = fs.faction.ordinal();
		switch (fs.faction) {
			case CROWN -> {
				Settlement weak = pick(all, s -> -s.safety);
				Settlement rich = pick(all, s -> s.prosperity);
				if (weak != null && weak.safety < 45 && r.nextBoolean()) {
					fs.operation = "Fortify " + weak.name;
					if (success) {
						weak.safety += 12;
						weak.legitimacy -= 4;
						fs.influence += 3;
						Chronicle.record(server, "The Crown Garrisons " + weak.name, "Crown soldiers arrived in " + weak.name + " and did not ask whether they were wanted.",
							weak.name, f, "", "op:" + day + ":crown");
					} else {
						fs.influence -= 4;
						Chronicle.record(server, "A Garrison That Never Came", "The Crown promised soldiers to " + weak.name + ". None arrived.", weak.name, f, "", "op:" + day + ":crown");
					}
				} else if (rich != null && rich.prosperity >= 60) {
					fs.operation = "Claim the road to " + rich.name;
					if (success) {
						String commander = Names.person(r);
						rich.prosperity += 4;
						rich.legitimacy -= 8;
						rich.leader = commander;
						fs.influence += 5;
						fs.relations[Faction.GUILD.ordinal()] -= 8;
						Chronicle.record(server, "The Crown Claims the Road", "The Crown claimed the trade road to " + rich.name + " and appointed " + commander + " as commander.",
							rich.name, f, "", "op:" + day + ":crown");
						Chronicle.missing(server, "Testimony from " + rich.name, "Local testimony disputes the official record.",
							"An unnamed traveller opposed the appointment. The official record does not mention them.", rich.name, f, "crown_road:" + rich.id);
					} else {
						fs.influence -= 3;
					}
				} else {
					fs.operation = "Hold the realm";
					fs.resources += 5;
				}
			}
			case GUILD -> {
				Settlement any = all.isEmpty() ? null : all.get(r.nextInt(all.size()));
				if (any != null && r.nextFloat() < 0.6F) {
					fs.operation = "Chart the wilds beyond " + any.name;
					SiteKind kind = r.nextBoolean() ? SiteKind.CACHE : SiteKind.ANOMALY;
					Site site = Sites.createNear(server, kind, any.x, any.z, 250, 700, r, any.id, f, kind == SiteKind.ANOMALY ? anomalyEdict(r).ordinal() : -1);
					if (site != null && success) {
						fs.influence += 3;
						String dir = Names.direction(site.x - any.x, site.z - any.z);
						if (r.nextFloat() < 0.35F) {
							Chronicle.missing(server, "A Survey With Pages Missing", "The Guild's survey " + dir + " of " + any.name + " is missing several pages.",
								"They found something there and sealed the records to keep it for themselves.", any.name, f, "site:" + site.id);
						} else {
							Chronicle.record(server, "The Guild Charts the " + capital(dir), "Surveyors charted " + (kind == SiteKind.CACHE ? "an old cache" : "a place where the rules run thin")
								+ " " + dir + " of " + any.name + ".", any.name, f, "", "site:" + site.id);
						}
					}
				} else if (any != null) {
					fs.operation = "Sell maps in " + any.name;
					any.prosperity += 5;
					fs.resources += 6;
				}
			}
			case COURT -> {
				Settlement sad = pick(all, s -> -s.legitimacy);
				if (sad != null && r.nextBoolean()) {
					fs.operation = "Keep the dead of " + sad.name;
					sad.legitimacy += success ? 5 : 0;
					fs.influence += success ? 2 : -2;
					if (success && r.nextFloat() < 0.5F) {
						Chronicle.record(server, "The Court Keeps Its Dead", "Morrow Court clerks recorded the names of " + sad.name + "'s dead, so that none would be lost.",
							sad.name, f, "", "op:" + day + ":court");
					}
				} else if (sad != null) {
					fs.operation = "Bind a spirit near " + sad.name;
					sad.anomaly += 6;
					fs.resources += 8;
					Chronicle.missing(server, "The Court Takes a Lodger", "The Morrow Court reports a quiet season in " + sad.name + ".",
						"They bound a wandering spirit beneath the chapel rather than let its history disappear.", sad.name, f, "court:" + sad.id);
				}
			}
			case BUREAU -> {
				Site oldest = Sites.oldestOpen(data, SiteKind.ANOMALY, day - 4);
				if (oldest != null && success && r.nextFloat() < 0.6F) {
					fs.operation = "Contain the anomaly at " + oldest.name;
					Sites.resolveByFaction(server, oldest, Faction.BUREAU, "Sealed by the Bureau of Impossible Things.");
					fs.influence += 6;
					Settlement near = Settlements.nearest(data, oldest.dim, oldest.x, oldest.z, 600);
					if (near != null) {
						near.anomaly -= 15;
					}
					Chronicle.record(server, "The Bureau Seals " + oldest.name, "Bureau agents contained the anomaly called " + oldest.name + ". Their report runs to one line.",
						oldest.name, f, "", "site:" + oldest.id);
				} else {
					Settlement any = all.isEmpty() ? null : all.get(r.nextInt(all.size()));
					if (any != null) {
						fs.operation = "Field office in " + any.name;
						any.anomaly += 8;
						fs.resources += 10;
						Chronicle.missing(server, "Nothing Unusual in " + any.name, "The Bureau field office in " + any.name + " reported nothing unusual.",
							"It was quietly tapping the anomaly beneath the well.", any.name, f, "bureau:" + any.id);
					}
				}
			}
		}
		for (Settlement s : all) {
			s.clamp();
		}
		fs.clamp();
		if (fs.influence < 10 && fs.resources < 15 && r.nextFloat() < 0.5F) {
			String old = fs.leader;
			fs.leader = Names.person(r);
			fs.influence += 15;
			Chronicle.record(server, "New Leadership for " + fs.faction.displayName().getString(), old + " has stepped down. " + fs.leader + " now speaks for " + fs.faction.displayName().getString() + ".",
				"", f, "", "leader:" + fs.faction.id + ":" + day);
		}
	}

	/** Keeps a few open leads in the world so exploration always has somewhere to go. */
	private static void ensureContent(MinecraftServer server, AfterworldData data, long day, RandomSource r) {
		if (!AwConfig.get().generateSites || data.settlements.isEmpty()) {
			return;
		}
		int open = Sites.countOpen(data, SiteKind.ANOMALY) + Sites.countOpen(data, SiteKind.LAIR) + Sites.countOpen(data, SiteKind.CACHE);
		int want = 3 + server.getPlayerList().getPlayerCount();
		if (open >= want) {
			return;
		}
		List<Settlement> all = new ArrayList<>(data.settlements.values());
		Settlement s = all.get(r.nextInt(all.size()));
		if (s.safety < 45) {
			Site lair = Sites.createNear(server, SiteKind.LAIR, s.x, s.z, 120, 260, r, s.id, Faction.CROWN.ordinal(), -1);
			if (lair != null) {
				Chronicle.record(server, "Something Hunts near " + s.name, "Livestock has been going missing from " + s.name + ". Tracks lead " + Names.direction(lair.x - s.x, lair.z - s.z) + ".",
					s.name, s.patron, "", "site:" + lair.id);
			}
		} else {
			Sites.createNear(server, SiteKind.ANOMALY, s.x, s.z, 200, 600, r, s.id, -1, anomalyEdict(r).ordinal());
		}
	}

	// ------------------------------------------------------------------ events

	private static void startCaravan(MinecraftServer server, AfterworldData data, Settlement s, long day, RandomSource r) {
		WorldEvent e = newEvent(data, WorldEvent.SILENT_CARAVAN, s.id, day, 5);
		Site site = Sites.createNear(server, SiteKind.CARAVAN, s.x, s.z, 90, 180, r, s.id, Faction.CROWN.ordinal(), e.id);
		if (site != null) {
			e.site = site.id;
		}
		Chronicle.record(server, "The Silent Caravan", "Caravans have stopped arriving at " + s.name + ". The last one was seen on the road " + Names.direction(site == null ? 0 : site.x - s.x, site == null ? 1 : site.z - s.z) + ".",
			s.name, Faction.CROWN.ordinal(), "", "event:" + e.id);
	}

	public static void endEvent(MinecraftServer server, AfterworldData data, WorldEvent e, long day, boolean resolved) {
		if (!e.active) {
			return;
		}
		e.active = false;
		e.endDay = day;
		Settlement s = data.settlementById(e.settlement);
		String place = s == null ? "" : s.name;
		switch (e.kind) {
			case WorldEvent.SILENT_CARAVAN -> {
				if (!resolved && s != null) {
					s.prosperity -= 10;
					Chronicle.record(server, "The Road Stays Silent", "No one came for the caravan bound for " + place + ". Prices there have doubled.", place, Faction.CROWN.ordinal(), "", "event:" + e.id);
				}
				Site site = data.sites.get(e.site);
				if (site != null && site.open()) {
					Sites.resolveByFaction(server, site, null, resolved ? "The caravan reached safety." : "The caravan was lost.");
				}
			}
			case WorldEvent.CROWN_OF_CROWS -> {
				if (s != null) {
					String successor = Names.person(RandomSource.create(day ^ s.id));
					s.leader = successor;
					s.legitimacy += 20;
					Chronicle.record(server, "A Successor in " + place, successor + " now keeps " + place + ". The crows have moved on.", place, s.patron, "", "event:" + e.id);
				}
			}
			default -> {
			}
		}
		data.setDirty();
	}

	public static WorldEvent newEvent(AfterworldData data, String kind, int settlement, long day, int length) {
		WorldEvent e = new WorldEvent();
		e.id = data.nextEventId++;
		e.kind = kind;
		e.settlement = settlement;
		e.startDay = day;
		e.endDay = day + length;
		data.events.add(e);
		data.setDirty();
		return e;
	}

	public static boolean hasEvent(AfterworldData data, String kind, int settlement) {
		for (WorldEvent e : data.events) {
			if (e.active && e.kind.equals(kind) && e.settlement == settlement) {
				return true;
			}
		}
		return false;
	}

	public static Edict anomalyEdict(RandomSource r) {
		return ANOMALY_EDICTS[r.nextInt(ANOMALY_EDICTS.length)];
	}

	private static Settlement pick(List<Settlement> all, java.util.function.ToIntFunction<Settlement> score) {
		Settlement best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Settlement s : all) {
			if (s.state == Settlement.ABANDONED) {
				continue;
			}
			int v = score.applyAsInt(s);
			if (v > bestScore) {
				bestScore = v;
				best = s;
			}
		}
		return best;
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** A bearing near {@code angle} at which a great power's seat stands well clear of every Warden's lair. */
	private static double clearOfLairs(BlockPos spawn, double angle, int dist) {
		for (int k = 0; k < 12; k++) {
			double a = angle + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * 0.25;
			double x = spawn.getX() + Math.cos(a) * dist;
			double z = spawn.getZ() + Math.sin(a) * dist;
			boolean clear = true;
			for (dev.afterworld.world.atlas.Atlas.Lair l : dev.afterworld.world.atlas.Atlas.LAIRS) {
				BlockPos at = dev.afterworld.world.atlas.Atlas.lair(l.warden());
				if (at != null && Math.hypot(at.getX() - x, at.getZ() - z) < 1200) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return a;
			}
		}
		return angle;
	}
}
