package dev.afterworld.site;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.AwConfig;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Ground;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Chronicle;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Names;
import dev.afterworld.world.Settlement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

/**
 * Sites are decided first and built later. A site materialises the first time a player comes
 * within sight of it, on ground no player has made their home. Checks run once every two
 * seconds per player and only compare a handful of coordinates.
 */
public final class Sites {
	public static final int F_GUARDS = 1;
	public static final int F_CACHE_REVEALED = 1 << 1;
	public static final int F_REPAIRED = 1 << 2;
	public static final int F_VISITED = 1 << 3;
	public static final int F_BOSS = 1 << 4;
	public static final int F_GATE_OPEN = 1 << 5;
	public static final int F_CHANDELIER = 1 << 6;
	public static final int F_TAPPED = 1 << 7;
	public static final int F_CENSUS = 1 << 8;
	public static final int F_LIAR = 1 << 9;

	@FunctionalInterface
	public interface Presence {
		/** Called every two seconds while a player is within the site's radius (squared distance given). */
		void near(ServerPlayer player, PlayerState state, Site site, double distSq);
	}

	@FunctionalInterface
	public interface ResolveListener {
		void resolved(MinecraftServer server, Site site, ServerPlayer by);
	}

	public static final Map<SiteKind, List<Presence>> PRESENCE = new EnumMap<>(SiteKind.class);
	public static final List<ResolveListener> RESOLVE_LISTENERS = new ArrayList<>();
	public static final List<java.util.function.BiConsumer<ServerLevel, Site>> BUILT_LISTENERS = new ArrayList<>();
	private static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap RELOCATIONS = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

