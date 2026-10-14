package dev.afterworld.world.wonder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The single piece of a wonder. Its box starts at the ground line (so terrain is levelled to it);
 * foundations below the line are placed too. All the blocks live in {@link WonderData}, so the
 * piece only remembers which wonder, where and which way round. A wonder with a clearing (the
 * Atlas's lairs) has its box widened by that much, and the trees standing in that ring are felled,
 * so it stands in an open space of its own rather than half swallowed by a forest.
 */
public final class WonderPiece extends StructurePiece {
	private final String wonder;
	private final BlockPos origin;
	private final Rotation turn;
	private final int clearing;

	WonderPiece(String wonder, BlockPos origin, Rotation turn, BoundingBox box, int clearing) {
		super(Wonders.PIECE, 0, box);
		this.wonder = wonder;
		this.origin = origin;
		this.turn = turn;
		this.clearing = clearing;
	}

	public WonderPiece(CompoundTag tag) {
		super(Wonders.PIECE, tag);
		this.wonder = tag.getStringOr("Wonder", "");
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 0), tag.getIntOr("OZ", 0));
		this.turn = Rotation.values()[Math.floorMod(tag.getIntOr("Turn", 0), 4)];
		this.clearing = tag.getIntOr("Clear", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Wonder", this.wonder);
		tag.putInt("OX", this.origin.getX());
		tag.putInt("OY", this.origin.getY());
		tag.putInt("OZ", this.origin.getZ());
		tag.putInt("Turn", this.turn.ordinal());
		tag.putInt("Clear", this.clearing);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		WonderData data = WonderData.get(this.wonder);
		if (data != null) {
			if (this.clearing > 0) {
				fell(level, data, chunkBox);
			}
			data.place(level, this.origin, this.turn, chunkBox, random);
		}
	}

	/** Fells the trees in the clearing ring round the wonder (inside its widened box, outside its own footprint). */
	private void fell(WorldGenLevel level, WonderData data, BoundingBox chunkBox) {
		BoundingBox box = this.getBoundingBox();
		int fx0 = this.origin.getX();
		int fz0 = this.origin.getZ();
		int fx1 = fx0 + data.width(this.turn) - 1;
		int fz1 = fz0 + data.depth(this.turn) - 1;
		int y0 = box.minY();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
			for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
				if (x >= fx0 && x <= fx1 && z >= fz0 && z <= fz1) {
					continue;
				}
				for (int y = y0 - 2; y <= y0 + 44; y++) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(Blocks.BAMBOO) || s.is(Blocks.MUSHROOM_STEM)
						|| s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA)) {
						level.setBlock(p, air, 2);
					}
				}
			}
		}
	}

	public String wonder() {
		return this.wonder;
	}

	public BlockPos origin() {
		return this.origin;
	}

	public Rotation turn() {
		return this.turn;
	}
}
