package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A giant mushroom: a curving stem under a dome, a flat plate or a drooping bell, its cap speckled
 * with a second block (glowing, in the Sporeveil).
 */
public record GiantMushroom(BlockState stem, BlockStateProvider cap, Optional<BlockStateProvider> spots, float spotChance,
	String shape, int minHeight, int maxHeight, int minCap, int maxCap) implements Feature {

	public static final MapCodec<GiantMushroom> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("stem").forGetter(GiantMushroom::stem),
		BlockStateProvider.DIRECT_CODEC.fieldOf("cap").forGetter(GiantMushroom::cap),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("spots").forGetter(GiantMushroom::spots),
		Codec.FLOAT.optionalFieldOf("spot_chance", 0.12F).forGetter(GiantMushroom::spotChance),
		Codec.STRING.optionalFieldOf("shape", "dome").forGetter(GiantMushroom::shape),
		Codec.intRange(3, 40).fieldOf("min_height").forGetter(GiantMushroom::minHeight),
		Codec.intRange(3, 48).fieldOf("max_height").forGetter(GiantMushroom::maxHeight),
		Codec.intRange(2, 12).fieldOf("min_cap").forGetter(GiantMushroom::minCap),
		Codec.intRange(2, 14).fieldOf("max_cap").forGetter(GiantMushroom::maxCap)
	).apply(i, GiantMushroom::new));

	@Override
	public MapCodec<GiantMushroom> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		BlockPos below = new BlockPos(x, y0 - 1, z);
		if (!Gen.isSoil(level.getBlockState(below)) || !level.getFluidState(below.above()).isEmpty()) {
			return false;
		}
		int h = this.minHeight + random.nextInt(Math.max(1, this.maxHeight - this.minHeight + 1));
		int r = this.minCap + random.nextInt(Math.max(1, this.maxCap - this.minCap + 1));
		if (y0 + h + r > level.getMaxY()) {
			return false;
		}
		// The stem drifts sideways as it rises.
		double ang = random.nextDouble() * Math.PI * 2;
		double drift = random.nextDouble() * 0.18;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		double sx = x;
		double sz = z;
		int thick = h > 12 ? 1 : 0;
		for (int i = 0; i < h; i++) {
			sx += Math.cos(ang) * drift;
			sz += Math.sin(ang) * drift;
			for (int dx = -thick; dx <= thick; dx++) {
				for (int dz = -thick; dz <= thick; dz++) {
					if (thick > 0 && Math.abs(dx) + Math.abs(dz) > 1) {
						continue;
					}
					p.set((int) Math.round(sx) + dx, y0 + i, (int) Math.round(sz) + dz);
					Gen.set(level, w, p, this.stem);
				}
			}
		}
		int cx = (int) Math.round(sx);
		int cz = (int) Math.round(sz);
		int top = y0 + h;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 0.3) {
					continue;
				}
				int lift = switch (this.shape) {
					case "flat" -> d > r - 1.2 ? -1 : 0;
					case "bell" -> -(int) Math.round(Math.pow(d / r, 1.6) * r * 0.9);
					default -> (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)) * 0.55) - (int) (r * 0.55);
				};
				p.set(cx + dx, top + lift + (this.shape.equals("dome") ? (int) (r * 0.55) : 0), cz + dz);
				BlockStateProvider pick = this.spots.isPresent() && random.nextFloat() < this.spotChance ? this.spots.get() : this.cap;
				Gen.setSoft(level, w, p, pick.getState(level, random, p));
				// The rim of a bell hangs down a block or two.
				if (this.shape.equals("bell") && d > r - 1.0) {
					for (int k = 1; k <= 1 + random.nextInt(2); k++) {
						p.move(0, -1, 0);
						Gen.setSoft(level, w, p, this.cap.getState(level, random, p));
					}
				}
			}
		}
		return true;
	}
}
