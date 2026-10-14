package dev.afterworld.client.hud;

import dev.afterworld.Afterworld;
import dev.afterworld.AwConfig;
import dev.afterworld.client.screen.Portraits;
import dev.afterworld.net.AwPayloads;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Voices in the world, on screen: a great power's words in a band above the hotbar with its
 * portrait and name, typed as they are spoken; the narrator's in quiet italics at the top.
 */
public final class VoiceSubtitles {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static AwPayloads.VoiceLine boss;
	private static long bossAt;
	private static SoundInstance bossSound;
	private static AwPayloads.VoiceLine narrator;
	private static long narratorAt;
	private static SoundInstance narratorSound;

	private VoiceSubtitles() {
	}

	/** Speaks a line and shows it (only ever when {@link Voices} lets it): returns how long it lasts, in ms. */
	static long start(AwPayloads.VoiceLine v) {
		Minecraft mc = Minecraft.getInstance();
		stopSounds();
		SoundInstance s = null;
		if (!v.line().sound().isEmpty() && AwConfig.get().voices) {
			s = new SimpleSoundInstance(Identifier.parse(v.line().sound()), SoundSource.VOICE, 1.0F, 1.0F,
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
			mc.getSoundManager().play(s);
		}
		if (v.style() == AwPayloads.VoiceLine.NARRATOR) {
			narrator = v;
			narratorAt = Util.getMillis();
			narratorSound = s;
		} else {
			boss = v;
			bossAt = Util.getMillis();
			bossSound = s;
		}
		return length(v);
	}

	/** Silences whatever line is being spoken and takes its words off the screen (it will be said again). */
	static void stop() {
		stopSounds();
		boss = null;
		narrator = null;
	}

	private static void stopSounds() {
		Minecraft mc = Minecraft.getInstance();
		if (narratorSound != null) {
			mc.getSoundManager().stop(narratorSound);
			narratorSound = null;
		}
		if (bossSound != null) {
			mc.getSoundManager().stop(bossSound);
			bossSound = null;
		}
	}

	private static long length(AwPayloads.VoiceLine v) {
		return v.line().ticks() > 0 ? v.line().ticks() * 50L : 1500 + v.line().text().length() * 45L;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		HudScale.begin(g);
		try {
			draw(g, delta);
		} finally {
			HudScale.end(g);
		}
	}

	private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden()) {
			return;
		}
		long now = Util.getMillis();
		if (boss != null) {
			long age = now - bossAt;
			long total = length(boss) + 1600;
			if (age > total) {
				boss = null;
			} else {
				drawBoss(g, mc, boss, age, total);
			}
		}
		if (narrator != null) {
			long age = now - narratorAt;
			long total = length(narrator) + 2200;
			if (age > total) {
				narrator = null;
			} else {
				drawNarrator(g, mc, narrator, age, total);
			}
		}
	}

	private static void drawBoss(GuiGraphicsExtractor g, Minecraft mc, AwPayloads.VoiceLine v, long age, long total) {
		float in = Mth.clamp(age / 250.0F, 0, 1);
		float out = Mth.clamp((total - age) / 500.0F, 0, 1);
		float k = Math.min(in, out);
		int w = Math.min(400, HudScale.width(g) - 40);
		int x = (HudScale.width(g) - w) / 2;
		List<FormattedCharSequence> lines = mc.font.split(Component.literal(v.line().text()), w - 70);
		int h = 16 + Math.max(2, lines.size()) * 10;
		int y = HudScale.height(g) - HudScale.fromGui(58) - h;
		g.fill(x, y, x + w, y + h, ARGB.color((int) (200 * k), 0x0B0C0F));
		g.fill(x, y, x + w, y + 1, ARGB.color((int) (255 * k), v.color()));
		Entity e = mc.level == null ? null : mc.level.getEntity(v.entity());
		if (e instanceof LivingEntity le) {
			g.enableScissor(x + 2, y + 1, x + 52, y + h);
			Portraits.bust(g, le, x + 2, y + 1, x + 52, y + h, 18.0F, age < length(v) ? 1.0F : 0.0F, Util.getMillis() / 1000.0F);
			g.disableScissor();
		}
		Component name = Component.literal(v.name()).withStyle(s -> s.withFont(DISPLAY));
		g.text(mc.font, name, x + 58, y + 4, ARGB.color((int) (255 * k), v.color()), true);
		float frac = v.line().revealed(age, Math.max(1, length(v) - 300));
		String text = v.line().text();
		String shown = text.substring(0, Math.min(text.length(), (int) Math.ceil(text.length() * frac)));
		int ty = y + 15;
		for (FormattedCharSequence seq : mc.font.split(Component.literal(shown), w - 70)) {
			g.text(mc.font, seq, x + 58, ty, ARGB.color((int) (255 * k), 0xF0E8D4), true);
			ty += 10;
		}
	}

	private static void drawNarrator(GuiGraphicsExtractor g, Minecraft mc, AwPayloads.VoiceLine v, long age, long total) {
		float in = Mth.clamp(age / 600.0F, 0, 1);
		float out = Mth.clamp((total - age) / 900.0F, 0, 1);
		float k = Math.min(in, out);
		int w = Math.min(360, HudScale.width(g) - 60);
		float frac = v.line().revealed(age, Math.max(1, length(v) - 300));
		String text = v.line().text();
		String shown = text.substring(0, Math.min(text.length(), (int) Math.ceil(text.length() * frac)));
		List<FormattedCharSequence> lines = mc.font.split(Component.literal(shown).withStyle(s -> s.withItalic(true)), w);
		// Below a title card while one is up, so the two never write over each other; and below the
		// quest panel when the line would run under it.
		int y = (int) Mth.lerp(TitleCards.card(), HudScale.height(g) * 0.18F, HudScale.height(g) * 0.27F + 40);
		int widest = 0;
		for (FormattedCharSequence seq : lines) {
			widest = Math.max(widest, mc.font.width(seq));
		}
		if (QuestHud.panelBottom > 0 && (HudScale.width(g) + widest) / 2 > QuestHud.panelLeft) {
			y = Math.max(y, QuestHud.panelBottom + 8);
		}
		for (FormattedCharSequence seq : lines) {
			int lw = mc.font.width(seq);
			g.text(mc.font, seq, (HudScale.width(g) - lw) / 2, y, ARGB.color((int) (240 * k), 0xE8DCC0), true);
			y += 11;
		}
	}

	public static void reset() {
		boss = null;
		narrator = null;
	}
}
