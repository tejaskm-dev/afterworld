package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A smouldering vent: a shallow basalt crater around a hole where smoke still rises from below. */
public record AshVent() implements Feature {
	public static final AshVent INSTANCE = new AshVent();
	public static final MapCodec<AshVent> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<AshVent> codec() {
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
		int r = 2 + random.nextInt(2);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double d = Math.hypot(dx, dz);
				int x = cx + dx;
				int z = cz + dz;
				int y = Gen.ground(level, x, z) - 1;
				if (d <= r + 0.5) {
					Gen.set(level, w, p.set(x, y + 1, z), Blocks.AIR.defaultBlockState());
					float f = random.nextFloat();
					Gen.set(level, w, p.set(x, y, z), (d < 1.2 ? Blocks.MAGMA_BLOCK : f < 0.45F ? Blocks.BLACKSTONE : f < 0.8F ? Blocks.BASALT : Blocks.SMOOTH_BASALT).defaultBlockState());
				} else if (d <= r + 1.5 && random.nextFloat() < 0.7F) {
					Gen.set(level, w, p.set(x, y, z), (random.nextBoolean() ? Blocks.TUFF : Blocks.COARSE_DIRT).defaultBlockState());
					if (random.nextFloat() < 0.25F) {
						Gen.setSoft(level, w, p.set(x, y + 1, z), Blocks.BASALT.defaultBlockState());
					}
				}
			}
		}
		// The vent: a campfire one block down on a hidden hay bale, so its signal smoke climbs high.
		int y = Gen.ground(level, cx, cz) - 1;
		Gen.set(level, w, p.set(cx, y - 1, cz), Blocks.HAY_BLOCK.defaultBlockState());
		Gen.set(level, w, p.set(cx, y, cz), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
		return true;
	}
}
