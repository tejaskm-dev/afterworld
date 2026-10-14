package dev.afterworld.rumour;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** A lead the player has heard. Rumours point at real world state, never at nothing. */
public final class RumourEntry {
	public static final int ACTIVE = 0;
	public static final int RESOLVED = 1;
	public static final int FAILED = 2;
	public static final int ABANDONED = 3;

	/** Reliability is hidden from the player until the rumour is settled. */
	public static final int ACCURATE = 0;
	public static final int INCOMPLETE = 1;
	public static final int OUTDATED = 2;
	public static final int FALSE = 3;

	public int id;
	public RumourKind kind = RumourKind.ANOMALY;
	public String title = "";
	public String text = "";
	public String source = "";
	public int reliability = ACCURATE;
	/** Base confidence the source implies, 0-100. Clues raise it. */
	public int sourceConfidence = 50;
	public int siteId = -1;
	public int faction = -1;
	public int x;
	public int y;
	public int z;
	public String dim = "minecraft:overworld";
	public final List<String> clues = new ArrayList<>();
	public int state = ACTIVE;
	public long day;
	/** Resolution summary written when settled. */
	public String outcome = "";

	public boolean active() {
		return state == ACTIVE;
	}

	public int confidence() {
		return Math.min(100, sourceConfidence + clues.size() * 15);
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("kind", kind.id);
		t.putString("title", title);
		t.putString("text", text);
		t.putString("source", source);
		t.putInt("rel", reliability);
		t.putInt("conf", sourceConfidence);
		t.putInt("site", siteId);
		t.putInt("faction", faction);
		t.putInt("x", x);
		t.putInt("y", y);
		t.putInt("z", z);
		t.putString("dim", dim);
		ListTag c = new ListTag();
		for (String s : clues) {
			c.add(StringTag.valueOf(s));
		}
		t.put("clues", c);
		t.putInt("state", state);
		t.putLong("day", day);
		t.putString("outcome", outcome);
		return t;
	}

	public static RumourEntry load(CompoundTag t) {
		RumourEntry r = new RumourEntry();
		r.id = t.getIntOr("id", 0);
		String kind = t.getStringOr("kind", "anomaly");
		for (RumourKind k : RumourKind.VALUES) {
			if (k.id.equals(kind)) {
				r.kind = k;
			}
		}
		r.title = t.getStringOr("title", "");
		r.text = t.getStringOr("text", "");
		r.source = t.getStringOr("source", "");
		r.reliability = t.getIntOr("rel", ACCURATE);
		r.sourceConfidence = t.getIntOr("conf", 50);
		r.siteId = t.getIntOr("site", -1);
		r.faction = t.getIntOr("faction", -1);
		r.x = t.getIntOr("x", 0);
		r.y = t.getIntOr("y", 64);
		r.z = t.getIntOr("z", 0);
		r.dim = t.getStringOr("dim", "minecraft:overworld");
		ListTag c = t.getListOrEmpty("clues");
		for (int i = 0; i < c.size(); i++) {
			r.clues.add(c.getStringOr(i, ""));
		}
		r.state = t.getIntOr("state", ACTIVE);
		r.day = t.getLongOr("day", 0);
		r.outcome = t.getStringOr("outcome", "");
		return r;
	}
}
