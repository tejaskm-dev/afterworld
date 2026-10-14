package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A natural arch of banded tuff and stone, ten to eighteen blocks across. */
public record StoneArch() implements Feature {
	public static final StoneArch INSTANCE = new StoneArch();
	public static final MapCodec<StoneArch> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<StoneArch> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		double a = random.nextDouble() * Math.PI;
		int span = 10 + random.nextInt(9);
		double ax = origin.getX() - Math.cos(a) * span / 2.0;
		double az = origin.getZ() - Math.sin(a) * span / 2.0;
		double bx = origin.getX() + Math.cos(a) * span / 2.0;
		double bz = origin.getZ() + Math.sin(a) * span / 2.0;
		int ga = Gen.ground(level, (int) ax, (int) az);
		int gb = Gen.ground(level, (int) bx, (int) bz);
		if (Math.abs(ga - gb) > 6 || !level.getFluidState(new BlockPos((int) ax, ga, (int) az)).isEmpty()) {
			return false;
		}
		double rise = span * (0.55 + random.nextDouble() * 0.25);
		double thick = 1.4 + random.nextDouble() * 0.6;
		int steps = span * 3;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int s = 0; s <= steps; s++) {
			double t = s / (double) steps;
			double x = ax + (bx - ax) * t;
			double z = az + (bz - az) * t;
			double y = ga + (gb - ga) * t + rise * Math.sin(Math.PI * t);
			double r = thick * (1.0 + 0.5 * (1 - Math.sin(Math.PI * t)));
			int ir = (int) Math.ceil(r);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dy = -ir; dy <= ir; dy++) {
					for (int dz = -ir; dz <= ir; dz++) {
						if (dx * dx + dy * dy + dz * dz > r * r) {
							continue;
						}
						int yy = (int) Math.floor(y) + dy;
						Gen.set(level, w, p.set((int) Math.floor(x) + dx, yy, (int) Math.floor(z) + dz), band(yy));
					}
				}
			}
			// The legs reach down into the ground.
			if (t < 0.12 || t > 0.88) {
				int gx = (int) Math.floor(x);
				int gz = (int) Math.floor(z);
				for (int yy = (int) Math.floor(y); yy >= Gen.ground(level, gx, gz) - 2; yy--) {
					Gen.set(level, w, p.set(gx, yy, gz), band(yy));
				}
			}
		}
		return true;
	}

	private static BlockState band(int y) {
		return switch (Math.floorMod(y, 7)) {
			case 0, 1 -> Blocks.CALCITE.defaultBlockState();
			case 2, 3, 4 -> Blocks.TUFF.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}
}
