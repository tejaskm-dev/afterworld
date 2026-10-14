package dev.afterworld.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.economy.Merchant;
import dev.afterworld.economy.Shop;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwGoods;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A merchant's counter. Across the top: the merchant (their painted face, name, what they think of
 * you) and your purse. Left: tabs for buying, selling and buying back, category chips (wrapped to
 * as many rows as they need), and the wares as rows with prices and little arrows for what's dear
 * or cheap here. Right: the chosen item in detail, how many, the total, and the deal. Haggling is
 * one roll a day, with one of three tactics. Everything is laid out to the room it has, from the
 * smallest window the game allows to the largest.
 */
public final class ShopScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int INK = 0xFF2A2018;
	private static final int PAPER = 0xFFE8DCC0;
	private static final int ROW = 20;
	private static final int HEADER = 44;

	private AwPayloads.ShopView view;
	private int tab;
	private int category = -1;
	private int selected = -1;
	private int qty = 1;
	private double scroll;
	private boolean haggling;
	private final long openedAt = Util.getMillis();
	private long messageAt;
	/** The list's area this frame, for the mouse wheel. */
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	public ShopScreen(AwPayloads.ShopView view) {
		super(Component.literal(view.name()));
		this.view = view;
	}

	public int entity() {
		return this.view.entity();
	}

	public void update(AwPayloads.ShopView next) {
		this.view = next;
		if (!next.message().getString().isEmpty()) {
			this.messageAt = Util.getMillis();
		}
		List<?> rows = rows();
		if (this.selected >= rows.size()) {
			this.selected = rows.isEmpty() ? -1 : rows.size() - 1;
		}
		this.qty = Math.max(1, Math.min(this.qty, maxQty()));
	}

	@Override
	protected int prefWidth() {
		return 440;
	}

	@Override
	protected int prefHeight() {
		return 262;
	}

	@Override
	public void onClose() {
		ClientPlayNetworking.send(new AwPayloads.ShopAction(Shop.CLOSE, 0, 0, 0));
		super.onClose();
	}

	// ------------------------------------------------------------------ data

	/** The rows on the current tab: buy offers (filtered), sellable stacks, or buyback. */
	private List<?> rows() {
		if (this.tab == 0) {
			List<Integer> idx = new ArrayList<>();
			for (int i = 0; i < this.view.buy().size(); i++) {
				if (this.category < 0 || this.view.buy().get(i).category() == this.category) {
					idx.add(i);
				}
			}
			return idx;
		}
		return this.tab == 1 ? this.view.sell() : this.view.buyback();
	}

	private ItemStack stackOf(int row) {
		List<?> rows = rows();
		if (row < 0 || row >= rows.size()) {
			return ItemStack.EMPTY;
		}
		if (this.tab == 0) {
			return this.view.buy().get((Integer) rows.get(row)).stack();
		}
		return ((AwPayloads.ShopView.Sell) rows.get(row)).stack();
	}

	private int priceOf(int row) {
		List<?> rows = rows();
		if (row < 0 || row >= rows.size()) {
			return 0;
		}
		if (this.tab == 0) {
			return this.view.buy().get((Integer) rows.get(row)).price();
		}
		return ((AwPayloads.ShopView.Sell) rows.get(row)).price();
	}

	private int maxQty() {
		if (this.selected < 0) {
			return 1;
		}
		if (this.tab == 0) {
			AwPayloads.ShopView.Buy b = this.view.buy().get((Integer) rows().get(this.selected));
			int byMoney = (int) Math.min(64, this.view.wallet() / Math.max(1, b.price()));
			int byStock = b.stock() < 0 ? 64 : b.stock();
			return Math.max(1, Math.min(byMoney, byStock));
		}
		if (this.tab == 1) {
			return Math.max(1, stackOf(this.selected).getCount());
		}
		return 1;
	}

	// ------------------------------------------------------------------ drawing

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
		// The stall: a stone-framed counter under a striped awning, a page of wares and a card set into it.
		Frames.panel(g, px, py, pw, ph, ARGB.opaque(this.view.color()));
		Frames.awning(g, px - Frames.BAND - 3, py - Frames.BAND - 11, pw + Frames.BAND * 2 + 6);
		header(g, px, py, pw);
		// The detail card keeps at least 132 wide; the list takes the rest.
		int detailW = Math.max(132, (int) (pw * 0.42F));
		int lw = pw - detailW;
		int top = py + HEADER + 2;
		int bottom = py + ph - 18;
		int listTop = tabs(g, px + 4, top, lw - 4, mx, my);
		g.fill(px + 4, top + 12, px + lw, bottom, PAPER);
		listTop = chips(g, px + 4, listTop, lw - 4, mx, my);
		list(g, px + 4, listTop, lw - 4, bottom - listTop - 2, mx, my);
		g.fill(px + lw + 4, top, px + pw - 4, bottom, 0xFFD9CAA4);
		detail(g, px + lw + 4, top, detailW - 8, bottom - top, mx, my);
		// The message line (or a hint), cut to the panel.
		Component msg = this.view.message();
		if (!msg.getString().isEmpty()) {
			float age = (Util.getMillis() - this.messageAt) / 1000.0F;
			int alpha = (int) (255 * Mth.clamp(4.0F - age, 0.25F, 1.0F));
			int color = msg.getStyle().getColor() == null ? 0xE8DCC0 : msg.getStyle().getColor().getValue();
			Component line = fit(msg, pw - 12);
			g.text(this.font, line, px + 6, py + ph - 13, ARGB.color(alpha, ARGB.opaque(color)), false);
			if (line != msg && my >= py + ph - 15 && my < py + ph - 2 && mx >= px && mx < px + pw) {
				tip(g, msg, mx, my);
			}
		} else {
			g.text(this.font, fit(Component.translatable("shop.afterworld.hint"), pw - 12), px + 6, py + ph - 13, 0xFF9A8E78, false);
		}
	}

	private void header(GuiGraphicsExtractor g, int px, int py, int pw) {
		// The merchant's face, painted from their skin: the face and the hat layer over it.
		Identifier skin = Afterworld.id("textures/entity/npc/" + this.view.merchant() + ".png");
		Frames.inset(g, px + 4, py + 4, 38, 38);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, px + 7, py + 7, 8, 8, 32, 32, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, px + 7, py + 7, 40, 8, 32, 32, 8, 8, 64, 64);
		// The purse (and today's haggle) on the right; whatever room is left goes to the name.
		String purse = String.format("%,d", this.view.wallet());
		int purseW = this.font.width(purse) + 20;
		Component deal = null;
		if (this.view.haggle() != 999) {
			deal = Component.translatable(this.view.haggle() < 0 ? "shop.afterworld.deal_good" : "shop.afterworld.deal_bad", Math.abs(this.view.haggle()));
		}
		int right = px + pw - 6;
		int wx = right - purseW;
		g.item(new ItemStack(AwGoods.GOLD_CROWN), wx, py + 6);
		g.text(this.font, purse, wx + 20, py + 10, 0xFFFFD27A, false);
		int textX = px + 48;
		int nameRoom = wx - 8 - textX;
		if (deal != null) {
			int dealRoom = Math.max(60, (pw - 48) / 2);
			Component d = fit(deal, dealRoom);
			g.text(this.font, d, right - this.font.width(d), py + 26, this.view.haggle() < 0 ? 0xFF9BD08A : 0xFFC86B5A, false);
		}
		int lowerRoom = (deal == null ? right : right - Math.min(this.font.width(deal), Math.max(60, (pw - 48) / 2)) - 8) - textX;
		Component name = Component.literal(this.view.name()).withStyle(s -> s.withFont(DISPLAY));
		float ns = Mth.clamp(nameRoom / (float) Math.max(1, this.font.width(name)), 1.0F, 1.4F);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(textX, py + 7);
		pose.scale(ns, ns);
		g.text(this.font, fit(name, (int) (nameRoom / ns)), 0, 0, ARGB.opaque(this.view.color()), false);
		pose.popMatrix();
		g.text(this.font, fit(Component.literal(this.view.role()).withStyle(s -> s.withItalic(true)), lowerRoom), textX, py + 21, 0xFFB8AE98, false);
		Component rep = Component.translatable("shop.afterworld.rep." + (this.view.repTier() + 2));
		g.text(this.font, fit(rep, lowerRoom), textX, py + 32, this.view.repTier() > 0 ? 0xFF9BD08A : this.view.repTier() < 0 ? 0xFFC86B5A : 0xFF9A8E78, false);
	}

	/** The three tabs along the top of the page. Returns the y below them. */
	private int tabs(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		String[] names = {"shop.afterworld.tab.buy", "shop.afterworld.tab.sell", "shop.afterworld.tab.buyback"};
		int natural = 0;
		for (String n : names) {
			natural += this.font.width(Component.translatable(n)) + 12 + 2;
		}
		// Natural widths if they fit, otherwise three equal tabs with the labels cut to fit.
		boolean even = natural > w;
		int tx = x + 2;
		for (int i = 0; i < 3; i++) {
			Component label = Component.translatable(names[i]);
			int tw = even ? (w - 4 - 4) / 3 : this.font.width(label) + 12;
			boolean on = this.tab == i;
			Frames.tab(g, tx, y - (on ? 1 : 0), tw, 13 + (on ? 1 : 0), on);
			g.text(this.font, fit(label, tw - 4), tx + (tw - this.font.width(fit(label, tw - 4))) / 2, y + 3, on ? Frames.INK : Frames.INK_DIM, false);
			int ti = i;
			this.hits.add(new Hit(tx, y, tw, 12, () -> {
				if (this.tab != ti) {
					this.tab = ti;
					this.selected = -1;
					this.scroll = 0;
					this.qty = 1;
					this.haggling = false;
					sound(AwSounds.UI_TAB, 1.0F, 0.5F);
				}
			}));
			tx += tw + 2;
		}
		return y + 14;
	}

	/** Category chips on the buy tab, wrapped onto as many rows as they need. Returns the y below them. */
	private int chips(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		if (this.tab != 0) {
			return y + 2;
		}
		boolean[] present = new boolean[Merchant.Category.values().length];
		int kinds = 0;
		for (AwPayloads.ShopView.Buy b : this.view.buy()) {
			if (!present[b.category()]) {
				present[b.category()] = true;
				kinds++;
			}
		}
		if (kinds < 2) {
			return y + 2;
		}
		int cx = x + 3;
		int cy = y + 1;
		for (int c = -1; c < Merchant.Category.values().length; c++) {
			if (c >= 0 && !present[c]) {
				continue;
			}
			Component label = Component.translatable(c < 0 ? "shop.afterworld.cat.all" : "shop.afterworld.cat." + Merchant.Category.values()[c].name().toLowerCase(java.util.Locale.ROOT));
			int cwid = Math.min(w - 6, this.font.width(label) + 8);
			if (cx + cwid > x + w - 3 && cx > x + 3) {
				cx = x + 3;
				cy += 12;
			}
			boolean on = this.category == c;
			boolean hot = mx >= cx && mx < cx + cwid && my >= cy && my < cy + 10;
			g.fill(cx, cy, cx + cwid, cy + 10, on ? 0xFFE8A33D : hot ? 0xFFC8B590 : 0xFFD2C29C);
			g.centeredText(this.font, fit(label, cwid - 4), cx + cwid / 2, cy + 1, on ? INK : 0xFF5A4A36);
			int cc = c;
			this.hits.add(new Hit(cx, cy, cwid, 10, () -> {
				this.category = cc;
				this.selected = -1;
				this.scroll = 0;
				sound(AwSounds.UI_SELECT, 1.6F, 0.3F);
			}));
			cx += cwid + 2;
		}
		g.fill(x + 3, cy + 12, x + w - 3, cy + 13, 0x30000000);
		return cy + 15;
	}

	private void list(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		this.listX = x;
		this.listY = y;
		this.listW = w;
		this.listH = h;
		List<?> rows = rows();
		int visible = Math.max(1, h / ROW);
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, rows.size() - visible));
		int first = (int) this.scroll;
		if (rows.isEmpty()) {
			wrapCentred(g, Component.translatable(this.tab == 1 ? "shop.afterworld.nothing_to_sell" : this.tab == 2 ? "shop.afterworld.no_buyback" : "shop.afterworld.empty"),
				x + w / 2, y + 16, w - 16, 0xFF7A6A52, 3, 10);
		}
		boolean bar = rows.size() > visible;
		int rw = bar ? w - 4 : w;
		for (int r = 0; r < visible && first + r < rows.size(); r++) {
			int row = first + r;
			int ry = y + r * ROW;
			ItemStack stack = stackOf(row);
			boolean hot = mx >= x && mx < x + rw && my >= ry && my < ry + ROW;
			boolean sel = row == this.selected;
			if (sel) {
				g.fill(x + 1, ry, x + rw - 1, ry + ROW - 1, 0xFFE9C98A);
			} else if (hot) {
				g.fill(x + 1, ry, x + rw - 1, ry + ROW - 1, 0xFFF2E6CA);
			} else if (r % 2 == 1) {
				g.fill(x + 1, ry, x + rw - 1, ry + ROW - 1, 0x10000000);
			}
			g.item(stack, x + 3, ry + 2);
			g.itemDecorations(this.font, stack, x + 3, ry + 2);
			int locked = 0;
			int stock = -1;
			int demand;
			if (this.tab == 0) {
				AwPayloads.ShopView.Buy b = this.view.buy().get((Integer) rows.get(row));
				locked = b.locked();
				stock = b.stock();
				demand = b.demand();
			} else {
				demand = ((AwPayloads.ShopView.Sell) rows.get(row)).demand();
			}
			// The price on the right (a coin, and an arrow for dear or cheap), the name in what's left.
			String price = String.format("%,d", priceOf(row)) + (this.tab == 1 ? " ea" : "");
			int prw = this.font.width(price);
			g.text(this.font, price, x + rw - prw - 14, ry + 6, 0xFF6A4A12, false);
			coin(g, x + rw - 11, ry + 6);
			int priceLeft = x + rw - prw - 14;
			if (demand != 0) {
				// Green up: dear here (they pay well / it costs more). Grey down: cheap here.
				int ax = priceLeft - 8;
				for (int k = 0; k < 3; k++) {
					int yy = demand > 0 ? ry + 9 - k : ry + 7 + k;
					g.fill(ax - k, yy, ax + k + 1, yy + 1, demand > 0 ? 0xFF3E8A3A : 0xFF8A7A62);
				}
				priceLeft = ax - 4;
			}
			int textW = priceLeft - (x + 23) - 3;
			g.text(this.font, fit(stack.getHoverName(), textW), x + 23, ry + 3, locked > 0 ? 0xFF8A7A62 : INK, false);
			if (locked > 0) {
				g.text(this.font, fit(Component.translatable("shop.afterworld.needs_renown", locked), textW), x + 23, ry + 11, 0xFFA8432E, false);
			} else if (stock >= 0) {
				g.text(this.font, fit(Component.translatable(stock == 0 ? "shop.afterworld.sold_out_short" : "shop.afterworld.stock", stock), textW), x + 23, ry + 11,
					stock == 0 ? 0xFFA8432E : 0xFF7A6A52, false);
			}
			if (hot) {
				int rr = row;
				this.hits.add(new Hit(x, ry, rw, ROW, () -> {
					this.selected = rr;
					this.qty = 1;
					this.haggling = false;
					sound(AwSounds.UI_SELECT, 1.2F, 0.3F);
				}));
				tip(g, stack, mx, my);
			}
		}
		// Scroll bar.
		if (bar) {
			int bh = Math.max(10, h * visible / rows.size());
			int by = y + (int) ((h - bh) * this.scroll / Math.max(1, rows.size() - visible));
			g.fill(x + w - 3, y, x + w - 1, y + h, 0x30000000);
			g.fill(x + w - 3, by, x + w - 1, by + bh, 0xFF8A6A42);
		}
	}

	private void coin(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x + 1, y, x + 6, y + 7, 0xFF8A5A12);
		g.fill(x, y + 1, x + 7, y + 6, 0xFF8A5A12);
		g.fill(x + 1, y + 1, x + 6, y + 6, 0xFFE8B33D);
		g.fill(x + 2, y + 2, x + 3, y + 4, 0xFFFFF0B0);
	}

	private void detail(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		ItemStack stack = stackOf(this.selected);
		if (stack.isEmpty()) {
			Component pick = Component.translatable(this.tab == 1 ? "shop.afterworld.pick_sell" : "shop.afterworld.pick");
			int n = Math.min(4, lines(pick, w - 16));
			wrapCentred(g, pick, x + w / 2, y + h / 2 - n * 5, w - 16, 0xFF6A5A42, 4, 10);
			return;
		}
		// The deal block is built from the bottom up; the item's details get what's above it.
		int tacticsW = 0;
		for (String t : TACTICS) {
			tacticsW = Math.max(tacticsW, this.font.width(Component.translatable(t)) + 8);
		}
		boolean stackTactics = tacticsW * 3 + 4 > w - 12;
		int haggleH = this.tab != 0 ? 0 : this.haggling && stackTactics ? 3 * 14 : 14;
		int dealH = (this.tab != 2 ? 14 : 0) + 12 + 18 + haggleH;
		int by = y + h - dealH - 2;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 6, y + 6);
		pose.scale(2.0F, 2.0F);
		g.item(stack, 0, 0);
		pose.popMatrix();
		int ty = wrap(g, stack.getHoverName(), x + 44, y + 8, w - 48, INK, 3, 10);
		ty = Math.max(ty, y + 42);
		List<Component> tip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL);
		int room = Math.max(0, (by - 8 - ty) / 10);
		int shown = 0;
		boolean cut = false;
		for (int i = 1; i < tip.size() && !cut; i++) {
			for (FormattedCharSequence l : this.font.split(tip.get(i), w - 12)) {
				if (shown >= room) {
					cut = true;
					break;
				}
				g.text(this.font, l, x + 6, ty, 0xFF4A3A2A, false);
				ty += 10;
				shown++;
			}
		}
		if (cut && mx >= x && mx < x + w && my >= y && my < by - 4) {
			tip(g, stack, mx, my);
		}
		// The deal.
		g.fill(x + 4, by - 4, x + w - 4, by - 3, 0x40000000);
		int unit = priceOf(this.selected);
		int max = maxQty();
		int ry = by;
		if (this.tab != 2) {
			qtyRow(g, x + 6, ry, w - 12, max, mx, my);
			ry += 14;
		}
		long total = (long) unit * (this.tab == 2 ? 1 : this.qty);
		Component tot = Component.translatable(this.tab == 1 ? "shop.afterworld.you_get" : "shop.afterworld.you_pay", String.format("%,d", total));
		g.text(this.font, fit(tot, w - 12), x + 6, ry + 2, 0xFF6A4A12, false);
		ry += 12;
		boolean canAfford = this.tab == 1 || this.view.wallet() >= total;
		boolean locked = this.tab == 0 && this.view.buy().get((Integer) rows().get(this.selected)).locked() > 0;
		Component act = Component.translatable(this.tab == 0 ? "shop.afterworld.buy" : this.tab == 1 ? "shop.afterworld.sell" : "shop.afterworld.buy_back");
		button(g, x + 6, ry, w - 12, 16, act, canAfford && !locked, mx, my, this::deal);
		ry += 18;
		if (this.tab == 0) {
			haggle(g, x + 6, ry, w - 12, stackTactics, mx, my);
		}
	}

	/** − count + (×8) Max, with a "Qty" label in front when there's room. */
	private void qtyRow(GuiGraphicsExtractor g, int x, int y, int w, int max, int mx, int my) {
		Component maxLabel = Component.translatable("shop.afterworld.max");
		Component qtyLabel = Component.translatable("shop.afterworld.qty");
		int maxW = Math.max(24, this.font.width(maxLabel) + 8);
		int core = 14 + 26 + 14 + 4 + maxW;
		boolean eight = core + 26 <= w;
		int labelW = this.font.width(qtyLabel) + 6;
		boolean label = core + (eight ? 26 : 0) + labelW <= w;
		int qx = x;
		if (label) {
			g.text(this.font, qtyLabel, qx, y + 2, 0xFF4A3A2A, false);
			qx += labelW;
		}
		button(g, qx, y, 14, 12, Component.literal("−"), this.qty > 1, mx, my, () -> this.qty = Math.max(1, this.qty - 1));
		g.centeredText(this.font, String.valueOf(this.qty), qx + 14 + 13, y + 2, INK);
		button(g, qx + 40, y, 14, 12, Component.literal("+"), this.qty < max, mx, my, () -> this.qty = Math.min(max, this.qty + 1));
		int nx = qx + 58;
		if (eight) {
			button(g, nx, y, 22, 12, Component.literal("×8"), max > 1, mx, my, () -> this.qty = Math.min(max, 8));
			nx += 26;
		}
		button(g, nx, y, maxW, 12, maxLabel, max > 1, mx, my, () -> this.qty = max);
	}

	private static final String[] TACTICS = {"shop.afterworld.tactic.flatter", "shop.afterworld.tactic.bargain", "shop.afterworld.tactic.rival"};

	private void haggle(GuiGraphicsExtractor g, int x, int y, int w, boolean stacked, int mx, int my) {
		boolean done = this.view.haggle() != 999;
		if (!this.haggling) {
			button(g, x, y, w, 12, Component.translatable(done ? "shop.afterworld.haggled" : "shop.afterworld.haggle"), !done, mx, my, () -> this.haggling = true);
			return;
		}
		int bw = stacked ? w : (w - 4) / 3;
		for (int i = 0; i < 3; i++) {
			int ti = i;
			int bx = stacked ? x : x + i * (bw + 2);
			int byy = stacked ? y + i * 14 : y;
			button(g, bx, byy, bw, 12, Component.translatable(TACTICS[i]), true, mx, my, () -> {
				ClientPlayNetworking.send(new AwPayloads.ShopAction(Shop.HAGGLE, 0, 0, ti));
				this.haggling = false;
			});
		}
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, boolean enabled, int mx, int my, Runnable action) {
		boolean hot = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
		Frames.button(g, this.font, x, y, w, h, fit(label, w - 6), hot, enabled, 0xE8A33D);
		if (enabled) {
			this.hits.add(new Hit(x, y, w, h, () -> {
				action.run();
				sound(AwSounds.UI_SELECT, 1.0F, 0.3F);
			}));
		}
	}

	private void deal() {
		if (this.selected < 0) {
			return;
		}
		List<?> rows = rows();
		switch (this.tab) {
			case 0 -> ClientPlayNetworking.send(new AwPayloads.ShopAction(Shop.BUY, (Integer) rows.get(this.selected), this.qty, 0));
			case 1 -> ClientPlayNetworking.send(new AwPayloads.ShopAction(Shop.SELL, ((AwPayloads.ShopView.Sell) rows.get(this.selected)).slot(), this.qty, 0));
			default -> ClientPlayNetworking.send(new AwPayloads.ShopAction(Shop.BUYBACK, ((AwPayloads.ShopView.Sell) rows.get(this.selected)).slot(), 1, 0));
		}
		this.qty = 1;
	}

	// ------------------------------------------------------------------ input

	@Override
	protected boolean scrolled(double x, double y, double dy) {
		if (x >= this.listX && x < this.listX + this.listW && y >= this.listY && y < this.listY + this.listH) {
			this.scroll -= dy;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN && this.selected >= 0) {
			deal();
			return true;
		}
		return super.keyPressed(event);
	}

	/** For tests. */
	public AwPayloads.ShopView viewForTest() {
		return this.view;
	}

	/** For tests: pick a row on the current tab, and open the haggling tactics. */
	public void selectForTest(int row, boolean haggle) {
		this.selected = Math.min(row, rows().size() - 1);
		this.haggling = haggle;
	}
}
