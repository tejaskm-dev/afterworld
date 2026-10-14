package dev.afterworld.world;

import dev.afterworld.Afterworld;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.world.wonder.WonderData;
import java.util.Arrays;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelData;

/**
 * The Waystone Camp: built once, round the spawn of a new world, before anyone arrives. It is a
 * placed wonder ({@code waystone_camp.aww}, the walled town) set on the median ground of its
 * footprint, with earth packed under any part that would overhang a dip, and the country around
 * it eased up or down to meet it over a wide ring, so it sits in the land rather than on a cut
 * plinth. Its markers stand the keepers in their districts, record the waystone and move the
 * world spawn to the town gate. Worlds that existed before the camp get a small one instead: the
 * waystone and the two keepers the story needs first.
 */
public final class Camp {
	private static final String WONDER = "waystone_camp";
	/** The wonder's walking level (its plateau is this many layers deep). */
	private static final int GROUND = 8;
	/** How far out the land is eased to meet the town. */
	private static final int RING = 24;

	private Camp() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(Camp::ensure);
	}

	private static void ensure(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		if (data.campBuilt) {
			return;
		}
		ServerLevel level = server.overworld();
		// Where the Atlas kept the ground clear of other buildings (the spawn settles near it, and
		// is moved to the town gate once the town stands).
		BlockPos spawn = data.founded ? level.getRespawnData().pos() : dev.afterworld.world.atlas.Atlas.townCentre(level);
		long t0 = System.nanoTime();
		try {
			if (!data.founded) {
				build(level, data, spawn);
			} else {
				small(level, data, spawn);
			}
		} catch (RuntimeException e) {
			Afterworld.LOG.error("Could not build the Waystone Camp", e);
		}
		data.campBuilt = true;
		data.setDirty();
		Afterworld.LOG.info("Afterworld camp ready in {} ms", (System.nanoTime() - t0) / 1_000_000);
	}

	private static void build(ServerLevel level, AfterworldData data, BlockPos spawn) {
		WonderData w = WonderData.get(WONDER);
		if (w == null || w.sx == 0) {
			small(level, data, spawn);
			return;
		}
		// The town turns to a quarter the world's seed picks, so no two worlds wake to the same view.
		Rotation rot = Rotation.values()[(int) Math.floorMod(level.getSeed() ^ 0x70E1L, 4L)];
		int sx = w.width(rot);
		int sz = w.depth(rot);
		// Chunk-aligned, with the spawn as near the middle as the grid allows.
		int ox = Math.floorDiv(spawn.getX() - sx / 2 + 8, 16) * 16;
		int oz = Math.floorDiv(spawn.getZ() - sz / 2 + 8, 16) * 16;
		for (int cx = (ox - RING) >> 4; cx <= (ox + sx + RING - 1) >> 4; cx++) {
			for (int cz = (oz - RING) >> 4; cz <= (oz + sz + RING - 1) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		// The median ground under the town's disc decides its height.
		int[] heights = new int[(sx / 4) * (sz / 4)];
		int n = 0;
		for (int x = 2; x < sx; x += 4) {
			for (int z = 2; z < sz; z += 4) {
				if (n < heights.length && Math.hypot(x - sx / 2.0, z - sz / 2.0) < sx / 2.0 - 4) {
					// Over water the water's surface is the ground (a town sunk below it would flood).
					int t = terrain(level, ox + x, oz + z);
					heights[n++] = level.getFluidState(new BlockPos(ox + x, t, oz + z)).isEmpty() ? t : surface(level, ox + x, oz + z);
				}
			}
		}
		Arrays.sort(heights, 0, n);
		int ground = heights[n / 2];
		BlockPos origin = new BlockPos(ox, ground - GROUND, oz);
		// Pack earth under anything that would hang over a dip; clear tall growth above.
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		double r = sx / 2.0 - 1.5;
		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				double d = Math.hypot(x + 0.5 - sx / 2.0, z + 0.5 - sz / 2.0);
				if (d > r) {
					continue;
				}
				int wx = ox + x;
				int wz = oz + z;
				int top = terrain(level, wx, wz);
				for (int y = top; y < origin.getY(); y++) {
					level.setBlock(p.set(wx, y, wz), dirt, 2);
				}
				for (int y = origin.getY() + w.sy; y <= top + 8 && y < level.getMaxY(); y++) {
					level.setBlock(p.set(wx, y, wz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		blend(level, ox + sx / 2.0, oz + sz / 2.0, r, ground);
		RandomSource random = RandomSource.create(level.getSeed() ^ 0xCA3D);
		for (int cx = ox >> 4; cx <= (ox + sx - 1) >> 4; cx++) {
			for (int cz = oz >> 4; cz <= (oz + sz - 1) >> 4; cz++) {
				BoundingBox box = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				w.place(level, origin, rot, box, random);
			}
		}
		BlockPos center = origin.offset(sx / 2, GROUND, sz / 2);
		data.campCenter = center.asLong();
		for (Map.Entry<BlockPos, String> m : w.markers(origin, rot)) {
			switch (m.getValue()) {
				case "waystone" -> Waystones.register(level, m.getKey(), "Waystone Camp", true);
				// Arriving at the gate, looking up the main street to the waystone.
				case "spawn" -> level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, m.getKey(), rot.rotate(Direction.NORTH).toYRot(), 0.0F));
				default -> {
				}
			}
		}
		Afterworld.LOG.info("Afterworld camp built at {}", center.toShortString());
	}

	/** For worlds older than the camp: a waystone and the first two keepers, beside the spawn. */
	private static void small(ServerLevel level, AfterworldData data, BlockPos spawn) {
		level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		int x = spawn.getX() + 3;
		int z = spawn.getZ();
		BlockPos stone = new BlockPos(x, surface(level, x, z), z);
		level.setBlock(stone, AwBlocks.WAYSTONE.defaultBlockState().setValue(dev.afterworld.block.WaystoneBlock.NATURAL, true), 3);
		Waystones.register(level, stone, "Waystone Camp", true);
		data.campCenter = stone.asLong();
		BlockPos isolde = stone.relative(Direction.SOUTH, 2);
		Npcs.spawn(level, Npc.ISOLDE, isolde.atY(surface(level, isolde.getX(), isolde.getZ())), 0.0F);
		BlockPos bram = stone.relative(Direction.EAST, 3);
		Npcs.spawn(level, Npc.BRAM, bram.atY(surface(level, bram.getX(), bram.getZ())), 90.0F);
		Afterworld.LOG.info("Afterworld small camp set at {}", stone.toShortString());
	}

	/**
	 * Eases the land in a ring round the town (from radius {@code r} out by {@link #RING}) from the
	 * town's level to its own, cutting hills down and filling hollows with whatever the ground there
	 * is made of. Water is left alone; trees standing on cut ground go with it.
	 */
	private static void blend(ServerLevel level, double cx, double cz, double r, int ground) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		int x0 = (int) Math.floor(cx - r - RING);
		int x1 = (int) Math.ceil(cx + r + RING);
		int z0 = (int) Math.floor(cz - r - RING);
		int z1 = (int) Math.ceil(cz + r + RING);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
				if (d <= r - 1 || d > r + RING) {
					continue;
				}
				int natural = terrain(level, x, z);
				BlockState top = level.getBlockState(p.set(x, natural - 1, z));
				if (!top.getFluidState().isEmpty() || !level.getFluidState(p.set(x, natural, z)).isEmpty()) {
					continue;
				}
				double t = Math.clamp((d - (r - 1)) / (RING + 1), 0.0, 1.0);
				t = t * t * (3 - 2 * t);
				int target = (int) Math.round(ground + (natural - ground) * t);
				if (target == natural) {
					continue;
				}
				BlockState skin = top.is(net.minecraft.tags.BlockTags.DIRT) || top.is(net.minecraft.tags.BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK)
					|| top.is(Blocks.GRAVEL) ? top : Blocks.GRASS_BLOCK.defaultBlockState();
				BlockState fill = top.is(net.minecraft.tags.BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
				if (natural > target) {
					// A hill: down to the new level, and everything standing on it (trees, snow) away.
					int clearTo = Math.max(natural, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) + 1;
					for (int y = target; y < clearTo && y < level.getMaxY(); y++) {
						level.setBlock(p.set(x, y, z), air, 2);
					}
				} else {
					for (int y = natural - 1; y < target - 1; y++) {
						level.setBlock(p.set(x, y, z), fill, 2);
					}
				}
				level.setBlock(p.set(x, target - 1, z), skin, 2);
			}
		}
	}

	/** The walking level of the ground itself, under any trees, logs or plants standing on it. */
	private static int terrain(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		while (p.getY() > level.getMinY()) {
			BlockState s = level.getBlockState(p);
			if (s.isAir() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.canBeReplaced()
				|| s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)) {
				p.move(0, -1, 0);
				continue;
			}
			break;
		}
		return p.getY() + 1;
	}

	private static int surface(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}
}
