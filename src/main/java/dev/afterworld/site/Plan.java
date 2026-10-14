package dev.afterworld.site;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A structure described as block placements relative to an origin. Later placements override
 * earlier ones, so builders paint broad shapes first and details after.
 */
public final class Plan {
	public final BlockPos origin;
	public final RandomSource random;
	final Long2ObjectLinkedOpenHashMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
	final List<Consumer<ServerLevel>> after = new ArrayList<>();

	public Plan(BlockPos origin, long seed) {
		this.origin = origin;
		this.random = RandomSource.create(seed);
	}

	public BlockPos at(int dx, int dy, int dz) {
		return origin.offset(dx, dy, dz);
	}

	public void set(int dx, int dy, int dz, BlockState state) {
		blocks.put(BlockPos.asLong(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz), state);
	}

	public BlockState get(int dx, int dy, int dz) {
		return blocks.get(BlockPos.asLong(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz));
	}

	public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	/** Fills, keeping each block with probability {@code keep} (ruins). */
	public void ruin(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state, float keep) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					if (random.nextFloat() < keep) {
						set(x, y, z, state);
					}
				}
			}
		}
	}

	/** Walls only (no floor or ceiling), optionally decayed toward the top. */
	public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state, float decay) {
		int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
		int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
		for (int y = minY; y <= maxY; y++) {
			float keep = 1.0F - decay * (float) (y - minY) / Math.max(1, maxY - minY);
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					if ((x == minX || x == maxX || z == minZ || z == maxZ) && random.nextFloat() < keep) {
						set(x, y, z, state);
					}
				}
			}
		}
	}

	public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
		fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
	}

	/** Mixes a base block with weathered variants, chosen per block. */
	public void mixed(int x1, int y1, int z1, int x2, int y2, int z2, BlockState[] palette, float[] weights) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, pick(palette, weights));
				}
			}
		}
	}

	public BlockState pick(BlockState[] palette, float[] weights) {
		float total = 0;
		for (float w : weights) {
			total += w;
		}
		float roll = random.nextFloat() * total;
		for (int i = 0; i < palette.length; i++) {
			roll -= weights[i];
			if (roll <= 0) {
				return palette[i];
			}
		}
		return palette[palette.length - 1];
	}

	public static BlockState facing(BlockState state, Direction dir) {
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
		}
		if (state.hasProperty(BlockStateProperties.FACING)) {
			return state.setValue(BlockStateProperties.FACING, dir);
		}
		return state;
	}

	/** Runs after every block is placed (loot tables, entities, block entity data). */
	public void then(Consumer<ServerLevel> action) {
		after.add(action);
	}

	public int size() {
		return blocks.size();
	}
}
