package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.ClientState;
import dev.afterworld.client.hud.EdictHud;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.BondType;
import dev.afterworld.player.Discipline;
import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import dev.afterworld.registry.AwSounds;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Expedition Journal: an old field book with five ribbons. Drawn immediate-mode: every frame
 * lays out its own click regions, so pages always match the latest data from the server.
 */
public class JournalScreen extends Screen {
	private static final int W = 352;
	private static final int H = 224;
	private static final int INK = 0xFF3B3226;
	private static final int FADED = 0xFF7A6C58;
	private static final int PAPER = 0xFFE6D9BB;
	private static final int PAPER_DARK = 0xFFD5C5A0;
	private static final int FRAME = 0xFF1B242B;
	private static final int AMBER = 0xFFB8792A;
	private static final int RED = 0xFFA8432E;
	private static final int TEAL = 0xFF2F7F77;
	private static final String[] TAB_IDS = {"edicts", "rumours", "chronicle", "world", "codex"};
	/** The facing (chapter) page's width, and the virtual canvas the whole book is drawn on. */
	private static final int CH = 196;
	private static final int CANVAS_W = CH + W + 4;
	private static final int CANVAS_H = H + 50;
	private static final int[] RIBBON = {0xFF7A5AB8, 0xFF2F7F77, 0xFF8E2430, 0xFFC9A227, 0xFF3F6E8C};
	private static int lastTab;

