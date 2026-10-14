package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.AwBiomes;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Repaints the ground of one Afterworld biome, column by column, with patchy noise: ash-grey heath
 * for the moor, moss and podzol in Hollowwood, calcite outcrops in the Shardfields, mud in the fen.
 * Runs once per chunk before anything grows, so vegetation lands on the painted ground.
 */
public record SurfacePaint(String palette) implements Feature {
	public static final MapCodec<SurfacePaint> CODEC = Codec.STRING.fieldOf("palette").xmap(SurfacePaint::new, SurfacePaint::palette);

	@Override
	public MapCodec<SurfacePaint> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ResourceKey<Biome> biome = switch (this.palette) {
			case "ashen" -> AwBiomes.ASHEN_MOOR;
			case "hollow" -> AwBiomes.HOLLOWWOOD;
			case "shard" -> AwBiomes.SHARDFIELDS;
			default -> AwBiomes.GLOAMFEN;
		};
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		long seed = level.getSeed() ^ this.palette.hashCode();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int y = Gen.ground(level, x, z) - 1;
				p.set(x, y, z);
				BlockState top = level.getBlockState(p);
				if (!Gen.isSoil(top) || top.is(Blocks.SAND) || !level.getBiome(p).is(biome)) {
					continue;
				}
				above.set(x, y + 1, z);
				boolean wet = !level.getFluidState(above).isEmpty();
				double n = Gen.noise(seed, x, z, 7.0);
				double m = Gen.noise(seed + 7, x, z, 3.0);
				double r = Gen.hash(seed + 13, x, z);
				switch (this.palette) {
					case "ashen" -> ashen(level, p, above, wet, n, m, r);
					case "hollow" -> hollow(level, p, above, wet, n, m, r);
					case "shard" -> shard(level, p, above, wet, n, m, r);
					default -> fen(level, p, above, wet, n, m, r);
				}
			}
		}
		return true;
	}

	private static void ashen(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockPos.MutableBlockPos above, boolean wet, double n, double m, double r) {
		BlockState surface = Blocks.GRASS_BLOCK.defaultBlockState();
		if (n > 0.64) {
			surface = Blocks.COARSE_DIRT.defaultBlockState();
		} else if (n < 0.2 && m > 0.45) {
			surface = Blocks.GRAVEL.defaultBlockState();
		} else if (m > 0.8) {
			surface = Blocks.PACKED_MUD.defaultBlockState();
		} else if (m < 0.1) {
			surface = Blocks.TUFF.defaultBlockState();
		}
		level.setBlock(p, surface, 2);
		if (wet || !level.getBlockState(above).isAir()) {
			return;
		}
		if (r < 0.10) {
			plant(level, above, Blocks.SHORT_DRY_GRASS.defaultBlockState());
		} else if (r < 0.13) {
			plant(level, above, Blocks.TALL_DRY_GRASS.defaultBlockState());
		} else if (r < 0.145) {
			plant(level, above, Blocks.DEAD_BUSH.defaultBlockState());
		}
	}

	private static void hollow(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockPos.MutableBlockPos above, boolean wet, double n, double m, double r) {
		BlockState surface = Blocks.GRASS_BLOCK.defaultBlockState();
		if (n > 0.6) {
			surface = Blocks.MOSS_BLOCK.defaultBlockState();
		} else if (n < 0.3) {
			surface = Blocks.PODZOL.defaultBlockState();
		} else if (m > 0.84) {
			surface = Blocks.ROOTED_DIRT.defaultBlockState();
		}
		level.setBlock(p, surface, 2);
		if (wet || !level.getBlockState(above).isAir()) {
			return;
		}
		if (surface.is(Blocks.MOSS_BLOCK)) {
			if (r < 0.28) {
				plant(level, above, Blocks.MOSS_CARPET.defaultBlockState());
			}
		} else if (r < 0.07) {
			plant(level, above, Blocks.FERN.defaultBlockState());
		} else if (r < 0.10) {
			plant(level, above, Blocks.LEAF_LITTER.defaultBlockState());
		} else if (r < 0.115) {
			plant(level, above, Blocks.BROWN_MUSHROOM.defaultBlockState());
		} else if (r < 0.12) {
			plant(level, above, Blocks.FIREFLY_BUSH.defaultBlockState());
		}
	}

	private static void shard(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockPos.MutableBlockPos above, boolean wet, double n, double m, double r) {
		// Small calcite outcrops flecked with amethyst, so they read as stone rather than snow.
		boolean calcite = n > 0.8 && m > 0.35;
		if (calcite) {
			level.setBlock(p, (r < 0.12 ? Blocks.AMETHYST_BLOCK : r < 0.3 ? Blocks.TUFF : Blocks.CALCITE).defaultBlockState(), 2);
			level.setBlock(p.below(), Blocks.CALCITE.defaultBlockState(), 2);
		}
		if (wet || !level.getBlockState(above).isAir()) {
			return;
		}
		if (calcite) {
			if (r < 0.05) {
				plant(level, above, Blocks.SMALL_AMETHYST_BUD.defaultBlockState());
			} else if (r < 0.065) {
				plant(level, above, Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState());
			}
		} else if (r < 0.06) {
			plant(level, above, Blocks.WILDFLOWERS.defaultBlockState());
		} else if (r < 0.10) {
			plant(level, above, Blocks.SHORT_GRASS.defaultBlockState());
		} else if (r < 0.11) {
			plant(level, above, (m < 0.33 ? Blocks.OXEYE_DAISY : m < 0.66 ? Blocks.CORNFLOWER : Blocks.WHITE_TULIP).defaultBlockState());
		}
	}

	private static void fen(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockPos.MutableBlockPos above, boolean wet, double n, double m, double r) {
		BlockState surface;
		if (wet) {
			surface = (n > 0.45 ? Blocks.MUD : Blocks.CLAY).defaultBlockState();
		} else if (n > 0.58) {
			surface = Blocks.MUD.defaultBlockState();
		} else if (n < 0.25) {
			surface = Blocks.MOSS_BLOCK.defaultBlockState();
		} else {
			surface = Blocks.GRASS_BLOCK.defaultBlockState();
		}
		level.setBlock(p, surface, 2);
		if (wet) {
			// Lily pads on shallow open water.
			if (r < 0.07) {
				BlockPos.MutableBlockPos s = above.mutable();
				for (int i = 0; i < 3 && !level.getFluidState(s).isEmpty(); i++) {
					s.move(0, 1, 0);
				}
				if (level.getBlockState(s).isAir() && !level.getFluidState(s.below()).isEmpty()) {
					plant(level, s, Blocks.LILY_PAD.defaultBlockState());
				}
			}
			return;
		}
		if (!level.getBlockState(above).isAir()) {
			return;
		}
		if (surface.is(Blocks.MOSS_BLOCK) && r < 0.22) {
			plant(level, above, Blocks.MOSS_CARPET.defaultBlockState());
		} else if (r < 0.06) {
			plant(level, above, Blocks.FERN.defaultBlockState());
		} else if (r < 0.08) {
			plant(level, above, Blocks.FIREFLY_BUSH.defaultBlockState());
		}
	}

	private static void plant(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (state.canSurvive(level, pos)) {
			level.setBlock(pos, state, 2);
		}
	}
}
