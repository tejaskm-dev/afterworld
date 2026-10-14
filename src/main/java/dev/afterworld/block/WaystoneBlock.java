package dev.afterworld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A waystone: a carved pillar with a crystal turning above it. Natural ones (the camp's, the
 * landmarks') cannot be broken outside creative; ones a traveller sets down can.
 */
public class WaystoneBlock extends Block {
	public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(3, 4, 3, 13, 22, 13));

	public WaystoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NATURAL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NATURAL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
			dev.afterworld.world.Waystones.use(sp, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (state.getValue(NATURAL) && !player.isCreative()) {
			return 0.0F;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (level instanceof ServerLevel sl) {
			String name = stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString()
				: by instanceof Player p ? p.getName().getString() + "'s Waystone" : null;
			dev.afterworld.world.Waystones.register(sl, pos, name, false);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		dev.afterworld.world.Waystones.forget(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * 0.35, pos.getY() + 1.55 + random.nextDouble() * 0.3,
				pos.getZ() + 0.5 + Math.sin(a) * 0.35, 0, 0.012, 0);
		}
	}
}
