package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A dead, twisted tree. On the moor it is bleached pale oak; in the fen it is black mangrove on
 * arched roots, hung with moss and, now and then, a lantern somebody left burning.
 */
public record GnarledTree(boolean fen) implements Feature {
	public static final MapCodec<GnarledTree> CODEC = Codec.BOOL.optionalFieldOf("fen", false).xmap(GnarledTree::new, GnarledTree::fen);

	@Override
	public MapCodec<GnarledTree> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		BlockPos below = new BlockPos(x, y0 - 1, z);
		if (!Gen.isSoil(level.getBlockState(below)) || !this.fen && !level.getFluidState(below.above()).isEmpty()) {
			return false;
		}
		BlockState log = (this.fen ? Blocks.MANGROVE_LOG : Blocks.PALE_OAK_LOG).defaultBlockState();
		int height = 5 + random.nextInt(5);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		if (this.fen) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (random.nextFloat() < 0.8F) {
					Gen.set(level, w, p.set(x + d.getStepX(), y0, z + d.getStepZ()), Blocks.MANGROVE_ROOTS.defaultBlockState());
					Gen.set(level, w, p.set(x + d.getStepX() * 2, y0 - 1, z + d.getStepZ() * 2), Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
				}
			}
		}
		int tx = x;
		int tz = z;
		for (int i = 0; i < height; i++) {
			if (i > 2 && i % 3 == 0) {
				tx += random.nextInt(3) - 1;
				tz += random.nextInt(3) - 1;
			}
			Gen.set(level, w, p.set(tx, y0 + i + (this.fen ? 1 : 0), tz), log);
		}
		int top = y0 + height + (this.fen ? 1 : 0);
		int branches = 2 + random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int by = top - 1 - random.nextInt(Math.max(1, height / 2));
			int bx = tx;
			int bz = tz;
			int len = 2 + random.nextInt(4);
			BlockState branch = log.setValue(RotatedPillarBlock.AXIS, d.getAxis());
			for (int s = 0; s < len; s++) {
				bx += d.getStepX();
				bz += d.getStepZ();
				if (random.nextFloat() < 0.45F) {
					by++;
					Gen.set(level, w, p.set(bx, by, bz), log);
				} else {
					Gen.set(level, w, p.set(bx, by, bz), branch);
				}
			}
			if (this.fen) {
				if (random.nextFloat() < 0.4F) {
					int chain = 1 + random.nextInt(2);
					for (int c = 1; c <= chain; c++) {
						Gen.setSoft(level, w, p.set(bx, by - c, bz), Blocks.IRON_CHAIN.defaultBlockState());
					}
					Gen.setSoft(level, w, p.set(bx, by - chain - 1, bz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				} else {
					int moss = 1 + random.nextInt(3);
					for (int c = 1; c <= moss; c++) {
						Gen.setSoft(level, w, p.set(bx, by - c, bz), Blocks.PALE_HANGING_MOSS.defaultBlockState());
					}
				}
			}
		}
		return true;
	}
}
