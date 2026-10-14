package dev.afterworld.player;

import net.minecraft.network.chat.Component;

/** Supernatural relationships: each gives a service in exchange for an obligation. */
public enum BondType {
	LANDLORD("landlord", 0x9FB7C9, 3),
	MOTH("moth", 0xE7C46A, 1),
	PILGRIM("pilgrim", 0x9C9A8A, 5);

	public static final BondType[] VALUES = values();

	public final String id;
	public final int color;
	/** Obligation period in days. */
	public final int period;

	BondType(String id, int color, int period) {
		this.id = id;
		this.color = color;
		this.period = period;
	}

	public Component displayName() {
		return Component.translatable("bond.afterworld." + id);
	}

	public static BondType byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}
}
