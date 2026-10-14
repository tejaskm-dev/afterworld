package dev.afterworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A village as the world remembers it: four numbers and a little history. Distant settlements
 * are never ticked; they change once per day from these values alone.
 */
public final class Settlement {
	public static final int NORMAL = 0;
	public static final int STRUGGLING = 1;
	public static final int ABANDONED = 2;
	public static final int REMEMBERED = 3;
	public static final int ANEW = 4;

	public int id;
	public String name = "";
	public long key;
	public String dim = "minecraft:overworld";
	public int x;
	public int y;
	public int z;
	public int safety = 55;
	public int prosperity = 50;
	public int legitimacy = 60;
	public int anomaly = 10;
	public int patron = Faction.CROWN.ordinal();
	public String leader = "";
	public int state = NORMAL;
	public long foundedDay;
	public long lastVisitDay;
	public long lastGolemDay = -10;
	public long lastRaidDay = -10;
	public int lowDays;
	// Counters gathered since the last daily update.
	public int kills;
	public int trades;
	public int deaths;
	public int helped;

	public void clamp() {
		safety = Mth.clamp(safety, 0, 100);
		prosperity = Mth.clamp(prosperity, 0, 100);
		legitimacy = Mth.clamp(legitimacy, 0, 100);
		anomaly = Mth.clamp(anomaly, 0, 100);
	}

	public String safetyWord() {
		return safety >= 75 ? "Fortified" : safety >= 55 ? "Guarded" : safety >= 35 ? "Uneasy" : safety >= 15 ? "Threatened" : "Besieged";
	}

	public String prosperityWord() {
		return prosperity >= 75 ? "Thriving" : prosperity >= 55 ? "Steady" : prosperity >= 35 ? "Lean" : prosperity >= 15 ? "Failing" : "Destitute";
	}

	public String legitimacyWord() {
		return legitimacy >= 70 ? "Trusted" : legitimacy >= 45 ? "Tolerated" : legitimacy >= 25 ? "Doubted" : "Defied";
	}

	public String anomalyWord() {
		return anomaly >= 70 ? "Unravelling" : anomaly >= 45 ? "Strange" : anomaly >= 20 ? "Restless" : "Quiet";
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("name", name);
		t.putLong("key", key);
		t.putString("dim", dim);
		t.putInt("x", x);
		t.putInt("y", y);
		t.putInt("z", z);
		t.putInt("safety", safety);
		t.putInt("prosperity", prosperity);
		t.putInt("legitimacy", legitimacy);
		t.putInt("anomaly", anomaly);
		t.putInt("patron", patron);
		t.putString("leader", leader);
		t.putInt("state", state);
		t.putLong("founded", foundedDay);
		t.putLong("visit", lastVisitDay);
		t.putLong("golem", lastGolemDay);
		t.putLong("raid", lastRaidDay);
		t.putInt("lowDays", lowDays);
		t.putInt("kills", kills);
		t.putInt("trades", trades);
		t.putInt("deaths", deaths);
		t.putInt("helped", helped);
		return t;
	}

	public static Settlement load(CompoundTag t) {
		Settlement s = new Settlement();
		s.id = t.getIntOr("id", 0);
		s.name = t.getStringOr("name", "Nameless");
		s.key = t.getLongOr("key", 0);
		s.dim = t.getStringOr("dim", "minecraft:overworld");
		s.x = t.getIntOr("x", 0);
		s.y = t.getIntOr("y", 64);
		s.z = t.getIntOr("z", 0);
		s.safety = t.getIntOr("safety", 55);
		s.prosperity = t.getIntOr("prosperity", 50);
		s.legitimacy = t.getIntOr("legitimacy", 60);
		s.anomaly = t.getIntOr("anomaly", 10);
		s.patron = t.getIntOr("patron", 0);
		s.leader = t.getStringOr("leader", "");
		s.state = t.getIntOr("state", NORMAL);
		s.foundedDay = t.getLongOr("founded", 0);
		s.lastVisitDay = t.getLongOr("visit", 0);
		s.lastGolemDay = t.getLongOr("golem", -10);
		s.lastRaidDay = t.getLongOr("raid", -10);
		s.lowDays = t.getIntOr("lowDays", 0);
		s.kills = t.getIntOr("kills", 0);
		s.trades = t.getIntOr("trades", 0);
		s.deaths = t.getIntOr("deaths", 0);
		s.helped = t.getIntOr("helped", 0);
		return s;
	}
}
