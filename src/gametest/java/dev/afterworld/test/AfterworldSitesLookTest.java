package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The rebuilt sites standing in the world: each is set down on open ground away from spawn and
 * photographed from the air, from its way in, and at dusk when its lights come up.
 */
public class AfterworldSitesLookTest implements FabricClientGameTest {
	/** The sites to photograph (sites5.REBUILT; the delves are photographed where worldgen puts them, in AfterworldUndergroundTest). */
	static final String[] SITES = {"moonlit_clearing", "hanged_lanterns", "druids_ring", "ranger_outpost", "frozen_watch_post", "snowbound_camp",
		"caravan_wreck", "shattered_caravan", "nomad_yurts", "wind_harp", "aurora_shrine", "starfall_crater", "cinder_watchtower",
		"colossus_graveyard", "bone_orchard", "beached_wreck", "drowned_shrine"};

	/** Layers below the ground line, where a site sinks deeper than the usual three (see sites5.SINK). */
	static final java.util.Map<String, Integer> SINK = java.util.Map.of("starfall_crater", 10);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(8);
				mc.gui.toastManager().clear();
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			java.util.List<BlockPos> used = new java.util.ArrayList<>();
			used.add(spawn);
			for (int i = 0; i < SITES.length; i++) {
				String name = SITES[i];
				int n = i;
				// Dry, fairly level ground in rings round spawn (which is on land), each site well
				// apart from the last and from the camp: never in a lake or the sea.
				BlockPos land = server.computeOnServer(s -> {
					var level = s.overworld();
					for (int r = 260; r < 1400; r += 70) {
						for (int a = 0; a < 360; a += 15) {
							double t = Math.toRadians(a + n * 7);
							int cx = spawn.getX() + (int) (Math.cos(t) * r);
							int cz = spawn.getZ() + (int) (Math.sin(t) * r);
							if (used.stream().anyMatch(u -> Math.hypot(u.getX() - cx, u.getZ() - cz) < 150)) {
								continue;
							}
							if (dry(level, cx, cz)) {
								BlockPos p = new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
								used.add(p);
								return p;
							}
						}
					}
					BlockPos p = spawn.offset(300 + n * 200, 0, -300);
					used.add(p);
					return p;
				});
				int x = land.getX();
				int z = land.getZ();
				server.runCommand("time set 6000");
				server.runCommand(String.format("tp @p %d 220 %d", x, z));
				context.waitTicks(40);
				sp.getConnection().waitForChunksRender(false, 2400);
				int y = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
				server.runCommand(String.format("execute positioned %d %d %d run afterworld build %s %d", x, y, z, name, SINK.getOrDefault(name, 3)));
				// The build is laid out from the chunk corner below the caller, a chunk to the west and north.
				int cx = ((x >> 4) << 4) + 8;
				int cz = ((z >> 4) << 4) + 8;
				shot(context, sp, "site-" + name + "-air", cx + 30, y + 26, cz + 30, cx, y + 2, cz);
				shot(context, sp, "site-" + name + "-way", cx + 1, y + 3, cz + 25, cx, y + 3, cz);
				server.runCommand("time set 13200");
				shot(context, sp, "site-" + name + "-dusk", cx - 26, y + 14, cz + 22, cx, y + 3, cz);
			}
			Afterworld.LOG.info("SITESLOOK done");
		}
	}

	/** Land at (x, z) and at four points twenty blocks round it, none of it water, none more than four blocks off. */
	private static boolean dry(net.minecraft.server.level.ServerLevel level, int x, int z) {
		// Heights are only known for loaded chunks (the rest read as the bottom of the world).
		for (int[] o : new int[][] {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}}) {
			level.getChunk((x + o[0]) >> 4, (z + o[1]) >> 4);
		}
		int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		for (int[] o : new int[][] {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}}) {
			int hh = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0], z + o[1]);
			int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + o[0], z + o[1]);
			var top = level.getBlockState(new BlockPos(x + o[0], hh - 1, z + o[1]));
			// Open ground for the camera: no thick canopy over it either.
			if (Math.abs(hh - h) > 6 || canopy - hh > 4 || !top.getFluidState().isEmpty() || top.is(net.minecraft.world.level.block.Blocks.ICE)) {
				return false;
			}
		}
		return h > level.getSeaLevel();
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		var server = sp.getServer();
		server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", x, y, z, lx, ly, lz));
		context.waitTicks(30);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.runOnClient(mc -> mc.levelExtractor.allChanged());
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
