package dev.afterworld.item;

import dev.afterworld.player.PlayerState;
import dev.afterworld.rumour.Rumours;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A scrap of someone else's map. Reading it gives you their lead, for better or worse. */
public class TornMapItem extends LoreItem {
	public TornMapItem(Properties properties) {
		super(properties, 1, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			Rumours.ask(sp, java.util.UUID.randomUUID(), "a torn map", null);
			if (!sp.isCreative()) {
				player.getItemInHand(hand).shrink(1);
			}
			PlayerState.of(sp).hudDirty = true;
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}
}
