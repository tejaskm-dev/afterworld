package dev.afterworld.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Warden's Board. On its own, two posts, a little roof and the day's notices pinned between them;
 * as a panel ({@link #PANEL}), a slab of notices against a wall, and panels side by side make one
 * great notice board (the town's has nine). Use it to read the contracts on offer (see
 * economy.Board): every panel of a board shows the same ones, those of its lowest, first panel.
 */
public class BoardBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty PANEL = BooleanProperty.create("panel");
	private static final VoxelShape NS = Shapes.or(Block.box(0, 0, 6, 16, 16, 10));
	private static final VoxelShape EW = Shapes.or(Block.box(6, 0, 0, 10, 16, 16));
	// A panel's back is against the wall behind it, its face toward where it looks.
	private static final VoxelShape PANEL_N = Block.box(0, 0, 12, 16, 16, 16);
	private static final VoxelShape PANEL_S = Block.box(0, 0, 0, 16, 16, 4);
	private static final VoxelShape PANEL_E = Block.box(0, 0, 0, 4, 16, 16);
	private static final VoxelShape PANEL_W = Block.box(12, 0, 0, 16, 16, 16);

	public BoardBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PANEL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PANEL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(PANEL)) {
			return switch (state.getValue(FACING)) {
				case SOUTH -> PANEL_S;
				case EAST -> PANEL_E;
				case WEST -> PANEL_W;
				default -> PANEL_N;
			};
		}
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			dev.afterworld.economy.Board.open(sp, anchor(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	/** The board a panel belongs to, named by its lowest (then westmost, then northmost) panel. */
	public static BlockPos anchor(BlockGetter level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		if (!(here.getBlock() instanceof BoardBlock) || !here.getValue(PANEL)) {
			return pos;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		todo.add(pos);
		seen.add(pos);
		BlockPos best = pos;
		while (!todo.isEmpty() && seen.size() < 64) {
			BlockPos p = todo.poll();
			if (p.getY() < best.getY() || p.getY() == best.getY() && (p.getX() < best.getX() || p.getX() == best.getX() && p.getZ() < best.getZ())) {
				best = p;
			}
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				BlockState s = level.getBlockState(n);
				if (!seen.contains(n) && s.getBlock() instanceof BoardBlock && s.getValue(PANEL)) {
					seen.add(n);
					todo.add(n);
				}
			}
		}
		return best;
	}
}
