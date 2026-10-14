package dev.afterworld.client.hud;

import dev.afterworld.AwConfig;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.ClientMoves;
import dev.afterworld.client.ClientState;
import dev.afterworld.registry.AwGear;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Two small instruments: the stamina wheel beside the crosshair (only while it isn't full), and
 * the Aspect emblem beside the hotbar, ringed by Resonance and burning when the ultimate is ready.
 */
public final class PowerHud {
	private static float shown = 1.0F;
	private static long fullSince = Util.getMillis();
	private static float wheelAlpha;
	private static float ringShown;
	private static boolean wasReady;
	private static long readyAt;

	private PowerHud() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) {
			return;
		}
		stamina(g, mc);
		aspect(g, mc);
		parryGlint(g);
	}

	// ------------------------------------------------------------------ stamina

	private static void stamina(GuiGraphicsExtractor g, Minecraft mc) {
		float max = Math.max(1, ClientState.staminaMax);
		float frac = Mth.clamp(ClientState.stamina / max, 0, 1);
		shown = Mth.lerp(0.25F, shown, frac);
		long now = Util.getMillis();
		boolean full = frac >= 0.999F && !ClientMoves.draining();
		if (!full) {
			fullSince = now;
		}
		float target = full && now - fullSince > 1200 || mc.player.isCreative() ? 0 : 1;
		wheelAlpha = Mth.lerp(0.15F, wheelAlpha, target);
		if (wheelAlpha < 0.02F) {
			return;
		}
		int cx = g.guiWidth() / 2 + 18;
		int cy = g.guiHeight() / 2;
		int dots = 36;
		boolean low = frac < 0.25F;
		boolean empty = frac <= 0.01F;
		float blink = empty ? 0.5F + 0.5F * Mth.sin(now / 90.0F) : 1.0F;
		int col = empty ? 0xE05A4A : low ? 0xE8A33D : 0x7FE3B0;
		for (int i = 0; i < dots; i++) {
			float t = i / (float) dots;
			double a = -Math.PI / 2 + t * Math.PI * 2;
			int x = cx + (int) Math.round(Math.cos(a) * 7.5);
			int y = cy + (int) Math.round(Math.sin(a) * 7.5);
			boolean lit = t < shown;
			int alpha = (int) (255 * wheelAlpha * (lit ? blink : 0.35F));
			g.fill(x, y, x + 1, y + 1, ARGB.color(alpha, lit ? col : 0x202428));
		}
	}

	// ------------------------------------------------------------------ the Aspect

	private static void aspect(GuiGraphicsExtractor g, Minecraft mc) {
		Aspect a = Aspect.byOrdinal(ClientState.aspect);
		if (a == null || !AwConfig.get().showEdictHud) {
			return;
		}
		float frac = Mth.clamp(ClientState.resonance / 100.0F, 0, 1);
		ringShown = Mth.lerp(0.2F, ringShown, frac);
		boolean ready = frac >= 0.999F;
		long now = Util.getMillis();
		if (ready && !wasReady) {
			readyAt = now;
		}
		wasReady = ready;
		int offhand = mc.player.getOffhandItem().isEmpty() ? 0 : 29;
		boolean leftSide = AwConfig.get().hudSide == 0;
		int cx = leftSide ? g.guiWidth() / 2 - 91 - 16 - offhand : g.guiWidth() / 2 + 91 + 16;
		int cy = g.guiHeight() - 12;
		// Backing disc.
		for (int r = 0; r <= 11; r++) {
			int half = (int) Math.round(Math.sqrt(121 - r * r));
			g.fill(cx - half, cy - r, cx + half, cy - r + 1, 0xB0101216);
			g.fill(cx - half, cy + r, cx + half, cy + r + 1, 0xB0101216);
		}
		// Resonance ring.
		int dots = 48;
		float pulse = ready ? 0.7F + 0.3F * Mth.sin(now / 160.0F) : 1.0F;
		for (int i = 0; i < dots; i++) {
			float t = i / (float) dots;
			double ang = -Math.PI / 2 + t * Math.PI * 2;
			int x = cx + (int) Math.round(Math.cos(ang) * 11.5);
			int y = cy + (int) Math.round(Math.sin(ang) * 11.5);
			boolean lit = t < ringShown;
			g.fill(x, y, x + 1, y + 1, lit ? ARGB.color((int) (255 * pulse), a.color) : 0x60303438);
		}
		if (ready) {
			// A burst when it fills, then a slow shimmer.
			float burst = Mth.clamp(1 - (now - readyAt) / 600.0F, 0, 1);
			int rr = 12 + (int) (burst * 8);
			for (int i = 0; i < 24; i++) {
				double ang = i * Math.PI / 12 + now / 900.0;
				int x = cx + (int) Math.round(Math.cos(ang) * rr);
				int y = cy + (int) Math.round(Math.sin(ang) * rr);
				g.fill(x, y, x + 1, y + 1, ARGB.color((int) (200 * Math.max(burst, 0.35F * pulse)), a.color));
			}
		}
		ItemStack heart = new ItemStack(AwGear.HEARTS.get(a));
		g.item(heart, cx - 8, cy - 8);
		if (ready) {
			Component key = AfterworldClient.ULTIMATE.getTranslatedKeyMessage();
			int w = mc.font.width(key);
			g.fill(cx - w / 2 - 2, cy - 23, cx + w / 2 + 2, cy - 14, 0xC0101216);
			g.centeredText(mc.font, key, cx, cy - 22, ARGB.color((int) (255 * pulse), a.color));
		}
	}

	// ------------------------------------------------------------------ the parry glint

	private static void parryGlint(GuiGraphicsExtractor g) {
		int f = ClientMoves.parryFlash;
		if (f < 0) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int len = 4 + f;
		int alpha = (int) (220 * (1 - f / 8.0F));
		int c = ARGB.color(alpha, 0xFFF4DC);
		g.fill(cx - len - 6, cy, cx - 6, cy + 1, c);
		g.fill(cx + 6, cy, cx + len + 6, cy + 1, c);
	}

	public static void reset() {
		wheelAlpha = 0;
		ringShown = 0;
		wasReady = false;
	}
}
