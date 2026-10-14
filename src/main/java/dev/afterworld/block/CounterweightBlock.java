package dev.afterworld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The cathedral's old counterweight release. Pull it once, and the great chandelier falls. */
public class CounterweightBlock extends Block {
	public CounterweightBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			return AwBlockHooks.counterweightUse.use(sl, pos, state, sp, ItemStack.EMPTY) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}
}
