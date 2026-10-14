package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Afterworld's trees, all from one feature: a shape (round, cone, flat, droop, column, giant, dead
 * or twisted), a log and leaves (oak-type leaves take the biome's foliage colour, so one block can
 * be white in the Pale Reach and violet in the Gloomwald), and optional decorations: things that
 * hang from the canopy, blocks mixed into it, and snow on top.
 */
public record AwTree(BlockState log, BlockStateProvider leaves, String shape, int minHeight, int maxHeight,
	Optional<BlockStateProvider> hang, float hangChance, Optional<BlockStateProvider> extra, float extraChance, boolean snow) implements Feature {

	public static final MapCodec<AwTree> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("log").forGetter(AwTree::log),
		BlockStateProvider.DIRECT_CODEC.fieldOf("leaves").forGetter(AwTree::leaves),
		Codec.STRING.fieldOf("shape").forGetter(AwTree::shape),
		Codec.intRange(2, 40).fieldOf("min_height").forGetter(AwTree::minHeight),
		Codec.intRange(2, 48).fieldOf("max_height").forGetter(AwTree::maxHeight),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("hang").forGetter(AwTree::hang),
		Codec.FLOAT.optionalFieldOf("hang_chance", 0.0F).forGetter(AwTree::hangChance),
		BlockStateProvider.DIRECT_CODEC.optionalFieldOf("extra").forGetter(AwTree::extra),
		Codec.FLOAT.optionalFieldOf("extra_chance", 0.0F).forGetter(AwTree::extraChance),
		Codec.BOOL.optionalFieldOf("snow", false).forGetter(AwTree::snow)
	).apply(i, AwTree::new));

	@Override
	public MapCodec<AwTree> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z);
		BlockPos below = new BlockPos(x, y0 - 1, z);
		BlockState ground = level.getBlockState(below);
		if (!(Gen.isSoil(ground) || ground.is(BlockTags.SAND) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.PACKED_ICE) || ground.is(Blocks.TERRACOTTA))
			|| !level.getFluidState(below.above()).isEmpty()) {
			return false;
		}
		int h = this.minHeight + random.nextInt(Math.max(1, this.maxHeight - this.minHeight + 1));
		if (y0 + h + 8 > level.getMaxY()) {
			return false;
		}
		Ctx c = new Ctx(level, w, random);
		switch (this.shape) {
			case "cone" -> cone(c, x, y0, z, h);
			case "flat" -> flat(c, x, y0, z, h);
			case "droop" -> droop(c, x, y0, z, h);
			case "column" -> column(c, x, y0, z, h);
			case "giant" -> giant(c, x, y0, z, h);
			case "dead" -> dead(c, x, y0, z, h);
			case "twisted" -> twisted(c, x, y0, z, h);
			default -> round(c, x, y0, z, h);
		}
		return true;
	}

	private record Ctx(WorldGenLevel level, Gen.Window w, RandomSource random) {
	}

	// ------------------------------------------------------------------ shapes

	private void round(Ctx c, int x, int y0, int z, int h) {
		trunk(c, x, y0, z, h);
		double r = 2.2 + c.random.nextDouble() * 1.3 + h / 10.0;
		blob(c, x, y0 + h - 1, z, r, r * 0.75);
		if (h > 7 && c.random.nextBoolean()) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(c.random);
			int by = y0 + h / 2 + 1;
			branch(c, x, by, z, d, 2 + c.random.nextInt(2));
			blob(c, x + d.getStepX() * 3, by + 2, z + d.getStepZ() * 3, r * 0.6, r * 0.5);
		}
	}

	private void cone(Ctx c, int x, int y0, int z, int h) {
		trunk(c, x, y0, z, h);
		double rmax = 1.6 + h / 5.0;
		int start = Math.max(2, h / 5);
		for (int i = start; i <= h + 1; i++) {
			double t = (i - start) / (double) (h + 1 - start);
			double r = rmax * (1 - t) + 0.4;
			if ((i - start) % 2 == 1) {
				r *= 0.62;
			}
			disc(c, x, y0 + i, z, r);
		}
		leaf(c, x, y0 + h + 2, z);
	}

	private void flat(Ctx c, int x, int y0, int z, int h) {
		// A trunk that leans, forks, and spreads into wide flat crowns.
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(c.random);
		int tx = x;
		int tz = z;
		int bend = h / 2;
		for (int i = 0; i < h; i++) {
			if (i > bend && i % 2 == 0) {
				tx += lean.getStepX();
				tz += lean.getStepZ();
			}
			log(c, tx, y0 + i, tz, Direction.Axis.Y);
		}
		disc(c, tx, y0 + h, tz, 3.4 + c.random.nextDouble());
		disc(c, tx, y0 + h + 1, tz, 2.2);
		Direction other = lean.getOpposite();
		int fx = x + other.getStepX() * 2;
		int fz = z + other.getStepZ() * 2;
		int fy = y0 + bend + 1;
		for (int i = 0; i < 3; i++) {
			log(c, x + other.getStepX() * (i / 2 + 1), fy + i, z + other.getStepZ() * (i / 2 + 1), Direction.Axis.Y);
		}
		disc(c, fx, fy + 3, fz, 2.4);
	}

	private void droop(Ctx c, int x, int y0, int z, int h) {
		trunk(c, x, y0, z, h);
		double r = 3.0 + c.random.nextDouble() * 1.5;
		blob(c, x, y0 + h, z, r, r * 0.55);
		// Curtains of leaves falling from the rim.
		for (int a = 0; a < 18; a++) {
			double ang = a * Math.PI * 2 / 18 + c.random.nextDouble() * 0.2;
			int lx = x + (int) Math.round(Math.cos(ang) * (r - 0.4));
			int lz = z + (int) Math.round(Math.sin(ang) * (r - 0.4));
			int len = 1 + c.random.nextInt(Math.max(1, h / 2));
			for (int k = 0; k < len; k++) {
				leaf(c, lx, y0 + h - 1 - k, lz);
			}
		}
	}

	private void column(Ctx c, int x, int y0, int z, int h) {
		trunk(c, x, y0, z, h);
		for (int i = h / 3; i <= h + 1; i++) {
			double t = (i - h / 3.0) / (h + 1 - h / 3.0);
			double r = 1.1 + Math.sin(t * Math.PI) * 1.2;
			disc(c, x, y0 + i, z, r);
		}
		leaf(c, x, y0 + h + 2, z);
	}

	private void giant(Ctx c, int x, int y0, int z, int h) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				for (int i = -1; i < h; i++) {
					log(c, x + dx, y0 + i, z + dz, Direction.Axis.Y);
				}
			}
		}
		// Roots flaring out at the foot.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (c.random.nextFloat() < 0.75F) {
				int rx = x + (d.getStepX() > 0 ? 2 : d.getStepX() < 0 ? -1 : c.random.nextInt(2));
				int rz = z + (d.getStepZ() > 0 ? 2 : d.getStepZ() < 0 ? -1 : c.random.nextInt(2));
				log(c, rx, y0, rz, d.getAxis());
				log(c, rx, y0 - 1, rz, Direction.Axis.Y);
			}
		}
		int arms = 3 + c.random.nextInt(3);
		for (int a = 0; a < arms; a++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(c.random);
			int by = y0 + h / 2 + c.random.nextInt(Math.max(1, h / 2 - 2));
			int len = 3 + c.random.nextInt(3);
			int bx = x + (d.getStepX() > 0 ? 1 : 0);
			int bz = z + (d.getStepZ() > 0 ? 1 : 0);
			for (int s = 1; s <= len; s++) {
				log(c, bx + d.getStepX() * s, by + s / 2, bz + d.getStepZ() * s, d.getAxis());
			}
			blob(c, bx + d.getStepX() * len, by + len / 2 + 1, bz + d.getStepZ() * len, 2.6 + c.random.nextDouble(), 1.9);
		}
		blob(c, x, y0 + h, z, 4.2 + c.random.nextDouble(), 3.0);
	}

	private void dead(Ctx c, int x, int y0, int z, int h) {
		trunk(c, x, y0, z, h);
		int branches = 2 + c.random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(c.random);
			int by = y0 + h / 2 + c.random.nextInt(Math.max(1, h / 2));
			int ex = x;
			int ez = z;
			int ey = by;
			for (int s = 1; s <= 2 + c.random.nextInt(3); s++) {
				ex += d.getStepX();
				ez += d.getStepZ();
				if (c.random.nextFloat() < 0.4F) {
					ey++;
				}
				log(c, ex, ey, ez, d.getAxis());
			}
			if (this.hang.isPresent() && c.random.nextFloat() < this.hangChance * 3) {
				hangFrom(c, ex, ey, ez);
			}
		}
	}

	private void twisted(Ctx c, int x, int y0, int z, int h) {
		int tx = x;
		int tz = z;
		for (int i = 0; i < h; i++) {
			if (i > 1 && c.random.nextFloat() < 0.35F) {
				Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(c.random);
				tx += d.getStepX();
				tz += d.getStepZ();
				log(c, tx, y0 + i, tz, d.getAxis());
			}
			log(c, tx, y0 + i, tz, Direction.Axis.Y);
		}
		blob(c, tx, y0 + h, tz, 2.4 + c.random.nextDouble(), 1.6);
		for (int k = 0; k < 2; k++) {
			blob(c, tx + c.random.nextInt(5) - 2, y0 + h - 2 + c.random.nextInt(2), tz + c.random.nextInt(5) - 2, 1.6, 1.2);
		}
	}

	// ------------------------------------------------------------------ pieces

	private void trunk(Ctx c, int x, int y0, int z, int h) {
		for (int i = 0; i < h; i++) {
			log(c, x, y0 + i, z, Direction.Axis.Y);
		}
	}

	private void branch(Ctx c, int x, int y, int z, Direction d, int len) {
		for (int s = 1; s <= len; s++) {
			log(c, x + d.getStepX() * s, y + s / 2, z + d.getStepZ() * s, d.getAxis());
		}
	}

	private void log(Ctx c, int x, int y, int z, Direction.Axis axis) {
		BlockState s = this.log.hasProperty(RotatedPillarBlock.AXIS) ? this.log.setValue(RotatedPillarBlock.AXIS, axis) : this.log;
		if (!c.w.contains(x, y, z)) {
			return;
		}
		BlockPos p = new BlockPos(x, y, z);
		BlockState at = c.level.getBlockState(p);
		if (Gen.soft(at) || at.is(BlockTags.LEAVES) || at.is(BlockTags.LOGS) || at.is(BlockTags.DIRT) || at.is(Blocks.GRASS_BLOCK)) {
			c.level.setBlock(p, s, 2);
		}
	}

	private void blob(Ctx c, int cx, int cy, int cz, double r, double ry) {
		int ir = (int) Math.ceil(r);
		int iy = (int) Math.ceil(ry);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				for (int dy = -iy; dy <= iy; dy++) {
					double d = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (ry * ry);
					if (d > 1.0 || d > 0.78 && c.random.nextFloat() < 0.45F) {
						continue;
					}
					leaf(c, cx + dx, cy + dy, cz + dz);
				}
			}
		}
		decorate(c, cx, cy, cz, ir, iy);
	}

	private void disc(Ctx c, int cx, int y, int cz, double r) {
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r || d > r - 0.6 && c.random.nextFloat() < 0.4F) {
					continue;
				}
				leaf(c, cx + dx, y, cz + dz);
			}
		}
		decorate(c, cx, y, cz, ir, 0);
	}

	private void leaf(Ctx c, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		BlockState state = this.leaves.getState(c.level, c.random, p);
		if (this.extra.isPresent() && c.random.nextFloat() < this.extraChance) {
			state = this.extra.get().getState(c.level, c.random, p);
		}
		if (state.hasProperty(BlockStateProperties.PERSISTENT)) {
			state = state.setValue(BlockStateProperties.PERSISTENT, true);
		}
		Gen.setSoft(c.level, c.w, p, state);
	}

	/** Things hanging under the canopy, and snow on top of it. */
	private void decorate(Ctx c, int cx, int cy, int cz, int ir, int iy) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				if (this.hang.isPresent() && c.random.nextFloat() < this.hangChance) {
					for (int dy = -iy - 1; dy <= iy; dy++) {
						p.set(cx + dx, cy + dy, cz + dz);
						if (c.w.contains(p.getX(), p.getY(), p.getZ()) && c.level.getBlockState(p).is(BlockTags.LEAVES)
							&& c.level.getBlockState(p.below()).isAir()) {
							hangFrom(c, p.getX(), p.getY(), p.getZ());
							break;
						}
					}
				}
				if (this.snow) {
					for (int dy = iy + 1; dy >= -iy; dy--) {
						p.set(cx + dx, cy + dy, cz + dz);
						if (c.w.contains(p.getX(), p.getY() + 1, p.getZ()) && c.level.getBlockState(p).is(BlockTags.LEAVES)) {
							BlockPos up = p.above();
							if (c.level.getBlockState(up).isAir()) {
								Gen.set(c.level, c.w, up, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + c.random.nextInt(2)));
							}
							break;
						}
					}
				}
			}
		}
	}

	private void hangFrom(Ctx c, int x, int y, int z) {
		int len = 1 + c.random.nextInt(3);
		for (int k = 1; k <= len; k++) {
			BlockPos p = new BlockPos(x, y - k, z);
			if (!c.w.contains(x, y - k, z) || !c.level.getBlockState(p).isAir()) {
				return;
			}
			BlockState s = this.hang.get().getState(c.level, c.random, p);
			if (!s.canSurvive(c.level, p)) {
				return;
			}
			c.level.setBlock(p, s, 2);
		}
	}
}
