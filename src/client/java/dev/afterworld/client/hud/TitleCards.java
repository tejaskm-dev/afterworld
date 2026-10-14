package dev.afterworld.client.hud;

import dev.afterworld.Afterworld;
import dev.afterworld.world.gen.AwBiomes;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;

/**
 * Cinematic title cards in the display face. Places and regions fade in with a widening rule
 * beneath them and an amber line of description; bosses get a dark band across the screen and a
 * brass sting. After the arrival, the world fades up out of white and names the traveller.
 */
public final class TitleCards {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static Component title;
	private static Component subtitle;
	private static int style;
	private static int color;
	private static long start = -1;
	private static ResourceKey<Biome> lastBiome;
	private static final Map<ResourceKey<Biome>, Long> REGION_SHOWN = new HashMap<>();
	private static long dawnAt = -1;
	private static Component dawnTitle;
	private static Component dawnSubtitle;
	private static int dawnColor;

	private TitleCards() {
	}

	/** A card that came while a conversation was open, waiting to be shown when it ends. */
	private record Held(int style, Component title, Component subtitle, int color) {
	}

	private static final java.util.ArrayDeque<Held> HELD = new java.util.ArrayDeque<>();

	/** Shows the next held card, if any (called when no conversation is open). */
	static void release() {
		if (!HELD.isEmpty() && card() <= 0) {
			Held h = HELD.poll();
			show(h.style(), h.title(), h.subtitle(), h.color());
		}
	}

