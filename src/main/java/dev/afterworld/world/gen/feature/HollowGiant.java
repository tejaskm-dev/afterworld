package dev.afterworld.world.gen.feature;

import com.mojang.serialization.MapCodec;
import dev.afterworld.site.Sites;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A Hollowwood giant: a dark trunk four to five blocks across, hollow from the roots up past
 * half its height, with a doorway, shroomlights set into the heartwood, buttress roots, crooked
 * branches and a wide canopy dripping glow berries.
 */
public record HollowGiant() implements Feature {
	public static final HollowGiant INSTANCE = new HollowGiant();
	public static final MapCodec<HollowGiant> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<HollowGiant> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int cx = origin.getX();
		int cz = origin.getZ();
		int y0 = Gen.ground(level, cx, cz);
		BlockPos below = new BlockPos(cx, y0 - 1, cz);
		if (!Gen.isSoil(level.getBlockState(below)) || !level.getFluidState(below.above()).isEmpty() || y0 + 44 > level.getMaxY()) {
			return false;
		}
		int radius = 3 + random.nextInt(2);
		int height = 20 + random.nextInt(14);
		int hollowTop = (int) (height * 0.62);
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		long seed = random.nextLong();
		BlockState bark = Blocks.DARK_OAK_WOOD.defaultBlockState();
		BlockState heart = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

		for (int y = -3; y <= height; y++) {
			double flare = y < 5 ? (5 - y) * 0.55 : 0;
			double narrow = y > height * 0.7 ? (y - height * 0.7) * 0.12 : 0;
			double rad = radius + 0.5 + flare - narrow;
			int ir = (int) Math.ceil(rad) + 1;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.hypot(dx, dz);
					double ang = Math.atan2(dz, dx);
					double rr = rad + (Gen.noise(seed, ang * 3 + 50, y * 0.5, 1.0) - 0.5) * 0.9;
					if (d > rr) {
						continue;
					}
					p.set(cx + dx, y0 + y, cz + dz);
					boolean hollow = y >= 0 && y <= hollowTop && d < rr - 1.7;
					int along = dx * door.getStepX() + dz * door.getStepZ();
					int lateral = Math.abs(dx * door.getStepZ() - dz * door.getStepX());
					boolean doorway = y >= 0 && y <= 2 && along > 0 && lateral <= 1;
					if (hollow || doorway) {
						Gen.set(level, w, p, y == -1 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
						continue;
					}
					Gen.set(level, w, p, d >= rr - 1.0 ? bark : heart);
				}
			}
		}
		// Inside: a mossy floor, lights set into the wall, and sometimes a forgotten barrel.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				p.set(cx + dx, y0, cz + dz);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolidRender()) {
					float f = random.nextFloat();
					Gen.set(level, w, p.below(), (f < 0.5F ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT).defaultBlockState());
					if (f < 0.35F) {
						Gen.set(level, w, p, Blocks.MOSS_CARPET.defaultBlockState());
					} else if (f < 0.42F) {
						Gen.set(level, w, p, Blocks.BROWN_MUSHROOM.defaultBlockState());
					}
				}
			}
		}
		for (int y = 2; y <= hollowTop; y += 3 + random.nextInt(2)) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int inner = radius - 1;
			Gen.set(level, w, p.set(cx + d.getStepX() * inner, y0 + y, cz + d.getStepZ() * inner), Blocks.SHROOMLIGHT.defaultBlockState());
		}
		if (random.nextFloat() < 0.45F) {
			Direction back = door.getOpposite();
			BlockPos barrel = new BlockPos(cx + back.getStepX() * (radius - 2), y0, cz + back.getStepZ() * (radius - 2));
			if (Gen.set(level, w, barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP))) {
				RandomizableContainer.setBlockEntityLootTable(level, random, barrel, ResourceKey.create(Registries.LOOT_TABLE, Sites.lootId("old_cache")));
			}
		}

		// Buttress roots.
		int roots = 5 + random.nextInt(4);
		for (int i = 0; i < roots; i++) {
			double a = (i + random.nextDouble() * 0.6) * Math.PI * 2 / roots;
			double x = cx + 0.5 + Math.cos(a) * (radius + 1);
			double z = cz + 0.5 + Math.sin(a) * (radius + 1);
			double y = y0 + 2 + random.nextInt(2);
			int len = 4 + random.nextInt(5);
			for (int s = 0; s < len; s++) {
				x += Math.cos(a);
				z += Math.sin(a);
				// Reading terrain beyond the neighbouring chunks is not allowed while generating.
				if (!w.contains((int) Math.floor(x), y0, (int) Math.floor(z))) {
					break;
				}
				int gy = Gen.ground(level, (int) Math.floor(x), (int) Math.floor(z));
				y = Math.max(gy - 1, y - 0.6 - random.nextDouble() * 0.4);
				for (int yy = (int) y; yy >= gy - 1; yy--) {
					Gen.set(level, w, p.set((int) Math.floor(x), yy, (int) Math.floor(z)), bark);
				}
			}
		}

		// Branches with leafy ends, then the crown.
		BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState azalea = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int branches = 3 + random.nextInt(4);
		for (int i = 0; i < branches; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double x = cx + 0.5;
			double z = cz + 0.5;
			double y = y0 + height * (0.55 + random.nextDouble() * 0.35);
			int len = 5 + random.nextInt(5);
			double rise = 0.3 + random.nextDouble() * 0.4;
			Direction.Axis axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
			for (int s = 0; s < len + radius; s++) {
				x += Math.cos(a);
				z += Math.sin(a);
				y += rise;
				Gen.set(level, w, p.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)),
					Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
			}
			blob(level, w, random, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), 2.5 + random.nextDouble() * 1.5, leaves, azalea);
		}
		blob(level, w, random, cx, y0 + height + 1, cz, 6 + random.nextDouble() * 2, leaves, azalea);
		for (int i = 0; i < 3; i++) {
			blob(level, w, random, cx + random.nextInt(9) - 4, y0 + height - 2 + random.nextInt(4), cz + random.nextInt(9) - 4, 3.5 + random.nextDouble() * 2, leaves, azalea);
		}
		return true;
	}

	/** A squashed ball of leaves with ragged edges; some undersides trail glow berries. */
	private static void blob(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z, double r, BlockState leaves, BlockState accent) {
		int ir = (int) Math.ceil(r);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				for (int dy = -ir / 2 - 1; dy <= ir / 2 + 1; dy++) {
					double d = dx * dx + dz * dz + dy * dy * 3.2;
					if (d > r * r * (0.85 + random.nextDouble() * 0.3)) {
						continue;
					}
					p.set(x + dx, y + dy, z + dz);
					Gen.setSoft(level, w, p, random.nextFloat() < 0.15F ? accent : leaves);
				}
				if (random.nextFloat() < 0.06F) {
					int bottom = y - ir / 2 - 2;
					int len = 2 + random.nextInt(5);
					for (int i = 0; i < len; i++) {
						BlockState vine = (i == len - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
							.setValue(CaveVines.BERRIES, random.nextFloat() < 0.45F);
						p.set(x + dx, bottom - i, z + dz);
						if (!w.contains(p.getX(), p.getY() + 1, p.getZ())
							|| !level.getBlockState(p.above()).is(Blocks.DARK_OAK_LEAVES) && i == 0 || !Gen.setSoft(level, w, p, vine)) {
							break;
						}
					}
				}
			}
		}
	}
}
