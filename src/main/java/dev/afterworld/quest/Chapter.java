package dev.afterworld.quest;

import java.util.List;

/**
 * The Path: the main story, chapter by chapter. A chapter is a set of objectives. In a
 * {@code guided} chapter they come one at a time (the tutorial); otherwise they are all open at
 * once and may be done in any order, except those marked {@code last}, which open when every
 * other objective is done (usually: go back and tell someone).
 */
public enum Chapter {
	PROLOGUE("prologue", true, List.of(
		new Objective("meet_isolde", Goal.TALK, "isolde", 1, false),
		new Objective("attune_camp", Goal.ATTUNE_CAMP, "", 1, false),
		new Objective("cast", Goal.CAST, "", 1, false),
		new Objective("meet_marta", Goal.TALK, "marta", 1, false),
		new Objective("dodge", Goal.DODGE, "", 1, false),
		new Objective("parry", Goal.PARRY, "", 1, false),
		new Objective("journal", Goal.JOURNAL, "", 1, false),
		new Objective("meet_bram", Goal.TALK, "bram", 1, false),
		new Objective("trade", Goal.TRADE, "", 2, false),
		new Objective("report_prologue", Goal.TALK, "isolde", 1, false))),
	LISTENING("listening", false, List.of(
		new Objective("contract", Goal.CONTRACT, "", 1, false),
		new Objective("anomaly", Goal.ANOMALY, "", 1, false),
		new Objective("shrine", Goal.SHRINE, "", 1, false),
		new Objective("waystone", Goal.ATTUNE_LANDMARK, "", 1, false),
		new Objective("report_listening", Goal.TALK, "isolde", 1, true))),
	THREE("three", false, List.of(
		new Objective("bell", Goal.BOSS_BELL, "", 1, false),
		new Objective("king", Goal.BOSS_KING, "", 1, false),
		new Objective("pilgrim", Goal.BOSS_PILGRIM, "", 1, false),
		new Objective("report_three", Goal.TALK, "isolde", 1, true))),
	WARDENS("wardens", false, List.of(
		new Objective("shogun", Goal.BOSS_SHOGUN, "", 1, false),
		new Objective("lich", Goal.BOSS_LICH, "", 1, false),
		new Objective("colossus", Goal.BOSS_COLOSSUS, "", 1, false),
		new Objective("sentinel", Goal.BOSS_SENTINEL, "", 1, false),
		new Objective("warden", Goal.BOSS_WARDEN, "", 1, false),
		new Objective("report_wardens", Goal.TALK, "isolde", 1, true)));

	public static final Chapter[] VALUES = values();

	public final String id;
	public final boolean guided;
	public final List<Objective> objectives;

	Chapter(String id, boolean guided, List<Objective> objectives) {
		this.id = id;
		this.guided = guided;
		this.objectives = objectives;
	}

	public static Chapter byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}

	public int indexOf(String objective) {
		for (int i = 0; i < objectives.size(); i++) {
			if (objectives.get(i).id().equals(objective)) {
				return i;
			}
		}
		return -1;
	}

	public enum Goal {
		TALK, ATTUNE_CAMP, ATTUNE_LANDMARK, CAST, JOURNAL, KILL, ANOMALY, SHRINE, BOSS_BELL, BOSS_KING, BOSS_PILGRIM,
		DODGE, PARRY, TRADE, CONTRACT, BOSS_SHOGUN, BOSS_LICH, BOSS_COLOSSUS, BOSS_SENTINEL, BOSS_WARDEN
	}

	public record Objective(String id, Goal goal, String arg, int count, boolean last) {
	}
}
