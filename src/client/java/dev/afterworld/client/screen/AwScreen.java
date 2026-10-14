package dev.afterworld.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The base of Afterworld's screens, so they read the same at every window size and GUI scale.
 *
 * A screen lays itself out on a canvas ({@link #cw} by {@link #ch}) and draws there; the canvas is
 * the window divided by a content scale. The scale respects the player's GUI scale: it is 1 unless
 * the window is so roomy the screen would look lost in it (then it grows a little, never past
 * three quarters of the window) or too small for the screen's minimum (then it shrinks). Either
 * way the total scale stays a whole number of pixels where it can, so the pixel font stays crisp.
 * Between its minimum and preferred sizes a screen is expected to lay itself out to fit, and the
 * helpers here fit text to the room it has (cut with an ellipsis, or wrapped) instead of spilling.
 *
 * Mouse input is mapped onto the canvas; clickable areas are registered while drawing as
 * {@link Hit}s, which also give the pointing-hand cursor and a soft hover tick.
 */
public abstract class AwScreen extends Screen {
	/** Content scale: canvas pixels to GUI pixels. */
	protected float ui = 1.0F;
	/** The canvas this screen lays itself out on. */
	protected int cw;
	protected int ch;
	protected final List<Hit> hits = new ArrayList<>();
	private Hit hovered;
	/** This frame's tooltip, drawn last on the canvas (so at the canvas's scale, kept inside it). */
	private Consumer<GuiGraphicsExtractor> pendingTip;
	private final ClientTooltipPositioner onCanvas = (sw, sh, x, y, w, h) -> DefaultTooltipPositioner.INSTANCE.positionTooltip(this.cw, this.ch, x, y, w, h);

	/** A clickable area on the canvas. */
	public record Hit(int x, int y, int w, int h, Runnable action) {
		public boolean in(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		boolean same(Hit o) {
			return o != null && o.x == x && o.y == y && o.w == w && o.h == h;
		}
	}

	protected AwScreen(Component title) {
		super(title);
	}

	/** The size the screen would like on its canvas. */
	protected abstract int prefWidth();

	protected abstract int prefHeight();

	/** The smallest canvas it can still be laid out on (the game never goes below 320 by 240). */
	protected int minWidth() {
		return 300;
	}

	protected int minHeight() {
		return 200;
	}

	/** Draws the screen on its canvas (mouse already mapped onto it). */
	protected abstract void draw(GuiGraphicsExtractor g, int mx, int my, float a);

	@Override
	protected void init() {
		super.init();
		this.ui = uiFor(prefWidth(), prefHeight(), minWidth(), minHeight(), widthShare(), heightShare());
		this.cw = (int) (this.width / this.ui);
		this.ch = (int) (this.height / this.ui);
	}

	/**
	 * The content scale (canvas pixels per GUI pixel) for a screen that would like
	 * {@code prefW x prefH} on its canvas and can be laid out down to {@code minW x minH}.
	 *
	 * <p>Sized to the window, not to Minecraft's GUI scale: the screen aims to fill about 70% of the
	 * window's width or 75% of its height (whichever comes first), so it looks the same at 1x, 3x,
	 * auto or 7x. The total scale (window pixels per canvas pixel) is a whole number, so the pixel
	 * font stays crisp; it never drops below 2 while the window still fits the screen's compact
	 * layout at 2, so text stays readable on small windows; and it never grows past what fits.
	 */
	public static float uiFor(int prefW, int prefH, int minW, int minH) {
		return uiFor(prefW, prefH, minW, minH, 0.70F, 0.75F);
	}

	/** How much of the window's width and height this screen aims to fill (see {@link #uiFor}). */
	protected float widthShare() {
		return 0.70F;
	}

	protected float heightShare() {
		return 0.75F;
	}

	public static float uiFor(int prefW, int prefH, int minW, int minH, float widthShare, float heightShare) {
		var window = net.minecraft.client.Minecraft.getInstance().getWindow();
		int gs = Math.max(1, window.getGuiScale());
		int fw = window.getWidth();
		int fh = window.getHeight();
		var config = dev.afterworld.AwConfig.get();
		double size = Math.clamp(config.uiSize, 0.5, 2.0);
		if (config.uiFollowsGuiScale) {
			// The game's way: as big as the GUI scale says, shrinking only if it doesn't fit.
			float fit = Math.min((fw / (float) gs - 8) / minW, (fh / (float) gs - 8) / minH);
			return fit >= 1.0F ? (float) size : Math.max(0.25F, fit);
		}
		// The largest whole scale at which the compact layout still fits the window.
		int fits = (int) Math.floor(Math.min((fw - 16.0) / minW, (fh - 16.0) / minH));
		if (fits < 1) {
			// A window smaller than the compact layout: shrink to fit, as best we can.
			return (float) Math.min((fw - 16.0) / minW, (fh - 16.0) / minH) / gs;
		}
		double target = Math.min(fw * widthShare / prefW, fh * heightShare / prefH) * size;
		int t = (int) Math.round(target);
		t = Math.max(t, Math.min(2, fits));
		t = Math.max(1, Math.min(t, fits));
		return t / (float) gs;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractRenderState(g, mx, my, a);
		this.hits.clear();
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(this.ui, this.ui);
		int x = (int) (mx / this.ui);
		int y = (int) (my / this.ui);
		draw(g, x, y, a);
		if (this.pendingTip != null) {
			g.nextStratum();
			this.pendingTip.accept(g);
			this.pendingTip = null;
		}
		pose.popMatrix();
		Hit over = null;
		for (int i = this.hits.size() - 1; i >= 0; i--) {
			if (this.hits.get(i).in(x, y)) {
				over = this.hits.get(i);
				break;
			}
		}
		if (over != null) {
			g.requestCursor(CursorTypes.POINTING_HAND);
			if (!over.same(this.hovered)) {
				sound(AwSounds.UI_HOVER, 1.0F, 0.12F);
			}
		}
		this.hovered = over;
	}

	// ------------------------------------------------------------------ input on the canvas

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / this.ui;
		double y = event.y() / this.ui;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = this.hits.size() - 1; i >= 0; i--) {
				if (this.hits.get(i).in(x, y)) {
					this.hits.get(i).action().run();
					return true;
				}
			}
		}
		if (clicked(x, y, event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A click that no hit area took, on the canvas. */
	protected boolean clicked(double x, double y, int button) {
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return scrolled(x / this.ui, y / this.ui, dy) || super.mouseScrolled(x, y, dx, dy);
	}

	protected boolean scrolled(double x, double y, double dy) {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	protected void sound(Holder<SoundEvent> sound, float pitch, float volume) {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
	}

	// ------------------------------------------------------------------ tooltips (on the canvas, at its scale)

	protected void tip(GuiGraphicsExtractor g, ItemStack stack, int mx, int my) {
		List<ClientTooltipComponent> lines = new ArrayList<>();
		for (Component c : Screen.getTooltipFromItem(this.minecraft, stack)) {
			lines.add(ClientTooltipComponent.create(c.getVisualOrderText()));
		}
		stack.getTooltipImage().ifPresent(image -> lines.add(lines.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
		defer(lines, mx, my, stack.get(DataComponents.TOOLTIP_STYLE), true);
	}

	protected void tip(GuiGraphicsExtractor g, List<Component> text, int mx, int my) {
		List<ClientTooltipComponent> lines = new ArrayList<>();
		for (Component c : text) {
			for (FormattedCharSequence seq : this.font.split(c, 240)) {
				lines.add(ClientTooltipComponent.create(seq));
			}
		}
		defer(lines, mx, my, null, false);
	}

	protected void tip(GuiGraphicsExtractor g, Component text, int mx, int my) {
		tip(g, List.of(text), mx, my);
	}

	private void defer(List<ClientTooltipComponent> lines, int mx, int my, Identifier style, boolean gapAfterFirst) {
		if (this.pendingTip == null && !lines.isEmpty()) {
			this.pendingTip = g -> g.tooltip(this.font, lines, mx, my, this.onCanvas, style, gapAfterFirst);
		}
	}

	// ------------------------------------------------------------------ fitting text

	/** The text, cut to {@code w} with an ellipsis if it does not fit. */
	protected String fit(String s, int w) {
		if (this.font.width(s) <= w) {
			return s;
		}
		return this.font.plainSubstrByWidth(s, Math.max(0, w - this.font.width("…"))) + "…";
	}

	protected Component fit(Component c, int w) {
		if (this.font.width(c) <= w) {
			return c;
		}
		return Component.literal(fit(c.getString(), w)).withStyle(c.getStyle());
	}

	/** Wrapped text, at most {@code maxLines} lines (the last cut with an ellipsis). Returns the y below it. */
	protected int wrap(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color, int maxLines, int lineH) {
		List<FormattedCharSequence> lines = this.font.split(text, Math.max(8, w));
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			if (i == maxLines - 1 && lines.size() > maxLines) {
				String rest = text.getString().substring(Math.min(text.getString().length(), charsBefore(lines, i)));
				g.text(this.font, fit(Component.literal(rest).withStyle(text.getStyle()), w), x, y, color, false);
			} else {
				g.text(this.font, lines.get(i), x, y, color, false);
			}
			y += lineH;
		}
		return y;
	}

	/** Like {@link #wrap} but each line centred on {@code cx}. */
	protected int wrapCentred(GuiGraphicsExtractor g, Component text, int cx, int y, int w, int color, int maxLines, int lineH) {
		List<FormattedCharSequence> lines = this.font.split(text, Math.max(8, w));
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			if (i == maxLines - 1 && lines.size() > maxLines) {
				String rest = text.getString().substring(Math.min(text.getString().length(), charsBefore(lines, i)));
				g.centeredText(this.font, fit(Component.literal(rest).withStyle(text.getStyle()), w), cx, y, color);
			} else {
				g.centeredText(this.font, lines.get(i), cx, y, color);
			}
			y += lineH;
		}
		return y;
	}

	private int charsBefore(List<FormattedCharSequence> lines, int index) {
		int n = 0;
		for (int i = 0; i < index; i++) {
			StringBuilder sb = new StringBuilder();
			lines.get(i).accept((pos, style, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
			n += sb.length();
			// split() drops the space a line broke at.
			n += 1;
		}
		return n;
	}

	/** How many lines {@code text} takes at width {@code w}. */
	protected int lines(Component text, int w) {
		return this.font.split(text, Math.max(8, w)).size();
	}
}
