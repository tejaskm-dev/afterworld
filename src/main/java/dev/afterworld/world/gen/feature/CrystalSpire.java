package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A leaning spire of amethyst sheathed in calcite, crusted with glowing clusters, with a few shards around its foot. */
public record CrystalSpire() implements Feature {
	public static final CrystalSpire INSTANCE = new CrystalSpire();
	public static final MapCodec<CrystalSpire> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<CrystalSpire> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int y0 = Gen.ground(level, x0, z0);
		if (!level.getFluidState(new BlockPos(x0, y0, z0)).isEmpty() || !Gen.isSoil(level.getBlockState(new BlockPos(x0, y0 - 1, z0)))
			&& !level.getBlockState(new BlockPos(x0, y0 - 1, z0)).is(Blocks.CALCITE)) {
			return false;
		}
		spire(level, w, random, x0, y0, z0, 9 + random.nextInt(22), true);
		int satellites = random.nextInt(4);
		for (int i = 0; i < satellites; i++) {
			int sx = x0 + random.nextInt(11) - 5;
			int sz = z0 + random.nextInt(11) - 5;
			spire(level, w, random, sx, Gen.ground(level, sx, sz), sz, 2 + random.nextInt(6), false);
		}
		return true;
	}

	private static void spire(WorldGenLevel level, Gen.Window w, RandomSource random, int x0, int y0, int z0, int h, boolean big) {
		double base = big ? 1.3 + random.nextDouble() * 1.6 * Math.min(1, h / 22.0) + 0.6 : 0.6 + random.nextDouble() * 0.6;
		double tx = (random.nextDouble() - 0.5) * 0.5;
		double tz = (random.nextDouble() - 0.5) * 0.5;
		long seed = random.nextLong();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = -3; y <= h; y++) {
			double t = Math.max(0, y) / (double) h;
			double rad = base * Math.pow(1 - t, 0.85) + 0.35;
			double cx = x0 + 0.5 + tx * Math.max(0, y);
			double cz = z0 + 0.5 + tz * Math.max(0, y);
			int ir = (int) Math.ceil(rad);
			for (int dx = -ir - 1; dx <= ir + 1; dx++) {
				for (int dz = -ir - 1; dz <= ir + 1; dz++) {
					int x = (int) Math.floor(cx) + dx;
					int z = (int) Math.floor(cz) + dz;
					double dd = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
					if (dd > rad) {
						continue;
					}
					double hsh = Gen.hash(seed, x * 7 + y * 131, z);
					BlockState s;
					if (dd > rad - 1.0) {
						s = (hsh < 0.5 ? Blocks.CALCITE : hsh < 0.92 ? Blocks.AMETHYST_BLOCK : Blocks.SMOOTH_QUARTZ).defaultBlockState();
					} else {
						s = (hsh < 0.08 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState();
					}
					Gen.set(level, w, p.set(x, y0 + y, z), s);
				}
			}
			// Clusters on the outside, which glow faintly at night.
			if (y > 0 && random.nextFloat() < (big ? 0.7F : 0.3F)) {
				Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
				int x = (int) Math.floor(cx + d.getStepX() * (rad + 0.6));
				int z = (int) Math.floor(cz + d.getStepZ() * (rad + 0.6));
				p.set(x, y0 + y, z);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.relative(d.getOpposite())).is(Blocks.AMETHYST_BLOCK)) {
					Gen.set(level, w, p, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, d));
				}
			}
		}
		int tipX = (int) Math.floor(x0 + 0.5 + tx * h);
		int tipZ = (int) Math.floor(z0 + 0.5 + tz * h);
		Gen.setSoft(level, w, p.set(tipX, y0 + h + 1, tipZ), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
	}
}