	private record Hit(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private final int[] scroll = new int[5];
	private int tab = lastTab;
	private int left;
	private int top;
	private int selectedSlot;
	private int selectedRumour = -1;
	private int chronicleSub;
	private String codexOpen = "";
	private boolean choosing;
	private int chosenDiscipline = -1;
	private int contentHeight;
	private int bookLeft;
	private float scale = 1.0F;
	private float ox;
	private float oy;
	private long openedAt;
	private long turnAt = -1;
	private int viewTop;
	private int viewBottom;

	protected JournalScreen() {
		super(Component.translatable("screen.afterworld.journal"));
	}

	public static void open() {
		Minecraft.getInstance().gui.setScreen(new JournalScreen());
	}

	/** Opens the journal on a specific tab (also used by the client game tests). */
	public static void open(int tab) {
		lastTab = Mth.clamp(tab, 0, TAB_IDS.length - 1);
		open();
	}

	@Override
	protected void init() {
		// The book is laid out on a fixed virtual canvas and scaled to fit the window.
		bookLeft = 10;
		left = bookLeft + CH;
		top = 32;
		// Sized to the window like every Afterworld screen (see AwScreen.uiFor); the book has no compact layout.
		scale = AwScreen.uiFor(CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H);
		ox = (width - CANVAS_W * scale) / 2.0F;
		oy = (height - CANVAS_H * scale) / 2.0F;
		if (openedAt == 0) {
			openedAt = Util.getMillis();
			sound(AwSounds.UI_OPEN, 1.0F, 0.6F);
		}
		request(tab);
	}

	private void request(int t) {
		ClientPlayNetworking.send(new AwPayloads.JournalAction(AwPayloads.JournalAction.OPEN, t, 0, ""));
	}

	private void action(int action, int a, int b) {
		ClientPlayNetworking.send(new AwPayloads.JournalAction(action, a, b, ""));
	}

	private void sound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s.value(), pitch, volume));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double vx = (event.x() - ox) / scale;
		double vy = (event.y() - oy) / scale;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.action != null && h.contains(vx, vy)) {
				AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
				h.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int view = viewBottom - viewTop;
		scroll[tab] = Mth.clamp(scroll[tab] - (int) (scrollY * 14), 0, Math.max(0, contentHeight - view));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (AfterworldClient.JOURNAL.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int rawX, int rawY, float a) {
		hits.clear();
		long now = Util.getMillis();
		float open = ease(Math.min(1.0F, (now - openedAt) / 260.0F));
		int mx = (int) ((rawX - ox) / scale);
		int my = (int) ((rawY - oy) / scale);
		// A warm pool of lamplight behind the book.
		g.fillGradient(0, 0, width, height, ARGB.color((int) (open * 120), 0x05080B), ARGB.color((int) (open * 170), 0x05080B));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(ox, oy);
		pose.scale(scale, scale);
		float grow = 0.95F + 0.05F * open;
		pose.translate(CANVAS_W / 2.0F, CANVAS_H / 2.0F);
		pose.scale(grow, grow);
		pose.translate(-CANVAS_W / 2.0F, -CANVAS_H / 2.0F);
		ribbons(g, mx, my, now);
		frame(g);
		CompoundTag data = ClientState.journal[tab];
		chapterPage(g, data, mx, my, now);
		int x = left + 14;
		int y = top + 12;
		g.text(font, Component.translatable("journal.afterworld.tab." + TAB_IDS[tab]), x, y, INK, false);
		g.horizontalLine(x, left + W - 30, y + 11, 0xFFB5A27C);
		viewTop = y + 16;
		viewBottom = top + H - 12;
		if (data == null) {
			centered(g, Component.translatable("journal.afterworld.loading").getString(), left + W / 2 - 8, top + H / 2, FADED);
		} else {
			g.enableScissor(left + 6, viewTop, left + W - 22, viewBottom);
			int start = viewTop - scroll[tab];
			int end = switch (tab) {
				case 0 -> edicts(g, data, x, start, mx, my);
				case 1 -> rumours(g, data, x, start, mx, my);
				case 2 -> chronicle(g, data, x, start, mx, my);
				case 3 -> world(g, data, x, start, mx, my);
				default -> codex(g, data, x, start, mx, my);
			};
			g.disableScissor();
			contentHeight = end - start;
			int view = viewBottom - viewTop;
			if (contentHeight > view) {
				int barH = Math.max(12, view * view / contentHeight);
				int barY = viewTop + (view - barH) * scroll[tab] / Math.max(1, contentHeight - view);
				g.fill(left + W - 26, viewTop, left + W - 24, viewBottom, 0x30000000);
				g.fill(left + W - 26, barY, left + W - 24, barY + barH, 0xFF8A7A5C);
			}
		}
		pageTurn(g, now);
		if (open < 1.0F) {
			g.fill(bookLeft - 10, top - 30, left + W, top + H + 14, ARGB.color((int) ((1 - open) * 255), 0x05080B));
		}
		pose.popMatrix();
		for (Hit h : hits) {
			if (h.tooltip != null && !h.tooltip.isEmpty() && h.contains(mx, my) && !h.tooltip.get(0).getString().startsWith("#")) {
				g.setComponentTooltipForNextFrame(font, h.tooltip, rawX, rawY);
				break;
			}
		}
	}

	private static float ease(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	/** A leather cover, two pages with a shadowed spine, and page edges stacked at the sides. */
	private void frame(GuiGraphicsExtractor g) {
		int x0 = bookLeft - 8;
		int x1 = left + W - 16 + 8;
		int y0 = top - 8;
		int y1 = top + H + 8;
		g.fill(x0 + 5, y0 + 7, x1 + 5, y1 + 7, 0x66000000);
		g.fill(x0, y0, x1, y1, 0xFF2A1A10);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF4A2E1A);
		g.outline(x0 + 4, y0 + 4, x1 - x0 - 8, y1 - y0 - 8, 0xFF6B4628);
		for (int x = x0 + 8; x < x1 - 8; x += 4) {
			g.fill(x, y0 + 2, x + 2, y0 + 3, 0xFF7A5434);
			g.fill(x, y1 - 3, x + 2, y1 - 2, 0xFF7A5434);
		}
		for (int[] c : new int[][] {{x0 + 3, y0 + 3, 1, 1}, {x1 - 3, y0 + 3, -1, 1}, {x0 + 3, y1 - 3, 1, -1}, {x1 - 3, y1 - 3, -1, -1}}) {
			for (int i = 0; i < 9; i++) {
				g.fill(c[0] + c[2] * i, c[1], c[0] + c[2] * i + 1, c[1] + c[3], 0xFFC9A227);
				g.fill(c[0], c[1] + c[3] * i, c[0] + c[2], c[1] + c[3] * i + 1, 0xFFC9A227);
			}
			g.fill(c[0] + c[2] * 2, c[1] + c[3] * 2, c[0] + c[2] * 4, c[1] + c[3] * 4, 0xFFE8C04A);
		}
		// Page stacks at the outer edges.
		for (int i = 1; i <= 3; i++) {
			g.fill(bookLeft - i, top + i, bookLeft - i + 1, top + H - i, i % 2 == 0 ? 0xFFCFC09C : 0xFFE6D9BB);
			g.fill(left + W - 16 + i - 1, top + i, left + W - 16 + i, top + H - i, i % 2 == 0 ? 0xFFCFC09C : 0xFFE6D9BB);
		}
		g.fill(bookLeft, top, left, top + H, PAPER);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("journal/paper"), bookLeft, top, CH, H);
		g.fill(left, top, left + W - 16, top + H, PAPER);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("journal/paper"), left, top, W - 16, H);
		g.fill(bookLeft, top, left + W - 16, top + 2, PAPER_DARK);
		g.fill(bookLeft, top + H - 2, left + W - 16, top + H, PAPER_DARK);
		// The spine: a soft shadow either side of the gutter.
		for (int i = 0; i < 12; i++) {
			int alpha = (int) (70 * (1 - i / 12.0F));
			g.fill(left - 1 - i, top, left - i, top + H, ARGB.color(alpha / 2, 0x3B2A16));
			g.fill(left + i, top, left + i + 1, top + H, ARGB.color(alpha, 0x3B2A16));
		}
		g.fill(left - 1, top, left + 1, top + H, 0x883B2A16);
	}

	/** Silk ribbons along the top edge, one per section; the open one hangs longest. */
	private void ribbons(GuiGraphicsExtractor g, int mx, int my, long now) {
		for (int i = 0; i < TAB_IDS.length; i++) {
			int rx = left + 14 + i * 40;
			boolean sel = i == tab;
			boolean hover = mx >= rx && mx < rx + 28 && my >= top - 30 && my < top;
			int up = sel ? 28 : hover ? 24 : 20;
			int ry = top - up;
			int color = sel ? RIBBON[i] : ARGB.scaleRGB(RIBBON[i], 0.72F);
			// Column by column, so the free end gets a swallowtail notch.
			for (int c = 0; c < 28; c++) {
				int notch = Math.max(0, 5 - Math.abs(c * 2 - 27) / 3);
				int shade = c < 2 ? ARGB.scaleRGB(color, 1.25F) : c >= 26 ? ARGB.scaleRGB(color, 0.55F) : color;
				g.fill(rx + c, ry + notch, rx + c + 1, top + 6, shade);
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("journal/tab_" + TAB_IDS[i]), rx + 6, ry + 5, 16, 16);
			int idx = i;
			hitFixed(rx, ry, 28, up, () -> {
				if (tab != idx) {
					turnAt = Util.getMillis();
					sound(AwSounds.UI_PAGE, 0.9F + idx * 0.05F, 0.6F);
				}
				tab = idx;
				lastTab = idx;
				choosing = false;
				request(idx);
			}, List.of(Component.translatable("journal.afterworld.tab." + TAB_IDS[i])));
		}
	}

	/** The facing page: each section's title page, with the traveller's portrait on the first. */
	private void chapterPage(GuiGraphicsExtractor g, CompoundTag data, int mx, int my, long now) {
		int cx = bookLeft + CH / 2;
		int y = top + 14;
		var pose = g.pose();
		Component title = Component.translatable("journal.afterworld.tab." + TAB_IDS[tab]);
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(1.6F, 1.6F);
		g.centeredText(font, title, 0, 0, INK);
		pose.popMatrix();
		y += 18;
		g.fill(cx - 50, y, cx - 4, y + 1, 0xFFB5A27C);
		g.fill(cx + 4, y, cx + 50, y + 1, 0xFFB5A27C);
		g.fill(cx - 1, y - 1, cx + 2, y + 2, RIBBON[tab]);
		y += 8;
		if (tab == 0) {
			portrait(g, data, cx, y, mx, my, now);
			return;
		}
		// An illustration: the section's emblem in a ruled medallion.
		int r = 26;
		int my0 = y + r + 4;
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt(r * r - dy * dy);
			int innerHalf = dy * dy < (r - 3) * (r - 3) ? (int) Math.sqrt((r - 3) * (r - 3) - dy * dy) : 0;
			g.fill(cx - half, my0 + dy, cx - innerHalf, my0 + dy + 1, ARGB.scaleRGB(RIBBON[tab], 0.8F));
			g.fill(cx + innerHalf, my0 + dy, cx + half, my0 + dy + 1, ARGB.scaleRGB(RIBBON[tab], 0.8F));
			g.fill(cx - innerHalf, my0 + dy, cx + innerHalf, my0 + dy + 1, 0x22000000);
		}
		pose.pushMatrix();
		pose.translate(cx - 16, my0 - 16);
		pose.scale(2.0F, 2.0F);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("journal/tab_" + TAB_IDS[tab]), 0, 0, 16, 16);
		pose.popMatrix();
		y = my0 + r + 10;
		Component quote = Component.translatable("journal.afterworld.quote." + TAB_IDS[tab]).withStyle(ChatFormatting.ITALIC);
		for (FormattedCharSequence line : font.split(quote, CH - 30)) {
			g.centeredText(font, line, cx, y, FADED);
			y += 10;
		}
		y += 6;
		if (data == null) {
			return;
		}
		if (tab == 3) {
			// Standing with each faction: name on the left, tier on the right.
			ListTag factions = data.getListOrEmpty("factions");
			int lx = bookLeft + 14;
			int rx = bookLeft + CH - 14;
			for (int i = 0; i < factions.size(); i++) {
				CompoundTag f = factions.getCompoundOrEmpty(i);
				Faction fac = Faction.byOrdinal(f.getIntOr("faction", 0));
				if (fac == null) {
					continue;
				}
				String tier = Faction.tierName(f.getIntOr("rep", 0)).getString();
				int tw = font.width(tier);
				g.text(font, trim(fac.displayName().getString(), rx - lx - tw - 8), lx, y, ARGB.opaque(fac.color), false);
				g.text(font, tier, rx - tw, y, INK, false);
				y += 11;
			}
			return;
		}
		for (Component line : stats(data)) {
			g.centeredText(font, line, cx, y, INK);
			y += 11;
		}
	}

	private List<Component> stats(CompoundTag d) {
		List<Component> out = new ArrayList<>();
		switch (tab) {
			case 1 -> {
				ListTag list = d.getListOrEmpty("rumours");
				int open = 0;
				for (int i = 0; i < list.size(); i++) {
					if (list.getCompoundOrEmpty(i).getIntOr("state", 0) == 0) {
						open++;
					}
				}
				out.add(Component.translatable("journal.afterworld.stat.leads", open));
				out.add(Component.translatable("journal.afterworld.stat.heard", list.size()));
			}
			case 2 -> {
				out.add(Component.translatable("journal.afterworld.stat.pages", d.getListOrEmpty("public").size()));
				out.add(Component.translatable("journal.afterworld.stat.legend", d.getListOrEmpty("legend").size()));
				out.add(Component.translatable("journal.afterworld.stat.missing", d.getListOrEmpty("missing").size()));
			}
			case 3 -> {
				ListTag factions = d.getListOrEmpty("factions");
				for (int i = 0; i < factions.size(); i++) {
					CompoundTag f = factions.getCompoundOrEmpty(i);
					Faction fac = Faction.byOrdinal(f.getIntOr("faction", 0));
					if (fac != null) {
						out.add(fac.displayName().copy().withColor(fac.color).append(Component.literal("  " + Faction.tierName(f.getIntOr("rep", 0)).getString()).withColor(INK)));
					}
				}
			}
			default -> {
				ListTag entries = d.getListOrEmpty("entries");
				if (!entries.isEmpty()) {
					out.add(Component.translatable("journal.afterworld.stat.codex", entries.size()));
				}
			}
		}
		return out;
	}

	/** The traveller in 3D on a little plinth, with origin, discipline and the wardrobe. */
	private void portrait(GuiGraphicsExtractor g, CompoundTag data, int cx, int y, int mx, int my, long now) {
		Minecraft mc = Minecraft.getInstance();
		int boxTop = y;
		int boxBottom = y + 112;
		g.fill(cx - 36, boxBottom - 4, cx + 36, boxBottom, 0x22000000);
		g.fill(cx - 28, boxBottom - 6, cx + 28, boxBottom - 4, 0x18000000);
		if (mc.player != null) {
			var raw = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0F);
			if (raw instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
				float t = (now % 3_600_000L) / 1000.0F;
				s.shadowPieces.clear();
				s.outlineColor = 0;
				s.nameTag = null;
				s.isCrouching = false;
				s.bodyRot = 180.0F + 25.0F + Mth.sin(t * 0.35F) * 35.0F;
				s.yRot = Mth.sin(t * 0.5F) * 10.0F;
				s.xRot = 0;
				s.walkAnimationSpeed = 0;
				s.capeLean = 5.0F + Mth.sin(t * 0.9F) * 3.0F;
				s.capeFlap = Mth.sin(t * 2.0F) * 2.0F;
				s.boundingBoxWidth = s.boundingBoxWidth / s.scale;
				s.boundingBoxHeight = s.boundingBoxHeight / s.scale;
				s.scale = 1.0F;
				float size = 48.0F;
				float cy = (boxTop + boxBottom) / 2.0F;
				var translation = new org.joml.Vector3f(0, (boxBottom - 6 - cy) / size, 0);
				var tilt = new org.joml.Quaternionf().rotateX(-5.0F * Mth.DEG_TO_RAD);
				var rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(tilt);
				Portraits.entity(g, s, size, translation, rotation, tilt, cx - 50, boxTop, cx + 50, boxBottom);
			}
		}
		y = boxBottom + 6;
		if (data != null) {
			String name = data.getStringOr("name", "");
			if (!name.isEmpty()) {
				g.centeredText(font, Component.literal(name), cx, y, AMBER);
				y += 11;
			}
			String originId = data.getStringOr("origin", "none");
			if (!originId.equals("none")) {
				g.centeredText(font, Component.translatable("origin.afterworld." + originId), cx, y, INK);
				y += 11;
			}
			Discipline dsc = Discipline.byOrdinal(data.getIntOr("discipline", 0));
			g.centeredText(font, dsc.displayName().copy().withColor(dsc.color), cx, y, INK);
			y += 11;
			Edict within = Edict.byOrdinal(data.getIntOr("affinity", -1));
			if (within != null) {
				g.centeredText(font, Component.translatable("journal.afterworld.within", within.displayName()).withColor(within.color), cx, y, INK);
				y += 11;
			}
			y += 3;
		}
		Component wardrobe = Component.translatable("journal.afterworld.wardrobe");
		int bw = font.width(wardrobe) + 16;
		button(g, wardrobe, cx - bw / 2, y, bw, 14, true, () -> Minecraft.getInstance().gui.setScreen(new ArrivalScreen(true, Discipline.NONE)),
			List.of(Component.translatable("journal.afterworld.wardrobe_tip")), mx, my);
	}

	/** Turning to a new section: a sheet sweeps from the right page over to the left. */
	private void pageTurn(GuiGraphicsExtractor g, long now) {
		if (turnAt < 0) {
			return;
		}
		float t = (now - turnAt) / 300.0F;
		if (t >= 1) {
			turnAt = -1;
			return;
		}
		float e = ease(t);
		int pageW = W - 16;
		int x0;
		int x1;
		if (e < 0.5F) {
			x0 = left;
			x1 = left + (int) (pageW * (1 - e * 2));
		} else {
			x0 = left - (int) (CH * (e - 0.5F) * 2);
			x1 = left;
		}
		if (x1 - x0 < 2) {
			return;
		}
		g.fill(x0, top, x1, top + H, PAPER);
		int edge = e < 0.5F ? x1 : x0;
		for (int i = 0; i < 10; i++) {
			int alpha = (int) (90 * (1 - i / 10.0F));
			int x = e < 0.5F ? edge - 1 - i : edge + i;
			if (x >= x0 && x < x1) {
				g.fill(x, top, x + 1, top + H, ARGB.color(alpha, 0x3B2A16));
			}
		}
		g.fill(x0, top, x1, top + 2, PAPER_DARK);
	}

	// ------------------------------------------------------------------ edicts

	private int edicts(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my) {
		int disc = d.getIntOr("discipline", 0);
		if (disc == 0 || choosing) {
			return chooser(g, d, x, y, mx, my, disc);
		}
		Discipline dsc = Discipline.byOrdinal(disc);
		int right = left + W - 34;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, EdictHud.disciplineIcon(disc), x, y, 16, 16);
		g.text(font, dsc.displayName().copy().withColor(dsc.color), x + 20, y + 1, 0xFFFFFFFF, false);
		Component sig = Component.translatable("journal.afterworld.signature", dsc.signatureName(), AfterworldClient.SIGNATURE.getTranslatedKeyMessage());
		g.text(font, sig, x + 20, y + 11, FADED, false);
		button(g, Component.translatable("journal.afterworld.change_discipline"), right - 86, y, 86, 14, true, () -> {
			choosing = true;
			chosenDiscipline = -1;
		}, List.of(Component.translatable("journal.afterworld.change_cost")), mx, my);
		y += 26;
		y = wrap(g, Component.translatable("discipline.afterworld." + dsc.id + ".signature.desc"), x, y, W - 50, INK) + 6;

		g.text(font, Component.translatable("journal.afterworld.slots"), x, y, AMBER, false);
		y += 12;
		int slotCount = d.getIntOr("slotCount", 3);
		int[] slots = d.getIntArray("slots").orElse(new int[] {-1, -1, -1, -1});
		for (int i = 0; i < slotCount; i++) {
			int sx = x + i * 74;
			boolean sel = i == selectedSlot;
			g.fill(sx, y, sx + 70, y + 26, sel ? 0xFFCDB98E : PAPER_DARK);
			g.outline(sx, y, 70, 26, sel ? AMBER : 0xFFAA9878);
			int e = i < slots.length ? slots[i] : -1;
			if (e >= 0) {
				Edict ed = Edict.byOrdinal(e);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EdictHud.edictIcon(e), sx + 4, y + 5, 16, 16);
				g.text(font, trim(ed.displayName().getString(), 44), sx + 23, y + 5, INK, false);
			} else {
				g.text(font, Component.translatable("journal.afterworld.empty_slot"), sx + 6, y + 9, FADED, false);
			}
			g.text(font, AfterworldClient.SLOT_KEYS[i].getTranslatedKeyMessage(), sx + 23, y + 15, FADED, false);
			int slot = i;
			hit(sx, y, 70, 26, () -> selectedSlot = slot, null);
		}
		y += 32;
		g.text(font, Component.translatable("journal.afterworld.library"), x, y, AMBER, false);
		g.text(font, Component.translatable("journal.afterworld.library_hint"), x + 60, y, FADED, false);
		y += 12;
		int learned = d.getIntOr("learned", 0);
		int[] cds = d.getIntArray("cooldowns").orElse(new int[Edict.COUNT]);
		for (Edict e : Edict.VALUES) {
			int col = e.ordinal() % 5;
			int row = e.ordinal() / 5;
			int ex = x + col * 58;
			int ey = y + row * 40;
			boolean known = (learned & (1 << e.ordinal())) != 0;
			boolean equipped = false;
			for (int s : slots) {
				equipped |= s == e.ordinal();
			}
			g.fill(ex, ey, ex + 54, ey + 36, known ? (equipped ? 0xFFD9C79C : PAPER_DARK) : 0xFFCFC3A6);
			g.outline(ex, ey, 54, 36, known ? 0xFF000000 | e.color : 0xFFB0A386);
			if (known) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EdictHud.edictIcon(e.ordinal()), ex + 19, ey + 3, 16, 16);
				centered(g, trim(e.displayName().getString(), 52), ex + 27, ey + 23, INK);
			} else {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("edict/unknown"), ex + 19, ey + 3, 16, 16);
				centered(g, "?", ex + 27, ey + 23, FADED);
			}
			List<Component> tip = new ArrayList<>();
			if (known) {
				tip.add(e.displayName().copy().withColor(e.color));
				tip.add(e.rule().copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
				tip.add(e.description());
				tip.add(e.limit().copy().withStyle(ChatFormatting.DARK_GRAY));
				tip.add(Component.translatable("journal.afterworld.cooldown", String.format("%.1f", cds.length > e.ordinal() ? cds[e.ordinal()] / 20F : e.cooldown / 20F)).withStyle(ChatFormatting.DARK_AQUA));
				tip.add(Component.translatable("journal.afterworld.click_equip").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				tip.add(Component.translatable("journal.afterworld.unknown_edict"));
				tip.add(Component.translatable("edict.afterworld." + e.id + ".hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			hit(ex, ey, 54, 36, known ? () -> action(AwPayloads.JournalAction.EQUIP, selectedSlot, e.ordinal()) : null, tip);
		}
		y += 84;
		button(g, Component.translatable("journal.afterworld.clear_slot"), x, y, 90, 14, true,
			() -> action(AwPayloads.JournalAction.EQUIP, selectedSlot, -1), null, mx, my);
		return y + 20;
	}

	private int chooser(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my, int current) {
		y = wrap(g, Component.translatable(current == 0 ? "journal.afterworld.choose_intro" : "journal.afterworld.change_intro"), x, y, W - 50, INK) + 6;
		for (int i = 1; i < Discipline.VALUES.length; i++) {
			Discipline dsc = Discipline.VALUES[i];
			int col = (i - 1) % 2;
			int row = (i - 1) / 2;
			int cx = x + col * 152;
			int cy = y + row * 74;
			boolean sel = chosenDiscipline == i;
			g.fill(cx, cy, cx + 148, cy + 70, sel ? 0xFFD9C79C : PAPER_DARK);
			g.outline(cx, cy, 148, 70, sel ? 0xFF000000 | dsc.color : 0xFFAA9878);
			if (i == current) {
				g.text(font, Component.translatable("journal.afterworld.current"), cx + 100, cy + 4, FADED, false);
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, EdictHud.disciplineIcon(i), cx + 4, cy + 4, 16, 16);
			g.text(font, dsc.displayName().copy().withColor(dsc.color), cx + 24, cy + 8, 0xFFFFFFFF, false);
			wrap(g, Component.translatable("discipline.afterworld." + dsc.id + ".style"), cx + 4, cy + 24, 140, INK);
			g.text(font, Component.translatable("journal.afterworld.starts_with", dsc.starter.displayName()), cx + 4, cy + 58, FADED, false);
			int idx = i;
			hit(cx, cy, 148, 70, () -> chosenDiscipline = idx, List.of(dsc.displayName().copy().withColor(dsc.color),
				Component.translatable("journal.afterworld.signature_short", dsc.signatureName()).withStyle(ChatFormatting.GOLD),
				Component.translatable("discipline.afterworld." + dsc.id + ".signature.desc"),
				Component.translatable("discipline.afterworld." + dsc.id + ".passive").withStyle(ChatFormatting.GRAY)));
		}
		y += 152;
		boolean can = chosenDiscipline > 0 && chosenDiscipline != current;
		Component label = can ? Component.translatable("journal.afterworld.take_up", Discipline.byOrdinal(chosenDiscipline).displayName())
			: Component.translatable("journal.afterworld.pick_one");
		button(g, label, x, y, 200, 16, can, () -> {
			action(AwPayloads.JournalAction.CHOOSE_DISCIPLINE, chosenDiscipline, 0);
			choosing = false;
		}, null, mx, my);
		if (current != 0) {
			button(g, Component.translatable("gui.back"), x + 206, y, 60, 16, true, () -> choosing = false, null, mx, my);
		}
		return y + 22;
	}

	// ------------------------------------------------------------------ rumours

	private int rumours(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my) {
		ListTag list = d.getListOrEmpty("rumours");
		int tracked = d.getIntOr("tracked", -1);
		if (list.isEmpty()) {
			y = wrap(g, Component.translatable("journal.afterworld.no_rumours"), x, y, W - 50, FADED) + 8;
		}
		int listW = 118;
		int ly = y;
		CompoundTag selected = null;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag r = list.getCompoundOrEmpty(i);
			int id = r.getIntOr("id", 0);
			if (selectedRumour < 0) {
				selectedRumour = id;
			}
			boolean sel = id == selectedRumour;
			if (sel) {
				selected = r;
			}
			int state = r.getIntOr("state", 0);
			g.fill(x, ly, x + listW, ly + 13, sel ? 0xFFCDB98E : (i % 2 == 0 ? 0x10000000 : 0x00000000));
			String mark = state == 0 ? (id == tracked ? "★" : "◆") : state == 1 ? "✓" : "✗";
			int markColor = state == 0 ? (id == tracked ? AMBER : TEAL) : state == 1 ? 0xFF5E7F3A : RED;
			g.text(font, mark, x + 2, ly + 3, markColor, false);
			g.text(font, trim(r.getStringOr("title", ""), listW - 14), x + 11, ly + 3, state == 0 ? INK : FADED, false);
			hit(x, ly, listW, 13, () -> selectedRumour = id, null);
			ly += 14;
		}
		int rx = x + listW + 8;
		int rw = W - 50 - listW - 8;
		int ry = y;
		if (selected != null) {
			CompoundTag r = selected;
			g.text(font, Component.literal(r.getStringOr("title", "")).withStyle(ChatFormatting.BOLD), rx, ry, INK, false);
			ry += 12;
			g.text(font, Component.translatable("journal.afterworld.heard_from", trim(r.getStringOr("source", ""), rw - 50)), rx, ry, FADED, false);
			ry += 12;
			ry = wrap(g, Component.literal("“" + r.getStringOr("text", "") + "”").withStyle(ChatFormatting.ITALIC), rx, ry, rw, INK) + 4;
			int state = r.getIntOr("state", 0);
			if (state == 0) {
				if (r.contains("bearing")) {
					ry = wrap(g, Component.translatable("journal.afterworld.bearing", r.getStringOr("bearing", ""), r.getStringOr("distance", "")), rx, ry, rw, TEAL) + 2;
				} else {
					ry = wrap(g, Component.translatable("journal.afterworld.other_world"), rx, ry, rw, FADED) + 2;
				}
				int conf = r.getIntOr("confidence", 50);
				g.text(font, Component.translatable("journal.afterworld.confidence", Component.translatable(conf >= 75 ? "journal.afterworld.conf_high" : conf >= 45 ? "journal.afterworld.conf_fair" : "journal.afterworld.conf_low")), rx, ry, FADED, false);
				bar(g, rx + 90, ry + 2, 60, conf, AMBER);
				ry += 12;
				if (r.getBooleanOr("arrived", false)) {
					g.text(font, Component.translatable("journal.afterworld.arrived"), rx, ry, 0xFF5E7F3A, false);
					ry += 11;
				}
			} else {
				int rel = r.getIntOr("reliability", 0);
				g.text(font, Component.translatable("journal.afterworld.reliability." + rel), rx, ry, rel == 0 ? 0xFF5E7F3A : RED, false);
				ry += 11;
				ry = wrap(g, Component.literal(r.getStringOr("outcome", "")), rx, ry, rw, INK) + 2;
			}
			ListTag clues = r.getListOrEmpty("clues");
			for (int i = 0; i < clues.size(); i++) {
				ry = wrap(g, Component.literal("• " + clues.getStringOr(i, "")), rx, ry, rw, FADED);
			}
			ry += 4;
			if (state == 0) {
				int id = r.getIntOr("id", 0);
				button(g, Component.translatable(id == tracked ? "journal.afterworld.tracked" : "journal.afterworld.track"), rx, ry, 70, 14, id != tracked,
					() -> action(AwPayloads.JournalAction.TRACK_RUMOUR, id, 0), List.of(Component.translatable("journal.afterworld.track_tip")), mx, my);
				button(g, Component.translatable("journal.afterworld.abandon"), rx + 76, ry, 70, 14, true,
					() -> action(AwPayloads.JournalAction.ABANDON_RUMOUR, id, 0), null, mx, my);
				ry += 18;
			}
		}
		y = Math.max(ly, ry) + 10;
		g.text(font, Component.translatable("journal.afterworld.contracts"), x, y, AMBER, false);
		y += 12;
		ListTag contracts = d.getListOrEmpty("contracts");
		for (int i = 0; i < contracts.size(); i++) {
			CompoundTag c = contracts.getCompoundOrEmpty(i);
			Faction f = Faction.byOrdinal(c.getIntOr("faction", 0));
			boolean active = c.getBooleanOr("active", false);
			boolean refused = c.getBooleanOr("refused", false);
			int bx = x + (i % 2) * 152;
			int by = y + (i / 2) * 18;
			Component label = Component.translatable(active ? "journal.afterworld.contract_taken" : refused ? "journal.afterworld.contract_refused_short" : "journal.afterworld.ask_work", f.displayName());
			button(g, label, bx, by, 148, 15, !active && !refused, () -> action(AwPayloads.JournalAction.CHOICE, 1, f.ordinal()),
				List.of(f.displayName().copy().withColor(f.color), Component.translatable("journal.afterworld.contract_tip." + f.id), Component.literal(c.getStringOr("leader", "")).withStyle(ChatFormatting.GRAY)), mx, my);
		}
		return y + 40;
	}

	// ------------------------------------------------------------------ chronicle

	private int chronicle(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my) {
		String[] subs = {"public", "legend", "missing"};
		for (int i = 0; i < subs.length; i++) {
			int bx = x + i * 98;
			int idx = i;
			button(g, Component.translatable("journal.afterworld.chronicle." + subs[i]), bx, y, 94, 14, chronicleSub != i, () -> {
				chronicleSub = idx;
				scroll[2] = 0;
			}, null, mx, my);
		}
		y += 20;
		int width = W - 50;
		if (chronicleSub == 1) {
			ListTag legend = d.getListOrEmpty("legend");
			if (legend.isEmpty()) {
				y = wrap(g, Component.translatable("journal.afterworld.legend_empty"), x, y, width, FADED);
			}
			for (int i = 0; i < legend.size(); i++) {
				CompoundTag e = legend.getCompoundOrEmpty(i);
				g.text(font, Component.translatable("journal.afterworld.day", e.getLongOr("day", 0)), x, y, AMBER, false);
				y = wrap(g, Component.literal(e.getStringOr("text", "")), x + 40, y, width - 40, INK) + 4;
			}
			return y;
		}
		ListTag list = d.getListOrEmpty(chronicleSub == 0 ? "public" : "missing");
		if (list.isEmpty()) {
			y = wrap(g, Component.translatable(chronicleSub == 0 ? "journal.afterworld.chronicle_empty" : "journal.afterworld.missing_empty"), x, y, width, FADED);
		}
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			int faction = e.getIntOr("faction", -1);
			int color = faction >= 0 ? 0xFF000000 | Faction.byOrdinal(faction).color : INK;
			g.fill(x, y, x + 2, y + 9, color);
			g.text(font, Component.translatable("journal.afterworld.day", e.getLongOr("day", 0)), x + 6, y, AMBER, false);
			g.text(font, Component.literal(e.getStringOr("title", "")).withStyle(ChatFormatting.BOLD), x + 46, y, INK, false);
			y += 11;
			y = wrap(g, Component.literal(e.getStringOr("text", "")), x + 6, y, width - 6, INK);
			if (chronicleSub == 2) {
				y = wrap(g, Component.translatable("journal.afterworld.page_torn"), x + 6, y, width - 6, RED);
				String cause = e.getStringOr("cause", "");
				if (!cause.isEmpty()) {
					int pages = pagesHeld();
					Component label = Component.translatable("journal.afterworld.restore", pages);
					int bw = font.width(label) + 12;
					button(g, label, x + 6, y + 1, bw, 13, pages >= 3,
						() -> ClientPlayNetworking.send(new AwPayloads.JournalAction(AwPayloads.JournalAction.RESTORE_PAGE, 0, 0, cause)),
						List.of(Component.translatable("journal.afterworld.restore_tip")), mx, my);
					y += 17;
				}
			}
			String place = e.getStringOr("place", "");
			String actor = e.getStringOr("actor", "");
			Component meta = Component.translatable("journal.afterworld.certainty." + e.getIntOr("certainty", 0));
			if (!place.isEmpty()) {
				meta = meta.copy().append(" · " + place);
			}
			if (!actor.isEmpty()) {
				meta = meta.copy().append(" · " + actor);
			}
			g.text(font, meta, x + 6, y, FADED, false);
			y += 14;
		}
		return y;
	}

	// ------------------------------------------------------------------ world

	private int world(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my) {
		int width = W - 50;
		int pending = d.getIntOr("pendingBond", -1);
		ListTag bonds = d.getListOrEmpty("bonds");
		g.text(font, Component.translatable("journal.afterworld.bonds"), x, y, AMBER, false);
		y += 12;
		if (pending >= 0) {
			BondType type = BondType.byOrdinal(pending);
			g.fill(x, y, x + width, y + 46, 0xFFD9C79C);
			g.outline(x, y, width, 46, 0xFF000000 | type.color);
			g.text(font, Component.translatable("journal.afterworld.bond_offered", type.displayName()), x + 4, y + 4, INK, false);
			wrap(g, Component.translatable("bond.afterworld." + type.id + ".terms"), x + 4, y + 15, width - 8, FADED);
			button(g, Component.translatable("journal.afterworld.accept"), x + width - 130, y + 30, 60, 13, true, () -> action(AwPayloads.JournalAction.CHOICE, 2, pending), null, mx, my);
			button(g, Component.translatable("journal.afterworld.decline"), x + width - 66, y + 30, 60, 13, true, () -> action(AwPayloads.JournalAction.CHOICE, 3, pending), null, mx, my);
			y += 52;
		}
		if (bonds.isEmpty() && pending < 0) {
			y = wrap(g, Component.translatable("journal.afterworld.no_bonds"), x, y, width, FADED) + 4;
		}
		for (int i = 0; i < bonds.size(); i++) {
			CompoundTag b = bonds.getCompoundOrEmpty(i);
			BondType type = BondType.byOrdinal(b.getIntOr("type", 0));
			boolean comp = b.getBooleanOr("complication", false);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("bond/" + type.id), x, y, 16, 16);
			g.text(font, type.displayName().copy().withColor(type.color), x + 20, y + 1, 0xFFFFFFFF, false);
			long due = b.getLongOr("due", 0);
			Component status = comp ? Component.translatable("journal.afterworld.bond_complication").withColor(0xFFA8432E)
				: Component.translatable("journal.afterworld.bond_due", Math.max(0, due));
			g.text(font, status, x + 20, y + 11, FADED, false);
			y += 22;
			y = wrap(g, Component.translatable("bond.afterworld." + type.id + ".service"), x + 4, y, width - 8, INK);
			y = wrap(g, Component.translatable("bond.afterworld." + type.id + ".obligation"), x + 4, y, width - 8, FADED) + 2;
			int ord = type.ordinal();
			button(g, Component.translatable("journal.afterworld.pay"), x + 4, y, 70, 13, true, () -> action(AwPayloads.JournalAction.PAY_BOND, ord, 0), null, mx, my);
			button(g, Component.translatable("journal.afterworld.release"), x + 80, y, 70, 13, true, () -> action(AwPayloads.JournalAction.RELEASE_BOND, ord, 0),
				List.of(Component.translatable("journal.afterworld.release_tip")), mx, my);
			y += 20;
		}
		y += 4;
		g.text(font, Component.translatable("journal.afterworld.factions"), x, y, AMBER, false);
		y += 12;
		ListTag factions = d.getListOrEmpty("factions");
		for (int i = 0; i < factions.size(); i++) {
			CompoundTag f = factions.getCompoundOrEmpty(i);
			Faction fac = Faction.byOrdinal(f.getIntOr("faction", 0));
			int rep = f.getIntOr("rep", 0);
			int tier = Faction.tier(rep);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("faction/" + fac.id), x, y, 16, 16);
			g.text(font, fac.displayName().copy().withColor(fac.color), x + 20, y, 0xFFFFFFFF, false);
			g.text(font, Component.translatable("journal.afterworld.standing", Faction.tierName(rep), rep), x + 20, y + 10, tier < 0 ? RED : INK, false);
			g.text(font, Component.translatable("journal.afterworld.influence"), x + 168, y, FADED, false);
			bar(g, x + 222, y + 2, 60, f.getIntOr("influence", 0), 0xFF000000 | fac.color);
			String op = f.getStringOr("op", "");
			g.text(font, trim(f.getStringOr("leader", ""), 120), x + 168, y + 10, FADED, false);
			y += 22;
			if (!op.isEmpty()) {
				g.text(font, Component.translatable("journal.afterworld.operation", trim(op, width - 40)), x + 20, y - 2, FADED, false);
				y += 10;
			}
			int claimed = f.getIntOr("claimed", 0);
			if (tier >= 1 && tier > claimed) {
				int ord = fac.ordinal();
				button(g, Component.translatable("journal.afterworld.claim_boon"), x + 20, y, 90, 13, true, () -> action(AwPayloads.JournalAction.CLAIM_BOON, ord, 0), null, mx, my);
				y += 17;
			}
			y += 3;
		}
		if (d.contains("settlement")) {
			CompoundTag s = d.getCompoundOrEmpty("settlement");
			y += 4;
			g.text(font, Component.translatable("journal.afterworld.nearby"), x, y, AMBER, false);
			y += 12;
			Faction patron = Faction.byOrdinal(s.getIntOr("patron", 0));
			g.text(font, Component.literal(s.getStringOr("name", "")).withStyle(ChatFormatting.BOLD), x, y, INK, false);
			g.text(font, Component.translatable("journal.afterworld.kept_by", s.getStringOr("leader", ""), patron.displayName()), x + 90, y, FADED, false);
			y += 12;
			String[] stats = {"safety", "prosperity", "legitimacy", "anomaly"};
			int[] colors = {0xFF5E7F3A, AMBER, 0xFF5B8DB8, 0xFF8E7CC3};
			for (int i = 0; i < stats.length; i++) {
				int sx = x + (i % 2) * 150;
				int sy = y + (i / 2) * 11;
				g.text(font, Component.translatable("journal.afterworld.stat." + stats[i]), sx, sy, FADED, false);
				bar(g, sx + 70, sy + 2, 70, s.getIntOr(stats[i], 0), colors[i]);
			}
			y += 24;
			y = wrap(g, Component.literal(s.getStringOr("words", "")), x, y, width, INK) + 4;
		}
		ListTag events = d.getListOrEmpty("events");
		if (!events.isEmpty()) {
			g.text(font, Component.translatable("journal.afterworld.events"), x, y, AMBER, false);
			y += 12;
			for (int i = 0; i < events.size(); i++) {
				CompoundTag e = events.getCompoundOrEmpty(i);
				Component line = Component.translatable("event.afterworld." + e.getStringOr("kind", ""))
					.append(e.getStringOr("place", "").isEmpty() ? "" : " · " + e.getStringOr("place", ""));
				g.text(font, line, x, y, INK, false);
				y += 11;
			}
		}
		return y + 6;
	}

	// ------------------------------------------------------------------ codex

	private static final String[][] CODEX = {
		{"edicts", "edict.return", "edict.echoes", "edict.borrowed_time", "edict.weightlessness", "edict.hunger", "edict.hospitality", "edict.trespass", "edict.stillness", "edict.witness", "edict.forgotten"},
		{"combos", "combo.echoed_edict", "combo.returning_echo", "combo.gravity_orbit", "combo.frozen_volley", "combo.suspended_sentence", "combo.rewind_refund", "combo.kept_promise", "combo.ambush", "combo.rule_collision"},
		{"disciplines", "discipline.vanguard", "discipline.vanguard.parry", "discipline.vanguard.last_word", "discipline.wayfarer", "discipline.wayfarer.wrong_turn", "discipline.arcanist", "discipline.keeper", "discipline.keeper.favour"},
		{"places", "place.settlement", "place.shrine", "place.anomaly", "place.lair", "place.cache", "place.unremembered", "place.cathedral", "place.vault"},
		{"anomalies", "anomaly.choice", "anomaly.sealed", "anomaly.tapped", "anomaly.weightlessness", "anomaly.echoes", "anomaly.stillness", "anomaly.witness", "anomaly.forgotten", "anomaly.hunger", "anomaly.return", "anomaly.hospitality"},
		{"relics", "relic.unfinished_compass", "relic.hungry_lantern", "relic.clockwork_heart", "relic.clockwork_heart.overheat", "relic.apologetic_blade", "relic.apologetic_blade.conscience", "relic.thimble"},
		{"bonds", "bond.landlord", "bond.landlord.tenancy", "bond.moth", "bond.pilgrim"},
		{"people", "people.isolde", "people.bram", "people.wren", "people.marta", "people.selene", "people.odo", "people.mab", "people.torvald", "people.ines", "people.rashid", "people.leyla", "people.akane"},
		{"actors", "boss.pilgrim", "pilgrim.shrine_repaired", "boss.pilgrim.peace", "boss.bell_ringer", "boss.bell_ringer.name", "boss.bell_ringer.counterweight", "boss.king", "boss.king.bargain", "boss.lich.phylactery", "boss.ashen_shogun", "boss.crimson_lich", "boss.rimeback_colossus", "boss.dune_sentinel", "boss.gilded_warden", "npc.roadwright", "npc.archivist", "npc.landlord"},
		{"vault", "vault.entered", "vault.adapt.mirrored", "vault.adapt.spiked", "vault.adapt.anchored", "vault.adapt.warded", "vault.adapt.listening", "vault.adapt.expected"},
		{"wonders", "wonder.titan", "wonder.angels", "wonder.cathedral", "wonder.citadel", "wonder.mill"},
		{"landmarks", "wonder.waystone", "wonder.moon_gazebo", "wonder.star_spire", "wonder.icyshroom", "wonder.lodge", "wonder.chapel", "wonder.oasis", "wonder.stilt_house", "wonder.airship"},
		{"sites", "wonder.caravan_wreck", "wonder.buried_obelisk", "wonder.sunken_bog_temple", "wonder.frozen_mage_tower", "wonder.overgrown_ziggurat", "wonder.moonlit_clearing", "wonder.barrow_mound", "wonder.ossuary_gate", "wonder.drowned_shrine", "wonder.lighthouse", "wonder.beached_wreck", "wonder.ember_kilns", "wonder.cinder_watchtower", "wonder.bone_orchard", "wonder.weavers_hollow", "wonder.hanged_lanterns", "wonder.crystal_observatory", "wonder.shattered_caravan", "wonder.tea_house", "wonder.lantern_dojo", "wonder.frozen_watch_post", "wonder.colossus_graveyard", "wonder.snowbound_camp", "wonder.rime_cavern", "wonder.aurora_shrine", "wonder.peak_monastery", "wonder.great_nest", "wonder.wind_harp", "wonder.nomad_yurts", "wonder.starfall_crater", "wonder.abandoned_mine", "wonder.canyon_outpost", "wonder.canopy_outpost", "wonder.spore_lab", "wonder.glowcap_grotto", "wonder.ranger_outpost", "wonder.druids_ring", "wonder.hermits_bough", "wonder.crimson_necropolis", "wonder.rimeback_den", "wonder.sun_temple", "wonder.gilded_vault"},
		{"rules", "rule.oathbreaker", "rumour.first", "rumour.outdated", "rumour.false", "event.silent_caravan", "quest.unremembered"}
	};

	private int codex(GuiGraphicsExtractor g, CompoundTag d, int x, int y, int mx, int my) {
		ListTag list = d.getListOrEmpty("codex");
		java.util.Set<String> known = new java.util.HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			known.add(list.getStringOr(i, ""));
		}
		int width = W - 50;
		g.text(font, Component.translatable("journal.afterworld.discoveries", known.size()), x, y, FADED, false);
		y += 14;
		for (String[] group : CODEX) {
			g.text(font, Component.translatable("journal.afterworld.codex." + group[0]), x, y, AMBER, false);
			y += 11;
			int col = 0;
			int rowY = y;
			for (int i = 1; i < group.length; i++) {
				String id = group[i];
				boolean has = known.contains(id);
				int cx = x + col * 150;
				Component title = has ? Component.translatable("codex.afterworld." + id) : Component.literal("? ? ?");
				g.text(font, Component.literal(has ? "◆ " : "◇ ").append(title), cx, rowY, has ? INK : 0xFFA89A7C, false);
				List<Component> tip = has ? List.of(Component.translatable("codex.afterworld." + id).withStyle(ChatFormatting.GOLD), Component.translatable("codex.afterworld." + id + ".desc"))
					: List.of(Component.translatable("journal.afterworld.undiscovered"), Component.translatable("codex.afterworld." + id + ".hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				String finalId = id;
				hit(cx, rowY, 146, 10, has ? () -> codexOpen = codexOpen.equals(finalId) ? "" : finalId : null, tip);
				col++;
				if (col == 2) {
					col = 0;
					rowY += 11;
				}
			}
			y = rowY + (col == 0 ? 0 : 11) + 6;
			for (int i = 1; i < group.length; i++) {
				if (group[i].equals(codexOpen)) {
					y = wrap(g, Component.translatable("codex.afterworld." + codexOpen + ".desc"), x + 8, y, width - 8, INK) + 6;
				}
			}
		}
		return y;
	}

	// ------------------------------------------------------------------ widgets

	/** How many Chronicle Pages the player is carrying. */
	private static int pagesHeld() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(dev.afterworld.registry.AwItems.CHRONICLE_PAGE)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	/** For tests: the open section, and the middle of a section's ribbon in screen (GUI) coordinates. */
	public int tabForTest() {
		return tab;
	}

	public double[] ribbonForTest(int i) {
		double vx = left + 14 + i * 40 + 14;
		double vy = top - 10;
		return new double[] {ox + vx * scale, oy + vy * scale};
	}

	/** A clickable spot inside the scrolling page; spots scrolled out of view are skipped. */
	private void hit(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
		boolean outsideView = (y + h <= viewTop || y >= viewBottom) && x >= left;
		if (outsideView) {
			return;
		}
		hits.add(new Hit(x, y, w, h, action, tooltip));
	}

	/** A clickable spot on the book itself (the ribbons), which never scrolls. */
	private void hitFixed(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
		hits.add(new Hit(x, y, w, h, action, tooltip));
	}

	private void button(GuiGraphicsExtractor g, Component label, int x, int y, int w, int h, boolean enabled, Runnable action, List<Component> tooltip, int mx, int my) {
		boolean hover = enabled && mx >= x && my >= y && mx < x + w && my < y + h;
		g.fill(x, y, x + w, y + h, enabled ? (hover ? 0xFF8A6A3E : 0xFF6E5432) : 0xFFB3A585);
		g.outline(x, y, w, h, enabled ? 0xFF3B2A18 : 0xFF9C8E6E);
		Component shown = trimC(label, w - 6);
		g.text(font, shown, x + w / 2 - font.width(shown) / 2, y + (h - 8) / 2, enabled ? 0xFFF1E6CC : 0xFF7A6C58, enabled);
		hit(x, y, w, h, enabled ? action : null, tooltip);
	}

	private void centered(GuiGraphicsExtractor g, String text, int cx, int y, int color) {
		g.text(font, text, cx - font.width(text) / 2, y, color, false);
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, int w, int value, int color) {
		g.fill(x, y, x + w, y + 5, 0x40000000);
		g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0, 100) / 100F), y + 5, color);
	}

	private int wrap(GuiGraphicsExtractor g, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	private String trim(String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
	}

	private Component trimC(Component c, int width) {
		String s = c.getString();
		return font.width(s) <= width ? c : Component.literal(trim(s, width));
	}
}
