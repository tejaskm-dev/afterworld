package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How the world looks with the pack's shader and textures: the same view at noon, at sunset (into
 * the sun), and at night; a lake; a forest; and the inventory in its parchment.
 */
public class AfterworldLookTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> mc.options.renderDistance().set(8));
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			BlockPos top = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn));
			look(context, sp, "look-noon", 6000, top.offset(0, 18, 0), 90, 25);
			look(context, sp, "look-noon-east", 6000, top.offset(0, 18, 0), -90, 20);
			look(context, sp, "look-sunset", 12300, top.offset(0, 18, 0), 90, 5);
			look(context, sp, "look-night", 18000, top.offset(0, 18, 0), 90, 15);
			look(context, sp, "look-dawn", 23300, top.offset(0, 18, 0), -90, 5);
			BlockPos lake = server.computeOnServer(s -> {
				var level = s.overworld();
				for (int r = 16; r < 400; r += 16) {
					for (int a = 0; a < 16; a++) {
						double t = a * Math.PI / 8;
						BlockPos p = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, spawn.offset((int) (Math.cos(t) * r), 0, (int) (Math.sin(t) * r))).below();
						if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.offset(4, 0, 4)).is(FluidTags.WATER)) {
							return p;
						}
					}
				}
				return null;
			});
			if (lake != null) {
				look(context, sp, "look-water", 6000, lake.offset(-6, 6, -6), -45, 30);
				look(context, sp, "look-water-sunset", 12300, lake.offset(-6, 4, -6), 90, 12);
			}
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
			context.waitTicks(20);
			context.takeScreenshot("look-inventory");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			Afterworld.LOG.info("LOOKTEST done");
		}
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext sp, String name, int time, BlockPos at, float yaw, float pitch) {
		var server = sp.getServer();
		server.runCommand("time set " + time);
		server.runCommand("tp @p " + at.getX() + " " + at.getY() + " " + at.getZ() + " " + yaw + " " + pitch);
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.runOnClient(mc -> mc.levelExtractor.allChanged());
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(30);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}
}
