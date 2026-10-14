package dev.afterworld.world;

import net.minecraft.nbt.CompoundTag;

/** One page of the shared Chronicle. Missing pages hide their text until someone uncovers the cause. */
public final class ChronicleEntry {
	public static final int CONFIRMED = 0;
	public static final int RUMOURED = 1;
	public static final int DISPUTED = 2;
	public static final int FALSIFIED = 3;
	public static final int ERASED = 4;

	public int id;
	public long day;
	public String title = "";
	public String text = "";
	/** For missing pages: the truth, revealed when {@link #missing} is cleared. */
	public String hidden = "";
	public boolean missing;
	public int certainty = CONFIRMED;
	public String place = "";
	public int faction = -1;
	public String actor = "";
	/** Key used to find and reveal a missing page from gameplay ("op:12", "site:4"...). */
	public String cause = "";

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putLong("day", day);
		t.putString("title", title);
		t.putString("text", text);
		t.putString("hidden", hidden);
		t.putBoolean("missing", missing);
		t.putInt("certainty", certainty);
		t.putString("place", place);
		t.putInt("faction", faction);
		t.putString("actor", actor);
		t.putString("cause", cause);
		return t;
	}

	public static ChronicleEntry load(CompoundTag t) {
		ChronicleEntry e = new ChronicleEntry();
		e.id = t.getIntOr("id", 0);
		e.day = t.getLongOr("day", 0);
		e.title = t.getStringOr("title", "");
		e.text = t.getStringOr("text", "");
		e.hidden = t.getStringOr("hidden", "");
		e.missing = t.getBooleanOr("missing", false);
		e.certainty = t.getIntOr("certainty", CONFIRMED);
		e.place = t.getStringOr("place", "");
		e.faction = t.getIntOr("faction", -1);
		e.actor = t.getStringOr("actor", "");
		e.cause = t.getStringOr("cause", "");
		return e;
	}
}
