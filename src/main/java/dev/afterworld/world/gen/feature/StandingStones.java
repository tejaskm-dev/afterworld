package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A ring of weathered tuff stones around a carved altar; some have fallen, the altar still burns. */
public record StandingStones() implements Feature {
	public static final StandingStones INSTANCE = new StandingStones();
	public static final MapCodec<StandingStones> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<StandingStones> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int cx = origin.getX();
		int cz = origin.getZ();
		int cy = Gen.ground(level, cx, cz);
		if (!level.getFluidState(new BlockPos(cx, cy, cz)).isEmpty()) {
			return false;
		}
		int n = 6 + random.nextInt(4);
		double radius = 4.5 + random.nextDouble() * 3;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + (random.nextDouble() - 0.5) * 0.3;
			int x = cx + (int) Math.round(Math.cos(a) * radius);
			int z = cz + (int) Math.round(Math.sin(a) * radius);
			int y = Gen.ground(level, x, z);
			if (!level.getFluidState(p.set(x, y, z)).isEmpty()) {
				continue;
			}
			for (int d = 1; d <= 2; d++) {
				Gen.set(level, w, p.set(x, y - d, z), Blocks.TUFF.defaultBlockState());
			}
			if (random.nextFloat() < 0.22F) {
				// Fallen: lying along the ring.
				int tx = (int) Math.round(-Math.sin(a));
				int tz = (int) Math.round(Math.cos(a));
				for (int k = 0; k < 3; k++) {
					Gen.set(level, w, p.set(x + tx * k, Gen.ground(level, x + tx * k, z + tz * k), z + tz * k), stone(random, false));
				}
				continue;
			}
			int h = 2 + random.nextInt(4);
			for (int k = 0; k < h; k++) {
				Gen.set(level, w, p.set(x, y + k, z), stone(random, k == h - 1));
			}
			if (random.nextFloat() < 0.3F) {
				int ox = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 0 : 1;
				int oz = 1 - ox;
				for (int k = 0; k < h - 1; k++) {
					Gen.set(level, w, p.set(x + ox, y + k, z + oz), stone(random, false));
				}
			}
		}
		Gen.set(level, w, p.set(cx, cy - 1, cz), Blocks.POLISHED_TUFF.defaultBlockState());
		Gen.set(level, w, p.set(cx, cy, cz), Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
		if (random.nextFloat() < 0.35F) {
			Gen.set(level, w, p.set(cx, cy + 1, cz), Blocks.SOUL_CAMPFIRE.defaultBlockState());
		} else {
			Gen.set(level, w, p.set(cx, cy + 1, cz), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
		}
		return true;
	}

	private static BlockState stone(RandomSource random, boolean top) {
		if (top && random.nextFloat() < 0.4F) {
			return Blocks.CHISELED_TUFF.defaultBlockState();
		}
		float f = random.nextFloat();
		return (f < 0.4F ? Blocks.TUFF_BRICKS : f < 0.7F ? Blocks.POLISHED_TUFF : f < 0.88F ? Blocks.MOSSY_STONE_BRICKS : Blocks.TUFF).defaultBlockState();
	}
}
