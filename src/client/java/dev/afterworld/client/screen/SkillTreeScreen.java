package dev.afterworld.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Skills;
import dev.afterworld.player.Skills.Kind;
import dev.afterworld.player.Skills.Node;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The skill tree, drawn as a tree: the root at the foot, the three limbs climbing from it into
 * their branches, small nodes along them, named skills at the forks and keystones at the tips. It
 * is a map to be looked around: drag to move it, scroll to zoom (about the pointer), R to bring the
 * root back. What is learned glows in its limb's colour, with the paths between; what can be
 * learned now pulses; the rest waits in the dark. Click a pulsing node to learn it.
 */
public final class SkillTreeScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int TEXT = 0xFFE8DCC0;
	private static final int DIM = 0xFF8E8676;
	private static final int TEAL = 0xFF7FE3D2;
	private static final int GOLD = 0xFFE8C873;
	private static final int RED = 0xFFC86B5A;
	/** Pixels per tree unit at zoom 1. */
	private static final float UNIT = 34.0F;
	/** The view, kept between visits: zoom, and the tree point at the canvas centre. */
	private static float zoom = 1.0F;
	private static float viewX = 0.0F;
	private static float viewY = 3.0F;
	private final CharacterScreen parent;
	private final long openedAt = Util.getMillis();
	private final Map<String, ItemStack> icons = new HashMap<>();
	// The canvas the tree is drawn in, in canvas pixels (set each frame).
	private int tx;
	private int ty;
	private int tw;
	private int th;
	private boolean pressed;
	private boolean dragged;
	private double pressX;
	private double pressY;
	private String hover;

	public SkillTreeScreen(CharacterScreen parent) {
		super(Component.translatable("skilltree.afterworld.title"));
		this.parent = parent;
		for (Node n : Skills.TREE) {
			this.icons.put(n.id(), new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(n.icon()))));
		}
	}

	@Override
	protected int prefWidth() {
		return 620;
	}

	@Override
	protected int prefHeight() {
		return 380;
	}

	@Override
	protected float widthShare() {
		return 0.96F;
	}

	@Override
	protected float heightShare() {
		return 0.94F;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 200.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 170), 0x05060A));
	}

	private Set<String> owned() {
		Set<String> out = new HashSet<>();
		out.add(Skills.ROOT);
		ListTag list = CharacterScreen.data().getListOrEmpty("skills");
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getStringOr(i, ""));
		}
		return out;
	}

	private static int limbColor(String branch) {
		return switch (branch) {
			case "blade" -> 0xFFE07A5A;
			case "wayfarer" -> 0xFF7FE3B0;
			case "arcanum" -> 0xFFB08AFF;
			case "bridge" -> 0xFFE8C873;
			default -> 0xFFF2E6C4;
		};
	}

	private static int size(Kind kind) {
		return switch (kind) {
			case ROOT -> 30;
			case KEYSTONE -> 28;
			case NOTABLE -> 22;
			case MINOR -> 16;
		};
	}

	/** A tree point on the canvas. */
	private float sx(float x) {
		return this.tx + this.tw / 2.0F + (x - viewX) * UNIT * zoom;
	}

	private float sy(float y) {
		return this.ty + this.th / 2.0F - (y - viewY) * UNIT * zoom;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		CompoundTag data = CharacterScreen.data();
		Set<String> owned = owned();
		int insight = data.getIntOr("insight", 0);
		int w = Math.min(prefWidth(), this.cw - 8);
		int h = Math.min(prefHeight(), this.ch - 8);
		int x = (this.cw - w) / 2;
		int y = (this.ch - h) / 2;
		Frames.panel(g, x, y, w, h, TEAL & 0xFFFFFF);

		// The header: back to the character, the title, and what there is to spend.
		Component back = Component.literal("◀ ").append(Component.translatable("skilltree.afterworld.back"));
		int bw = this.font.width(back) + 14;
		boolean backHot = mx >= x + 8 && mx < x + 8 + bw && my >= y + 8 && my < y + 26;
		Frames.button(g, this.font, x + 8, y + 8, bw, 18, back, backHot, true, TEAL & 0xFFFFFF);
		this.hits.add(new Hit(x + 8, y + 8, bw, 18, this::onClose));
		Component title = this.title.copy().withStyle(s -> s.withFont(DISPLAY));
		g.centeredText(this.font, title, this.cw / 2, y + 10, TEXT);
		int learned = owned.size() - 1;
		Component ins = Component.translatable("skilltree.afterworld.insight", insight);
		Component count = Component.translatable("skilltree.afterworld.learned_count", learned, Skills.TREE.size() - 1);
		g.text(this.font, ins, x + w - 10 - this.font.width(ins), y + 9, insight > 0 ? TEAL : DIM, false);
		g.text(this.font, count, x + w - 10 - this.font.width(count), y + 20, DIM, false);

		// The canvas.
		this.tx = x + 6;
		this.ty = y + 32;
		this.tw = w - 12;
		this.th = h - 32 - 18;
		Frames.inset(g, this.tx, this.ty, this.tw, this.th);
		g.enableScissor(this.tx + 2, this.ty + 2, this.tx + this.tw - 2, this.ty + this.th - 2);
		long now = Util.getMillis();
		stars(g, now);
		// The paths: lit where both ends are learned, dim where one is (the way on), dark otherwise.
		for (Node n : Skills.TREE) {
			for (String other : Skills.LINKS.getOrDefault(n.id(), List.of())) {
				if (n.id().compareTo(other) >= 0) {
					continue;
				}
				Node o = Skills.byId(other);
				boolean a1 = owned.contains(n.id());
				boolean a2 = owned.contains(other);
				int col = limbColor(n.kind() == Kind.ROOT ? o.branch() : n.branch());
				int c = a1 && a2 ? col : a1 || a2 ? ARGB.color(120, col) : 0xFF2E2A36;
				line(g, sx(n.x()), sy(n.y()), sx(o.x()), sy(o.y()), a1 && a2 ? 3.0F : 2.0F, c);
			}
		}
		// The nodes.
		this.hover = null;
		boolean overCanvas = mx >= this.tx && mx < this.tx + this.tw && my >= this.ty && my < this.ty + this.th;
		for (Node n : Skills.TREE) {
			float cx = sx(n.x());
			float cy = sy(n.y());
			float half = size(n.kind()) * Mth.clamp(zoom, 0.6F, 1.6F) / 2.0F;
			if (cx < this.tx - half || cx > this.tx + this.tw + half || cy < this.ty - half || cy > this.ty + this.th + half) {
				continue;
			}
			boolean has = owned.contains(n.id());
			boolean open = !has && Skills.reachable(owned, n.id());
			boolean afford = open && insight >= n.cost();
			boolean hot = overCanvas && Math.abs(mx - cx) <= half + 1 && Math.abs(my - cy) <= half + 1;
			if (hot) {
				this.hover = n.id();
			}
			drawNode(g, n, cx, cy, half, has, open, afford, hot, now);
		}
		g.disableScissor();
		g.centeredText(this.font, fit(Component.translatable("skilltree.afterworld.hint"), w - 20), this.cw / 2, y + h - 13, DIM);
		if (this.hover != null) {
			tooltip(g, Skills.byId(this.hover), owned, insight, mx, my);
			Node n = Skills.byId(this.hover);
			if (!owned.contains(n.id()) && Skills.reachable(owned, n.id()) && insight >= n.cost()) {
				g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
			}
		}
	}

	/** A faint field of stars behind the tree, drifting a little slower than it (a sense of depth). */
	private void stars(GuiGraphicsExtractor g, long now) {
		for (int i = 0; i < 90; i++) {
			int hx = (int) (((i * 73856093L) ^ (i * 19349663L)) & 0x7fffffff);
			float fx = (hx % 1000) / 1000.0F;
			float fy = ((hx / 1000) % 1000) / 1000.0F;
			float px = this.tx + Math.floorMod((int) (fx * this.tw * 2 - viewX * UNIT * zoom * 0.35F), this.tw);
			float py = this.ty + Math.floorMod((int) (fy * this.th * 2 + viewY * UNIT * zoom * 0.35F), this.th);
			float tw = 0.5F + 0.5F * Mth.sin(now / 900.0F + i);
			int alpha = (int) (40 + 60 * tw);
			g.fill((int) px, (int) py, (int) px + 1, (int) py + 1, ARGB.color(alpha, 0xD8D0FF));
		}
	}

	private void drawNode(GuiGraphicsExtractor g, Node n, float cx, float cy, float half, boolean has, boolean open, boolean afford, boolean hot, long now) {
		int col = limbColor(n.branch());
		float pulse = 0.5F + 0.5F * Mth.sin(now / 220.0F);
		int frame = has ? col : afford ? ARGB.color((int) (150 + 105 * pulse), TEAL) : open ? 0xFF6A6478 : 0xFF3A3546;
		int fill = has ? ARGB.color(255, ARGB.scaleRGB(col, 0.28F)) : hot ? 0xFF262A33 : 0xFF14161C;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		if (n.kind() == Kind.KEYSTONE) {
			// Keystones stand as diamonds, a ring of light round the learned ones.
			pose.rotate((float) Math.PI / 4.0F);
			float k = half * 0.78F;
			if (has) {
				g.fill(Math.round(-k - 3), Math.round(-k - 3), Math.round(k + 3), Math.round(k + 3), ARGB.color((int) (70 + 40 * pulse), col));
			}
			g.fill(Math.round(-k - 1), Math.round(-k - 1), Math.round(k + 1), Math.round(k + 1), frame);
			g.fill(Math.round(-k + 1), Math.round(-k + 1), Math.round(k - 1), Math.round(k - 1), fill);
			pose.rotate((float) -Math.PI / 4.0F);
		} else {
			int r = Math.round(half);
			if (n.kind() == Kind.ROOT || has && n.kind() == Kind.NOTABLE) {
				g.fill(-r - 2, -r - 2, r + 2, r + 2, ARGB.color((int) (60 + 30 * pulse), col));
			}
			g.fill(-r, -r, r, r, frame);
			g.fill(-r + 1, -r + 1, r - 1, r - 1, fill);
			if (n.kind() == Kind.NOTABLE || n.kind() == Kind.ROOT) {
				// Named skills get a double rim.
				g.outline(-r + 2, -r + 2, 2 * r - 4, 2 * r - 4, ARGB.color(has ? 200 : 90, col));
			}
		}
		// The node's emblem, an item, dimmed where it is not learned.
		float s = Mth.clamp(half * 2.0F / 22.0F, 0.55F, 1.3F);
		pose.scale(s, s);
		g.item(this.icons.get(n.id()), -8, -8);
		pose.popMatrix();
		if (!has && !open) {
			int r = Math.round(half);
			g.fill(Math.round(cx) - r + 1, Math.round(cy) - r + 1, Math.round(cx) + r - 1, Math.round(cy) + r - 1, 0x99101218);
		}
	}

	private void tooltip(GuiGraphicsExtractor g, Node n, Set<String> owned, int insight, int mx, int my) {
		List<Component> lines = new ArrayList<>();
		int col = limbColor(n.branch());
		lines.add(Component.translatable("skill.afterworld." + n.id()).withColor(col));
		lines.add(Component.translatable("skilltree.afterworld.kind." + n.kind().name().toLowerCase(java.util.Locale.ROOT))
			.append(Component.literal("  ·  ")).append(Component.translatable("skill.afterworld.branch." + n.branch())).withColor(DIM));
		lines.add(Component.translatable("skill.afterworld." + n.id() + ".desc").withColor(TEXT));
		if (n.kind() != Kind.ROOT) {
			if (owned.contains(n.id())) {
				lines.add(Component.translatable("character.afterworld.learned").withColor(GOLD));
			} else if (!Skills.reachable(owned, n.id())) {
				lines.add(Component.translatable("skilltree.afterworld.need_path").withColor(DIM));
			} else if (insight < n.cost()) {
				lines.add(Component.translatable("skilltree.afterworld.need_insight", n.cost()).withColor(RED));
			} else {
				lines.add(Component.translatable("skilltree.afterworld.learn", n.cost()).withColor(TEAL));
			}
		}
		tip(g, lines, mx, my);
	}

	/** A straight path of the given thickness between two canvas points. */
	private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1) {
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(dy, dx));
		int hw = Math.max(1, Math.round(width / 2));
		g.fill(0, -hw, Math.round(len), hw, color);
		pose.popMatrix();
	}

	// ------------------------------------------------------------------ looking round, learning

	private boolean onCanvas(double x, double y) {
		return x >= this.tx && x < this.tx + this.tw && y >= this.ty && y < this.ty + this.th;
	}

	@Override
	protected boolean clicked(double x, double y, int button) {
		if (button == InputConstants.MOUSE_BUTTON_LEFT && onCanvas(x, y)) {
			this.pressed = true;
			this.dragged = false;
			this.pressX = x;
			this.pressY = y;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.pressed) {
			double x = event.x() / this.ui;
			double y = event.y() / this.ui;
			if (!this.dragged && Math.hypot(x - this.pressX, y - this.pressY) > 3) {
				this.dragged = true;
			}
			if (this.dragged) {
				viewX -= (float) (dx / this.ui / (UNIT * zoom));
				viewY += (float) (dy / this.ui / (UNIT * zoom));
				clampView();
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.pressed && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.pressed = false;
			if (!this.dragged && this.hover != null) {
				learn(Skills.byId(this.hover));
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean scrolled(double x, double y, double dy) {
		if (!onCanvas(x, y) || dy == 0) {
			return false;
		}
		// Zoom about the pointer: the tree point under it stays under it.
		float px = viewX + (float) ((x - this.tx - this.tw / 2.0) / (UNIT * zoom));
		float py = viewY - (float) ((y - this.ty - this.th / 2.0) / (UNIT * zoom));
		float next = Mth.clamp(zoom * (dy > 0 ? 1.15F : 1 / 1.15F), 0.5F, 2.2F);
		viewX = px - (px - viewX) * zoom / next;
		viewY = py - (py - viewY) * zoom / next;
		zoom = next;
		clampView();
		return true;
	}

	private static void clampView() {
		viewX = Mth.clamp(viewX, -7.0F, 7.0F);
		viewY = Mth.clamp(viewY, -1.0F, 7.0F);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_R) {
			zoom = 1.0F;
			viewX = 0.0F;
			viewY = 3.0F;
			sound(AwSounds.UI_TAB, 1.2F, 0.4F);
			return true;
		}
		return super.keyPressed(event);
	}

	private void learn(Node n) {
		if (n == null || n.kind() == Kind.ROOT) {
			return;
		}
		Set<String> owned = owned();
		int insight = CharacterScreen.data().getIntOr("insight", 0);
		if (owned.contains(n.id()) || !Skills.reachable(owned, n.id()) || insight < n.cost()) {
			sound(AwSounds.UI_HOVER, 0.6F, 0.3F);
			return;
		}
		sound(AwSounds.UI_SELECT, 1.0F, 0.6F);
		ClientPlayNetworking.send(new AwPayloads.CharacterAction(AwPayloads.CharacterAction.BUY_SKILL, 0, n.id()));
	}

	/** For tests: learns a node as a click on it would. */
	public void learnForTest(String id) {
		learn(Skills.byId(id));
	}

	/** For tests: sets the view. */
	public static void viewForTest(float x, float y, float z) {
		viewX = x;
		viewY = y;
		zoom = z;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
