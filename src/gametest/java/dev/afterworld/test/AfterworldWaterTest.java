package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Water as the shader pack draws it: a broad stretch of open water near spawn photographed from
 * its shore at noon, from above, at a glancing angle at sunset, at night and from under the
 * surface, with a little falling water beside it; then the same views with the pack switched off,
 * to tell the shaders' doing from the textures'.
 */
public class AfterworldWaterTest implements FabricClientGameTest {
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
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			// Creative and flying, not a spectator: a spectator's eyes are never "in water", so the
			// pack's underwater view would not show.
			server.runCommand("gamemode creative @p");
			context.waitTicks(5);
			context.runOnClient(mc -> {
				mc.player.getAbilities().flying = true;
				mc.player.onUpdateAbilities();
			});
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			// Open water: a point whose water is at least four deep, with water all round it for ten blocks.
			int[] w = server.computeOnServer(s -> water(s.overworld(), spawn));
			Afterworld.LOG.info("WATERTEST open water at {} {} {}, shore at {} {}", w[0], w[1], w[2], w[3], w[4]);
			int wx = w[0];
			int wy = w[1];
			int wz = w[2];
			int sx = w[3];
			int sz = w[4];
			// Falling water on the shore, to see a stream drawn.
			int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz));
			// The shore may stand well above the water: never film from inside it.
			int eye = Math.max(ground + 2, wy + 3);
			server.runCommand(String.format("fill %d %d %d %d %d %d stone", sx, ground, sz, sx, ground + 3, sz));
			server.runCommand(String.format("setblock %d %d %d water", sx, ground + 4, sz));
			for (boolean shaders : new boolean[] {true, false}) {
				String tag = shaders ? "" : "-noshader";
				setShaders(context, shaders);
				if (shaders && !inUse(context)) {
					// Iris quietly falls back to plain rendering when a pack does not compile: say so.
					throw new AssertionError("the shader pack failed to load (see the log for the compile error)");
				}
				server.runCommand("time set 6000");
				shot(context, sp, "water-shore" + tag, sx, eye, sz, wx, wy, wz);
				shot(context, sp, "water-above" + tag, wx - 6, wy + 14, wz - 6, wx + 4, wy, wz + 4);
				shot(context, sp, "water-stream" + tag, sx + 5, ground + 4, sz + 5, sx, ground + 2, sz);
				server.runCommand("time set 12400");
				shot(context, sp, "water-sunset" + tag, sx, eye, sz, wx * 2 - sx, wy, wz * 2 - sz);
				server.runCommand("time set 18000");
				shot(context, sp, "water-night" + tag, sx, eye, sz, wx, wy, wz);
				server.runCommand("time set 6000");
				shot(context, sp, "water-under-up" + tag, wx, wy - 2, wz, wx + 6, wy + 4, wz + 2);
				shot(context, sp, "water-under-across" + tag, wx, wy - 2, wz, wx + 10, wy - 2, wz + 3);
			}
			setShaders(context, true);
			Afterworld.LOG.info("WATERTEST done");
		}
	}

	/** {wx, water surface y, wz, shore x, shore z}: open water near spawn and the nearest dry land to it. */
	private static int[] water(ServerLevel level, BlockPos spawn) {
		for (int r = 32; r < 1600; r += 24) {
			for (int a = 0; a < 360; a += 10) {
				int x = spawn.getX() + (int) (Math.cos(Math.toRadians(a)) * r);
				int z = spawn.getZ() + (int) (Math.sin(Math.toRadians(a)) * r);
				level.getChunk(x >> 4, z >> 4);
				if (depth(level, x, z) < 4) {
					continue;
				}
				boolean open = true;
				for (int[] o : new int[][] {{10, 0}, {-10, 0}, {0, 10}, {0, -10}}) {
					level.getChunk((x + o[0]) >> 4, (z + o[1]) >> 4);
					open &= depth(level, x + o[0], z + o[1]) >= 2;
				}
				if (!open) {
					continue;
				}
				int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				// Walk back toward spawn to the first dry ground: the shore.
				double dx = spawn.getX() - x;
				double dz = spawn.getZ() - z;
				double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
				for (int k = 1; k < 120; k++) {
					int qx = x + (int) Math.round(dx / len * k);
					int qz = z + (int) Math.round(dz / len * k);
					level.getChunk(qx >> 4, qz >> 4);
					if (depth(level, qx, qz) == 0) {
						return new int[] {x, surface, z, qx, qz};
					}
				}
				return new int[] {x, surface, z, x + 12, z + 12};
			}
		}
		return new int[] {spawn.getX(), 63, spawn.getZ(), spawn.getX() + 8, spawn.getZ() + 8};
	}

	private static int depth(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
	}

	/** Whether Iris is drawing with a shader pack (false when the pack failed to compile, or Iris is absent). */
	private static boolean inUse(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object iris = api.getMethod("getInstance").invoke(null);
				return (Boolean) api.getMethod("isShaderPackInUse").invoke(iris);
			} catch (Throwable t) {
				return true;
			}
		});
	}

	/** Switches the shader pack on or off through Iris's API (asked by name: Iris is only in the test pack). */
	private static void setShaders(ClientGameTestContext context, boolean on) {
		context.runOnClient(mc -> {
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object iris = api.getMethod("getInstance").invoke(null);
				Object config = api.getMethod("getConfig").invoke(iris);
				config.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
			} catch (Throwable t) {
				Afterworld.LOG.warn("WATERTEST could not switch shaders {}: {}", on ? "on" : "off", t.toString());
			}
		});
		context.waitTicks(40);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		sp.getServer().runCommand(String.format("tp @p %d %d %d facing %d %d %d", x, y, z, lx, ly, lz));
		context.runOnClient(mc -> {
			mc.player.getAbilities().flying = true;
			mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		});
		context.waitTicks(30);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
