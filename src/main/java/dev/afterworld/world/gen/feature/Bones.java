package dev.afterworld.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.world.gen.Gen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The bones of something enormous, half sunk into the ground: a spine, arching ribs and a skull.
 * Bleached on the shore and in the dunes, frosted in the Whitewaste.
 */
public record Bones(BlockState bone, int minLength, int maxLength) implements Feature {
	public static final MapCodec<Bones> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("bone").forGetter(Bones::bone),
		Codec.intRange(6, 30).optionalFieldOf("min_length", 10).forGetter(Bones::minLength),
		Codec.intRange(6, 34).optionalFieldOf("max_length", 18).forGetter(Bones::maxLength)
	).apply(i, Bones::new));

	@Override
	public MapCodec<Bones> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		Gen.Window w = Gen.Window.around(level, origin);
		int x = origin.getX();
		int z = origin.getZ();
		int y0 = Gen.ground(level, x, z) - 1 - random.nextInt(2);
		Direction along = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		Direction across = along.getClockWise();
		int len = this.minLength + random.nextInt(Math.max(1, this.maxLength - this.minLength + 1));
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int sx = x - along.getStepX() * len / 2;
		int sz = z - along.getStepZ() * len / 2;
		for (int s = 0; s < len; s++) {
			int bx = sx + along.getStepX() * s;
			int bz = sz + along.getStepZ() * s;
			// The spine sags a little in the middle.
			int sy = y0 + 1 + (int) Math.round(Math.sin(s / (double) len * Math.PI) * 1.5);
			put(level, w, p.set(bx, sy, bz), along.getAxis());
			if (s > 2 && s < len - 2 && s % 2 == 0) {
				double r = 3.0 + Math.sin(s / (double) len * Math.PI) * 2.5;
				for (int a = 0; a <= 16; a++) {
					double ang = a / 16.0 * Math.PI;
					double off = Math.cos(ang) * r;
					int ry = sy + (int) Math.round(Math.sin(ang) * r * 0.85) - (int) (r * 0.3);
					Direction.Axis axis = Math.abs(Math.cos(ang)) > 0.7 ? Direction.Axis.Y : across.getAxis();
					if (random.nextFloat() < 0.88F) {
						put(level, w, p.set(bx + (int) Math.round(across.getStepX() * off), ry, bz + (int) Math.round(across.getStepZ() * off)), axis);
					}
				}
			}
		}
		// The skull, a cluster at the head end with hollow eyes.
		int hx = sx + along.getStepX() * (len + 1);
		int hz = sz + along.getStepZ() * (len + 1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					boolean eye = dy == 1 && (along.getAxis() == Direction.Axis.X ? dz != 0 && dx == along.getStepX() : dx != 0 && dz == along.getStepZ());
					if (!eye && random.nextFloat() < 0.9F) {
						put(level, w, p.set(hx + dx, y0 + dy, hz + dz), Direction.Axis.Y);
					}
				}
			}
		}
		return true;
	}

	private void put(WorldGenLevel level, Gen.Window w, BlockPos.MutableBlockPos p, Direction.Axis axis) {
		BlockState s = this.bone.hasProperty(RotatedPillarBlock.AXIS) ? this.bone.setValue(RotatedPillarBlock.AXIS, axis) : this.bone;
		Gen.set(level, w, p, s);
	}
}
