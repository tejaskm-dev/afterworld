package dev.afterworld.item;

import dev.afterworld.AwHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The Expedition Journal: Edicts, Codex, Rumours, Chronicle and Bonds in one book. */
public class JournalItem extends LoreItem {
	public JournalItem(Properties properties) {
		super(properties, 2, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			AwHooks.openJournal.run();
		}
		return InteractionResult.SUCCESS;
	}
}
