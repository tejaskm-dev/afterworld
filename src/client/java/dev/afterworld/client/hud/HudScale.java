package dev.afterworld.client.hud;

import dev.afterworld.AwConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The scale of Afterworld's free-standing HUD (the quest tracker, notices, title cards, voices and
 * damage numbers), sized to the window the way its screens are (see AwScreen.uiFor) rather than
 * by Minecraft's GUI scale: the tracker takes about a quarter of the window's width at any GUI
 * scale, in whole window pixels so the text stays crisp, and never smaller than two window
 * pixels per HUD pixel where the window has room. Things tied to the hotbar (the Edict strip,
 * powers, the purse) keep the game's GUI scale so they stay lined up with it.
 *
 * <p>Draw between {@link #begin} and {@link #end}, laid out on {@link #width} by {@link #height}.
 */
public final class HudScale {
	/** The width the tracker is laid out for, and the room the HUD needs as a whole. */
	private static final int REFERENCE = 170;
	private static final int MIN_W = 320;
	private static final int MIN_H = 200;

	private HudScale() {
	}

	/** HUD pixels per GUI pixel. */
	public static float factor() {
		var window = Minecraft.getInstance().getWindow();
		int gs = Math.max(1, window.getGuiScale());
		int fw = window.getWidth();
		int fh = window.getHeight();
		AwConfig config = AwConfig.get();
		double size = Math.clamp(config.uiSize, 0.5, 2.0);
		if (config.uiFollowsGuiScale) {
			return (float) size;
		}
		int fits = (int) Math.floor(Math.min(fw / (double) MIN_W, fh / (double) MIN_H));
		if (fits < 1) {
			return 1.0F;
		}
		double target = Math.min(fw * 0.24 / REFERENCE, fh * 0.40 / 120.0) * size;
		int t = (int) Math.round(target);
		t = Math.max(t, Math.min(2, fits));
		t = Math.max(1, Math.min(t, fits));
		return t / (float) gs;
	}

	public static int width(GuiGraphicsExtractor g) {
		return (int) Math.ceil(g.guiWidth() / factor());
	}

	public static int height(GuiGraphicsExtractor g) {
		return (int) Math.ceil(g.guiHeight() / factor());
	}

	/** A length in GUI pixels (the game's own HUD: boss bars, the hotbar) as HUD pixels. */
	public static int fromGui(float guiPixels) {
		return (int) Math.ceil(guiPixels / factor());
	}

	public static void begin(GuiGraphicsExtractor g) {
		float f = factor();
		g.pose().pushMatrix();
		g.pose().scale(f, f);
	}

	public static void end(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}
}