	public static void show(int newStyle, Component newTitle, Component newSubtitle, int newColor) {
		// Behind a conversation a card would play out unseen: it waits for the conversation to end.
		if (Voices.conversation()) {
			HELD.add(new Held(newStyle, newTitle, newSubtitle, newColor));
			return;
		}
		// A boss card always wins; a place card does not interrupt one already on screen.
		if (start >= 0 && style == AwPayloads.Title.BOSS && newStyle != AwPayloads.Title.BOSS && Util.getMillis() - start < 3000) {
			return;
		}
		title = newTitle;
		subtitle = newSubtitle;
		style = newStyle;
		color = newColor;
		start = Util.getMillis();
		Holder<net.minecraft.sounds.SoundEvent> sound = newStyle == AwPayloads.Title.BOSS ? AwSounds.UI_BOSS : AwSounds.UI_AREA;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), 1.0F, 0.7F));
	}

	/** How strongly a title card shows right now (0 when none), so other HUD pieces can make way. */
	public static float card() {
		if (start < 0 || title == null) {
			return 0;
		}
		long el = Util.getMillis() - start;
		float total = style == AwPayloads.Title.BOSS ? 4600 : 4000;
		if (el > total) {
			return 0;
		}
		return Math.max(0, Math.min(1, el < 700 ? el / 700.0F : el > total - 1000 ? (total - el) / 1000.0F : 1.0F));
	}

	/** The same, for a boss's card only. */
	public static float bossCard() {
		return style == AwPayloads.Title.BOSS ? card() : 0;
	}

	/** Once a second: entering one of Afterworld's major biomes announces it (at most every fifteen minutes). */
	public static void tickRegion(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.player.tickCount % 20 != 0) {
			return;
		}
		Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
		ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
		if (key == null || key.equals(lastBiome)) {
			return;
		}
		lastBiome = key;
		// Only the major lands get a card; seas, rivers, shores and caves pass quietly.
		if (!AwBiomes.MAJOR.contains(key)) {
			return;
		}
		long now = Util.getMillis();
		Long last = REGION_SHOWN.get(key);
		if (last != null && now - last < 15 * 60 * 1000) {
			return;
		}
		REGION_SHOWN.put(key, now);
		String path = key.identifier().getPath();
		show(AwPayloads.Title.REGION, Component.translatable("biome.afterworld." + path), Component.translatable("title.afterworld.region." + path), 0xE8DCC0);
	}

	/** The world fading up after the arrival, then the traveller's name across it. */
	public static void dawn(String name, Origin origin, Discipline discipline, Edict affinity) {
		dawnAt = Util.getMillis();
		Minecraft mc = Minecraft.getInstance();
		dawnTitle = Component.literal(name.isEmpty() ? mc.getUser().getName() : name);
		MutableComponent sub = origin.displayName().copy();
		if (discipline != Discipline.NONE) {
			sub.append(" \u00b7 ").append(discipline.displayName());
		}
		if (affinity != null) {
			sub.append(" \u00b7 ").append(affinity.displayName());
		}
		dawnSubtitle = sub;
		dawnColor = affinity != null ? affinity.color : 0xE8DCC0;
	}

	public static void reset() {
		start = -1;
		dawnAt = -1;
		lastBiome = null;
		REGION_SHOWN.clear();
		HELD.clear();
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
		if (dawnAt >= 0) {
			long el = Util.getMillis() - dawnAt;
			float a = 1 - el / 1800.0F;
			if (dawnTitle != null && el > 900) {
				show(AwPayloads.Title.PLACE, dawnTitle, dawnSubtitle, dawnColor);
				dawnTitle = null;
			}
			if (a <= 0) {
				dawnAt = -1;
			} else {
				g.fill(0, 0, HudScale.width(g), HudScale.height(g), ARGB.color((int) (255 * a * a), 0xFFF6E2));
			}
		}
		if (start < 0 || title == null || mc.gui.hud.isHidden()) {
			return;
		}
		long el = Util.getMillis() - start;
		boolean boss = style == AwPayloads.Title.BOSS;
		float total = boss ? 4600 : 4000;
		if (el > total) {
			start = -1;
			return;
		}
		float a = el < 700 ? el / 700.0F : el > total - 1000 ? (total - el) / 1000.0F : 1.0F;
		a = Math.max(0, Math.min(1, a));
		int alpha = (int) (a * 255);
		if (alpha < 6) {
			return;
		}
		Font font = mc.font;
		int w = HudScale.width(g);
		int h = HudScale.height(g);
		int cy = (int) (h * (boss ? 0.24F : 0.27F));
		float grow = Math.min(1.0F, el / 900.0F);
		grow = 1 - (1 - grow) * (1 - grow);
		if (boss) {
			int band = (int) (34 * Math.min(1.0F, el / 400.0F));
			g.fillGradient(0, cy - band, w, cy, ARGB.color((int) (a * 0), 0x000000), ARGB.color((int) (a * 170), 0x080404));
			g.fillGradient(0, cy, w, cy + band + 8, ARGB.color((int) (a * 170), 0x080404), ARGB.color(0, 0x000000));
		}
		Component big = title.copy().withStyle(s -> s.withFont(DISPLAY));
		float scale = crisp(Math.min(boss ? 2.0F : 1.75F, (w - 40) / (float) Math.max(1, font.width(big))));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(w / 2.0F, cy - 5 * scale);
		pose.scale(scale, scale);
		int titleColor = boss ? ARGB.color(alpha, 0xE8DCC0) : ARGB.color(alpha, color);
		int titleHalf = font.width(big) / 2;
		g.text(font, big, -titleHalf + 1, 1, ARGB.color(alpha / 3, 0x000000), false);
		g.text(font, big, -titleHalf, 0, titleColor, false);
		pose.popMatrix();
		int ruleY = cy + (int) (7 * scale);
		int len = (int) (grow * Math.min(140, w / 3));
		int lineColor = ARGB.color((int) (a * 170), boss ? 0xC9714A : 0xE8A33D);
		g.fill(w / 2 - len, ruleY, w / 2 - 5, ruleY + 1, lineColor);
		g.fill(w / 2 + 5, ruleY, w / 2 + len, ruleY + 1, lineColor);
		for (int dy = -2; dy <= 2; dy++) {
			int half = 2 - Math.abs(dy);
			g.fill(w / 2 - half, ruleY + dy, w / 2 + half + 1, ruleY + dy + 1, ARGB.color(alpha, boss ? 0xC9714A : 0xE8A33D));
		}
		if (subtitle != null) {
			float sa = Math.max(0, Math.min(1, (el - 350) / 600.0F)) * a;
			g.centeredText(font, subtitle, w / 2, ruleY + 7, ARGB.color((int) (sa * 230), boss ? 0xC9714A : 0xE8A33D));
		}
	}

	/** A scale that lands display-font pixels on whole screen pixels. */
	private static float crisp(float desired) {
		int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		return Math.max(1, Math.round(desired * gs)) / (float) gs;
	}
}
