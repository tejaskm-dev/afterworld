package dev.afterworld.player;

import net.minecraft.nbt.CompoundTag;

/** One line of a player's Personal Legend. */
public record LegendEntry(long day, String kind, String text) {
	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putLong("day", day);
		t.putString("kind", kind);
		t.putString("text", text);
		return t;
	}

	public static LegendEntry load(CompoundTag t) {
		return new LegendEntry(t.getLongOr("day", 0), t.getStringOr("kind", "milestone"), t.getStringOr("text", ""));
	}
}
