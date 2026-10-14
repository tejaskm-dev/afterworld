package dev.afterworld.client.hud;

import dev.afterworld.client.screen.DialogueScreen;
import dev.afterworld.net.AwPayloads;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * One voice at a time. A conversation is the traveller's own and always wins: while one is open,
 * the narrator and the great powers wait, and a line they were in the middle of stops and is heard
 * again afterwards. Their lines otherwise queue one after another, a breath between them, instead
 * of speaking over each other; a great power's line that has waited too long (its moment gone) is
 * dropped. Title cards that arrive mid-conversation wait for it too. {@link VoiceSubtitles} speaks
 * and draws whichever line this lets through.
 */
public final class Voices {
	private record Pending(AwPayloads.VoiceLine line, long arrived) {
	}

	private static final Deque<Pending> QUEUE = new ArrayDeque<>();
	/** A breath between one line and the next. */
	private static final long GAP = 450;
	/** A great power's line older than this is no longer worth saying. */
	private static final long STALE = 20_000;
	private static Pending speaking;
	private static long speakingUntil;
	private static long quietUntil;
	/** The most voices heard at once, for tests. */
	public static int mostAtOnce;

	private Voices() {
	}

	public static void receive(AwPayloads.VoiceLine v) {
		QUEUE.add(new Pending(v, Util.getMillis()));
		pump();
	}

	/** Every client tick. */
	public static void tick() {
		pump();
		if (!conversation()) {
			TitleCards.release();
		}
	}

	/** Whether a conversation is on screen (it has the floor). */
	public static boolean conversation() {
		return Minecraft.getInstance().gui.screen() instanceof DialogueScreen;
	}

	/** Whether the narrator or a great power is speaking right now. */
	public static boolean speaking() {
		return speaking != null;
	}

	/** A conversation begins to speak: whatever else is speaking stops, to be heard again after it. */
	public static void hush() {
		if (speaking != null) {
			VoiceSubtitles.stop();
			QUEUE.addFirst(speaking);
			speaking = null;
		}
	}

	/** A conversation's voice has started: counts toward {@link #mostAtOnce}. */
	public static void conversationSpeaks() {
		mostAtOnce = Math.max(mostAtOnce, speaking != null ? 2 : 1);
	}

	private static void pump() {
		long now = Util.getMillis();
		if (speaking != null && now >= speakingUntil) {
			speaking = null;
			quietUntil = now + GAP;
		}
		QUEUE.removeIf(p -> p.line().style() == AwPayloads.VoiceLine.BOSS && now - p.arrived() > STALE);
		if (speaking != null || now < quietUntil || conversation()) {
			return;
		}
		Pending next = QUEUE.poll();
		if (next == null) {
			return;
		}
		speaking = next;
		speakingUntil = now + VoiceSubtitles.start(next.line());
		mostAtOnce = Math.max(mostAtOnce, 1);
	}

	public static void reset() {
		QUEUE.clear();
		speaking = null;
		quietUntil = 0;
		mostAtOnce = 0;
	}
}
