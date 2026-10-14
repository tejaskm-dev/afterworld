package dev.afterworld.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** World systems plug their behaviour into Afterworld blocks through these hooks. */
public final class AwBlockHooks {
	@FunctionalInterface
	public interface Use {
		boolean use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack stack);
	}

	@FunctionalInterface
	public interface Broken {
		void broken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
	}

	public static Use shrineUse = (l, p, s, pl, st) -> false;
	public static Use anomalyUse = (l, p, s, pl, st) -> false;
	public static Broken anomalyBroken = (l, p, s, pl) -> {
	};
	public static Broken escapementBroken = (l, p, s, pl) -> {
	};
	public static Use counterweightUse = (l, p, s, pl, st) -> false;

	private AwBlockHooks() {
	}
}