	private Sites() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Sites::tick);
		SiteInteractions.init();
	}

	public static void onPresence(SiteKind kind, Presence p) {
		PRESENCE.computeIfAbsent(kind, k -> new ArrayList<>()).add(p);
	}

	/** Registers a presence handler for every kind of site. */
	public static void onPresenceAll(Presence p) {
		for (SiteKind kind : SiteKind.VALUES) {
			onPresence(kind, p);
		}
	}

	// ------------------------------------------------------------------ creation

	public static Site create(MinecraftServer server, SiteKind kind, String dim, int x, int z, int settlement, int faction, int data, String name) {
		AfterworldData d = AfterworldData.get(server);
		Site s = new Site();
		s.id = d.nextSiteId++;
		s.kind = kind;
		s.dim = dim;
		s.x = x;
		s.z = z;
		s.seed = server.overworld().getSeed() ^ (s.id * 0x5DEECE66DL) ^ ((long) x << 20) ^ z;
		s.settlement = settlement;
		s.faction = faction;
		s.data = data;
		s.createdDay = Progress.day(server);
		s.name = name == null || name.isEmpty() ? defaultName(s, RandomSource.create(s.seed)) : name;
		d.sites.put(s.id, s);
		d.setDirty();
		return s;
	}

	/** Finds a spot between {@code min} and {@code max} blocks from a point, away from villages and other sites. */
	public static Site createNear(MinecraftServer server, SiteKind kind, int cx, int cz, int min, int max, RandomSource r, int settlement, int faction, int data) {
		AfterworldData d = AfterworldData.get(server);
		for (int attempt = 0; attempt < 12; attempt++) {
			double a = r.nextDouble() * Math.PI * 2;
			double dist = min + r.nextDouble() * (max - min);
			int x = (int) (cx + Math.cos(a) * dist);
			int z = (int) (cz + Math.sin(a) * dist);
			if (clear(d, x, z, kind.radius + 24)) {
				return create(server, kind, "minecraft:overworld", x, z, settlement, faction, data, null);
			}
		}
		return null;
	}

	private static boolean clear(AfterworldData d, int x, int z, int spacing) {
		for (Site s : d.sites.values()) {
			int need = spacing + s.kind.radius;
			if (sq(s.x - x) + sq(s.z - z) < (long) need * need) {
				return false;
			}
		}
		for (Settlement s : d.settlements.values()) {
			if (sq(s.x - x) + sq(s.z - z) < 110L * 110L) {
				return false;
			}
		}
		return true;
	}

	private static long sq(long v) {
		return v * v;
	}

	static String defaultName(Site s, RandomSource r) {
		return switch (s.kind) {
			case ANOMALY -> anomalyName(Edict.byOrdinal(s.data), r);
			case LAIR -> new String[] {"Gnawbone's Den", "The Grey Mother's Hollow", "Old Rattle's Ring", "The Marked Den"}[r.nextInt(4)];
			case CACHE -> Names.first(r) + "'s Cache";
			case CARAVAN -> "The Silent Caravan";
			case ROADSTOP -> "The Road's Last Stop";
			case SHRINE -> "A Pilgrim Shrine";
			case UNREMEMBERED -> Names.place(r);
			case CATHEDRAL -> "The Hollow Cathedral";
			case VAULT -> "The Vault of Seconds";
			case BASTION -> "The Crown Bastion";
			case OBSERVATORY -> "The Guild Observatory";
			case MAUSOLEUM -> "The Morrow Mausoleum";
			case CONTAINMENT -> "Containment Site " + new String[] {"Six", "Nine", "Eleven", "Thirteen"}[r.nextInt(4)];
			case ARCHIVE -> "The Archive of Almost";
		};
	}

	static String anomalyName(Edict e, RandomSource r) {
		String[] places = {"Orchard", "Quarry", "Hollow", "Glade", "Barrow", "Field", "Copse", "Pond"};
		String place = places[r.nextInt(places.length)];
		if (e == null) {
			return "The Strange " + place;
		}
		return switch (e) {
			case WEIGHTLESSNESS -> "The Weightless " + place;
			case ECHOES -> "The Echoing " + place;
			case STILLNESS -> "The Still " + place;
			case WITNESS -> "The Watching " + place;
			case FORGOTTEN -> "The Forgetting " + place;
			case HUNGER -> "The Hungry " + place;
			case RETURN -> "The Returning " + place;
			case HOSPITALITY -> "The Courteous " + place;
			case TRESPASS -> "The Open " + place;
			case BORROWED_TIME -> "The Borrowed " + place;
		};
	}

	// ------------------------------------------------------------------ queries

	public static int countOpen(AfterworldData d, SiteKind kind) {
		int n = 0;
		for (Site s : d.sites.values()) {
			if (s.kind == kind && s.open()) {
				n++;
			}
		}
		return n;
	}

	public static int countOpenNear(AfterworldData d, SiteKind kind, int x, int z, int radius) {
		int n = 0;
		for (Site s : d.sites.values()) {
			if (s.kind == kind && s.open() && sq(s.x - x) + sq(s.z - z) <= (long) radius * radius) {
				n++;
			}
		}
		return n;
	}

	public static Site oldestOpen(AfterworldData d, SiteKind kind, long createdBefore) {
		Site best = null;
		for (Site s : d.sites.values()) {
			if (s.kind == kind && s.open() && s.createdDay <= createdBefore && (best == null || s.createdDay < best.createdDay)) {
				best = s;
			}
		}
		return best;
	}

	public static Site nearestOpen(AfterworldData d, SiteKind kind, String dim, double x, double z) {
		Site best = null;
		double bestD = Double.MAX_VALUE;
		for (Site s : d.sites.values()) {
			if ((kind == null || s.kind == kind) && s.open() && s.dim.equals(dim)) {
				double dd = sq((long) (s.x - x)) + sq((long) (s.z - z));
				if (dd < bestD) {
					bestD = dd;
					best = s;
				}
			}
		}
		return best;
	}

	public static Site at(MinecraftServer server, String dim, BlockPos pos, SiteKind kind) {
		AfterworldData d = AfterworldData.get(server);
		for (Site s : d.sites.values()) {
			if ((kind == null || s.kind == kind) && s.dim.equals(dim) && s.built()) {
				int[] f = SiteBuilder.footprint(s.kind);
				int dx = pos.getX() - s.x;
				int dz = pos.getZ() - s.z;
				if (dx >= f[0] - 2 && dx <= f[1] + 2 && dz >= f[2] - 2 && dz <= f[3] + 2) {
					return s;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ resolution

	public static void resolve(ServerPlayer by, Site site, String outcome) {
		if (!site.open()) {
			return;
		}
		MinecraftServer server = by.level().getServer();
		site.state = Site.RESOLVED;
		site.resolvedDay = Progress.day(server);
		site.outcome = outcome;
		site.resolvedBy = Optional.of(by.getUUID());
		AfterworldData.get(server).setDirty();
		Chronicle.reveal(by, "site:" + site.id);
		for (ResolveListener l : RESOLVE_LISTENERS) {
			l.resolved(server, site, by);
		}
	}

	public static void resolveByFaction(MinecraftServer server, Site site, Faction faction, String outcome) {
		if (!site.open()) {
			return;
		}
		site.state = Site.RESOLVED;
		site.resolvedDay = Progress.day(server);
		site.outcome = outcome;
		AfterworldData.get(server).setDirty();
		if (site.built() && site.kind == SiteKind.ANOMALY) {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			if (level != null && level.hasChunk(site.x >> 4, site.z >> 4)) {
				BlockPos core = site.pos().above(2);
				if (level.getBlockState(core).is(dev.afterworld.registry.AwBlocks.ANOMALY_CORE)) {
					level.setBlock(core, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
				}
			}
		}
		for (ResolveListener l : RESOLVE_LISTENERS) {
			l.resolved(server, site, null);
		}
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 11) {
			return;
		}
		AfterworldData data = AfterworldData.get(server);
		if (data.sites.isEmpty()) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			ServerLevel level = p.level();
			String dim = level.dimension().identifier().toString();
			PlayerState ps = PlayerState.of(p);
			for (Site site : data.sites.values()) {
				if (!site.dim.equals(dim)) {
					continue;
				}
				double dx = site.x - p.getX();
				double dz = site.z - p.getZ();
				double d2 = dx * dx + dz * dz;
				if (site.state == Site.PENDING) {
					if (d2 < 88 * 88 && AwConfig.get().generateSites && !Builds.busy()) {
						materialize(level, data, site);
					}
					continue;
				}
				int r = site.kind.radius + 8;
				if (d2 < (double) r * r * 4) {
					if ((site.flags & F_VISITED) == 0 && d2 < (double) r * r) {
						site.flags |= F_VISITED;
						data.setDirty();
					}
					List<Presence> handlers = PRESENCE.get(site.kind);
					if (handlers != null) {
						for (Presence h : handlers) {
							h.near(p, ps, site, d2);
						}
					}
				}
			}
		}
	}

	/** Builds a site right now, skipping the proximity and suitability checks (commands, tests). */
	public static void forceBuild(ServerLevel level, Site site) {
		AfterworldData data = AfterworldData.get(level.getServer());
		int[] f = SiteBuilder.footprint(site.kind);
		for (int x = site.x + f[0]; x <= site.x + f[1] + 15; x += 16) {
			for (int z = site.z + f[2]; z <= site.z + f[3] + 15; z += 16) {
				level.getChunk(Math.min(x, site.x + f[1]) >> 4, Math.min(z, site.z + f[3]) >> 4);
			}
		}
		site.y = medianGround(level, site, f);
		finishBuild(level, data, site);
	}

	private static void finishBuild(ServerLevel level, AfterworldData data, Site site) {
		if (site.kind == SiteKind.VAULT) {
			site.data = Math.max(8, Math.min(SiteBuilder.VAULT_DEPTH, site.y - level.getMinY() - 14));
		}
		site.state = Site.BUILT;
		data.setDirty();
		Plan plan = SiteBuilder.plan(level, site, new BlockPos(site.x, site.y, site.z));
		Builds.submit(level, plan, () -> {
			for (var l : BUILT_LISTENERS) {
				l.accept(level, site);
			}
		});
	}

	private static void materialize(ServerLevel level, AfterworldData data, Site site) {
		int[] f = SiteBuilder.footprint(site.kind);
		for (int x = site.x + f[0]; x <= site.x + f[1] + 15; x += 16) {
			for (int z = site.z + f[2]; z <= site.z + f[3] + 15; z += 16) {
				if (!level.hasChunk(Math.min(x, site.x + f[1]) >> 4, Math.min(z, site.z + f[3]) >> 4)) {
					return;
				}
			}
		}
		int attempts = RELOCATIONS.get(site.id);
		if (attempts < 8 && !suitable(level, site, f)) {
			RELOCATIONS.put(site.id, attempts + 1);
			RandomSource r = RandomSource.create(site.seed + attempts);
			double a = r.nextDouble() * Math.PI * 2;
			site.x += (int) (Math.cos(a) * (40 + site.kind.radius));
			site.z += (int) (Math.sin(a) * (40 + site.kind.radius));
			data.setDirty();
			return;
		}
		int y = medianGround(level, site, f);
		site.y = y;
		site.state = Site.BUILT;
		data.setDirty();
		Plan plan = SiteBuilder.plan(level, site, new BlockPos(site.x, y, site.z));
		Builds.submit(level, plan, () -> {
			for (var l : BUILT_LISTENERS) {
				l.accept(level, site);
			}
		});
	}

	/** Not water, not somebody's home. */
	private static boolean suitable(ServerLevel level, Site site, int[] f) {
		int threshold = AwConfig.get().protectedChunkInhabitedTicks;
		for (int x = site.x + f[0]; x <= site.x + f[1]; x += 8) {
			for (int z = site.z + f[2]; z <= site.z + f[3]; z += 8) {
				ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
				if (chunk.getInhabitedTime() > threshold) {
					return false;
				}
			}
		}
		BlockPos top = Ground.surface(level, site.x, site.z);
		if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()) {
			return false;
		}
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int[] c : new int[][] {{f[0], f[2]}, {f[1], f[2]}, {f[0], f[3]}, {f[1], f[3]}}) {
			int h = Ground.surface(level, site.x + c[0], site.z + c[1]).getY();
			lo = Math.min(lo, h);
			hi = Math.max(hi, h);
		}
		if (site.kind == SiteKind.VAULT && top.getY() - level.getMinY() < SiteBuilder.VAULT_DEPTH + 16) {
			return false;
		}
		return hi - lo <= 12;
	}

	private static int medianGround(ServerLevel level, Site site, int[] f) {
		int[] hs = new int[9];
		int i = 0;
		for (int a = 0; a < 3; a++) {
			for (int b = 0; b < 3; b++) {
				int x = site.x + (f[0] + (f[1] - f[0]) * a / 2) / 2;
				int z = site.z + (f[2] + (f[3] - f[2]) * b / 2) / 2;
				hs[i++] = Ground.surface(level, x, z).getY();
			}
		}
		java.util.Arrays.sort(hs);
		return hs[4] - 1;
	}

	// ------------------------------------------------------------------ helpers for other systems

	public static void reward(ServerPlayer p, net.minecraft.world.item.ItemStack stack) {
		dev.afterworld.util.Inv.give(p, stack);
	}

	public static Identifier lootId(String name) {
		return dev.afterworld.Afterworld.id("chests/" + name);
	}

	static void sparkle(ServerLevel level, Vec3 at) {
		Fx.burst(level, ParticleTypes.END_ROD, at, 20, 0.5, 0.05);
		Fx.sound(level, at, AwSounds.WORLD_SHARD, 1.0F, 1.0F);
	}

	static Component text(String s, int color) {
		return Component.literal(s).withColor(color);
	}
}
