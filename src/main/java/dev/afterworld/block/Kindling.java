package dev.afterworld.block;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A torch in hand lights a cold campfire or candle, the way anyone would expect it to. */
public final class Kindling {
	private Kindling() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var held = player.getItemInHand(hand);
			if (!held.is(Items.TORCH) && !held.is(Items.SOUL_TORCH) && !held.is(Items.COPPER_TORCH)) {
				return InteractionResult.PASS;
			}
			var pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)
				|| state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
				return InteractionResult.PASS;
			}
			String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
			if (!id.contains("campfire") && !id.contains("candle")) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.1F);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
