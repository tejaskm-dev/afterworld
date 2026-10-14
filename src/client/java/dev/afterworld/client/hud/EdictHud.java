package dev.afterworld.client.hud;

import dev.afterworld.Afterworld;
import dev.afterworld.AwConfig;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.ClientState;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Edict strip beside the hotbar: equipped Edicts with their keys and cooldown sweeps, the
 * discipline signature, and small marks for active rule windows. Draws nothing until synced.
 */
public final class EdictHud {
	private static final Identifier SLOT = Afterworld.id("hud/slot");
	private static final Identifier SLOT_SIG = Afterworld.id("hud/slot_signature");
	private static final Identifier BELL = Afterworld.id("hud/suppressed");

	private EdictHud() {
	}

	public static Identifier edictIcon(int ordinal) {
		Edict e = Edict.byOrdinal(ordinal);
		return Afterworld.id("edict/" + (e == null ? "unknown" : e.id));
	}

	public static Identifier disciplineIcon(int ordinal) {
		return Afterworld.id("discipline/" + Discipline.byOrdinal(ordinal).id);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!ClientState.received || !AwConfig.get().showEdictHud || mc.gui.hud.isHidden() || mc.player == null || mc.player.isSpectator()) {
			return;
		}
		boolean any = ClientState.discipline != 0;
		for (int i = 0; i < ClientState.slotCount; i++) {
			any |= ClientState.slots[i] >= 0;
		}
		if (!any) {
			return;
		}
		Font font = mc.font;
		int count = ClientState.slotCount;
		int stripWidth = count * 22 + (ClientState.discipline != 0 ? 26 : 0);
		int x = AwConfig.get().hudSide == 1 ? g.guiWidth() / 2 - 91 - 10 - stripWidth - (mc.player.getOffhandItem().isEmpty() ? 0 : 29)
			: g.guiWidth() / 2 + 91 + 10;
		int y = g.guiHeight() - 23;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		// Narrow windows: shrink the strip toward the hotbar rather than letting it fall off-screen.
		float scale = 1.0F;
		if (AwConfig.get().hudSide == 0) {
			int room = g.guiWidth() - x - 2;
			if (stripWidth > room && room > 20) {
				scale = Math.max(0.5F, room / (float) stripWidth);
			}
		} else if (x < 2) {
			scale = Math.max(0.5F, (x + stripWidth - 2) / (float) stripWidth);
			x = 2;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y + 22);
		pose.scale(scale, scale);
		pose.translate(-x, -(y + 22));
		for (int i = 0; i < count; i++) {
			int sx = x + i * 22;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, sx, y, 22, 22);
			int edict = ClientState.slots[i];
			if (edict >= 0) {
				Edict e = Edict.byOrdinal(edict);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, edictIcon(edict), sx + 3, y + 3, 16, 16);
				float rem = ClientState.cdRemain[i] - partial;
				if (rem > 0 && ClientState.cdTotal[i] > 0) {
					float frac = Mth.clamp(rem / ClientState.cdTotal[i], 0, 1);
					int h = Math.max(1, Math.round(16 * frac));
					g.fill(sx + 3, y + 3 + (16 - h), sx + 19, y + 19, 0xB0101418);
					String secs = rem > 200 ? String.valueOf(Math.round(rem / 20)) : String.format("%.1f", rem / 20F);
					g.centeredText(font, secs, sx + 11, y + 8, 0xFFE8DCC0);
				} else if (e != null && edict == pulseEdict && pulseTicks > 0) {
					g.outline(sx + 2, y + 2, 18, 18, ARGB.color(Math.min(255, pulseTicks * 25), e.color));
				}
				if (ClientState.suppressed(i)) {
					g.fill(sx + 3, y + 3, sx + 19, y + 19, 0x90201510);
					g.blitSprite(RenderPipelines.GUI_TEXTURED, BELL, sx + 7, y + 7, 8, 8);
				}
			}
			keyLabel(g, font, AfterworldClient.SLOT_KEYS[i], sx + 11, y - 7);
		}
		if (ClientState.discipline != 0) {
			int sx = x + count * 22 + 4;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SIG, sx, y, 22, 22);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, disciplineIcon(ClientState.discipline), sx + 3, y + 3, 16, 16);
			float rem = ClientState.sigRemain - partial;
			if (rem > 0 && ClientState.sigTotal > 0) {
				float frac = Mth.clamp(rem / ClientState.sigTotal, 0, 1);
				int h = Math.max(1, Math.round(16 * frac));
				g.fill(sx + 3, y + 3 + (16 - h), sx + 19, y + 19, 0xB0101418);
				g.centeredText(font, String.valueOf((int) Math.ceil(rem / 20)), sx + 11, y + 8, 0xFFE8DCC0);
			}
			keyLabel(g, font, AfterworldClient.SIGNATURE, sx + 11, y - 7);
		}
		// Rule windows: a row of small coloured marks above the strip.
		int mx = x;
		int my = y - 16;
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_ECHO, Edict.ECHOES.color);
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_HUNGER, Edict.HUNGER.color);
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_PACT, Edict.HOSPITALITY.color);
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_FORGOTTEN, Edict.FORGOTTEN.color);
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_WRONG_TURN, 0x7FB069);
		mx = mark(g, mx, my, AwPayloads.Hud.FLAG_FAVOUR, 0xD9B45A);
		mark(g, mx, my, AwPayloads.Hud.FLAG_OATHBREAKER, 0xB0302A);
		pose.popMatrix();
		if (pulseTicks > 0) {
			pulseTicks--;
		}
	}

	private static int mark(GuiGraphicsExtractor g, int x, int y, int flag, int color) {
		if (!ClientState.flag(flag)) {
			return x;
		}
		int c = 0xFF000000 | color;
		g.fill(x + 2, y, x + 4, y + 6, c);
		g.fill(x + 1, y + 1, x + 5, y + 5, c);
		g.fill(x, y + 2, x + 6, y + 4, c);
		return x + 8;
	}

	private static void keyLabel(GuiGraphicsExtractor g, Font font, KeyMapping key, int cx, int y) {
		String label = key.getTranslatedKeyMessage().getString();
		if (label.length() > 3) {
			label = label.substring(0, 3);
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(0.75F, 0.75F);
		g.centeredText(font, label, 0, 0, 0xFFB8AE98);
		pose.popMatrix();
	}

	private static int pulseEdict = -1;
	private static int pulseTicks;

	/** Briefly outlines a slot when its Edict comes off cooldown. */
	public static void pulse(int edict) {
		pulseEdict = edict;
		pulseTicks = 10;
	}
}
