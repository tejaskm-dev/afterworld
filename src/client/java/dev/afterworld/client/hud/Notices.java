package dev.afterworld.client.hud;

import dev.afterworld.AwConfig;
import dev.afterworld.client.ClientState;
import dev.afterworld.net.AwPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** Quiet journal-style notices in the top-left: codex updates, rumours heard, pages written. */
public final class Notices {
	private static final int LIFE = 100;

	private Notices() {
	}

	public static int color(int kind) {
		return switch (kind) {
			case AwPayloads.Notice.CODEX -> 0x4FB3A9;
			case AwPayloads.Notice.CHRONICLE -> 0xE8A33D;
			case AwPayloads.Notice.RUMOUR -> 0xC9B98F;
			case AwPayloads.Notice.BOND -> 0x9FB7C9;
			case AwPayloads.Notice.EDICT -> 0xB79CE8;
			case AwPayloads.Notice.ADAPT -> 0xC4553B;
			default -> 0x9BD08A;
		};
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
		if (ClientState.notices.isEmpty() || mc.gui.hud.isHidden() || !AwConfig.get().showWorldNotices || mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		ClientState.notices.removeIf(n -> now - n.start() > LIFE || now < n.start());
		Font font = mc.font;
		int y = 6;
		for (ClientState.Notice n : ClientState.notices) {
			long age = now - n.start();
			float alpha = age < 8 ? age / 8F : age > LIFE - 20 ? (LIFE - age) / 20F : 1F;
			int a = Math.max(8, Math.round(alpha * 255));
			int w = Math.min(260, font.width(n.text()) + 14);
			g.fill(4, y, 4 + w, y + 14, ARGB.color(Math.round(a * 0.78F), 0x14181C));
			g.fill(4, y, 6, y + 14, ARGB.color(a, color(n.kind())));
			g.text(font, n.text(), 10, y + 3, ARGB.color(a, 0xE8DCC0), false);
			y += 17;
		}
	}
}
