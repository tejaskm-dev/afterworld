package dev.afterworld.client.title;

import dev.afterworld.Afterworld;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The night from the concept art, animated: stars, a haloed moon, mountains, a lit castle,
 * floating Edict shards, fireflies and a lone traveller on the amber road. Pure GUI draws; it
 * costs a few hundred quads a frame.
 */
public final class TitleScene {
	private static final Identifier MOON = Afterworld.id("textures/gui/title/moon.png");
	private static final Identifier HALO = Afterworld.id("textures/gui/title/halo.png");
	private static final Identifier MOUNTAINS = Afterworld.id("textures/gui/title/mountains.png");
	private static final Identifier HILLS = Afterworld.id("textures/gui/title/hills.png");
	private static final Identifier CASTLE = Afterworld.id("textures/gui/title/castle.png");
	private static final Identifier FOREGROUND = Afterworld.id("textures/gui/title/foreground.png");
	public static final Identifier LOGO = Afterworld.id("textures/gui/title/logo.png");
	public static final int LOGO_W = 416;
	public static final int LOGO_H = 66;
	private static final int[] SHARD_COLORS = {0xE8A33D, 0x4FB3A9, 0x9BD08A, 0xC4553B, 0xB79CE8};
	private static final float[][] STARS = new float[170][4];
	private static final float[][] FLIES = new float[34][4];

	static {
		RandomSource r = RandomSource.create(1984);
		for (float[] s : STARS) {
			s[0] = r.nextFloat();
			s[1] = r.nextFloat() * 0.62F;
			s[2] = r.nextFloat() * 6.28F;
			s[3] = r.nextFloat() < 0.12F ? 2 : 1;
		}
		for (float[] f : FLIES) {
			f[0] = r.nextFloat();
			f[1] = r.nextFloat();
			f[2] = r.nextFloat() * 6.28F;
			f[3] = 0.4F + r.nextFloat() * 0.8F;
		}
	}

	private TitleScene() {
	}

	/** Draws the whole scene. {@code dim} 0..1 darkens it for screens that sit on top. */
	public static void render(GuiGraphicsExtractor g, int w, int h, double mouseX, double mouseY, float time, float dim) {
		float s = Math.max(w / 480.0F, h / 270.0F);
		float ox = (w - 480 * s) / 2;
		float oy = (h - 270 * s) / 2;
		float px = (float) ((mouseX - w / 2.0) / Math.max(1, w));
		float py = (float) ((mouseY - h / 2.0) / Math.max(1, h));

		g.fillGradient(0, 0, w, h, 0xFF0B1218, 0xFF1E2C36);
		for (float[] st : STARS) {
			float tw = 0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(time * (0.8F + st[2] * 0.2F) + st[2]));
			int x = (int) (st[0] * w - px * 6);
			int y = (int) (st[1] * h - py * 4);
			int size = (int) st[3];
			g.fill(x, y, x + size, y + size, ARGB.color((int) (tw * 230), 0xE8DCC0));
		}
		shootingStar(g, w, h, time);

		blit(g, HALO, ox + (310 - px * 4) * s, oy + (6 - py * 3) * s, 160 * s, 160 * s, 256, 256, 1.0F);
		blit(g, MOON, ox + (347 - px * 5) * s, oy + (41 - py * 3) * s, 86 * s, 86 * s, 96, 96, 1.0F);

		float mx = -px * 10;
		blit(g, MOUNTAINS, ox + (mx - 16) * s, oy + 122 * s, 512 * s, 140 * s, 512, 140, 1.0F);
		blit(g, CASTLE, ox + (282 - px * 16) * s, oy + (92 - py * 4) * s, 92 * s, 98 * s, 160, 170, 1.0F);
		blit(g, HILLS, ox + (-px * 22 - 16) * s, oy + 150 * s, 512 * s, 140 * s, 512, 140, 1.0F);

		for (int i = 0; i < SHARD_COLORS.length; i++) {
			float sx = 34 + i * 26 - px * 26;
			float sy = 70 + (i % 2) * 12 + Mth.sin(time * 0.9F + i * 1.3F) * 4 - py * 8;
			shard(g, (int) (ox + sx * s), (int) (oy + sy * s), (int) Math.max(3, 4 * s), SHARD_COLORS[i], time + i);
		}

