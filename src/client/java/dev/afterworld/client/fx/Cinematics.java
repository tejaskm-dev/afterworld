package dev.afterworld.client.fx;

import dev.afterworld.Afterworld;
import dev.afterworld.AwConfig;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.net.AwPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Beats for the camera. An ultimate draws letterbox bars, flashes the screen in its colour and
 * names itself in large letters; a perfect dodge or parry gives a sharp edge flash; a boss
 * changing phase gets the bars and its name.
 */
public final class Cinematics {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static long start = -100000;
	private static long length;
	private static int color;
	private static Component title = Component.empty();
	private static Component subtitle = Component.empty();
	private static boolean bars;
	private static long flashAt = -100000;
	private static int flashColor;

	private Cinematics() {
	}

	public static void play(AwPayloads.Cinematic c) {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		switch (c.kind()) {
			case AwPayloads.Cinematic.ULTIMATE -> {
				Aspect a = Aspect.byOrdinal(c.arg());
				boolean mine = mc.player != null && c.entity() == mc.player.getId();
				Shake.kick(mine ? 3.5F : 2.0F, 14);
				flash(c.color(), now);
				if (a != null && AwConfig.get().cinematics) {
					start = now;
					length = 2200;
					color = c.color();
					bars = true;
					title = Component.translatable("aspect.afterworld." + a.id).withStyle(s -> s.withFont(DISPLAY));
					var who = mc.level == null ? null : mc.level.getEntity(c.entity());
					subtitle = who == null || mine ? Component.translatable("cinematic.afterworld.unleashed")
						: Component.translatable("cinematic.afterworld.unleashed_by", who.getDisplayName());
				}
			}
			case AwPayloads.Cinematic.PERFECT -> flash(c.color(), now);
			case AwPayloads.Cinematic.BOSS_PHASE -> {
				Shake.kick(2.5F, 12);
				flash(c.color(), now);
				if (AwConfig.get().cinematics) {
					start = now;
					length = 2600;
					color = c.color();
					bars = true;
					var who = mc.level == null ? null : mc.level.getEntity(c.entity());
					title = (who == null ? Component.empty() : who.getDisplayName().copy()).withStyle(s -> s.withFont(DISPLAY));
					subtitle = Component.translatable("cinematic.afterworld.phase." + c.arg());
				}
			}
			default -> {
			}
		}
	}

	private static void flash(int rgb, long now) {
		flashAt = now;
		flashColor = rgb;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		long now = Util.getMillis();
		int w = g.guiWidth();
		int h = g.guiHeight();
		long fa = now - flashAt;
		if (fa < 350) {
			float k = 1 - fa / 350.0F;
			int edge = (int) (90 * k);
			int c = ARGB.color(edge, flashColor);
			int t = Math.max(6, h / 10);
			g.fillGradient(0, 0, w, t, c, ARGB.color(0, flashColor));
			g.fillGradient(0, h - t, w, h, ARGB.color(0, flashColor), c);
			g.fill(0, 0, w, h, ARGB.color((int) (28 * k), flashColor));
		}
		long age = now - start;
		if (age < 0 || age > length || !bars) {
			return;
		}
		float in = Mth.clamp(age / 250.0F, 0, 1);
		float out = Mth.clamp((length - age) / 400.0F, 0, 1);
		float k = Math.min(in, out);
		int bar = (int) (h * 0.09F * ease(k));
		g.fill(0, 0, w, bar, 0xFF050608);
		g.fill(0, h - bar, w, h, 0xFF050608);
		float ta = Mth.clamp((age - 150) / 300.0F, 0, 1) * out;
		if (ta > 0.02F) {
			var pose = g.pose();
			pose.pushMatrix();
			float grow = 2.2F + 0.15F * (age / (float) length);
			pose.translate(w / 2.0F, h * 0.30F);
			pose.scale(grow, grow);
			Minecraft mc = Minecraft.getInstance();
			g.centeredText(mc.font, title, 0, 0, ARGB.color((int) (255 * ta), color));
			pose.popMatrix();
			g.centeredText(mc.font, subtitle, w / 2, (int) (h * 0.30F) + 24, ARGB.color((int) (200 * ta), 0xE8DCC0));
		}
	}

	private static float ease(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	public static void reset() {
		start = -100000;
		flashAt = -100000;
	}
}
