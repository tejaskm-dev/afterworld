package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.npc.Npc;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * A Warden's Board up close: the day's notices pinned to dark planks, each with what it asks,
 * where, and what it pays, and a seal to take it. Down the right side, the contracts already
 * carried, with a hand-in for gatherings and a way to give one up. On a narrow window the notices
 * take fewer columns and scroll.
 */
public final class BoardScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int INK = 0xFF2A2018;
	private static final int FADED = 0xFF6E6250;
	private static final int PAPER = 0xFFE6D9B8;
	private static final int AMBER = 0xFFE8A33D;
	private static final int[] KIND_COLOURS = {0xFFA0302A, 0xFF5B7FA0, 0xFF3F7A5A, 0xFF8A6A2A, 0xFF7A4A8A};

	private CompoundTag data;
	private final BlockPos board;
	private final long openedAt = Util.getMillis();
	private double scroll;
	/** The notices' area this frame, for the mouse wheel. */
	private int areaX;
	private int areaY;
	private int areaW;
	private int areaH;
	private int maxScroll;

	public BoardScreen(CompoundTag data) {
		super(Component.translatable("board.afterworld.title"));
		this.data = data;
		this.board = BlockPos.of(data.getLongOr("board", 0));
	}

	public void update(CompoundTag next) {
		this.data = next;
	}

	@Override
	protected int prefWidth() {
		return 420;
	}

	@Override
	protected int prefHeight() {
		return 250;
	}

	@Override
	public void onClose() {
		sound(AwSounds.UI_CLOSE, 1.0F, 1.0F);
		super.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		g.fill(0, 0, this.width, this.height, 0xA0080604);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int pw = Math.min(prefWidth(), this.cw - 20);
		int ph = Math.min(prefHeight(), this.ch - 20);
		int px = (this.cw - pw) / 2;
		int py = (this.ch - ph) / 2;
		float open = Mth.clamp((Util.getMillis() - this.openedAt) / 220.0F, 0, 1);
		py += (int) ((1 - open) * (1 - open) * 18);
		planks(g, px, py, pw, ph);
		// The header: a carved name plate.
		Component title = Component.translatable("board.afterworld.title").withStyle(s -> s.withFont(DISPLAY));
		Frames.plaque(g, this.font, fit(title, pw - 60), px + pw / 2, py + 3, pw - 40);
		int sideW = Mth.clamp(pw / 3, 96, 132);
		offers(g, px + 8, py + 28, pw - sideW - 20, ph - 36, mx, my);
		active(g, px + pw - sideW - 6, py + 28, sideW, ph - 36, mx, my);
	}

	/** Dark planks with a frame, so the notices have something to hang on. */
	private void planks(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF140C07);
		g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF5A3E26);
		g.fill(x, y, x + w, y + h, 0xFF3A2818);
		for (int py = y + 12; py < y + h; py += 14) {
			g.fill(x, py, x + w, py + 1, 0xFF2A1C10);
			g.fill(x, py + 1, x + w, py + 2, 0xFF46301E);
		}
		// Nail heads at the corners.
		for (int[] n : new int[][] {{x + 3, y + 3}, {x + w - 5, y + 3}, {x + 3, y + h - 5}, {x + w - 5, y + h - 5}}) {
			g.fill(n[0], n[1], n[0] + 2, n[1] + 2, 0xFF8A8A90);
		}
	}

	private void offers(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		this.areaX = x;
		this.areaY = y;
		this.areaW = w;
		this.areaH = h;
		List<CompoundTag> offers = list("offers");
		if (offers.isEmpty()) {
			this.maxScroll = 0;
			wrap(g, Component.translatable("board.afterworld.empty"), x + 6, y + 10, w - 12, 0xFFD9CAA4, 4, 10);
			return;
		}
		// As many columns as fit at a readable width (at most three, two for a short day).
		int gap = 6;
		int cols = Mth.clamp((w + gap) / (108 + gap), 1, offers.size() <= 4 ? 2 : 3);
		int rows = (offers.size() + cols - 1) / cols;
		int cw = (w - gap * (cols - 1)) / cols;
		int ch = Mth.clamp((h - gap * (rows - 1)) / rows, 92, 110);
		int content = rows * ch + (rows - 1) * gap;
		this.maxScroll = Math.max(0, content - h);
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
		boolean full = list("active").size() >= this.data.getIntOr("max", 3);
		boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
		g.enableScissor(x - 2, y - 2, x + w + 4, y + h + 4);
		for (int i = 0; i < offers.size(); i++) {
			CompoundTag o = offers.get(i);
			int cx = x + (i % cols) * (cw + gap);
			int cy = y + (i / cols) * (ch + gap) - (int) this.scroll;
			if (cy + ch < y - 4 || cy > y + h + 4) {
				continue;
			}
			boolean over = inside && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
			note(g, o, cx, cy, cw, ch, i, over, full, mx, my, y, y + h);
		}
		g.disableScissor();
		if (this.maxScroll > 0) {
			int bh = Math.max(12, h * h / content);
			int by = y + (int) ((h - bh) * this.scroll / this.maxScroll);
			g.fill(x + w + 2, y, x + w + 4, y + h, 0x40000000);
			g.fill(x + w + 2, by, x + w + 4, by + bh, 0xFFC07A1C);
		}
	}

	/** One notice: a pinned sheet with a coloured tab for its kind. */
	private void note(GuiGraphicsExtractor g, CompoundTag o, int x, int y, int w, int h, int index, boolean over, boolean full, int mx, int my, int clipTop, int clipBottom) {
		String kind = o.getStringOr("kind", "bounty");
		int kc = KIND_COLOURS[Math.max(0, kindIndex(kind))];
		boolean taken = o.getBooleanOr("taken", false);
		int lift = over && !taken ? 1 : 0;
		y -= lift;
		g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x60000000);
		g.fill(x, y, x + w, y + h, taken ? 0xFFB9AE92 : PAPER);
		g.fill(x, y, x + w, y + 1, 0xFFF3EAD0);
		g.fill(x, y + h - 1, x + w, y + h, 0xFFB9A67F);
		g.fill(x, y, x + 3, y + h, kc);
		// The tack.
		g.fill(x + w / 2 - 1, y + 2, x + w / 2 + 2, y + 5, 0xFFC07A1C);
		g.text(this.font, fit(Component.translatable("board.afterworld.kind." + kind).withStyle(s -> s.withFont(DISPLAY)), w - 12), x + 7, y + 6, kc, false);
		Component head = headline(o);
		int ty = wrap(g, head, x + 7, y + 18, w - 12, INK, 2, 10);
		int bodyLines = Math.max(0, (y + h - 30 - (ty + 2)) / 10);
		if (bodyLines > 0) {
			wrap(g, body(o), x + 7, ty + 2, w - 12, FADED, bodyLines, 10);
		}
		// What it pays, along the bottom; who is paying, beside the seal to take it.
		Component pay = Component.translatable("board.afterworld.pay", o.getLongOr("marks", 0), o.getIntOr("renown", 0));
		g.text(this.font, fit(pay, w - 12), x + 7, y + h - 27, 0xFF7A5A12, false);
		Component label = Component.translatable(full ? "board.afterworld.full" : "board.afterworld.take");
		int bw = Math.min(w - 14, this.font.width(label) + 10);
		int f = o.getIntOr("faction", -1);
		if (f >= 0) {
			Faction fac = Faction.byOrdinal(f);
			int room = w - 14 - (taken ? 0 : bw + 4);
			g.text(this.font, fit(fac.displayName(), room), x + 7, y + h - 15, ARGB.opaque(fac.color), false);
		}
		if (taken) {
			stamp(g, Component.translatable("board.afterworld.taken"), x + w - 8, y + h - 30, 0xFF7A2A20);
			return;
		}
		int bx = x + w - bw - 5;
		int by = y + h - 17;
		boolean hot = !full && mx >= bx && mx < bx + bw && my >= by && my < by + 13;
		g.fill(bx, by, bx + bw, by + 13, full ? 0xFF9A8E78 : hot ? 0xFFB8402E : 0xFFA0302A);
		g.fill(bx, by + 12, bx + bw, by + 13, 0xFF5A1A14);
		g.centeredText(this.font, fit(label, bw - 4), bx + bw / 2, by + 3, 0xFFF3EAD0);
		if (!full && by >= clipTop && by + 13 <= clipBottom) {
			this.hits.add(new Hit(bx, by, bw, 13, () -> send(AwPayloads.BoardAction.TAKE, index)));
		}
	}

	private void stamp(GuiGraphicsExtractor g, Component text, int right, int y, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		int w = this.font.width(text);
		pose.translate(right - w / 2.0F - 6, y + 4);
		pose.rotate((float) Math.toRadians(-8));
		g.fill(-w / 2 - 4, -6, w / 2 + 4, 7, ARGB.color(60, color));
		g.text(this.font, text, -w / 2, -3, ARGB.opaque(color), false);
		pose.popMatrix();
	}

	private void active(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		List<CompoundTag> active = list("active");
		g.fill(x, y, x + w, y + h, 0xC0201410);
		g.fill(x, y, x + w, y + 14, 0xFF2A1A10);
		g.text(this.font, fit(Component.translatable("board.afterworld.carried", active.size(), this.data.getIntOr("max", 3)), w - 10), x + 5, y + 3, AMBER, false);
		if (active.isEmpty()) {
			wrap(g, Component.translatable("board.afterworld.none_carried"), x + 5, y + 20, w - 10, 0xFF9A8E78, Math.max(1, (h - 24) / 10), 10);
			return;
		}
		int ry = y + 18;
		for (int i = 0; i < active.size(); i++) {
			CompoundTag c = active.get(i);
			String kind = c.getStringOr("kind", "bounty");
			int kc = KIND_COLOURS[Math.max(0, kindIndex(kind))];
			g.fill(x + 3, ry, x + 5, ry + 50, kc);
			g.text(this.font, fit(Component.translatable("board.afterworld.kind." + kind), w - 12), x + 8, ry, ARGB.opaque(kc | 0x404040), false);
			int ty = wrap(g, headline(c), x + 8, ry + 10, w - 12, 0xFFE8DCC0, 2, 10);
			Component status = status(c);
			ty = wrap(g, status, x + 8, ty + 1, w - 12, 0xFFB8AE98, 2, 10);
			int bx = x + 8;
			int by = Math.max(ty + 2, ry + 36);
			int room = w - 12;
			if ("gathering".equals(kind)) {
				boolean can = c.getIntOr("held", 0) >= c.getIntOr("count", 1);
				int used = button(g, Component.translatable("board.afterworld.hand_in"), bx, by, room / 2 - 2, can, mx, my, () -> send(AwPayloads.BoardAction.HAND_IN, idx(c))) + 4;
				bx += used;
				room -= used;
			}
			button(g, Component.translatable("board.afterworld.abandon"), bx, by, room, true, mx, my, () -> send(AwPayloads.BoardAction.ABANDON, idx(c)));
			ry = by + 18;
			if (ry > y + h - 40) {
				break;
			}
		}
	}

	private int idx(CompoundTag c) {
		List<CompoundTag> active = list("active");
		for (int i = 0; i < active.size(); i++) {
			if (active.get(i) == c) {
				return i;
			}
		}
		return -1;
	}

	private int button(GuiGraphicsExtractor g, Component label, int x, int y, int maxW, boolean enabled, int mx, int my, Runnable action) {
		int bw = Math.min(maxW, this.font.width(label) + 8);
		boolean hot = enabled && mx >= x && mx < x + bw && my >= y && my < y + 12;
		g.fill(x, y, x + bw, y + 12, !enabled ? 0xFF3A3430 : hot ? 0xFF6A4A2A : 0xFF4A3424);
		g.fill(x, y + 11, x + bw, y + 12, 0xFF1A120C);
		g.centeredText(this.font, fit(label, bw - 4), x + bw / 2, y + 2, enabled ? 0xFFE8DCC0 : 0xFF7A7068);
		if (enabled) {
			this.hits.add(new Hit(x, y, bw, 12, action));
		}
		return bw;
	}

	// ------------------------------------------------------------------ words

	private Component headline(CompoundTag o) {
		String kind = o.getStringOr("kind", "bounty");
		int count = o.getIntOr("count", 1);
		return switch (kind) {
			case "bounty" -> Component.literal(o.getStringOr("who", "?"));
			case "delivery" -> Component.translatable("board.afterworld.head.delivery", count, itemName(o.getStringOr("what", "")), keeperName(o.getStringOr("who", "")));
			case "gathering" -> Component.translatable("board.afterworld.head.gathering", count, itemName(o.getStringOr("what", "")));
			default -> Component.translatable("board.afterworld.head." + kind);
		};
	}

	private Component body(CompoundTag o) {
		String kind = o.getStringOr("kind", "bounty");
		String bearing = o.getStringOr("bearing", "");
		String distance = o.getStringOr("distance", "");
		return switch (kind) {
			case "bounty" -> Component.translatable("board.afterworld.desc.bounty", bearing, distance);
			case "delivery" -> Component.translatable("board.afterworld.desc.delivery", keeperName(o.getStringOr("who", "")), bearing, distance);
			case "survey" -> Component.translatable("board.afterworld.desc.survey", bearing, distance);
			case "gathering" -> Component.translatable("board.afterworld.desc.gathering");
			default -> Component.translatable("board.afterworld.desc.escort");
		};
	}

	private Component status(CompoundTag c) {
		String kind = c.getStringOr("kind", "bounty");
		String where = c.contains("bearing") ? c.getStringOr("bearing", "") + ", " + c.getStringOr("distance", "") : "";
		return switch (kind) {
			case "bounty" -> c.getBooleanOr("spawned", false) ? Component.translatable("board.afterworld.status.bounty_found")
				: Component.translatable("board.afterworld.status.go", where);
			case "survey" -> Component.translatable("board.afterworld.status.survey", c.getIntOr("have", 0), c.getIntOr("count", 3), where);
			case "gathering" -> Component.translatable("board.afterworld.status.gathering", Math.min(c.getIntOr("held", 0), c.getIntOr("count", 1)), c.getIntOr("count", 1));
			case "delivery" -> c.getIntOr("held", 0) >= c.getIntOr("count", 1) ? Component.translatable("board.afterworld.status.delivery_ready", where)
				: Component.translatable("board.afterworld.status.delivery_need", c.getIntOr("held", 0), c.getIntOr("count", 1));
			default -> Component.translatable("board.afterworld.status.go", where);
		};
	}

	private static Component itemName(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(id)).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(id));
	}

	private static Component keeperName(String id) {
		Npc n = Npc.byId(id);
		return Component.literal(n == null ? id : n.displayName);
	}

	private static int kindIndex(String kind) {
		return switch (kind) {
			case "bounty" -> 0;
			case "delivery" -> 1;
			case "survey" -> 2;
			case "gathering" -> 3;
			default -> 4;
		};
	}

	private List<CompoundTag> list(String key) {
		List<CompoundTag> out = new ArrayList<>();
		for (Tag t : this.data.getListOrEmpty(key)) {
			if (t instanceof CompoundTag c) {
				out.add(c);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ input

	private void send(int action, int index) {
		if (index < 0) {
			return;
		}
		ClientPlayNetworking.send(new AwPayloads.BoardAction(action, index, this.board));
		sound(action == AwPayloads.BoardAction.TAKE ? AwSounds.UI_CHOOSE : AwSounds.UI_SELECT, 1.0F, 1.0F);
	}

	@Override
	protected boolean scrolled(double x, double y, double dy) {
		if (this.maxScroll > 0 && x >= this.areaX && x < this.areaX + this.areaW && y >= this.areaY && y < this.areaY + this.areaH) {
			this.scroll -= dy * 24;
			return true;
		}
		return false;
	}
}
