package dev.afterworld.item;

import net.minecraft.world.item.Item;

/** A coin: picked up straight into the wallet (see the item entity mixin). */
public class CoinItem extends Item {
	public final int value;

	public CoinItem(Properties properties, int value) {
		super(properties);
		this.value = value;
	}
}
