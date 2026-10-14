package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Small ruins scattered through the world in each biome's own stone: broken walls around a paved
 * floor, a colonnade, the stump of a round tower, a roadside shrine or an old well. Some keep a
 * chest. They are the little things to stop for between the big ones.
 */
public record Ruin(BlockStateProvider wall, BlockStateProvider floor, Optional<BlockStateProvider> accent, String style,
	Optional<ResourceKey<LootTable>> loot, float lootChance, boolean underwater) implements Feature {

	public static final MapCodec<Ruin> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.DIRECT_CODEC.fieldOf("wall").forGetter(Ruin::wall),
		BlockStateProvider.DIRECT_CODEC.fieldOf("floor").forGetter(Ruin::floor),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("accent").forGetter(Ruin::accent),
		Codec.STRING.optionalFieldOf("style", "walls").forGetter(Ruin::style),
		ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot").forGetter(Ruin::loot),
		Codec.FLOAT.optionalFieldOf("loot_chance", 0.35F).forGetter(Ruin::lootChance),
		Codec.BOOL.optionalFieldOf("underwater", false).forGetter(Ruin::underwater)
	).apply(i, Ruin::new));

	@Override
	public MapCodec<Ruin> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		// Only on fairly level, dry ground.
		int lo = y0;
		int hi = y0;
		for (int[] o : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			if (!w.contains(x + o[0], y0, z + o[1])) {
				return false;
			}
			int gy = Gen.ground(level, x + o[0], z + o[1]);
			lo = Math.min(lo, gy);
			hi = Math.max(hi, gy);
		}
		// Land ruins want dry ground; sunken ones want to be under water.
		if (hi - lo > 3 || level.getFluidState(new BlockPos(x, y0, z)).isEmpty() == this.underwater) {
			return false;
		}
		Ctx c = new Ctx(level, w, random);
		BlockPos chest = switch (this.style) {
			case "colonnade" -> colonnade(c, x, y0, z);
			case "tower" -> tower(c, x, y0, z);
			case "shrine" -> shrine(c, x, y0, z);
			case "well" -> well(c, x, y0, z);
			default -> walls(c, x, y0, z);
		};
		if (chest != null && this.loot.isPresent() && random.nextFloat() < this.lootChance && w.contains(chest.getX(), chest.getY(), chest.getZ())) {
			level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), 2);
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, this.loot.get());
		}
		return true;
	}

	private record Ctx(WorldGenLevel level, Gen.Window w, RandomSource random) {
	}

	private void wall(Ctx c, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		Gen.set(c.level, c.w, p, this.wall.getState(c.level, c.random, p));
	}

	private void floor(Ctx c, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		Gen.set(c.level, c.w, p, this.floor.getState(c.level, c.random, p));
	}

	private void accent(Ctx c, int x, int y, int z) {
		if (this.accent.isEmpty()) {
			return;
		}
		BlockPos p = new BlockPos(x, y, z);
		var s = this.accent.get().getState(c.level, c.random, p);
		if (c.w.contains(x, y, z) && (c.level.getBlockState(p).isAir() || this.underwater && c.level.getBlockState(p).is(Blocks.WATER)) && s.canSurvive(c.level, p)) {
			c.level.setBlock(p, s, 2);
		}
	}

	private void clear(Ctx c, int x, int y, int z) {
		Gen.set(c.level, c.w, new BlockPos(x, y, z), (this.underwater ? Blocks.WATER : Blocks.AIR).defaultBlockState());
	}

	/** A paved floor framed by broken walls. */
	private BlockPos walls(Ctx c, int x, int y0, int z) {
		int hw = 3 + c.random.nextInt(3);
		int hd = 3 + c.random.nextInt(3);
		for (int dx = -hw; dx <= hw; dx++) {
			for (int dz = -hd; dz <= hd; dz++) {
				boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hd;
				if (!edge) {
					if (c.random.nextFloat() < 0.75F) {
						floor(c, x + dx, y0 - 1, z + dz);
					}
					for (int y = 0; y < 4; y++) {
						clear(c, x + dx, y0 + y, z + dz);
					}
					continue;
				}
				int hgt = (int) (Math.max(0, Gen.noise(x * 31L + z, dx, dz, 2.5) * 6 - 1));
				if (dx == 0 && dz == -hd) {
					hgt = 0;   // doorway
				}
				for (int y = -1; y < hgt; y++) {
					wall(c, x + dx, y0 + y, z + dz);
				}
			}
		}
		for (int k = 0; k < 6; k++) {
			int rx = x + c.random.nextInt(hw * 2 + 5) - hw - 2;
			int rz = z + c.random.nextInt(hd * 2 + 5) - hd - 2;
			if (c.w.contains(rx, y0, rz)) {
				wall(c, rx, Gen.ground(c.level, rx, rz), rz);
			}
		}
		accent(c, x - hw + 1, y0, z + hd - 1);
		accent(c, x + hw - 1, y0, z + hd - 1);
		return new BlockPos(x + hw - 1, y0, z - hd + 1);
	}

	/** Two rows of pillars, a few still bridged by their lintels. */
	private BlockPos colonnade(Ctx c, int x, int y0, int z) {
		Direction along = c.random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		Direction across = along.getClockWise();
		int n = 3 + c.random.nextInt(3);
		for (int k = 0; k < n; k++) {
			int bx = x + along.getStepX() * (k * 3 - n * 3 / 2);
			int bz = z + along.getStepZ() * (k * 3 - n * 3 / 2);
			int[] heights = new int[2];
			for (int side = 0; side < 2; side++) {
				int px = bx + across.getStepX() * (side == 0 ? -2 : 2);
				int pz = bz + across.getStepZ() * (side == 0 ? -2 : 2);
				int ph = c.random.nextFloat() < 0.35F ? 1 + c.random.nextInt(3) : 5;
				heights[side] = ph;
				for (int y = -1; y < ph; y++) {
					wall(c, px, y0 + y, pz);
				}
			}
			if (heights[0] == 5 && heights[1] == 5) {
				for (int s = -2; s <= 2; s++) {
					wall(c, bx + across.getStepX() * s, y0 + 5, bz + across.getStepZ() * s);
				}
			}
			for (int s = -1; s <= 1; s++) {
				if (c.random.nextFloat() < 0.7F) {
					floor(c, bx + across.getStepX() * s, y0 - 1, bz + across.getStepZ() * s);
				}
			}
		}
		accent(c, x, y0, z);
		return new BlockPos(x + across.getStepX(), y0, z + across.getStepZ());
	}

	/** The stump of a round tower, open to the sky. */
	private BlockPos tower(Ctx c, int x, int y0, int z) {
		double r = 3.2;
		int top = 5 + c.random.nextInt(8);
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.hypot(dx, dz);
				if (d <= r - 0.8) {
					floor(c, x + dx, y0 - 1, z + dz);
					for (int y = 0; y < top; y++) {
						clear(c, x + dx, y0 + y, z + dz);
					}
				} else if (d <= r + 0.4) {
					double ang = Math.atan2(dz, dx);
					int hgt = (int) (top * (0.45 + 0.55 * Math.abs(Math.sin(ang * 1.5 + x))));
					if (dz == (int) -r && dx == 0) {
						hgt = 0;
					}
					for (int y = -1; y < hgt; y++) {
						wall(c, x + dx, y0 + y, z + dz);
					}
				}
			}
		}
		accent(c, x, y0, z + 1);
		return new BlockPos(x + 1, y0, z - 1);
	}

	/** A raised plinth with posts at the corners and an offering at its heart. */
	private BlockPos shrine(Ctx c, int x, int y0, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				floor(c, x + dx, y0 - 1, z + dz);
				floor(c, x + dx, y0, z + dz);
				for (int y = 1; y < 4; y++) {
					clear(c, x + dx, y0 + y, z + dz);
				}
			}
		}
		for (int[] o : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			for (int y = 1; y <= 2; y++) {
				wall(c, x + o[0], y0 + y, z + o[1]);
			}
			accent(c, x + o[0], y0 + 3, z + o[1]);
		}
		wall(c, x, y0 + 1, z);
		accent(c, x, y0 + 2, z);
		return new BlockPos(x, y0 + 1, z + 1);
	}

	/** A round well of the local stone, still holding water. */
	private BlockPos well(Ctx c, int x, int y0, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > 2.4) {
					continue;
				}
				if (d < 1.2) {
					for (int y = -4; y < 0; y++) {
						Gen.set(c.level, c.w, new BlockPos(x + dx, y0 + y, z + dz), Blocks.WATER.defaultBlockState());
					}
					wall(c, x + dx, y0 - 5, z + dz);
				} else {
					for (int y = -5; y < 1; y++) {
						wall(c, x + dx, y0 + y, z + dz);
					}
				}
			}
		}
		for (int[] o : new int[][] {{-2, 0}, {2, 0}}) {
			for (int y = 1; y <= 3; y++) {
				Gen.set(c.level, c.w, new BlockPos(x + o[0], y0 + y, z + o[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			Gen.set(c.level, c.w, new BlockPos(x + dx, y0 + 4, z), Blocks.SPRUCE_SLAB.defaultBlockState());
		}
		accent(c, x, y0 + 3, z);
		return null;
	}
}
