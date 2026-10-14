package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.economy.Bank;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwGoods;
import dev.afterworld.registry.AwSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/** The Guild Ledger: what you carry, what's safe in the Guild's keeping, and the buttons between. */
public final class BankScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int INK = 0xFF2A2018;
	private AwPayloads.BankView view;
	private final long openedAt = Util.getMillis();

	public BankScreen(AwPayloads.BankView view) {
		super(Component.translatable("screen.afterworld.bank"));
		this.view = view;
	}

	public void update(AwPayloads.BankView v) {
		this.view = v;
	}

	@Override
	protected int prefWidth() {
		return 280;
	}

	@Override
	protected int prefHeight() {
		return 176;
	}

	@Override
	protected int minWidth() {
		return 220;
	}

	@Override
	protected int minHeight() {
		return 150;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 180.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 140), 0x05070A));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int pw = Math.min(prefWidth(), this.cw - 12);
		int ph = Math.min(prefHeight(), this.ch - 12);
		int px = (this.cw - pw) / 2;
		int py = (this.ch - ph) / 2;
		// The Guild's ledger, open on the traveller's page.
		Frames.ledger(g, px, py, pw, ph);
		int pad = 12;
		int inner = pw - pad * 2;
		// The title, scaled down if the panel is narrow.
		Component title = this.title.copy().withStyle(s -> s.withFont(DISPLAY));
		float ts = Math.min(1.5F, inner / (float) Math.max(1, this.font.width(title)));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(px + pw / 2.0F, py + 8);
		pose.scale(ts, ts);
		g.centeredText(this.font, title, 0, 0, 0xFF2B3550);
		pose.popMatrix();
		int y = py + 10 + (int) (10 * ts) + 4;
		y = wrap(g, Component.translatable("screen.afterworld.bank.sub"), px + pad, y, inner, 0xFF6A5A42, 2, 10) + 6;
		y = row(g, px + pad, y, inner, Component.translatable("screen.afterworld.bank.carried"), this.view.marks());
		y = row(g, px + pad, y, inner, Component.translatable("screen.afterworld.bank.banked"), this.view.banked());
		g.text(this.font, fit(Component.translatable("screen.afterworld.bank.interest", this.view.interest()), inner), px + pad, y + 2, 0xFF3E7A3A, false);
		// Two rows of three: deposit 10, 100, all; withdraw the same.
		int gap = 3;
		int bh = 16;
		int bw = (inner - gap * 2) / 3;
		int by = Math.max(y + 16, py + ph - pad - bh * 2 - gap);
		long[] amounts = {10, 100, -1};
		for (int i = 0; i < 3; i++) {
			int bx = px + pad + i * (bw + gap);
			Component dep = amounts[i] < 0 ? Component.translatable("screen.afterworld.bank.deposit_all") : Component.translatable("screen.afterworld.bank.deposit", amounts[i]);
			Component wd = amounts[i] < 0 ? Component.translatable("screen.afterworld.bank.withdraw_all") : Component.translatable("screen.afterworld.bank.withdraw", amounts[i]);
			btn(g, bx, by, bw, bh, dep, mx, my, Bank.DEPOSIT, amounts[i]);
			btn(g, bx, by + bh + gap, bw, bh, wd, mx, my, Bank.WITHDRAW, amounts[i]);
		}
	}

	/** A label on the left, the amount (with a crown) on the right. Returns the y below it. */
	private int row(GuiGraphicsExtractor g, int x, int y, int w, Component label, long v) {
		String s = String.format("%,d", v);
		int sw = this.font.width(s);
		g.text(this.font, fit(label, w - sw - 24), x, y + 4, INK, false);
		g.item(new ItemStack(AwGoods.GOLD_CROWN), x + w - sw - 18, y);
		g.text(this.font, s, x + w - sw, y + 4, 0xFF6A4A12, false);
		g.fill(x, y + 16, x + w, y + 17, 0x20000000);
		return y + 19;
	}

	private void btn(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int mx, int my, int action, long amount) {
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
		Frames.button(g, this.font, x, y, w, h, fit(label, w - 8), hot, true, 0x3E7A3A);
		this.hits.add(new Hit(x, y, w, h, () -> {
			ClientPlayNetworking.send(new AwPayloads.BankAction(action, amount));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.COIN.value(), 1.0F, 0.5F));
		}));
	}
}
