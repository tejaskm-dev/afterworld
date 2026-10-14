package dev.afterworld.player;

import net.minecraft.nbt.CompoundTag;

/** A formed Bond, its obligation clock and whether its complication is active. */
public final class BondState {
	public final BondType type;
	public long formedDay;
	public long lastPaidDay;
	public boolean complication;
	/** Bond-specific counter (deaths protected this period, shrine visits...). */
	public int counter;

	public BondState(BondType type, long day) {
		this.type = type;
		this.formedDay = day;
		this.lastPaidDay = day;
	}

	public long dueDay() {
		return lastPaidDay + type.period;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("type", type.id);
		t.putLong("formed", formedDay);
		t.putLong("paid", lastPaidDay);
		t.putBoolean("complication", complication);
		t.putInt("counter", counter);
		return t;
	}

	public static BondState load(CompoundTag t) {
		String id = t.getStringOr("type", "");
		for (BondType type : BondType.VALUES) {
			if (type.id.equals(id)) {
				BondState b = new BondState(type, t.getLongOr("formed", 0));
				b.lastPaidDay = t.getLongOr("paid", b.formedDay);
				b.complication = t.getBooleanOr("complication", false);
				b.counter = t.getIntOr("counter", 0);
				return b;
			}
		}
		return null;
	}
}
