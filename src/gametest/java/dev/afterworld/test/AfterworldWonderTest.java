package dev.afterworld.test;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import dev.afterworld.world.wonder.WonderData;
import dev.afterworld.world.wonder.WonderPiece;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Finds each wonder in a normal world and photographs it by day, from two sides, and by night. */
public class AfterworldWonderTest implements FabricClientGameTest {
	private static final String[] WONDERS = {"titan", "angels", "cathedral", "citadel", "mill"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.WAYFARER);
			// The test framework draws only five chunks; wonders need to be seen from further off.
			context.runOnClient(mc -> mc.options.renderDistance().set(12));
			var server = sp.getServer();
			server.runOnServer(s -> s.getPlayerList().setViewDistance(12));
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			for (String name : WONDERS) {
				BlockPos found = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					var holder = structure(level, name);
					if (holder == null) {
						return null;
					}
					Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 120, false);
					return hit == null ? null : hit.getFirst();
				});
				if (found == null) {
					Afterworld.LOG.error("WONDERTEST could not find {}", name);
					continue;
				}
				server.runCommand("tp @p " + found.getX() + " 220 " + found.getZ());
				context.waitTicks(240);
				sp.getConnection().waitForChunksRender(false, 2400);
				BoundingBox box = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					StructureStart start = level.getChunk(found.getX() >> 4, found.getZ() >> 4).getStartForStructure(structure(level, name).value());
					// The piece's own box: the start's box is padded for terrain blending.
					return start != null && start.isValid() && !start.getPieces().isEmpty() ? start.getPieces().get(0).getBoundingBox() : null;
				});
				if (box == null) {
					Afterworld.LOG.error("WONDERTEST {} has no start at {}", name, found);
					continue;
				}
				int[] check = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					StructureStart start = level.getChunk(found.getX() >> 4, found.getZ() >> 4).getStartForStructure(structure(level, name).value());
					WonderPiece piece = (WonderPiece) start.getPieces().get(0);
					// Load every chunk the wonder covers, then compare it with its file.
					for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
						for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
							level.getChunk(x, z);
						}
					}
					return WonderData.get(name).verify(level, piece.origin(), piece.turn());
				});
				Afterworld.LOG.info("WONDERTEST {} placed {} blocks, {} differ", name, check[0], check[1]);
				// Ores, lakes and the like may later change a few blocks; anything more means placement is broken.
				if (check[0] == 0 || check[1] * 20 > check[0]) {
					throw new AssertionError("Wonder " + name + " is incomplete: " + check[1] + " of " + check[0] + " blocks differ");
				}
				int cx = (box.minX() + box.maxX()) / 2;
				int cz = (box.minZ() + box.maxZ()) / 2;
				int size = Math.max(box.getXSpan(), box.getZSpan());
				int ground = box.minY();
				int lookY = ground + box.getYSpan() / 3;
				int d = size * 9 / 10;
				Afterworld.LOG.info("WONDERTEST {} at {} {} {} size {}x{}x{}", name, cx, ground, cz, box.getXSpan(), box.getYSpan(), box.getZSpan());
				server.runCommand("time set 5000");
				shot(context, sp, "wonder-" + name, cx + d, ground + size / 4, cz + d / 2, cx, lookY, cz);
				shot(context, sp, "wonder-" + name + "-far-side", cx - d, ground + size / 5, cz - d / 2, cx, lookY, cz);
				shot(context, sp, "wonder-" + name + "-close", cx + size / 2, ground + 4, cz + size / 5, cx, lookY + box.getYSpan() / 6, cz);
				server.runCommand("time set 18000");
				shot(context, sp, "wonder-" + name + "-night", cx - d / 2, ground + size / 6, cz + d * 3 / 4, cx, lookY, cz);
			}
		}
	}

	private static Holder.Reference<Structure> structure(ServerLevel level, String name) {
		var key = ResourceKey.create(Registries.STRUCTURE, Afterworld.id(name));
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key).orElse(null);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		// Never film from inside a hill.
		int floor = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
		int eye = Math.max(y, floor + 4);
		sp.getServer().runCommand("tp @p " + x + " " + eye + " " + z + " facing " + lx + " " + ly + " " + lz);
		// Chunks out to the wider view come in a circle, so wait for them by time, then for drawing.
		context.waitTicks(100);
		sp.getConnection().waitForChunksRender(false, 2400);
		// After a long jump some sections are left undrawn; rebuild them all (F3+A) before the shot.
		context.runOnClient(mc -> mc.levelExtractor.allChanged());
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(10);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}
}
