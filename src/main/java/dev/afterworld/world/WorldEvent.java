package dev.afterworld.world;

import net.minecraft.nbt.CompoundTag;

/** A regional event in progress: the Silent Caravan, the Road's Last Stop, a Crown of Crows. */
public final class WorldEvent {
	public static final String SILENT_CARAVAN = "silent_caravan";
	public static final String ROADS_LAST_STOP = "roads_last_stop";
	public static final String CROWN_OF_CROWS = "crown_of_crows";
	public static final String ANOMALY_BLOOM = "anomaly_bloom";

	public int id;
	public String kind = "";
	public int settlement = -1;
	public int site = -1;
	public long startDay;
	public long endDay;
	public int stage;
	public boolean active = true;

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("kind", kind);
		t.putInt("settlement", settlement);
		t.putInt("site", site);
		t.putLong("start", startDay);
		t.putLong("end", endDay);
		t.putInt("stage", stage);
		t.putBoolean("active", active);
		return t;
	}

	public static WorldEvent load(CompoundTag t) {
		WorldEvent e = new WorldEvent();
		e.id = t.getIntOr("id", 0);
		e.kind = t.getStringOr("kind", "");
		e.settlement = t.getIntOr("settlement", -1);
		e.site = t.getIntOr("site", -1);
		e.startDay = t.getLongOr("start", 0);
		e.endDay = t.getLongOr("end", 0);
		e.stage = t.getIntOr("stage", 0);
		e.active = t.getBooleanOr("active", true);
		return e;
	}
}
