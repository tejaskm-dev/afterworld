package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Looks at the world the way a player does, to find rendering and terrain problems: under water,
 * at a river's edge, on a mountain top among the clouds, and in each kind of cold forest.
 */
public class AfterworldDiagTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed(System.getenv().getOrDefault("AW_DIAG_SEED", "afterworld")))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.WAYFARER);
			var server = sp.getServer();
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(8);
				mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			});
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 2000");
			server.runCommand("gamemode spectator @p");
			for (String biome : System.getenv().getOrDefault("AW_DIAG_BIOMES",
					"afterworld:silverrun,afterworld:mirrorsea,afterworld:frostmere_pines,afterworld:whitewaste,afterworld:skyspire_peaks,afterworld:windworn_highlands").split(",")) {
				visit(context, sp, server, biome.trim());
			}
		}
	}

	private static void visit(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext server, String biome) {
		BlockPos at = server.computeOnServer(s -> {
			var level = s.overworld();
			var key = Identifier.parse(biome);
			var found = level.findClosestBiome3d(h -> h.unwrapKey().map(k -> k.identifier().equals(key)).orElse(false),
				BlockPos.ZERO.atY(64), 6400, 32, 64);
			return found == null ? null : found.getFirst();
		});
		if (at == null) {
			Afterworld.LOG.info("DIAG {} not found", biome);
			return;
		}
		String name = biome.substring(biome.indexOf(':') + 1);
		server.runCommand("tp @p " + at.getX() + " 200 " + at.getZ());
		context.waitTicks(60);
		int top = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()));
		int floor = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.OCEAN_FLOOR, at.getX(), at.getZ()));
		Afterworld.LOG.info("DIAG {} at {} {} surface {} floor {}", biome, at.getX(), at.getZ(), top, floor);
		server.runCommand("tp @p " + at.getX() + " " + (top + 6) + " " + (at.getZ() - 14) + " facing " + at.getX() + " " + top + " " + at.getZ());
		context.waitTicks(80);
		sp.getConnection().waitForChunksRender(false);
		context.takeScreenshot("diag-" + name + "-surface");
		if (top - floor >= 3) {
			// Under the water, looking along it.
			int y = floor + 1 + (top - floor) / 2;
			server.runCommand("tp @p " + at.getX() + " " + y + " " + at.getZ() + " facing " + (at.getX() + 10) + " " + (y - 1) + " " + at.getZ());
			context.waitTicks(30);
			context.takeScreenshot("diag-" + name + "-underwater");
		}
		// From above, to see the shape of the land and any odd terrain.
		server.runCommand("tp @p " + (at.getX() - 30) + " " + (top + 30) + " " + (at.getZ() - 30) + " facing " + at.getX() + " " + top + " " + at.getZ());
		context.waitTicks(60);
		context.takeScreenshot("diag-" + name + "-above");
	}
}
