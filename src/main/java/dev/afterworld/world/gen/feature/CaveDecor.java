package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Dresses caves: finds pockets of cave air in the chunk and, around each, repaints the floor and
 * ceiling stone and sets things on and under them (crystals, glowing fungus, roots, icicles,
 * moss). Only bare stone is repainted, and nothing within a few blocks of the surface.
 */
public record CaveDecor(int tries, int minY, int maxY, int radius,
	Optional<BlockStateProvider> floorPaint, Optional<BlockStateProvider> floor, float floorChance,
	Optional<BlockStateProvider> ceilingPaint, Optional<BlockStateProvider> ceiling, float ceilingChance) implements Feature {

	public static final MapCodec<CaveDecor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.intRange(1, 64).optionalFieldOf("tries", 6).forGetter(CaveDecor::tries),
		Codec.INT.optionalFieldOf("min_y", -48).forGetter(CaveDecor::minY),
		Codec.INT.optionalFieldOf("max_y", 50).forGetter(CaveDecor::maxY),
		Codec.intRange(1, 7).optionalFieldOf("radius", 4).forGetter(CaveDecor::radius),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("floor_paint").forGetter(CaveDecor::floorPaint),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("floor").forGetter(CaveDecor::floor),
		Codec.FLOAT.optionalFieldOf("floor_chance", 0.3F).forGetter(CaveDecor::floorChance),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("ceiling_paint").forGetter(CaveDecor::ceilingPaint),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("ceiling").forGetter(CaveDecor::ceiling),
		Codec.FLOAT.optionalFieldOf("ceiling_chance", 0.25F).forGetter(CaveDecor::ceilingChance)
	).apply(i, CaveDecor::new));

	@Override
	public MapCodec<CaveDecor> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		boolean any = false;
		for (int t = 0; t < this.tries; t++) {
			int x = x0 + 4 + random.nextInt(8);
			int z = z0 + 4 + random.nextInt(8);
			int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 8;
			int top = Math.min(this.maxY, surface);
			if (top <= this.minY) {
				continue;
			}
			int y = this.minY + random.nextInt(top - this.minY);
			p.set(x, y, z);
			if (!level.getBlockState(p).isAir()) {
				continue;
			}
			int floorY = scan(level, p, -1, 12);
			int ceilY = scan(level, p, 1, 12);
			if (floorY != Integer.MIN_VALUE) {
				cluster(level, w, random, x, floorY, z, true);
				any = true;
			}
			if (ceilY != Integer.MIN_VALUE) {
				cluster(level, w, random, x, ceilY, z, false);
				any = true;
			}
		}
		return any;
	}

	/** The y of the first stone block found going {@code dir} from an air pocket, if it is stone. */
	private static int scan(WorldGenLevel level, BlockPos.MutableBlockPos from, int dir, int max) {
		BlockPos.MutableBlockPos s = from.mutable();
		for (int i = 0; i < max; i++) {
			s.move(0, dir, 0);
			BlockState b = level.getBlockState(s);
			if (!b.isAir()) {
				return stone(b) ? s.getY() : Integer.MIN_VALUE;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean stone(BlockState b) {
		return b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(BlockTags.DIRT);
	}

	private void cluster(WorldGenLevel level, Gen.Window w, RandomSource random, int cx, int cy, int cz, boolean onFloor) {
		Optional<BlockStateProvider> paint = onFloor ? this.floorPaint : this.ceilingPaint;
		Optional<BlockStateProvider> deco = onFloor ? this.floor : this.ceiling;
		float chance = onFloor ? this.floorChance : this.ceilingChance;
		int dir = onFloor ? 1 : -1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int dx = -this.radius; dx <= this.radius; dx++) {
			for (int dz = -this.radius; dz <= this.radius; dz++) {
				if (dx * dx + dz * dz > this.radius * this.radius || random.nextFloat() < 0.2F) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				if (!w.contains(x, cy, z)) {
					continue;
				}
				// Find this column's own floor (or ceiling) near the cluster's.
				for (int dy = -3; dy <= 3; dy++) {
					p.set(x, cy + dy, z);
					q.set(x, cy + dy + dir, z);
					if (!w.contains(x, q.getY(), z) || !w.contains(x, p.getY(), z)) {
						continue;
					}
					if (stone(level.getBlockState(p)) && level.getBlockState(q).isAir()) {
						if (paint.isPresent()) {
							level.setBlock(p, paint.get().getState(level, random, p), 2);
						}
						if (deco.isPresent() && random.nextFloat() < chance) {
							BlockState s = deco.get().getState(level, random, q);
							if (s.canSurvive(level, q)) {
								level.setBlock(q, s, 2);
							}
						}
						break;
					}
				}
			}
		}
	}
}
