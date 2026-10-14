package dev.afterworld.block;

import dev.afterworld.registry.AwBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A seal stone: the lock on a site's vault. Touch it to read what it asks (light the kilns, strike
 * the stones in the right order...). When its puzzle is done it turns, glows, and what it held
 * shut opens. The puzzle itself lives in its {@link SealBlockEntity}.
 */
public class SealBlock extends Block implements EntityBlock {
	public static final BooleanProperty SOLVED = BooleanProperty.create("solved");

	public SealBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SOLVED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SOLVED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SealBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != AwBlocks.SEAL_ENTITY || state.getValue(SOLVED)) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<SealBlockEntity>) SealBlockEntity::tick;
	}

	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (!state.getValue(SOLVED) && level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SealBlockEntity be
			&& be.offer(sl, pos, state, sp, stack)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SealBlockEntity be) {
			if (!state.getValue(SOLVED) && be.challenge(sl, pos, sp)) {
				return InteractionResult.SUCCESS;
			}
			if (state.getValue(SOLVED)) {
				sp.sendSystemMessage(Component.translatable("seal.afterworld.solved").withColor(0xE8C060));
			} else {
				sp.sendSystemMessage(be.hintText().withColor(0xE8DCC0));
			}
			dev.afterworld.util.Fx.sound(sl, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.7F);
			dev.afterworld.util.Fx.burst(sl, ParticleTypes.ENCHANT, Vec3.atCenterOf(pos).add(0, 0.6, 0), 14, 0.4, 0.3);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
			if (state.getValue(SOLVED)) {
				level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.05, z, 0, 0.03, 0);
			} else {
				level.addParticle(ParticleTypes.ENCHANT, x, pos.getY() + 1.6, z, 0, -0.6, 0);
			}
		}
	}
}
