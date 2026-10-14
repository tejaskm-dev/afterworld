package dev.afterworld.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class Inv {
	private Inv() {
	}

	/** How many of {@code item} the player carries in their main inventory and hands. */
	public static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Takes up to {@code count} of {@code item}; returns how many were taken. */
	public static int take(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		int left = count;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int k = Math.min(left, s.getCount());
				s.shrink(k);
				left -= k;
			}
		}
		inv.setChanged();
		return count - left;
	}

	/** Puts the stack in the player's inventory, or at their feet when it is full. */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
			item.setNoPickUpDelay();
			player.level().addFreshEntity(item);
		}
	}
}
