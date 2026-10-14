package dev.afterworld.test;

import dev.afterworld.Afterworld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.QuartPos;

/**
 * Writes a biome map of the land around a point (AW_MAP_X, AW_MAP_Z, AW_MAP_RADIUS, AW_MAP_STEP)
 * for a given seed (AW_DIAG_SEED) to biomemap.txt, one "x z biome" line per sample, so the shape
 * and size of each region can be looked at as a picture.
 */
public class AfterworldMapTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed(System.getenv().getOrDefault("AW_DIAG_SEED", "afterworld")))
			.create()) {
			int cx = Integer.parseInt(System.getenv().getOrDefault("AW_MAP_X", "0"));
			int cz = Integer.parseInt(System.getenv().getOrDefault("AW_MAP_Z", "0"));
			int radius = Integer.parseInt(System.getenv().getOrDefault("AW_MAP_RADIUS", "2048"));
			int step = Integer.parseInt(System.getenv().getOrDefault("AW_MAP_STEP", "32"));
			String text = sp.getServer().computeOnServer(s -> {
				var level = s.overworld();
				StringBuilder b = new StringBuilder();
				for (int z = cz - radius; z <= cz + radius; z += step) {
					for (int x = cx - radius; x <= cx + radius; x += step) {
						var biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(72), QuartPos.fromBlock(z));
						b.append(x).append(' ').append(z).append(' ')
							.append(biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?")).append('\n');
					}
				}
				return b.toString();
			});
			try {
				Files.writeString(Path.of("biomemap.txt"), text);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			Afterworld.LOG.info("MAPTEST wrote {} samples", text.lines().count());
		}
	}
}
