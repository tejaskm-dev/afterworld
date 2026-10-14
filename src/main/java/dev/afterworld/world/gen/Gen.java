package dev.afterworld.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Small helpers shared by Afterworld's features. Features may only touch the 3x3 chunks around the
 * chunk being decorated, so every write goes through {@link #set} with that bound checked.
 */
public final class Gen {
	private Gen() {
	}

	/** The write window for a feature whose origin lies in the chunk being decorated. */
	public record Window(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
		public static Window around(WorldGenLevel level, BlockPos origin) {
			int cx = origin.getX() >> 4;
			int cz = origin.getZ() >> 4;
			return new Window((cx - 1) << 4, (cz - 1) << 4, ((cx + 2) << 4) - 1, ((cz + 2) << 4) - 1, level.getMinY() + 1, level.getMaxY() - 1);
		}

		public boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
		}
	}

	public static boolean set(WorldGenLevel level, Window w, BlockPos pos, BlockState state) {
		if (!w.contains(pos.getX(), pos.getY(), pos.getZ())) {
			return false;
		}
		level.setBlock(pos, state, 2);
		return true;
	}

	/** Sets only where nothing solid is in the way (air, plants, snow, water). */
	public static boolean setSoft(WorldGenLevel level, Window w, BlockPos pos, BlockState state) {
		if (!w.contains(pos.getX(), pos.getY(), pos.getZ()) || !soft(level.getBlockState(pos))) {
			return false;
		}
		level.setBlock(pos, state, 2);
		return true;
	}

	public static boolean soft(BlockState s) {
		return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SNOW) || s.is(Blocks.VINE);
	}

	/** Y of the first free block above real ground: below water, and below any trees or plants. */
	public static int ground(WorldGenLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, z);
		while (p.getY() > level.getMinY()) {
			BlockState s = level.getBlockState(p);
			if (!(s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || soft(s) || s.is(Blocks.MUSHROOM_STEM)
				|| s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MANGROVE_ROOTS))) {
				break;
			}
			p.move(0, -1, 0);
		}
		return p.getY() + 1;
	}

	public static boolean isSoil(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.DIRT) || s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL);
	}

	/** Smooth value noise in [0, 1], stable for a world seed. */
	public static double noise(long seed, double x, double z, double scale) {
		x /= scale;
		z /= scale;
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash(seed, x0, z0);
		double b = hash(seed, x0 + 1, z0);
		double c = hash(seed, x0, z0 + 1);
		double d = hash(seed, x0 + 1, z0 + 1);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
	}

	public static double hash(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
