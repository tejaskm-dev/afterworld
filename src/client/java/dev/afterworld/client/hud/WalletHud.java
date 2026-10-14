package dev.afterworld.client.hud;

import dev.afterworld.registry.AwGoods;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The purse on screen: shown for a few seconds whenever Marks change (with the change floating
 * beside it), and always while the inventory is open.
 */
public final class WalletHud {
	public static long marks;
	public static long banked;
	private static int lastDelta;
	private static long changedAt = -100000;

	private WalletHud() {
	}

	public static void apply(long m, long b, int delta) {
		marks = m;
		banked = b;
		if (delta != 0) {
			lastDelta = delta;
			changedAt = Util.getMillis();
		}
	}

	public static void reset() {
		marks = 0;
		banked = 0;
		changedAt = -100000;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		long age = Util.getMillis() - changedAt;
		boolean inventory = mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
		if (age > 4500 && !inventory) {
			return;
		}
		float a = inventory ? 1.0F : age < 4000 ? 1.0F : 1.0F - (age - 4000) / 500.0F;
		int alpha = (int) (255 * a);
		String s = String.format("%,d", marks);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int x = inventory ? w - mc.font.width(s) - 26 : w / 2 + 100;
		int y = inventory ? 6 : h - 38;
		g.fill(x - 3, y - 2, x + mc.font.width(s) + 22, y + 15, ARGB.color((int) (alpha * 0.7F), 0x14100C));
		g.item(new ItemStack(AwGoods.GOLD_CROWN), x - 1, y - 1);
		g.text(mc.font, s, x + 17, y + 3, ARGB.color(alpha, 0xFFD27A), false);
		if (age < 2500 && lastDelta != 0) {
			float rise = age / 2500.0F;
			String d = (lastDelta > 0 ? "+" : "") + String.format("%,d", lastDelta);
			int da = (int) (255 * (1 - rise));
			g.text(mc.font, d, x + 17 + mc.font.width(s) + 6, (int) (y + 3 - rise * 10), ARGB.color(da, lastDelta > 0 ? 0x9BD08A : 0xC86B5A), true);
		}
	}
}
