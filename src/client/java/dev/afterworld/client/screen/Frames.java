package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The pieces Afterworld's windows are built from, painted by tools/gen_gui.py to sit beside the
 * Allure resource pack: lavender stone frames round a deep violet body, stone buttons and tabs, a
 * gold-rimmed title plaque, dividers and bars, and a few windows drawn as objects (a parchment
 * scroll, a stall's awning, the Guild's ledger, a carved tablet). Every screen and HUD panel draws
 * through here, so the look of the whole interface is decided in one place.
 *
 * <p>Panels take the rectangle their content lives in; the frame is drawn round it.
 */
public final class Frames {
	public static final Identifier PANEL = Afterworld.id("frame/panel");
	public static final Identifier INSET = Afterworld.id("frame/inset");
	public static final Identifier BUTTON = Afterworld.id("frame/button");
	public static final Identifier BUTTON_HOVER = Afterworld.id("frame/button_hover");
	public static final Identifier BUTTON_DOWN = Afterworld.id("frame/button_down");
	public static final Identifier BUTTON_OFF = Afterworld.id("frame/button_off");
	public static final Identifier TAB = Afterworld.id("frame/tab");
	public static final Identifier TAB_ACTIVE = Afterworld.id("frame/tab_active");
	public static final Identifier PLAQUE = Afterworld.id("frame/plaque");
	public static final Identifier DIVIDER = Afterworld.id("frame/divider");
	public static final Identifier GEM = Afterworld.id("frame/gem");
	public static final Identifier BAR = Afterworld.id("frame/bar");
	public static final Identifier BAR_FILL = Afterworld.id("frame/bar_fill");
	public static final Identifier PARCHMENT = Afterworld.id("frame/parchment");
	public static final Identifier AWNING = Afterworld.id("frame/awning");
	public static final Identifier LEDGER = Afterworld.id("frame/ledger");
	public static final Identifier TABLET = Afterworld.id("frame/tablet");

	/** Text on the violet body, dim text there, and ink for text on stone, parchment or paper. */
	public static final int TEXT = 0xFFE8DCC0;
	public static final int TEXT_DIM = 0xFFA89EB8;
	public static final int INK = 0xFF2A1E36;
	public static final int INK_DIM = 0xFF5E5470;
	public static final int PAPER_INK = 0xFF3A2614;
	/** How far the stone band reaches outside a panel's content rectangle. */
	public static final int BAND = 5;

	private Frames() {
	}

	/** A window: a stone frame round a violet body, with a thin line of the screen's own colour inside its top edge. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x - BAND, y - BAND, w + BAND * 2, h + BAND * 2);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, ARGB.color(200, accent));
	}

	/** A sunken area inside a panel: a list, a card, a portrait's backing. */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, x, y, w, h);
	}

	/** A stone button: raised when idle, lit when hovered, flat and grey when it can't be pressed. */
	public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean hot, boolean enabled, int accent) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_OFF : hot ? BUTTON_HOVER : BUTTON, x, y, w, h);
		if (enabled && hot) {
			g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, ARGB.color(255, accent));
		}
		int tw = font.width(label);
		int color = !enabled ? 0xFF5A5866 : INK;
		g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2 - (enabled ? 1 : 0), color, false);
	}

	/** A tab on a panel's edge; the chosen one is lighter and stands proud. */
	public static void tab(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, active ? TAB_ACTIVE : TAB, x, y, w, h);
	}

	/** A title on a gold-rimmed stone plaque, centred on {@code cx}. Returns the plaque's height. */
	public static int plaque(GuiGraphicsExtractor g, Font font, Component title, int cx, int y, int maxW) {
		int tw = Math.min(font.width(title), maxW - 24);
		int w = tw + 28;
		int h = 20;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, PLAQUE, cx - w / 2, y, w, h);
		g.text(font, title, cx - tw / 2, y + 7, INK, false);
		return h;
	}

	/** A line with a gem in its middle, to part a panel's sections. */
	public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, DIVIDER, x, y, w, 5);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, GEM, x + w / 2 - 3, y, 6, 5);
	}

	/** A bar, filled from the left in {@code color}. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fill, int color) {
		int hh = Math.max(4, h + 2);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR, x - 1, y - 1, w + 2, hh);
		int f = Math.round((w + 2) * Math.max(0, Math.min(1, fill)));
		if (f >= 4) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL, x - 1, y - 1, f, hh, ARGB.color(255, color));
		}
	}

	/** A parchment scroll (the conversation box): text on it is {@link #PAPER_INK}. */
	public static void parchment(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, PARCHMENT, x - 4, y - 6, w + 8, h + 12);
	}

	/** A stall's striped awning across the top of a window (the shop). */
	public static void awning(GuiGraphicsExtractor g, int x, int y, int w) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, AWNING, x, y, w, 14);
	}

	/** The Guild's ledger: two cream pages in green leather (the bank). */
	public static void ledger(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, LEDGER, x - 6, y - 6, w + 12, h + 12);
	}

	/** A carved slab of dark stone with a lit rune line (the Ladder's gate). */
	public static void tablet(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, TABLET, x - 6, y - 6, w + 12, h + 12);
	}

	static int darker(int rgb, float k) {
		int r = (rgb >> 16) & 0xFF;
		int gg = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return ((int) (r * k) << 16) | ((int) (gg * k) << 8) | (int) (b * k);
	}

	static int lighter(int rgb, float k) {
		int r = (rgb >> 16) & 0xFF;
		int gg = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return ((int) (r + (255 - r) * k) << 16) | ((int) (gg + (255 - gg) * k) << 8) | (int) (b + (255 - b) * k);
	}
}
