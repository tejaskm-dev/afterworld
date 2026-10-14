package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.world.atlas.Atlas;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Where everything stands, over a wide square of a new world: every structure that starts there
 * (ours and the game's), whether any two of them overlap, and whether anything starts inside the
 * waking town or by a Warden's lair. Only the structure layout is generated, so it is quick.
 */
public class AfterworldSitingTest implements FabricClientGameTest {
	private static final int RADIUS = 48;

	private record Placed(String id, BoundingBox box, ChunkPos start) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			ChunkPos mid = server.computeOnServer(s -> ChunkPos.containing(Atlas.townCentre(s.overworld())));
			List<Placed> placed = new ArrayList<>();
			// Row by row, so the server keeps ticking between them.
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				int row = dz;
				placed.addAll(server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
					List<Placed> out = new ArrayList<>();
					for (int dx = -RADIUS; dx <= RADIUS; dx++) {
						ChunkAccess chunk = level.getChunkSource().getChunk(mid.x() + dx, mid.z() + row, ChunkStatus.STRUCTURE_STARTS, true);
						if (chunk == null) {
							continue;
						}
						for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
							if (e.getValue().isValid()) {
								String id = registry.getKey(e.getKey()) == null ? "?" : registry.getKey(e.getKey()).toString();
								out.add(new Placed(id, e.getValue().getBoundingBox(), e.getValue().getChunkPos()));
							}
						}
					}
					return out;
				}));
			}
			Map<String, Integer> counts = new TreeMap<>();
			for (Placed p : placed) {
				counts.merge(p.id(), 1, Integer::sum);
			}
			Afterworld.LOG.info("SITINGTEST {} structures in {}x{} chunks round {}: {}", placed.size(), RADIUS * 2 + 1, RADIUS * 2 + 1, mid, counts);
			// Overlaps: two different structures whose boxes share any space (underground ones rarely meet the surface).
			int overlaps = 0;
			Map<String, Integer> pairs = new HashMap<>();
			for (int i = 0; i < placed.size(); i++) {
				for (int j = i + 1; j < placed.size(); j++) {
					Placed a = placed.get(i);
					Placed b = placed.get(j);
					if (a.box().intersects(b.box())) {
						overlaps++;
						String key = a.id().compareTo(b.id()) < 0 ? a.id() + " + " + b.id() : b.id() + " + " + a.id();
						if (pairs.merge(key, 1, Integer::sum) <= 2) {
							Afterworld.LOG.info("SITINGTEST overlap {} at {} (start {}, box {}) and {} at {} (start {}, box {})", a.id(), center(a.box()), a.start(), a.box(),
								b.id(), center(b.box()), b.start(), b.box());
						}
					}
				}
			}
			Afterworld.LOG.info("SITINGTEST overlaps: {} {}", overlaps, pairs);
			// Nothing in the town, nothing by a lair.
			BlockPos town = server.computeOnServer(s -> Atlas.townCentre(s.overworld()));
			int inTown = 0;
			for (Placed p : placed) {
				BlockPos c = center(p.box());
				if (Math.hypot(c.getX() - town.getX(), c.getZ() - town.getZ()) < 110 && !p.id().contains("mineshaft") && !p.id().contains("stronghold")) {
					inTown++;
					Afterworld.LOG.info("SITINGTEST in the town: {} at {}", p.id(), c);
				}
			}
			Afterworld.LOG.info("SITINGTEST done: {} overlaps, {} in the town", overlaps, inTown);
		}
	}

	private static BlockPos center(BoundingBox b) {
		return new BlockPos((b.minX() + b.maxX()) / 2, (b.minY() + b.maxY()) / 2, (b.minZ() + b.maxZ()) / 2);
	}
}
