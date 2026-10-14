package dev.afterworld.client;

import dev.afterworld.net.AwPayloads;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Everything the client mirrors from the server. Cooldowns count down locally between updates. */
public final class ClientState {
	public static int discipline;
	public static int slotCount = 3;
	public static final int[] slots = {-1, -1, -1, -1};
	public static final int[] cdRemain = new int[4];
	public static final int[] cdTotal = new int[4];
	public static int sigRemain;
	public static int sigTotal;
	public static int flags;
	public static int learnedMask;
	public static boolean received;

	public static final CompoundTag[] journal = new CompoundTag[5];
	public static int journalVersion;
	/** An arrival (or wardrobe) screen the server asked for, opened once nothing else is on screen. */
	public static dev.afterworld.net.AwPayloads.OpenArrival pendingArrival;

	public record Notice(int kind, Component text, long start) {
	}

	public static final Deque<Notice> notices = new ArrayDeque<>();

	private ClientState() {
	}

	public static void apply(AwPayloads.Hud h) {
		discipline = h.discipline();
		slotCount = h.slotCount();
		System.arraycopy(h.slots(), 0, slots, 0, 4);
		System.arraycopy(h.cdRemain(), 0, cdRemain, 0, 4);
		System.arraycopy(h.cdTotal(), 0, cdTotal, 0, 4);
		sigRemain = h.sigRemain();
		sigTotal = h.sigTotal();
		flags = h.flags();
		learnedMask = h.learnedMask();
		received = true;
	}

	public static void tick() {
		for (int i = 0; i < 4; i++) {
			if (cdRemain[i] > 0) {
				cdRemain[i]--;
			}
		}
		if (sigRemain > 0) {
			sigRemain--;
		}
	}

	private static boolean climbTaught;

	/** The first time this session the traveller takes hold of a wall: how climbing works. */
	public static void firstClimb() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (climbTaught || mc.level == null) {
			return;
		}
		climbTaught = true;
		notice(dev.afterworld.net.AwPayloads.Notice.WORLD, Component.translatable("notice.afterworld.climb_help"), mc.level.getGameTime());
	}

	public static void notice(int kind, Component text, long now) {
		notices.addLast(new Notice(kind, text, now));
		while (notices.size() > 4) {
			notices.removeFirst();
		}
	}

	public static boolean flag(int f) {
		return (flags & f) != 0;
	}

	public static boolean suppressed(int slot) {
		return (flags & (1 << (AwPayloads.Hud.SUPPRESS_SHIFT + slot))) != 0;
	}

	// ---- movement and powers (from MoveSync and AspectSync)
	public static float stamina = 100;
	public static float staminaMax = 100;
	public static int gifts;
	public static boolean sliding;
	public static int aspect = -1;
	public static float resonance;
	public static int aspectsOwned;

	public static boolean hasGift(dev.afterworld.movement.Gifts.Gift g) {
		return (gifts & g.bit()) != 0;
	}

	public static void reset() {
		received = false;
		stamina = 100;
		staminaMax = 100;
		gifts = 0;
		aspect = -1;
		resonance = 0;
		aspectsOwned = 0;
		pendingArrival = null;
		for (int i = 0; i < 4; i++) {
			slots[i] = -1;
			cdRemain[i] = 0;
		}
		notices.clear();
		java.util.Arrays.fill(journal, null);
	}
}
