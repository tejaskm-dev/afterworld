package dev.afterworld.block;

import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something worth a closer look, lying where it fell: a broken wheel, scattered papers, tracks in
 * the dust, a carved glyph, a note. Use it to read what it tells you; the traveller remembers.
 */
public class ClueBlock extends Block implements EntityBlock {
	public enum Kind implements StringRepresentable {
		WAGON("wagon"), MANIFEST("manifest"), TRACKS("tracks"), GLYPH("glyph"), NOTE("note");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}
	}

	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

	public ClueBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.NOTE));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ClueBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			Kind k = state.getValue(KIND);
			// A site may give a clue words of its own; otherwise it says what its kind always says.
			String lore = level.getBlockEntity(pos) instanceof ClueBlockEntity be ? be.lore() : "";
			String key = lore.isEmpty() ? "clue.afterworld." + k.getSerializedName() : "lore.afterworld." + lore;
			PlayerState s = PlayerState.of(sp);
			boolean fresh = s.flags.add(lore.isEmpty() ? "clue:" + k.getSerializedName() : "lore:" + lore);
			sp.sendSystemMessage(Component.translatable(key).withColor(0xE8DCC0));
			if (fresh) {
				Progress.legend(sp, "discovery", Component.translatable(key + ".short").getString());
				Fx.privateSound(sp, dev.afterworld.registry.AwSounds.UI_DISCOVERY, 1.0F, 1.2F);
				if (!lore.isEmpty()) {
					dev.afterworld.player.Renown.grant(sp, 5, Component.translatable(key + ".short").getString());
				}
				s.questDirty = true;
			}
			Fx.sound(sl, Vec3.atCenterOf(pos), SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
			Fx.burst(sl, ParticleTypes.END_ROD, Vec3.atCenterOf(pos), 6, 0.3, 0.02);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
		}
	}
}
