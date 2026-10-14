package dev.afterworld.client.fx;

import dev.afterworld.Afterworld;
import dev.afterworld.AwConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * A flash of colour at the edges of the screen, clear in the middle: an Aspect let loose, an
 * Edict taking hold, a perfect parry. Quick to rise and slower to fade.
 */
public final class ScreenFlash {
	private static final Identifier VIGNETTE = Afterworld.id("fx/vignette");
	private static long start = -1;
	private static float strength;
	private static int color;
	private static int length;

	private ScreenFlash() {
	}

	public static void start(float s, int rgb, int ticks) {
		if (start >= 0 && s < strength * level()) {
			return;
		}
		start = Util.getMillis();
		strength = Mth.clamp(s, 0, 1);
		color = rgb;
		length = Math.max(100, ticks * 50);
	}

	/** How strong the current flash still is, 0..1. */
	private static float level() {
		if (start < 0) {
			return 0;
		}
		float t = (Util.getMillis() - start) / (float) length;
		return t >= 1 ? 0 : t < 0.12F ? t / 0.12F : (float) Math.pow(1 - (t - 0.12F) / 0.88F, 1.6);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (start < 0) {
			return;
		}
		float k = level();
		if (Util.getMillis() - start >= length) {
			start = -1;
			return;
		}
		float a = k * strength * (float) Mth.clamp(AwConfig.get().abilityEffects, 0, 1);
		if (a <= 0.01F) {
			return;
		}
		g.blitSprite(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, g.guiWidth(), g.guiHeight(), ARGB.color((int) (255 * a), color));
	}
}
