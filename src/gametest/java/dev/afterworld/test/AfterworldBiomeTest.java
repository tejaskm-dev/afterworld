package dev.afterworld.test;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.world.gen.AwBiomes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Finds each Afterworld biome in a normal world and photographs it from the air and the ground. */
public class AfterworldBiomeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.WAYFARER);
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("time set 5000");
			server.runCommand("gamemode spectator @p");
			for (ResourceKey<Biome> key : AwBiomes.ALL) {
				String name = key.identifier().getPath();
				BlockPos found = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, 6400, 32, 64);
					return hit == null ? null : hit.getFirst();
				});
				if (found == null) {
					Afterworld.LOG.error("BIOMETEST could not find {}", name);
					continue;
				}
				server.runCommand("tp @p " + found.getX() + " 200 " + found.getZ());
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				int y = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ()));
				context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
				Afterworld.LOG.info("BIOMETEST {} at {} {} {}", name, found.getX(), y, found.getZ());
				server.runCommand("tp @p " + (found.getX() + 34) + " " + (y + 30) + " " + (found.getZ() + 34) + " facing " + found.getX() + " " + y + " " + found.getZ());
				context.waitTicks(30);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("biome-" + name + "-air");
				server.runCommand("tp @p " + (found.getX() + 14) + " " + (y + 4) + " " + (found.getZ() + 14) + " facing " + (found.getX() - 10) + " " + (y + 2) + " " + (found.getZ() - 10));
				context.waitTicks(30);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("biome-" + name + "-ground");
			}
		}
	}
}
