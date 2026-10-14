package dev.afterworld.world.atlas;

import dev.afterworld.Afterworld;
import dev.afterworld.world.AfterworldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * The Atlas: where a world's one-of-a-kind places stand. Most of the land is left to chance (ruins,
 * camps, landmarks on their random grids), but the five Wardens' lairs are laid out like a map
 * drawn before the world: one of each, on a wide ring round the waking place, a fifth of the way
 * round from each other. Which way the ring is turned, the order of the lairs on it and how far out
 * each one lies change with the seed; each lair then finds the nearest dry, gentle ground in its
 * own country (blossom for the dojo, sand for the temple...). The plan is drawn as the overworld
 * loads, before a single chunk exists, and kept with the world so it never moves. The ring is wide
 * and far out (a long road to each Warden), with the country between left for the smaller places.
 */
public final class Atlas {
	/** A Warden and the structure that is its lair. */
	public record Lair(String warden, String structure) {
	}

	public static final List<Lair> LAIRS = List.of(
		new Lair("shogun", "lantern_dojo"),
		new Lair("lich", "crimson_necropolis"),
		new Lair("colossus", "rimeback_den"),
		new Lair("sentinel", "sun_temple"),
		new Lair("warden", "gilded_vault"));

	/** Places kept close to the town, one each: the first gate of the Warden's Ladder. */
	private record Near(String structure, int min, int max) {
	}

	private static final List<Near> NEAR = List.of(new Near("ladder_gate_first", 450, 900));

	/** The ring the lairs stand on: between these distances from the town. */
	private static final int RING_IN = 4500;
	private static final int RING_OUT = 8500;
	/** How finely the ring is surveyed for its countries. */
	private static final int SURVEY = 160;
	/** No two lairs closer than this. */
	private static final int APART = 2400;
	/** Steeper than this (blocks of rise across the footprint) and the ground isn't gentle. */
	private static final int GENTLE = 10;
	private static final int NOT_GENTLE = 1_000;
	/** How many spots of its own country a lair tries before settling for rougher ground. */
	private static final int TRIES = 60;

	/** A surveyed spot on the ring. */
	private record Spot(int x, int z, double angle, Holder<Biome> biome) {
	}

	private static volatile Map<String, ChunkPos> placed = Map.of();

