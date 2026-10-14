package dev.afterworld.world;

import net.minecraft.network.chat.Component;

public enum Faction {
	CROWN("crown", 0xC9A227),
	GUILD("guild", 0x5B8DB8),
	COURT("court", 0x8E7CC3),
	BUREAU("bureau", 0x4FA89B);

	public static final Faction[] VALUES = values();
	public static final int COUNT = VALUES.length;

	public final String id;
	public final int color;

	Faction(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public Component displayName() {
		return Component.translatable("faction.afterworld." + id);
	}

	public static Faction byOrdinal(int i) {
		return i >= 0 && i < COUNT ? VALUES[i] : null;
	}

	/** Reputation tiers. Thresholds are inclusive lower bounds. */
	public static int tier(int rep) {
		if (rep <= -50) return -2;
		if (rep <= -15) return -1;
		if (rep < 15) return 0;
		if (rep < 40) return 1;
		if (rep < 75) return 2;
		return 3;
	}

	public static Component tierName(int rep) {
		return Component.translatable("faction.afterworld.tier." + (tier(rep) + 2));
	}
}
