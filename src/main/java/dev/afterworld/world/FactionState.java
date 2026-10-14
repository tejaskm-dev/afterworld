package dev.afterworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** A faction's standing in the world: how much it can do, who leads it, what it is doing now. */
public final class FactionState {
	public final Faction faction;
	public int influence = 40;
	public int resources = 50;
	public String leader = "";
	public final int[] relations = new int[Faction.COUNT];
	public long lastOpDay = -5;
	public String operation = "";
	public int opTarget = -1;

	public FactionState(Faction faction) {
		this.faction = faction;
	}

	public void clamp() {
		influence = Mth.clamp(influence, 0, 100);
		resources = Mth.clamp(resources, 0, 100);
		for (int i = 0; i < relations.length; i++) {
			relations[i] = Mth.clamp(relations[i], -100, 100);
		}
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("influence", influence);
		t.putInt("resources", resources);
		t.putString("leader", leader);
		t.putIntArray("relations", relations);
		t.putLong("lastOp", lastOpDay);
		t.putString("op", operation);
		t.putInt("opTarget", opTarget);
		return t;
	}

	public void load(CompoundTag t) {
		influence = t.getIntOr("influence", 40);
		resources = t.getIntOr("resources", 50);
		leader = t.getStringOr("leader", "");
		int[] r = t.getIntArray("relations").orElse(new int[0]);
		System.arraycopy(r, 0, relations, 0, Math.min(r.length, relations.length));
		lastOpDay = t.getLongOr("lastOp", -5);
		operation = t.getStringOr("op", "");
		opTarget = t.getIntOr("opTarget", -1);
	}
}