	private Atlas() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Afterworld.id("atlas"), AtlasPlacement.CODEC);
		// The overworld is announced before its spawn is chosen, so before any chunk is generated.
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				long t0 = System.nanoTime();
				placed = Map.copyOf(plan(server, level));
				reserve(server, level);
				Afterworld.LOG.info("Atlas drawn in {} ms: town {} lairs {}", (System.nanoTime() - t0) / 1_000_000, town == null ? "?" : town.toShortString(), describe());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			placed = Map.of();
			town = null;
			Siting.clearReserved();
		});
	}

	/**
	 * Where the waking town stands: the nearest dry, gentle ground big enough for it, sought out
	 * from the point the game looks for a spawn from (with some terrain mods that is simply the
	 * world's middle, which may well be sea). Chosen once, as the overworld loads, and kept.
	 */
	public static BlockPos townCentre(ServerLevel level) {
		BlockPos t = town;
		if (t == null) {
			ChunkPos o = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
			return new BlockPos(o.getMiddleBlockX(), 64, o.getMiddleBlockZ());
		}
		return t;
	}

	private static volatile @Nullable BlockPos town;

	/**
	 * The waking place: welcoming country on the edge of a real landmass (SpawnFinder's scoring,
	 * rings 256 blocks apart out to six kilometres), on ground dry and gentle enough for the whole
	 * town; failing that, the nearest such ground to the world's middle.
	 */
	private static BlockPos findTown(ServerLevel level) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		ChunkPos o = gen.getOrigin(rs);
		int ox = o.getMiddleBlockX();
		int oz = o.getMiddleBlockZ();
		BlockPos welcome = null;
		double welcomeScore = 0;
		search:
		for (int ring = 0; ring <= 24; ring++) {
			int n = ring == 0 ? 1 : ring * 8;
			for (int i = 0; i < n; i++) {
				double a = ring == 0 ? 0 : i * Math.PI * 2 / n;
				int x = ox + (int) Math.round(Math.cos(a) * ring * 256);
				int z = oz + (int) Math.round(Math.sin(a) * ring * 256);
				double score = dev.afterworld.world.SpawnFinder.score(level, x, z);
				if (score < 1.5 || score <= welcomeScore) {
					continue;
				}
				// Welcoming: now find gentle ground for the town close by.
				BlockPos gentle = gentleNear(level, gen, rs, x, z);
				if (gentle != null) {
					welcome = gentle;
					welcomeScore = score;
					if (score >= 3.6) {
						break search;
					}
				}
			}
		}
		if (welcome != null) {
			return welcome;
		}
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		int tries = 0;
		for (BlockPos.MutableBlockPos sp : BlockPos.spiralAround(BlockPos.ZERO, 20, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH)) {
			if (++tries > 260) {
				break;
			}
			int x = ox + sp.getX() * 32;
			int z = oz + sp.getZ() * 32;
			int relief = townRelief(level, gen, rs, x, z);
			if (relief <= 12) {
				return new BlockPos(x, 64, z);
			}
			if (relief < bestRelief) {
				bestRelief = relief;
				best = new BlockPos(x, 64, z);
			}
		}
		return best != null && bestRelief < NOT_GENTLE ? best : new BlockPos(ox, 64, oz);
	}

	/** Dry, gentle ground for the town within about a hundred blocks of (x, z), or null. */
	private static @Nullable BlockPos gentleNear(ServerLevel level, ChunkGenerator gen, RandomState rs, int x, int z) {
		int tries = 0;
		for (BlockPos.MutableBlockPos sp : BlockPos.spiralAround(BlockPos.ZERO, 3, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH)) {
			if (++tries > 49) {
				break;
			}
			int cx = x + sp.getX() * 32;
			int cz = z + sp.getZ() * 32;
			if (townRelief(level, gen, rs, cx, cz) <= 12) {
				return new BlockPos(cx, 64, cz);
			}
		}
		return null;
	}

	/** Like {@link #relief} but over the town's whole disc (it is 160 across). */
	private static int townRelief(ServerLevel level, ChunkGenerator gen, RandomState rs, int x, int z) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int[] o : new int[][] {{0, 0}, {-40, -40}, {40, -40}, {-40, 40}, {40, 40}, {0, -70}, {0, 70}, {-70, 0}, {70, 0}, {-50, -50}, {50, 50}, {-50, 50}, {50, -50}}) {
			int surface = gen.getBaseHeight(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG, level, rs);
			int floor = gen.getBaseHeight(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
			if (surface - floor > 2 || surface <= gen.getSeaLevel() + 1) {
				return NOT_GENTLE;
			}
			lo = Math.min(lo, surface);
			hi = Math.max(hi, surface);
		}
		return hi - lo;
	}

	/** Nothing else may be built on the town, the lairs or the great powers' seats. */
	private static void reserve(MinecraftServer server, ServerLevel level) {
		Siting.clearReserved();
		BlockPos town = townCentre(level);
		Siting.reserve(town.getX() >> 4, town.getZ() >> 4, TOWN_RESERVE);
		for (ChunkPos c : placed.values()) {
			Siting.reserve(c.x(), c.z(), LAIR_RESERVE);
		}
		try {
			for (dev.afterworld.site.Site site : AfterworldData.get(server).sites.values()) {
				if (SEATS.contains(site.kind)) {
					Siting.reserve(site.x >> 4, site.z >> 4, LAIR_RESERVE);
				}
			}
		} catch (RuntimeException e) {
			Afterworld.LOG.warn("The Atlas couldn't read the world's sites", e);
		}
	}

	/** The great powers' seats and the shrines and village of the story, laid out by WorldSim. */
	public static final java.util.Set<dev.afterworld.site.SiteKind> SEATS = java.util.EnumSet.of(dev.afterworld.site.SiteKind.SHRINE,
		dev.afterworld.site.SiteKind.CATHEDRAL, dev.afterworld.site.SiteKind.VAULT, dev.afterworld.site.SiteKind.UNREMEMBERED);
	/** In chunks: the town with its blended ring, and a lair or seat with its clearing. */
	private static final int TOWN_RESERVE = 8;
	public static final int LAIR_RESERVE = 3;

	/** The chunk a site's structure starts in, in the world being played (null: it has no place). */
	public static @Nullable ChunkPos chunk(String structure) {
		return placed.get(structure);
	}

	/** A Warden's lair ("shogun"), as the middle of its start chunk at sea level, or null. */
	public static @Nullable BlockPos lair(String warden) {
		for (Lair l : LAIRS) {
			if (l.warden().equals(warden)) {
				ChunkPos c = chunk(l.structure());
				return c == null ? null : new BlockPos(c.getMiddleBlockX(), 63, c.getMiddleBlockZ());
			}
		}
		return null;
	}

	public static String describe() {
		StringBuilder b = new StringBuilder();
		for (Lair l : LAIRS) {
			ChunkPos c = placed.get(l.structure());
			if (c != null) {
				b.append(l.warden()).append(' ').append(c.getMiddleBlockX()).append(',').append(c.getMiddleBlockZ())
					.append(" (").append((int) Math.hypot(c.getMiddleBlockX() - (town == null ? 0 : town.getX()), c.getMiddleBlockZ() - (town == null ? 0 : town.getZ())))
					.append(" m) ");
			}
		}
		return b.toString().trim();
	}

	// ------------------------------------------------------------------ drawing the plan

	private static Map<String, ChunkPos> plan(MinecraftServer server, ServerLevel level) {
		AfterworldData data = null;
		try {
			data = AfterworldData.get(server);
		} catch (RuntimeException e) {
			Afterworld.LOG.warn("The Atlas can't keep its plan with this world", e);
		}
		Map<String, ChunkPos> out = new HashMap<>();
		Long keptTown = data == null ? null : data.atlas.get("town");
		if (keptTown == null && data != null && data.campCenter != Long.MIN_VALUE) {
			// A world from before the Atlas: its camp already stands.
			keptTown = data.campCenter;
		}
		town = keptTown != null ? BlockPos.of(keptTown) : findTown(level);
		if (data != null && keptTown == null) {
			data.atlas.put("town", town.asLong());
			data.setDirty();
		}
		if (data != null) {
			for (Lair l : LAIRS) {
				Long at = data.atlas.get(l.structure());
				if (at != null) {
					out.put(l.structure(), ChunkPos.unpack(at));
				}
			}
			for (Near n : NEAR) {
				Long at = data.atlas.get(n.structure());
				if (at != null) {
					out.put(n.structure(), ChunkPos.unpack(at));
				}
			}
			if (out.size() == LAIRS.size() + NEAR.size()) {
				return out;
			}
		}
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		BiomeResolver biomes = gen.getBiomeSource().createCachingResolver(rs);
		Map<Long, Integer> reliefs = new HashMap<>();
		List<BlockPos> taken = new ArrayList<>();
		for (ChunkPos c : out.values()) {
			taken.add(new BlockPos(c.getMiddleBlockX(), 0, c.getMiddleBlockZ()));
		}
		lairs(level, gen, rs, biomes, out, taken, reliefs);
		near(level, gen, rs, biomes, out, taken, reliefs);
		if (data != null) {
			for (var e : out.entrySet()) {
				data.atlas.put(e.getKey(), e.getValue().pack());
			}
			data.setDirty();
		}
		return out;
	}

	/** The places kept close to the town: each on dry, gentle ground of its own country, at a seeded bearing. */
	private static void near(ServerLevel level, ChunkGenerator gen, RandomState rs, BiomeResolver biomes, Map<String, ChunkPos> out,
		List<BlockPos> taken, Map<Long, Integer> reliefs) {
		RandomSource r = RandomSource.create(level.getSeed() ^ 0x6A7E5L);
		int tx = town.getX();
		int tz = town.getZ();
		for (Near n : NEAR) {
			double want = r.nextDouble() * Math.PI * 2.0;
			if (out.containsKey(n.structure())) {
				continue;
			}
			List<Spot> ring = new ArrayList<>();
			for (int x = -n.max(); x <= n.max(); x += 48) {
				for (int z = -n.max(); z <= n.max(); z += 48) {
					double d = Math.hypot(x, z);
					if (d >= n.min() && d <= n.max()) {
						ring.add(new Spot(tx + x, tz + z, Math.atan2(z, x),
							biomes.getNoiseBiome(QuartPos.fromBlock(tx + x), QuartPos.fromBlock(72), QuartPos.fromBlock(tz + z))));
					}
				}
			}
			TagKey<Biome> home = TagKey.create(Registries.BIOME, Afterworld.id("has_structure/" + n.structure()));
			List<Spot> homes = ring.stream().filter(sp -> sp.biome().is(home)).toList();
			BlockPos at = choose(level, gen, rs, biomes, home, homes, want, taken, GENTLE, reliefs);
			if (at == null) {
				at = choose(level, gen, rs, biomes, null, ring, want, taken, GENTLE + 6, reliefs);
			}
			if (at == null) {
				at = new BlockPos(tx + (int) (Math.cos(want) * n.min()), 0, tz + (int) (Math.sin(want) * n.min()));
			}
			out.put(n.structure(), ChunkPos.containing(at));
			taken.add(at);
		}
	}

	/** The five lairs on their ring (those not already placed). */
	private static void lairs(ServerLevel level, ChunkGenerator gen, RandomState rs, BiomeResolver biomes, Map<String, ChunkPos> out,
		List<BlockPos> taken, Map<Long, Integer> reliefs) {
		boolean all = true;
		for (Lair l : LAIRS) {
			all &= out.containsKey(l.structure());
		}
		if (all) {
			return;
		}
		RandomSource r = RandomSource.create(level.getSeed() ^ 0x0A71A5L);
		// Each lair's ideal bearing: the ring turned by the seed, the lairs in a seeded order round it.
		double base = r.nextDouble() * Math.PI * 2.0;
		List<Lair> slots = new ArrayList<>(LAIRS);
		Util.shuffle(slots, r);
		Map<Lair, Double> ideal = new HashMap<>();
		for (int i = 0; i < slots.size(); i++) {
			ideal.put(slots.get(i), base + i * (Math.PI * 2.0 / slots.size()));
		}
		// Survey the ring round the town once: which country lies where.
		int tx = town.getX();
		int tz = town.getZ();
		List<Spot> survey = new ArrayList<>();
		for (int x = -RING_OUT; x <= RING_OUT; x += SURVEY) {
			for (int z = -RING_OUT; z <= RING_OUT; z += SURVEY) {
				double d = Math.hypot(x, z);
				if (d >= RING_IN && d <= RING_OUT) {
					survey.add(new Spot(tx + x, tz + z, Math.atan2(z, x),
						biomes.getNoiseBiome(QuartPos.fromBlock(tx + x), QuartPos.fromBlock(72), QuartPos.fromBlock(tz + z))));
				}
			}
		}
		Map<Lair, List<Spot>> homes = new HashMap<>();
		for (Lair l : LAIRS) {
			TagKey<Biome> home = TagKey.create(Registries.BIOME, Afterworld.id("has_structure/" + l.structure()));
			homes.put(l, survey.stream().filter(sp -> sp.biome().is(home)).toList());
		}
		// The rarest countries choose first, so a lair with one patch of its land on the ring gets it.
		List<Lair> order = new ArrayList<>(LAIRS);
		order.sort(java.util.Comparator.comparingInt((Lair l) -> homes.get(l).size()).thenComparing(Lair::warden));
		for (Lair l : order) {
			if (out.containsKey(l.structure())) {
				continue;
			}
			double want = ideal.get(l);
			TagKey<Biome> home = TagKey.create(Registries.BIOME, Afterworld.id("has_structure/" + l.structure()));
			BlockPos at = choose(level, gen, rs, biomes, home, homes.get(l), want, taken, GENTLE, reliefs);
			if (at == null) {
				at = choose(level, gen, rs, biomes, home, homes.get(l), want, taken, GENTLE + 6, reliefs);
			}
			if (at == null) {
				at = choose(level, gen, rs, biomes, null, homes.get(l), want, taken, GENTLE + 6, reliefs);
			}
			if (at == null) {
				// Its country isn't on the ring at all: the gentlest dry ground near its bearing.
				Afterworld.LOG.warn("The Atlas found no {} on the ring; the lair stands where it can", l.structure());
				at = choose(level, gen, rs, biomes, null, survey, want, taken, GENTLE + 8, reliefs);
			}
			if (at == null) {
				at = new BlockPos(tx + (int) (Math.cos(want) * (RING_IN + RING_OUT) / 2), 0, tz + (int) (Math.sin(want) * (RING_IN + RING_OUT) / 2));
			}
			out.put(l.structure(), ChunkPos.containing(at));
			taken.add(at);
		}
	}

	/**
	 * Of these spots, the one nearest the bearing {@code want} that is clear of other lairs, dry, no
	 * rougher than {@code rough} and (if {@code home} is given) wholly inside that country, edges and all.
	 */
	private static @Nullable BlockPos choose(ServerLevel level, ChunkGenerator gen, RandomState rs, BiomeResolver biomes, @Nullable TagKey<Biome> home,
		List<Spot> spots, double want, List<BlockPos> taken, int rough, Map<Long, Integer> reliefs) {
		List<Spot> near = new ArrayList<>(spots);
		near.sort(java.util.Comparator.comparingDouble(sp -> Math.abs(Math.atan2(Math.sin(sp.angle() - want), Math.cos(sp.angle() - want)))));
		int tries = 0;
		for (Spot sp : near) {
			if (crowded(sp.x(), sp.z(), taken)) {
				continue;
			}
			if (home != null && !inside(biomes, home, sp.x(), sp.z())) {
				continue;
			}
			if (++tries > TRIES) {
				break;
			}
			int relief = reliefs.computeIfAbsent(BlockPos.asLong(sp.x(), 0, sp.z()), k -> relief(level, gen, rs, sp.x(), sp.z()));
			if (relief <= rough) {
				return new BlockPos(sp.x(), 0, sp.z());
			}
		}
		return null;
	}

	/** Whether the ground for a lair and its clearing (40 blocks each way) is all the one country. */
	private static boolean inside(BiomeResolver biomes, TagKey<Biome> home, int x, int z) {
		for (int[] o : new int[][] {{-40, -40}, {40, -40}, {-40, 40}, {40, 40}, {0, -44}, {0, 44}, {-44, 0}, {44, 0}}) {
			if (!biomes.getNoiseBiome(QuartPos.fromBlock(x + o[0]), QuartPos.fromBlock(72), QuartPos.fromBlock(z + o[1])).is(home)) {
				return false;
			}
		}
		return true;
	}

	private static boolean crowded(int x, int z, List<BlockPos> taken) {
		for (BlockPos t : taken) {
			if (Math.hypot(t.getX() - x, t.getZ() - z) < APART) {
				return true;
			}
		}
		return false;
	}

	/** How much the ground rises and falls across a lair's footprint, or a great deal if it is wet or low. */
	private static int relief(ServerLevel level, ChunkGenerator gen, RandomState rs, int x, int z) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int[] o : new int[][] {{0, 0}, {-22, -22}, {22, -22}, {-22, 22}, {22, 22}, {0, -38}, {0, 38}, {-38, 0}, {38, 0}}) {
			int surface = gen.getBaseHeight(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG, level, rs);
			int floor = gen.getBaseHeight(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
			if (surface - floor > 2 || surface <= gen.getSeaLevel() + 1) {
				return NOT_GENTLE;
			}
			lo = Math.min(lo, surface);
			hi = Math.max(hi, surface);
		}
		return hi - lo;
	}
}
