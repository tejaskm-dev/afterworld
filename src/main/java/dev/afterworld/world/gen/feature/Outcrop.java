package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A rock outcrop: a few overlapping, half-buried lumps of a stone palette, with an optional cap
 * (moss, snow, sand) on the upward faces. Boulders, ice lumps, sandstone knuckles, coral heads.
 */
public record Outcrop(BlockStateProvider body, Optional<BlockStateProvider> cap, float capChance, float minSize, float maxSize, int lumps) implements Feature {
	public static final MapCodec<Outcrop> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.DIRECT_CODEC.fieldOf("body").forGetter(Outcrop::body),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("cap").forGetter(Outcrop::cap),
		Codec.FLOAT.optionalFieldOf("cap_chance", 0.6F).forGetter(Outcrop::capChance),
		Codec.FLOAT.optionalFieldOf("min_size", 1.6F).forGetter(Outcrop::minSize),
		Codec.FLOAT.optionalFieldOf("max_size", 3.2F).forGetter(Outcrop::maxSize),
		Codec.intRange(1, 8).optionalFieldOf("lumps", 3).forGetter(Outcrop::lumps)
	).apply(i, Outcrop::new));

	@Override
	public MapCodec<Outcrop> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		if (!level.getFluidState(new BlockPos(x, y0, z)).isEmpty()) {
			return false;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int n = 1 + random.nextInt(this.lumps);
		for (int k = 0; k < n; k++) {
			double r = this.minSize + random.nextDouble() * (this.maxSize - this.minSize);
			int cx = x + (k == 0 ? 0 : random.nextInt(5) - 2);
			int cz = z + (k == 0 ? 0 : random.nextInt(5) - 2);
			int cy = y0 - 1 + (int) (r * 0.3);
			double ry = r * (0.6 + random.nextDouble() * 0.35);
			int ir = (int) Math.ceil(r);
			int iy = (int) Math.ceil(ry);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					for (int dy = -iy; dy <= iy; dy++) {
						double d = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (ry * ry);
						if (d > 1.0 || d > 0.8 && random.nextFloat() < 0.3F) {
							continue;
						}
						p.set(cx + dx, cy + dy, cz + dz);
						if (!w.contains(p.getX(), p.getY(), p.getZ())) {
							continue;
						}
						boolean top = (dx * dx + dz * dz) / (r * r) + ((dy + 1) * (dy + 1)) / (ry * ry) > 1.0;
						if (top && this.cap.isPresent() && random.nextFloat() < this.capChance) {
							level.setBlock(p, this.cap.get().getState(level, random, p), 2);
						} else {
							level.setBlock(p, this.body.getState(level, random, p), 2);
						}
					}
				}
			}
		}
		return true;
	}
}
