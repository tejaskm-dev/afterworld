package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Repaints one biome's ground column by column and sows its plants, all from data: each surface
 * layer applies where one of three noises (broad patches, small patches, scattered) falls in its
 * range, and plants are rolled per column. Runs once per chunk before anything grows.
 */
public record Ground(ResourceKey<Biome> biome, List<Layer> surface, List<Plant> plants, Optional<BlockState> under, boolean sand) implements Feature {
	public record Layer(BlockState state, int noise, float min, float max) {
		public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockState.CODEC.fieldOf("state").forGetter(Layer::state),
			Codec.intRange(0, 2).optionalFieldOf("noise", 0).forGetter(Layer::noise),
			Codec.FLOAT.optionalFieldOf("min", 0.0F).forGetter(Layer::min),
			Codec.FLOAT.optionalFieldOf("max", 1.01F).forGetter(Layer::max)
		).apply(i, Layer::new));
	}

	public record Plant(BlockState state, float chance) {
		public static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockState.CODEC.fieldOf("state").forGetter(Plant::state),
			Codec.FLOAT.fieldOf("chance").forGetter(Plant::chance)
		).apply(i, Plant::new));
	}

	public static final MapCodec<Ground> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(Ground::biome),
		Layer.CODEC.listOf().optionalFieldOf("surface", List.of()).forGetter(Ground::surface),
		Plant.CODEC.listOf().optionalFieldOf("plants", List.of()).forGetter(Ground::plants),
		BlockState.CODEC.optionalFieldOf("under").forGetter(Ground::under),
		Codec.BOOL.optionalFieldOf("sand", false).forGetter(Ground::sand)
	).apply(i, Ground::new));

	@Override
	public MapCodec<Ground> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		long seed = level.getSeed() ^ this.biome.identifier().hashCode() * 0x9E3779B97F4A7C15L;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int y = Gen.ground(level, x, z) - 1;
				p.set(x, y, z);
				BlockState top = level.getBlockState(p);
				boolean soil = Gen.isSoil(top) && (this.sand || !top.is(BlockTags.SAND));
				if (!soil || !level.getBiome(p).is(this.biome)) {
					continue;
				}
				above.set(x, y + 1, z);
				boolean wet = !level.getFluidState(above).isEmpty();
				double[] n = {Gen.noise(seed, x, z, 9.0), Gen.noise(seed + 7, x, z, 3.5), Gen.hash(seed + 13, x, z)};
				for (Layer l : this.surface) {
					double v = n[l.noise];
					if (v >= l.min && v < l.max) {
						level.setBlock(p, l.state, 2);
						this.under.ifPresent(u -> level.setBlock(p.below(), u, 2));
						break;
					}
				}
				if (wet || this.plants.isEmpty() || !level.getBlockState(above).isAir()) {
					continue;
				}
				double r = Gen.hash(seed + 29, x, z);
				double acc = 0;
				for (Plant plant : this.plants) {
					acc += plant.chance;
					if (r < acc) {
						place(level, above, plant.state);
						break;
					}
				}
			}
		}
		return true;
	}

	static void place(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (!state.canSurvive(level, pos)) {
			return;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			if (level.getBlockState(pos.above()).isAir()) {
				DoublePlantBlock.placeAt(level, state, pos, 2);
			}
		} else {
			level.setBlock(pos, state, 2);
		}
	}
}
