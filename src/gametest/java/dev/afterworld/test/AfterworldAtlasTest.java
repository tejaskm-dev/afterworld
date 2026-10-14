package dev.afterworld.test;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.world.atlas.Atlas;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The Atlas in a new world: each Warden's lair exactly once, far from the waking place, in its own
 * country where it can be; found by the game's own locating; and actually standing when you get
 * there. Photographs each from the air.
 */
public class AfterworldAtlasTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> mc.options.renderDistance().set(10));
			var server = sp.getServer();
			server.runOnServer(s -> s.getPlayerList().setViewDistance(10));
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("time set 5000");
			server.runCommand("gamemode spectator @p");
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			Afterworld.LOG.info("ATLASTEST spawn {} plan: {}", spawn.toShortString(), server.computeOnServer(s -> Atlas.describe()));
			int problems = 0;
			for (Atlas.Lair lair : Atlas.LAIRS) {
				BlockPos at = Atlas.lair(lair.warden());
				if (at == null) {
					Afterworld.LOG.error("ATLASTEST {} has no place", lair.warden());
					problems++;
					continue;
				}
				// The game's own /locate, from the spawn.
				BlockPos located = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					Holder<Structure> h = structure(level, lair.structure());
					Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(h), spawn, 100, false);
					return hit == null ? null : hit.getFirst();
				});
				String biome = server.computeOnServer(s -> s.overworld().getBiome(at.atY(80)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
				int far = (int) Math.sqrt(at.distSqr(spawn.atY(at.getY())));
				server.runCommand("tp @p " + at.getX() + " 200 " + at.getZ());
				context.waitTicks(120);
				sp.getConnection().waitForChunksRender(false, 2400);
				int[] box = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					ChunkPos c = ChunkPos.containing(at);
					StructureStart start = level.getChunk(c.x(), c.z()).getStartForStructure(structure(level, lair.structure()).value());
					if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
						return null;
					}
					var b = start.getPieces().get(0).getBoundingBox();
					return new int[] {(b.minX() + b.maxX()) / 2, b.minY(), (b.minZ() + b.maxZ()) / 2, Math.max(b.getXSpan(), b.getZSpan())};
				});
				Afterworld.LOG.info("ATLASTEST {} ({}) at {} {} m from spawn, in {}; locate says {}; standing: {}", lair.warden(), lair.structure(), at.toShortString(),
					far, biome, located == null ? "nothing" : located.toShortString(), box != null);
				if (box == null || located == null || far < 1000) {
					problems++;
					continue;
				}
				int cx = box[0];
				int cz = box[2];
				int ground = box[1];
				int size = box[3];
				int floor = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, cx + size, cz + size / 2));
				server.runCommand("tp @p " + (cx + size) + " " + Math.max(ground + size / 2, floor + 6) + " " + (cz + size / 2) + " facing " + cx + " " + (ground + 6) + " " + cz);
				context.waitTicks(60);
				sp.getConnection().waitForChunksRender(false, 2400);
				context.runOnClient(mc -> mc.levelExtractor.allChanged());
				context.waitTicks(10);
				sp.getConnection().waitForChunksRender(false, 2400);
				context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
				context.takeScreenshot("atlas-" + lair.warden());
			}
			// No stray copies left near the waking place.
			int strays = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				int n = 0;
				for (int x = -6; x <= 6; x++) {
					for (int z = -6; z <= 6; z++) {
						var chunk = level.getChunk((spawn.getX() >> 4) + x, (spawn.getZ() >> 4) + z);
						for (Atlas.Lair l : Atlas.LAIRS) {
							StructureStart st = chunk.getStartForStructure(structure(level, l.structure()).value());
							if (st != null && st.isValid()) {
								n++;
							}
						}
					}
				}
				return n;
			});
			Afterworld.LOG.info("ATLASTEST lairs within 100 m of spawn: {}; problems: {}", strays, problems);
			if (problems > 0 || strays > 0) {
				throw new AssertionError("The Atlas is wrong: " + problems + " problems, " + strays + " lairs by the spawn");
			}
			Afterworld.LOG.info("ATLASTEST done");
		}
	}

	private static Holder.Reference<Structure> structure(ServerLevel level, String name) {
		var key = ResourceKey.create(Registries.STRUCTURE, Afterworld.id(name));
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key).orElse(null);
	}
}
