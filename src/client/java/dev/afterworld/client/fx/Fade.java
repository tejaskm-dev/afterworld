package dev.afterworld.client.fx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/** A full-screen fade to a colour and back (travel, cutscenes). */
public final class Fade {
	private static long start = -1;
	private static int in;
	private static int hold;
	private static int out;
	private static int color;

	private Fade() {
	}

	public static void start(int inTicks, int holdTicks, int outTicks, int rgb) {
		start = Util.getMillis();
		in = inTicks * 50;
		hold = holdTicks * 50;
		out = outTicks * 50;
		color = rgb;
	}

	public static boolean active() {
		return start >= 0;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (start < 0) {
			return;
		}
		long el = Util.getMillis() - start;
		float a;
		if (el < in) {
			a = el / (float) Math.max(1, in);
		} else if (el < in + hold) {
			a = 1;
		} else if (el < in + hold + out) {
			a = 1 - (el - in - hold) / (float) Math.max(1, out);
		} else {
			start = -1;
			return;
		}
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color((int) (255 * a), color));
	}
}
