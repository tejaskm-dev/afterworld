package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Signs that others came down here before you, left on the floors of caves: a miners' camp with a
 * cold fire, bedrolls, a chest and a lantern on a post; a forgotten shrine of candles with an
 * offering pot; a lost explorer's bones beside their pack; a ring of mushrooms round a dead fire.
 * Only where a cave opens wide and level enough to stand in, never near the surface.
 */
public record CaveCamp(int tries, int minY, int maxY, Identifier loot) implements Feature {
	public static final MapCodec<CaveCamp> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.intRange(1, 16).optionalFieldOf("tries", 4).forGetter(CaveCamp::tries),
		Codec.INT.optionalFieldOf("min_y", -48).forGetter(CaveCamp::minY),
		Codec.INT.optionalFieldOf("max_y", 40).forGetter(CaveCamp::maxY),
		Identifier.CODEC.fieldOf("loot").forGetter(CaveCamp::loot)
	).apply(i, CaveCamp::new));

	@Override
	public MapCodec<CaveCamp> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int t = 0; t < this.tries; t++) {
			int x = x0 + 4 + random.nextInt(8);
			int z = z0 + 4 + random.nextInt(8);
			int top = Math.min(this.maxY, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 14);
			// Every cave floor in this column (stone with open air over it), from the top down; then
			// one of the roomy ones, chosen at random.
			IntList floors = new IntArrayList();
			boolean open = false;
			for (int y = top; y >= this.minY; y--) {
				BlockState b = level.getBlockState(p.set(x, y, z));
				if (b.isAir()) {
					open = true;
					continue;
				}
				if (open && stone(b)) {
					floors.add(y + 1);
				}
				open = false;
			}
			IntList roomy = new IntArrayList();
			for (int i = 0; i < floors.size(); i++) {
				if (roomy(level, w, x, floors.getInt(i), z)) {
					roomy.add(floors.getInt(i));
				}
			}
			if (roomy.isEmpty()) {
				continue;
			}
			int fy = roomy.getInt(random.nextInt(roomy.size()));
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			switch (random.nextInt(7)) {
				case 0, 1, 2 -> camp(level, w, random, x, fy, z, facing);
				case 3, 4 -> shrine(level, w, random, x, fy, z, facing);
				case 5 -> explorer(level, w, random, x, fy, z, facing);
				default -> ring(level, w, random, x, fy, z);
			}
			return true;
		}
		return false;
	}

	private static boolean stone(BlockState b) {
		return b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(BlockTags.DIRT) || b.is(Blocks.GRAVEL) || b.is(Blocks.TUFF) || b.is(Blocks.CALCITE);
	}

	/** Most of a 5x5 patch (18 of its 25 cells) is level floor with three clear blocks over it, dry. */
	private static boolean roomy(WorldGenLevel level, Gen.Window w, int x, int fy, int z) {
		int good = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (!w.contains(x + dx, fy + 3, z + dz)) {
					return false;
				}
				boolean ok = stone(level.getBlockState(p.set(x + dx, fy - 1, z + dz)));
				for (int k = 0; k < 3 && ok; k++) {
					BlockState b = level.getBlockState(p.set(x + dx, fy + k, z + dz));
					ok = b.isAir() && b.getFluidState().isEmpty();
				}
				if (ok) {
					good++;
				}
			}
		}
		return good >= 18;
	}

	/** Places a block where it can stand: into air, on a solid floor. */
	private static boolean put(WorldGenLevel level, Gen.Window w, int x, int y, int z, BlockState s) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!w.contains(x, y, z) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return false;
		}
		level.setBlock(pos, s, 2);
		return true;
	}

	/** Places a block on top of one just placed (a lantern on its post, candles on the shrine). */
	private static void stack(WorldGenLevel level, Gen.Window w, int x, int y, int z, BlockState s) {
		BlockPos pos = new BlockPos(x, y, z);
		if (w.contains(x, y, z) && level.getBlockState(pos).isAir()) {
			level.setBlock(pos, s, 2);
		}
	}

	private void chest(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z, Direction facing) {
		if (put(level, w, x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing))) {
			RandomizableContainer.setBlockEntityLootTable(level, random, new BlockPos(x, y, z), ResourceKey.create(Registries.LOOT_TABLE, this.loot));
		}
	}

	/** A miners' camp: a cold fire, two bedrolls, a chest and a barrel, a lantern on a post, a few rails. */
	private void camp(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z, Direction facing) {
		Direction side = facing.getClockWise();
		put(level, w, x, y, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		for (int s : new int[] {-1, 1}) {
			int bx = x + side.getStepX() * 2 * s;
			int bz = z + side.getStepZ() * 2 * s;
			put(level, w, bx, y, bz, (s < 0 ? Blocks.CARPET.brown() : Blocks.CARPET.red()).defaultBlockState());
			put(level, w, bx + facing.getStepX(), y, bz + facing.getStepZ(), Blocks.CARPET.white().defaultBlockState());
		}
		int cx = x - facing.getStepX() * 2;
		int cz = z - facing.getStepZ() * 2;
		chest(level, w, random, cx, y, cz, facing);
		put(level, w, cx + side.getStepX(), y, cz + side.getStepZ(), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		int lx = x + facing.getStepX() * 2 + side.getStepX();
		int lz = z + facing.getStepZ() * 2 + side.getStepZ();
		if (put(level, w, lx, y, lz, Blocks.SPRUCE_FENCE.defaultBlockState())) {
			stack(level, w, lx, y + 1, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		}
		for (int k = -1; k <= 1; k++) {
			put(level, w, x - side.getStepX() * 2 + facing.getStepX() * k, y, z - side.getStepZ() * 2 + facing.getStepZ() * k, Blocks.RAIL.defaultBlockState());
		}
	}

	/** A forgotten shrine: a little pillar with candles on it, an offering pot, two kneeling steps. */
	private void shrine(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z, Direction facing) {
		if (put(level, w, x, y, z, Blocks.STONE_BRICKS.defaultBlockState())) {
			stack(level, w, x, y + 1, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			stack(level, w, x, y + 2, z, Blocks.DYED_CANDLE.white().defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(4))
				.setValue(CandleBlock.LIT, random.nextInt(3) == 0));
		}
		Direction side = facing.getClockWise();
		put(level, w, x + side.getStepX(), y, z + side.getStepZ(), Blocks.DECORATED_POT.defaultBlockState());
		for (int s : new int[] {-1, 1}) {
			put(level, w, x + facing.getStepX() * 2 + side.getStepX() * s, y, z + facing.getStepZ() * 2 + side.getStepZ() * s,
				Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite()));
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (random.nextInt(4) == 0 && (dx != 0 || dz != 0)) {
					put(level, w, x + dx, y, z + dz, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true));
				}
			}
		}
	}

	/** Someone who did not come back: bones, a skull, their pack, a burnt-out candle, a web above. */
	private void explorer(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z, Direction facing) {
		Direction side = facing.getClockWise();
		put(level, w, x, y, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, side.getAxis()));
		put(level, w, x + facing.getStepX(), y, z + facing.getStepZ(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)));
		chest(level, w, random, x - side.getStepX(), y, z - side.getStepZ(), facing);
		put(level, w, x + side.getStepX() * 2, y, z + side.getStepZ() * 2, Blocks.CARPET.brown().defaultBlockState());
		put(level, w, x + side.getStepX(), y, z + side.getStepZ() - facing.getStepZ(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1).setValue(CandleBlock.LIT, false));
		stack(level, w, x, y + 2, z, Blocks.COBWEB.defaultBlockState());
	}

	/** A ring of mushrooms round a dead fire, the floor inside gone to mycelium. */
	private void ring(WorldGenLevel level, Gen.Window w, RandomSource random, int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos f = new BlockPos(x + dx, y - 1, z + dz);
				if (w.contains(f.getX(), f.getY(), f.getZ()) && stone(level.getBlockState(f))) {
					level.setBlock(f, Blocks.MYCELIUM.defaultBlockState(), 2);
				}
			}
		}
		put(level, w, x, y, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		for (int a = 0; a < 12; a++) {
			double t = a * Math.PI / 6;
			int mx = x + (int) Math.round(Math.cos(t) * 2.2);
			int mz = z + (int) Math.round(Math.sin(t) * 2.2);
			put(level, w, mx, y, mz, (a % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
		}
	}
}
