package dev.afterworld.block;

import dev.afterworld.dungeon.Ladder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The stones of the Warden's Ladder. The gate stands in the world and lets a traveller in; on each
 * floor the up-rung lights when the floor is won (step on it, or touch it, to climb on) and the
 * out-rung, on landings and after an echo, leads home.
 */
public class LadderStoneBlock extends Block {
	public enum Kind implements StringRepresentable {
		GATE("gate"),
		UP("up"),
		OUT("out");

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public LadderStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.GATE).setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, ACTIVE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			Ladder.use(sl, sp, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (state.getValue(KIND) != Kind.GATE && state.getValue(ACTIVE) && level instanceof ServerLevel sl && entity instanceof ServerPlayer sp
			&& !sp.isShiftKeyDown()) {
			Ladder.use(sl, sp, pos, state);
		}
		super.stepOn(level, pos, state, entity);
	}
}
