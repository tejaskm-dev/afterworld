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
 * Tall things standing out of the ground: tapering spires (obelisks, termite towers, ice spikes),
 * leaning shards (crystals), clusters of columns (basalt, ice) and low mounds. The body palette can
 * be studded with a glowing block, and the top can use its own tip palette.
 */
public record Spire(BlockStateProvider body, Optional<BlockStateProvider> tip, Optional<BlockStateProvider> glow, float glowChance,
	String style, int minHeight, int maxHeight, float minRadius, float maxRadius) implements Feature {

	public static final MapCodec<Spire> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.DIRECT_CODEC.fieldOf("body").forGetter(Spire::body),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("tip").forGetter(Spire::tip),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("glow").forGetter(Spire::glow),
		Codec.FLOAT.optionalFieldOf("glow_chance", 0.0F).forGetter(Spire::glowChance),
		Codec.STRING.optionalFieldOf("style", "taper").forGetter(Spire::style),
		Codec.intRange(2, 60).fieldOf("min_height").forGetter(Spire::minHeight),
		Codec.intRange(2, 80).fieldOf("max_height").forGetter(Spire::maxHeight),
		Codec.FLOAT.optionalFieldOf("min_radius", 1.0F).forGetter(Spire::minRadius),
		Codec.FLOAT.optionalFieldOf("max_radius", 2.5F).forGetter(Spire::maxRadius)
	).apply(i, Spire::new));

	@Override
	public MapCodec<Spire> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		int h = this.minHeight + random.nextInt(Math.max(1, this.maxHeight - this.minHeight + 1));
		if (y0 + h + 2 > level.getMaxY()) {
			return false;
		}
		double r = this.minRadius + random.nextDouble() * (this.maxRadius - this.minRadius);
		switch (this.style) {
			case "shard" -> shard(level, w, random, x, y0, z, h, r);
			case "columns" -> columns(level, w, random, x, y0, z, h, r);
			case "mound" -> taper(level, w, random, x, y0 - 1, z, h, r, 0.5);
			default -> taper(level, w, random, x, y0 - 2, z, h + 2, r, 1.0);
		}
		return true;
	}

	private void taper(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y0, int z, int h, double r, double power) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		double wobble = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < h; i++) {
			double t = i / (double) h;
			double rr = r * Math.pow(1 - t, power) + 0.35;
			double ox = Math.sin(wobble + t * 3) * r * 0.25 * t;
			double oz = Math.cos(wobble + t * 3) * r * 0.25 * t;
			int ir = (int) Math.ceil(rr);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.hypot(dx - ox, dz - oz);
					if (d > rr) {
						continue;
					}
					p.set(x + dx, y0 + i, z + dz);
					put(level, w, random, p, t);
				}
			}
		}
	}

	private void shard(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y0, int z, int h, double r) {
		double ang = random.nextDouble() * Math.PI * 2;
		double lean = 0.2 + random.nextDouble() * 0.45;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = -2; i < h; i++) {
			double t = Math.max(0, i) / (double) h;
			double cx = x + Math.cos(ang) * lean * Math.max(0, i);
			double cz = z + Math.sin(ang) * lean * Math.max(0, i);
			double rr = r * (1 - t) + 0.3;
			int ir = (int) Math.ceil(rr);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > rr * 1.25) {
						continue;
					}
					p.set((int) Math.round(cx) + dx, y0 + i, (int) Math.round(cz) + dz);
					put(level, w, random, p, t);
				}
			}
		}
	}

	private void columns(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y0, int z, int h, double r) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				if (dx * dx + dz * dz > r * r || random.nextFloat() < 0.25F) {
					continue;
				}
				int ch = Math.max(2, (int) (h * (0.35 + random.nextDouble() * 0.65) * (1 - Math.hypot(dx, dz) / (r + 1) * 0.5)));
				for (int i = -2; i < ch; i++) {
					p.set(x + dx, y0 + i, z + dz);
					put(level, w, random, p, i / (double) ch);
				}
			}
		}
	}

	private void put(WorldGenLevel level, Gen.Window w, RandomSource random, BlockPos.MutableBlockPos p, double t) {
		if (!w.contains(p.getX(), p.getY(), p.getZ())) {
			return;
		}
		BlockStateProvider pick = this.body;
		if (this.glow.isPresent() && random.nextFloat() < this.glowChance) {
			pick = this.glow.get();
		} else if (this.tip.isPresent() && t > 0.78) {
			pick = this.tip.get();
		}
		level.setBlock(p, pick.getState(level, random, p), 2);
	}
}
