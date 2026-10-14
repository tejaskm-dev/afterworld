package dev.afterworld.quest;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The keepers' questlines. Each is a short chain of stages; conversations start, advance and
 * finish them (see the dialogue scripts), minigames and finds advance them too. The two most
 * recently touched open questlines show in the tracker under the main story.
 */
public final class SideQuests {
	public static final int FINISHED = 99;

	public enum Side {
		STARS("stars", "selene", 3),
		WAYFARER("wayfarer", "odo", 3),
		BREW("brew", "mab", 4),
		HUNT("hunt", "torvald", 4),
		BELL("bell", "ines", 3),
		CARAVAN("caravan", "rashid", 3),
		EDGES("edges", "wren", 2),
		MEAL("meal", "bram", 4),
		FORGE("forge", "marta", 2),
		DUEL("duel", "akane", 2);

		public static final Side[] VALUES = values();
		public final String id;
		public final String keeper;
		/** Stages before the questline is done. */
		public final int stages;

		Side(String id, String keeper, int stages) {
			this.id = id;
			this.keeper = keeper;
			this.stages = stages;
		}

		public static Side byId(String id) {
			for (Side s : VALUES) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return null;
		}
	}

	private SideQuests() {
	}

	/** The stage a traveller is at (-1: not started, {@link #FINISHED}: done). */
	public static int stage(PlayerState s, Side q) {
		return s.side.getOrDefault(q.id, -1);
	}

	public static void start(ServerPlayer p, Side q) {
		PlayerState s = PlayerState.of(p);
		if (stage(s, q) >= 0) {
			return;
		}
		s.side.put(q.id, 0);
		touch(s, q);
		Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.side_started", Component.translatable("side.afterworld." + q.id)));
		Fx.privateSound(p, AwSounds.UI_RUMOUR, 0.8F, 1.0F);
	}

	public static void advance(ServerPlayer p, Side q) {
		PlayerState s = PlayerState.of(p);
		int st = stage(s, q);
		if (st < 0 || st == FINISHED) {
			return;
		}
		s.side.put(q.id, st + 1);
		touch(s, q);
		Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.side_advanced", Component.translatable("side.afterworld." + q.id)));
	}

	/** Advances only if the questline is exactly at {@code from} (minigames call this on success). */
	public static void advanceFrom(ServerPlayer p, Side q, int from) {
		if (stage(PlayerState.of(p), q) == from) {
			advance(p, q);
		}
	}

	public static void finish(ServerPlayer p, Side q) {
		PlayerState s = PlayerState.of(p);
		if (stage(s, q) == FINISHED) {
			return;
		}
		s.side.put(q.id, FINISHED);
		s.recentSide.remove(q.id);
		s.questDirty = true;
		Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.side_done", Component.translatable("side.afterworld." + q.id)));
		Progress.legend(p, "quest", "Finished “" + Component.translatable("side.afterworld." + q.id).getString() + "”.");
		Renown.grant(p, 250, Component.translatable("side.afterworld." + q.id).getString());
		Fx.privateSound(p, AwSounds.UI_CHRONICLE, 1.0F, 1.0F);
	}

	private static void touch(PlayerState s, Side q) {
		s.recentSide.remove(q.id);
		s.recentSide.add(0, q.id);
		s.questDirty = true;
	}

	/** Tracker lines for the open questlines most recently touched. */
	public static List<AwPayloads.QuestSync.Entry> entries(PlayerState s) {
		List<AwPayloads.QuestSync.Entry> out = new ArrayList<>();
		for (String id : s.recentSide) {
			Side q = Side.byId(id);
			if (q == null) {
				continue;
			}
			int st = stage(s, q);
			if (st < 0 || st == FINISHED) {
				continue;
			}
			out.add(new AwPayloads.QuestSync.Entry(AwPayloads.QuestSync.SIDE,
				Component.translatable("side.afterworld." + q.id).append(": ").append(Component.translatable("side.afterworld." + q.id + "." + st)),
				0, 0, false, false, 0, 0, 0, "minecraft:overworld"));
			if (out.size() >= 2) {
				break;
			}
		}
		return out;
	}
}
