package dev.afterworld.player;

import dev.afterworld.registry.AwTags;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Key things stay with the traveller through death: the Journal, the relics and Edict fragments
 * (anything in the {@code afterworld:soulbound} item tag). They are left out of the death spill
 * and handed back on respawn, in the same slots.
 */
public final class Soulbound {
	private Soulbound() {
	}

	public static void init() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (alive || oldPlayer.isSpectator() || newPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				return;   // the whole inventory came across already
			}
			Inventory from = oldPlayer.getInventory();
			Inventory to = newPlayer.getInventory();
			for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
				ItemStack stack = from.getItem(i);
				if (!stack.isEmpty() && is(stack)) {
					if (to.getItem(i).isEmpty()) {
						to.setItem(i, stack.copy());
					} else {
						to.add(stack.copy());
					}
				}
			}
		});
	}

	private static final String MARK = "afterworld_soulbound";

	public static boolean is(ItemStack stack) {
		if (stack.is(AwTags.SOULBOUND)) {
			return true;
		}
		var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARK, false);
	}

	/** Binds one particular stack to its owner (the starting kit), whatever kind of item it is. */
	public static ItemStack mark(ItemStack stack) {
		net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(MARK, true));
		return stack;
	}

	/**
	 * Just before a death spill: soulbound things worn or held in the off hand move into the pack,
	 * which keeps them. With no room, they trade places with an ordinary item, which spills instead.
	 */
	public static void stow(Inventory inv) {
		for (int slot : Inventory.EQUIPMENT_SLOT_MAPPING.keySet()) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty() || !is(stack)) {
				continue;
			}
			int into = inv.getFreeSlot();
			if (into < 0) {
				for (int i = 0; i < Inventory.INVENTORY_SIZE && into < 0; i++) {
					if (!is(inv.getItem(i))) {
						into = i;
					}
				}
			}
			if (into >= 0) {
				ItemStack displaced = inv.getItem(into);
				inv.setItem(into, stack);
				inv.setItem(slot, displaced);
			}
		}
	}
}
