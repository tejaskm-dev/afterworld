package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A broken stone causeway wandering across the fen on old piers, with lamp posts still lit. */
public record Causeway() implements Feature {
	public static final Causeway INSTANCE = new Causeway();
	public static final MapCodec<Causeway> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<Causeway> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		double a = random.nextDouble() * Math.PI * 2;
		double x = origin.getX() + 0.5;
		double z = origin.getZ() + 0.5;
		int len = 12 + random.nextInt(14);
		int pathY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < len; i++) {
			a += (random.nextDouble() - 0.5) * 0.35;
			x += Math.cos(a);
			z += Math.sin(a);
			int ix = (int) Math.floor(x);
			int iz = (int) Math.floor(z);
			if (!w.contains(ix, pathY, iz) || random.nextFloat() < 0.08F) {
				continue;
			}
			int px = (int) Math.round(-Math.sin(a));
			int pz = (int) Math.round(Math.cos(a));
			for (int k = -1; k <= 1; k++) {
				int bx = ix + px * k;
				int bz = iz + pz * k;
				// Only read terrain where this chunk's feature may write: nothing further out.
				if (!w.contains(bx, pathY, bz)) {
					continue;
				}
				int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, bx, bz) - 1;
				int y = Math.max(pathY, surface);
				if (y > pathY + 2) {
					continue;
				}
				Gen.set(level, w, p.set(bx, y, bz), k == 0 || random.nextFloat() < 0.7F ? path(random) : Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState());
				// Piers down to the bottom every few steps.
				if (k == 0 && i % 3 == 0) {
					for (int yy = y - 1; yy > y - 12 && !level.getFluidState(p.set(bx, yy, bz)).isEmpty() || yy == y - 1; yy--) {
						Gen.set(level, w, p.set(bx, yy, bz), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
						if (level.getBlockState(p.set(bx, yy - 1, bz)).isSolidRender()) {
							break;
						}
					}
				}
			}
			if (i % 7 == 3) {
				int side = random.nextBoolean() ? 1 : -1;
				int lx = ix + px * side * 2;
				int lz = iz + pz * side * 2;
				Gen.set(level, w, p.set(lx, pathY, lz), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
				Gen.set(level, w, p.set(lx, pathY + 1, lz), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
				Gen.set(level, w, p.set(lx, pathY + 2, lz), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
				Gen.set(level, w, p.set(lx, pathY + 3, lz), Blocks.LANTERN.defaultBlockState());
			}
		}
		return true;
	}

	private static BlockState path(RandomSource random) {
		float f = random.nextFloat();
		return (f < 0.45F ? Blocks.MOSSY_STONE_BRICKS : f < 0.65F ? Blocks.STONE_BRICKS : f < 0.82F ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
	}
}
