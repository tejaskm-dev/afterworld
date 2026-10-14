package dev.afterworld.economy;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

/**
 * One contract from a Warden's Board: what it asks, where, and what it pays. Offers and accepted
 * contracts share this shape; an accepted one also carries its progress.
 */
public final class Contract {
	public enum Kind {
		/** A named elite, at a place. */
		BOUNTY,
		/** A trade good, carried to a keeper. */
		DELIVERY,
		/** Three survey points, stood at in turn. */
		SURVEY,
		/** N of something, brought back to any board. */
		GATHERING,
		/** A caravan pinned down on the road, seen through. */
		ESCORT;

		public final String id = name().toLowerCase(Locale.ROOT);

		public static Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			return BOUNTY;
		}
	}

	/** Where this offer came from (board position and day), so a taken offer is not shown again. */
	public String origin = "";
	public Kind kind = Kind.BOUNTY;
	/** Bounty: the creature's entity id. Delivery and gathering: the item id. */
	public String what = "";
	/** Bounty: the elite's name. Delivery: the keeper's id. */
	public String who = "";
	public int count = 1;
	public int have;
	/** Target points as packed block positions (bounty and escort: one; survey: three). */
	public long[] points = new long[0];
	public String dim = "minecraft:overworld";
	public long marks;
	public int renown;
	/** Faction ordinal that gains standing, or -1. */
	public int faction = -1;
	// ---- progress (accepted contracts only) ----
	public boolean spawned;
	/** Bounty: the spawned elite's UUID. */
	public String elite = "";
	public int siteId = -1;
	public boolean done;
	public boolean failed;
	/** Seconds stood at the current survey point. */
	public int dwell;

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("origin", origin);
		t.putString("kind", kind.id);
		t.putString("what", what);
		t.putString("who", who);
		t.putInt("count", count);
		t.putInt("have", have);
		t.put("points", new LongArrayTag(points));
		t.putString("dim", dim);
		t.putLong("marks", marks);
		t.putInt("renown", renown);
		t.putInt("faction", faction);
		t.putBoolean("spawned", spawned);
		t.putString("elite", elite);
		t.putInt("site", siteId);
		t.putBoolean("done", done);
		t.putBoolean("failed", failed);
		return t;
	}

	public static Contract load(CompoundTag t) {
		Contract c = new Contract();
		c.origin = t.getStringOr("origin", "");
		c.kind = Kind.byId(t.getStringOr("kind", "bounty"));
		c.what = t.getStringOr("what", "");
		c.who = t.getStringOr("who", "");
		c.count = t.getIntOr("count", 1);
		c.have = t.getIntOr("have", 0);
		c.points = t.getLongArray("points").orElse(new long[0]);
		c.dim = t.getStringOr("dim", "minecraft:overworld");
		c.marks = t.getLongOr("marks", 0);
		c.renown = t.getIntOr("renown", 0);
		c.faction = t.getIntOr("faction", -1);
		c.spawned = t.getBooleanOr("spawned", false);
		c.elite = t.getStringOr("elite", "");
		c.siteId = t.getIntOr("site", -1);
		c.done = t.getBooleanOr("done", false);
		c.failed = t.getBooleanOr("failed", false);
		return c;
	}
}
