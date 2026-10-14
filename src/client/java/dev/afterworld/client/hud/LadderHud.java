package dev.afterworld.client.hud;

import dev.afterworld.client.screen.Frames;
import dev.afterworld.client.screen.LadderScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The climb in progress, on the left while in the Warden's Ladder: the floor, the wave and how many
 * creatures are left (a bar that empties as they fall), or what to do next once the floor is won.
 */
public final class LadderHud {
	private static CompoundTag state = new CompoundTag();
	private static long changedAt;

	private LadderHud() {
	}

	public static void set(CompoundTag t) {
		if (t.getIntOr("wave", 0) != state.getIntOr("wave", 0) || !t.getStringOr("phase", "").equals(state.getStringOr("phase", ""))) {
			changedAt = Util.getMillis();
		}
		state = t;
	}

	public static void reset() {
		state = new CompoundTag();
	}

	public static boolean active() {
		return !state.isEmpty();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (state.isEmpty() || mc.player == null || mc.gui.hud.isHidden()
			|| mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
			return;
		}
		HudScale.begin(g);
		try {
			draw(g, mc.font, HudScale.height(g));
		} finally {
			HudScale.end(g);
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, int screenH) {
		int floor = state.getIntOr("floor", 1);
		int tier = Mth.clamp(state.getIntOr("tier", 0), 0, 4);
		int wave = state.getIntOr("wave", 0);
		int waves = state.getIntOr("waves", 0);
		int foes = state.getIntOr("foes", 0);
		int total = Math.max(1, state.getIntOr("total", 1));
		boolean echo = state.getBooleanOr("echo", false);
		String phase = state.getStringOr("phase", "arriving");
		int color = LadderScreen.TIER_COLORS[tier];
		Component title = Component.translatable("ladder.afterworld.hud.title").append(" · ").append(Component.translatable("ladder.afterworld.hud.floor", floor));
		Component line = switch (phase) {
			case "fighting" -> echo ? Component.translatable("ladder.afterworld.hud.echo")
				: Component.translatable("ladder.afterworld.hud.wave", wave, waves).append("  ").append(Component.translatable("ladder.afterworld.hud.foes", foes));
			case "breather" -> Component.translatable("ladder.afterworld.hud.breather");
			case "cleared" -> Component.translatable("ladder.afterworld.hud.cleared");
			case "resting" -> Component.translatable("ladder.afterworld.hud.resting");
			default -> Component.translatable("ladder.afterworld.hud.arriving");
		};
		int w = Math.max(150, Math.max(font.width(title), font.width(line)) + 16);
		// On the left, a little way down: notices and renown take the top corner.
		int x = 8;
		int y = Math.max(64, screenH / 4);
		int h = phase.equals("fighting") ? 36 : 30;
		Frames.panel(g, x, y, w, h, color);
		g.text(font, title, x + 7, y + 7, ARGB.color(255, color), true);
		float flash = Mth.clamp(1 - (Util.getMillis() - changedAt) / 900.0F, 0, 1);
		int ink = ARGB.srgbLerp(flash, 0xFFE8DCC0, 0xFFFFFFFF);
		g.text(font, line, x + 7, y + 18, phase.equals("cleared") ? 0xFFFFD27A : ink, true);
		if (phase.equals("fighting")) {
			Frames.bar(g, x + 7, y + 29, w - 14, 2, foes / (float) total, color);
		}
	}
}
