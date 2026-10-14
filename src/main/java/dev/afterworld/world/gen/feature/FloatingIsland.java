package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.world.gen.Gen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * "Fields where stones float as if they forgot which way is down." A chunk of meadow hanging in
 * the sky: grassy top, a rocky underside streaked with calcite and amethyst, glow berries
 * trailing beneath, and sometimes a lone azalea tree.
 */
public record FloatingIsland() implements Feature {
	public static final FloatingIsland INSTANCE = new FloatingIsland();
	public static final MapCodec<FloatingIsland> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<FloatingIsland> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int ground = Gen.ground(level, origin.getX(), origin.getZ());
		int r = 7 + random.nextInt(8);
		double depth = r * (0.9 + random.nextDouble() * 0.7);
		// Always float clear of the ground, however deep the underside goes.
		int top = ground + (int) (depth * 1.3) + 10 + random.nextInt(24);
		if (top + 10 > level.getMaxY()) {
			return false;
		}
		long seed = random.nextLong();
		boolean crystal = true;
		// Some islands are held down: iron chains run from the underside to the earth.
		boolean chained = random.nextFloat() < 0.45F;
		List<BlockPos> tops = new ArrayList<>();
		List<BlockPos> bottoms = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r - 3; dx <= r + 3; dx++) {
			for (int dz = -r - 3; dz <= r + 3; dz++) {
				double ang = Math.atan2(dz, dx);
				double edge = r * (0.8 + 0.35 * Gen.noise(seed, Math.cos(ang) * 3 + 10, Math.sin(ang) * 3 + 10, 1.0));
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > edge) {
					continue;
				}
				double t = d / edge;
				int bump = (int) Math.round((1 - t) * 2.0 * Gen.noise(seed + 1, dx, dz, 3.0));
				int bottom = 1 + (int) Math.round(depth * Math.pow(1 - t, 0.75) * (0.7 + 0.6 * Gen.noise(seed + 2, dx, dz, 2.5)));
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				for (int k = -bottom; k <= bump; k++) {
					double h = Gen.hash(seed + 3, x * 31 + k, z);
					BlockState s;
					boolean rim = t > 0.86;
					if (k == bump) {
						s = rim && h < 0.5 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
					} else if (k >= bump - 2) {
						s = rim ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
					} else if (crystal && h < 0.1) {
						s = Blocks.AMETHYST_BLOCK.defaultBlockState();
					} else if (k < -bottom + 2 && h < 0.22) {
						s = Blocks.CALCITE.defaultBlockState();
					} else if (h < 0.25) {
						s = Blocks.CALCITE.defaultBlockState();
					} else if (h < 0.4) {
						s = Blocks.TUFF.defaultBlockState();
					} else {
						s = Blocks.STONE.defaultBlockState();
					}
					Gen.set(level, w, p.set(x, top + k, z), s);
				}
				tops.add(new BlockPos(x, top + bump + 1, z));
				bottoms.add(new BlockPos(x, top - bottom - 1, z));
			}
		}
		for (BlockPos tp : tops) {
			float f = random.nextFloat();
			if (f < 0.3F) {
				Gen.setSoft(level, w, tp, Blocks.SHORT_GRASS.defaultBlockState());
			} else if (f < 0.36F) {
				Gen.setSoft(level, w, tp, Blocks.WILDFLOWERS.defaultBlockState());
			} else if (f < 0.39F) {
				Gen.setSoft(level, w, tp, (random.nextBoolean() ? Blocks.OXEYE_DAISY : Blocks.CORNFLOWER).defaultBlockState());
			}
		}
		for (BlockPos bp : bottoms) {
			float f = random.nextFloat();
			if (f < 0.05F) {
				int len = 2 + random.nextInt(4);
				for (int i = 0; i < len; i++) {
					if (!Gen.setSoft(level, w, bp.below(i), Blocks.HANGING_ROOTS.defaultBlockState())) {
						break;
					}
				}
			} else if (f < 0.09F) {
				Gen.setSoft(level, w, bp, Blocks.GLOW_LICHEN.defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.UP), true));
			} else if (f < 0.17F) {
				int len = 1 + random.nextInt(5);
				for (int i = 0; i < len; i++) {
					boolean tip = i == len - 1;
					BlockState vine = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.4F);
					if (!Gen.setSoft(level, w, bp.below(i), vine)) {
						break;
					}
				}
			} else if (crystal && f < 0.17F) {
				Gen.setSoft(level, w, bp, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
			}
		}
		if (chained) {
			// Three or four chains, from the underside down to the ground (when it is close enough).
			int n = 3 + random.nextInt(2);
			for (int c = 0; c < n; c++) {
				double a = c * Math.PI * 2 / n + random.nextDouble() * 0.5;
				int cx = origin.getX() + (int) Math.round(Math.cos(a) * r * 0.55);
				int cz = origin.getZ() + (int) Math.round(Math.sin(a) * r * 0.55);
				if (!w.contains(cx, top, cz)) {
					continue;
				}
				int floor = Gen.ground(level, cx, cz);
				int y = top - 2;
				while (y > floor && level.getBlockState(p.set(cx, y, cz)).isSolid()) {
					y--;
				}
				if (y - floor > 48) {
					continue;
				}
				for (; y >= floor; y--) {
					Gen.set(level, w, p.set(cx, y, cz), Blocks.IRON_CHAIN.defaultBlockState());
				}
				Gen.set(level, w, p.set(cx, floor - 1, cz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			}
		}
		if (r >= 8 && random.nextFloat() < 0.65F) {
			BlockPos base = new BlockPos(origin.getX(), top + 1, origin.getZ());
			int h = 4 + random.nextInt(3);
			for (int i = 0; i < h; i++) {
				Gen.set(level, w, base.above(i), Blocks.OAK_LOG.defaultBlockState());
			}
			BlockState leaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			BlockState plain = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (int dx = -3; dx <= 3; dx++) {
				for (int dy = -1; dy <= 2; dy++) {
					for (int dz = -3; dz <= 3; dz++) {
						if (dx * dx + dy * dy * 2 + dz * dz <= 10 + random.nextInt(3)) {
							Gen.setSoft(level, w, base.offset(dx, h - 1 + dy, dz), random.nextFloat() < 0.35F ? leaves : plain);
						}
					}
				}
			}
		}
		return true;
	}
}
