package dev.afterworld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The heart of a pilgrim shrine. Cracked until someone brings an offering. */
public class ShrineStoneBlock extends Block {
	public static final BooleanProperty REPAIRED = BooleanProperty.create("repaired");

	public ShrineStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(REPAIRED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(REPAIRED);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			return AwBlockHooks.shrineUse.use(sl, pos, state, sp, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		return InteractionResult.SUCCESS;
	}
}