		blit(g, FOREGROUND, ox + (-px * 34 - 16) * s, oy + 206 * s, 512 * s, 120 * s, 512, 120, 1.0F);
		lantern(g, (int) (ox + (186 - px * 34 - 16 + 4) * s), (int) (oy + (206 + 67) * s), s, time);
		for (float[] f : FLIES) {
			float t = (time * 0.03F * f[3] + f[1]) % 1.0F;
			float x = (f[0] + Mth.sin(time * 0.4F + f[2]) * 0.02F) * w;
			float y = h * (1.0F - t * 0.55F);
			float a = Mth.clamp(Mth.sin(t * 3.1416F), 0, 1) * (0.55F + 0.45F * Mth.sin(time * 3 + f[2]));
			int sz = Math.max(1, (int) (s * 0.6F));
			g.fill((int) x, (int) y, (int) x + sz, (int) y + sz, ARGB.color((int) (a * 255), 0xFFD27A));
		}
		vignette(g, w, h);
		if (dim > 0) {
			g.fill(0, 0, w, h, ARGB.color((int) (dim * 255), 0x0B1218));
		}
	}

	private static void blit(GuiGraphicsExtractor g, Identifier tex, float x, float y, float dw, float dh, int tw, int th, float alpha) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0, Math.round(dw), Math.round(dh), tw, th, tw, th, ARGB.white(alpha));
	}

	private static void shard(GuiGraphicsExtractor g, int cx, int cy, int r, int color, float t) {
		int glow = ARGB.color(46 + (int) (20 * Mth.sin(t * 2)), color);
		diamond(g, cx, cy, r * 2, glow);
		diamond(g, cx, cy, r, 0xFF000000 | color);
		diamond(g, cx - r / 3, cy - r / 3, Math.max(1, r / 3), 0xFFFFF6DC);
	}

	private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static void lantern(GuiGraphicsExtractor g, int x, int y, float s, float t) {
		float flicker = 0.75F + 0.25F * Mth.sin(t * 9) * Mth.sin(t * 5.3F);
		int r = (int) (10 * s);
		for (int i = 3; i >= 1; i--) {
			int rr = r * i / 3;
			g.fill(x - rr, y - rr, x + rr, y + rr, ARGB.color((int) (18 * flicker * (4 - i)), 0xE8A33D));
		}
	}

	private static void shootingStar(GuiGraphicsExtractor g, int w, int h, float time) {
		float cycle = time % 13.0F;
		if (cycle > 1.2F) {
			return;
		}
		float t = cycle / 1.2F;
		int seed = (int) (time / 13.0F);
		RandomSource r = RandomSource.create(seed * 31L);
		float x0 = (0.15F + r.nextFloat() * 0.5F) * w;
		float y0 = (0.05F + r.nextFloat() * 0.2F) * h;
		for (int i = 0; i < 14; i++) {
			float tt = t - i * 0.012F;
			if (tt < 0) {
				break;
			}
			int x = (int) (x0 + tt * w * 0.35F);
			int y = (int) (y0 + tt * h * 0.12F);
			g.fill(x, y, x + 2, y + 1, ARGB.color((int) (220 * (1 - i / 14.0F) * (1 - t)), 0xFFF6DC));
		}
	}

	private static void vignette(GuiGraphicsExtractor g, int w, int h) {
		g.fillGradient(0, 0, w, h / 5, 0x88070A0E, 0x00070A0E);
		g.fillGradient(0, h - h / 4, w, h, 0x00070A0E, 0xAA070A0E);
		int side = w / 8;
		for (int i = 0; i < side; i += 2) {
			int a = (int) (110 * (1 - i / (float) side));
			g.fill(i, 0, i + 2, h, ARGB.color(a / 6, 0x070A0E));
			g.fill(w - i - 2, 0, w - i, h, ARGB.color(a / 6, 0x070A0E));
		}
	}

	/** The logo, scaled to a width; returns its drawn height. */
	public static int logo(GuiGraphicsExtractor g, int cx, int y, int width, float alpha) {
		int height = width * LOGO_H / LOGO_W;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, cx - width / 2, y, 0, 0, width, height, LOGO_W, LOGO_H, LOGO_W, LOGO_H, ARGB.white(alpha));
		return height;
	}
}
