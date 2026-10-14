package dev.afterworld.world;

import dev.afterworld.Afterworld;
import dev.afterworld.world.gen.AwBiomes;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/**
 * Picks where a new world's travellers wake up. Vanilla is happy to drop you on a scrap of island
 * in a frozen sea; Afterworld wants you on the edge of a real landmass, in gentle country, with the
 * world opening out in front of you. Runs once, when a world is first started, before anything is
 * built around the spawn.
 */
public final class SpawnFinder {
	/** Where a first morning should be spent. */
	private static final Set<ResourceKey<Biome>> WELCOMING = Set.of(AwBiomes.VESPER_DOWNS, AwBiomes.MOSSMERE_WOOD, AwBiomes.PALE_REACH,
		AwBiomes.PETALFALL, AwBiomes.DUSKPINE_WEALD, AwBiomes.ASHEN_MOOR);
	/** Not land: seas, rivers and shores. */
	private static final Set<ResourceKey<Biome>> WET = Set.of(AwBiomes.MIRRORSEA, AwBiomes.MIRRORDEEP, AwBiomes.EMBER_SHALLOWS, AwBiomes.RIMESEA,
		AwBiomes.RIMEDEEP, AwBiomes.RIMEFLOE, AwBiomes.SILVERRUN, AwBiomes.BONE_SHORE, AwBiomes.BREAKSTONE_SHORE);
	private static final Set<ResourceKey<Biome>> COLD = Set.of(AwBiomes.FROSTMERE_PINES, AwBiomes.WHITEWASTE, AwBiomes.GLIMMERFROST,
		AwBiomes.RIMEGLASS_SPIRES, AwBiomes.SKYSPIRE_PEAKS);

	private SpawnFinder() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(SpawnFinder::choose);
	}

	private static void choose(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		if (data.founded || data.spawnChosen) {
			return;
		}
		if (data.atlas.containsKey("town")) {
			// The Atlas already chose the waking place (with this same scoring) before the world was
			// generated; the camp is built there and moves the spawn to its gate.
			data.spawnChosen = true;
			data.setDirty();
			return;
		}
		data.spawnChosen = true;
		data.setDirty();
		ServerLevel level = server.overworld();
		BlockPos start = level.getRespawnData().pos();
		long t0 = System.nanoTime();
		BlockPos best = null;
		double bestScore = -1;
		// Spiral out in rings of candidates 256 blocks apart; the first very good one wins.
		search:
		for (int ring = 0; ring <= 24; ring++) {
			int n = ring == 0 ? 1 : ring * 8;
			for (int i = 0; i < n; i++) {
				double a = ring == 0 ? 0 : i * Math.PI * 2 / n;
				int x = start.getX() + (int) Math.round(Math.cos(a) * ring * 256);
				int z = start.getZ() + (int) Math.round(Math.sin(a) * ring * 256);
				double score = score(level, x, z);
				if (score > bestScore) {
					bestScore = score;
					best = new BlockPos(x, 0, z);
				}
				if (score >= 3.6) {
					break search;
				}
			}
		}
		if (best == null || bestScore < 1.5) {
			Afterworld.LOG.info("Afterworld spawn: kept vanilla's spawn ({} best score {})", start, bestScore);
			return;
		}
		// Generate the chunk so its height is real, then stand on its surface.
		level.getChunk(best.getX() >> 4, best.getZ() >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, best.getX(), best.getZ());
		BlockPos spawn = new BlockPos(best.getX(), y, best.getZ());
		level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawn, 0.0F, 0.0F));
		Afterworld.LOG.info("Afterworld spawn: moved from {} to {} (score {}, {} ms)", start.toShortString(), spawn.toShortString(),
			String.format("%.2f", bestScore), (System.nanoTime() - t0) / 1_000_000);
	}

	/**
	 * How good a place this is to wake up: mostly land for 200 blocks around (2 points), the centre
	 * in welcoming country (1), and little snow (up to 1).
	 */
	public static double score(ServerLevel level, int cx, int cz) {
		ResourceKey<Biome> centre = biome(level, cx, cz);
		if (centre == null || !WELCOMING.contains(centre)) {
			return 0;
		}
		int land = 0;
		int cold = 0;
		int total = 0;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				ResourceKey<Biome> b = biome(level, cx + dx * 64, cz + dz * 64);
				total++;
				if (b != null && !WET.contains(b)) {
					land++;
				}
				if (b != null && COLD.contains(b)) {
					cold++;
				}
			}
		}
		double landShare = land / (double) total;
		if (landShare < 0.75) {
			return landShare;
		}
		return landShare * 2 + 1 + (1 - cold / (double) total);
	}

	private static ResourceKey<Biome> biome(ServerLevel level, int x, int z) {
		Holder<Biome> b = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(72), QuartPos.fromBlock(z));
		return b.unwrapKey().orElse(null);
	}
}
