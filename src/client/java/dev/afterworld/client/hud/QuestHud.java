package dev.afterworld.client.hud;

import dev.afterworld.Afterworld;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.net.AwPayloads.QuestSync.Entry;
import dev.afterworld.registry.AwAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The Path on screen. Top right, the tracker: the chapter on one quiet line; then the objective in
 * hand, written large, with an arrow that turns to point the way and how far it is; then the rest
 * of the chapter's objectives, smaller. New objectives slide in marked new; finished ones are
 * ticked, glow and go. The keys for the Journal and for the next target show for a while whenever
 * something changes. In the world: the focused objective's place is marked by a diamond and its
 * distance (raised clear of a keeper's name plate when it is close, never written over one), and
 * when the place is off screen an arrow on a ring round the crosshair points the way to turn; a
 * pillar of light stands on it (see {@link dev.afterworld.client.fx.QuestBeam}). Looking at someone
 * who'll talk shows how.
 */
public final class QuestHud {
	private static final Identifier ARROW = Afterworld.id("hud/arrow");
	public static Component head = Component.empty();
	public static Component title = Component.empty();
	public static final List<Entry> ENTRIES = new ArrayList<>();
	/** Which pointed-at entry the marker follows; cycled with the focus key. */
	public static int focus;
	private static long changedAt;
	private static long focusAt;
	/** When each objective (by kind and words) was first seen, and when it was finished: for the flourishes. */
	private static final Map<String, Long> SEEN = new HashMap<>();
	private static final Map<String, Long> DONE_AT = new HashMap<>();
	private static boolean synced;
	/** The last frame's camera, captured during world rendering, for projecting places to the screen. */
	public static final Matrix4f VIEW_PROJ = new Matrix4f();
	public static Vec3 cameraPos = Vec3.ZERO;
	public static boolean cameraValid;

	private static final int MIN_WIDTH = 150;
	private static final int MAX_WIDTH = 206;
	private static final long NEW_FOR = 4000;
	private static final long DONE_FOR = 4500;
	private static final long HINT_FOR = 9000;
	private static final int CREAM = 0xFFF6EDDC;
	private static final int SOFT = 0xFFCDC3B2;
	private static final int DIM = 0xFF8A8478;
	private static final int GOLD = 0xFFB8935A;

	private QuestHud() {
	}

	public static void apply(AwPayloads.QuestSync q) {
		boolean newChapter = !q.title().getString().equals(title.getString());
		head = q.head();
		title = q.title();
		ENTRIES.clear();
		ENTRIES.addAll(q.entries());
		long now = Util.getMillis();
		for (Entry e : ENTRIES) {
			// What was already there when the traveller came in is not news.
			SEEN.putIfAbsent(key(e), synced ? now : 0L);
			if (e.done()) {
				DONE_AT.putIfAbsent(key(e), synced ? now : 0L);
			}
		}
		synced = true;
		if (newChapter) {
			focus = 0;
		}
		changedAt = now;
	}

	private static String key(Entry e) {
		return e.kind() + "|" + e.text().getString();
	}

	public static void reset() {
		ENTRIES.clear();
		head = Component.empty();
		title = Component.empty();
		cameraValid = false;
		SEEN.clear();
		DONE_AT.clear();
		synced = false;
	}

	/** The entries that point somewhere, in focus order (open main objectives first). */
	public static List<Entry> pointed() {
		List<Entry> out = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.hasTarget() && !e.done()) {
				out.add(e);
			}
		}
		return out;
	}

	public static Entry focused() {
		List<Entry> p = pointed();
		if (p.isEmpty()) {
			return null;
		}
		return p.get(Math.floorMod(focus, p.size()));
	}

	public static void cycleFocus() {
		focus++;
		focusAt = Util.getMillis();
	}

	/** Where the focused place is, with the ground found for places given as "on the surface there". */
	public static Vec3 target(Entry e) {
		Minecraft mc = Minecraft.getInstance();
		double y = e.y();
		if (e.y() == Integer.MIN_VALUE) {
			y = mc.level != null && mc.level.hasChunk(e.x() >> 4, e.z() >> 4)
				? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.x(), e.z())
				: mc.player != null ? mc.player.getY() : 64;
		}
		return new Vec3(e.x() + 0.5, y, e.z() + 0.5);
	}

	public static boolean sameDimension(Entry e) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().identifier().toString().equals(e.dim());
	}

	public static int color(Entry e) {
		return switch (e.kind()) {
			case AwPayloads.QuestSync.MARK -> 0x7FC8E8;
			case AwPayloads.QuestSync.LEAD -> 0xC9B98F;
			case AwPayloads.QuestSync.CONTRACT -> 0xE8A33D;
			default -> 0xE8C873;
		};
	}

	/** Which way a place lies from where the traveller is looking, in degrees: 0 ahead, +90 to the right. */
	public static float bearing(Minecraft mc, Vec3 at) {
		double dx = at.x - mc.player.getX();
		double dz = at.z - mc.player.getZ();
		float toward = (float) Math.toDegrees(Math.atan2(-dx, dz));
		return Mth.wrapDegrees(toward - mc.player.getYRot());
	}

	// ------------------------------------------------------------------ the tracker

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
		if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
			return;
		}
		talkPrompt(g, mc);
		if (ENTRIES.isEmpty() && title.getString().isEmpty()) {
			panelBottom = 0;
			return;
		}
		marker(g, mc);
		panel(g, mc);
	}

	private static float fade = 1;
	/** Where the panel was drawn last frame (gui coordinates), so other HUD text can keep clear of it. */
	public static int panelLeft;
	public static int panelBottom;

	/** The objective in hand: the focused one, else the first open main objective. */
	private static Entry lead() {
		Entry f = focused();
		if (f != null) {
			return f;
		}
		for (Entry e : ENTRIES) {
			if (!e.done()) {
				return e;
			}
		}
		return null;
	}

	/** How long ago an objective was finished, or -1 if it is open. */
	private static long doneFor(Entry e, long now) {
		if (!e.done()) {
			return -1;
		}
		return now - DONE_AT.getOrDefault(key(e), 0L);
	}

	private static void panel(GuiGraphicsExtractor g, Minecraft mc) {
		// The panel steps aside while a boss's title card fills the top of the screen.
		fade = 1 - TitleCards.bossCard();
		panelBottom = 0;
		if (fade < 0.04F) {
			return;
		}
		Font font = mc.font;
		long now = Util.getMillis();
		int sw = HudScale.width(g);
		Entry lead = lead();
		List<Entry> rest = new ArrayList<>();
		for (Entry e : ENTRIES) {
			long done = doneFor(e, now);
			if (e != lead && (done < 0 || done < DONE_FOR)) {
				rest.add(e);
			}
		}
		Component header = header();
		int width = Math.max(MIN_WIDTH, font.width(header) + 6);
		if (lead != null) {
			width = Math.max(width, Math.min(MAX_WIDTH, font.width(display(lead)) + 8));
		}
		for (Entry e : rest) {
			width = Math.max(width, Math.min(MAX_WIDTH, font.width(display(e)) + 20));
		}
		width = Math.min(width, MAX_WIDTH);
		int x = sw - width - 10;
		int y = 9;
		// Below the boss bars when the panel would reach under them (vanilla draws them 19 apart from 12).
		int bars = ((dev.afterworld.client.mixin.BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).afterworld$events().size();
		if (bars > 0 && x - 10 < sw / 2 + HudScale.fromGui(93)) {
			y = Math.min(HudScale.fromGui(12 + bars * 19), HudScale.height(g) / 3 + 10) + 2;
		}

		// Measure first, so the backdrop fits what it holds.
		List<FormattedCharSequence> leadLines = lead == null ? List.of() : cap(font.split(display(lead), width - 4), 3);
		boolean chip = lead != null && lead.hasTarget() && !lead.done();
		int h = 14;
		if (lead != null) {
			h += leadLines.size() * 10 + (chip ? 12 : 0) + 5;
		}
		List<List<FormattedCharSequence>> restLines = new ArrayList<>();
		for (Entry e : rest) {
			List<FormattedCharSequence> ls = cap(font.split(display(e), width - 14), 2);
			restLines.add(ls);
			h += ls.size() * 10 + 2;
		}
		float hint = Mth.clamp(1 - (now - Math.max(changedAt, focusAt) - HINT_FOR) / 800.0F, 0, 1);
		if (hint > 0) {
			h += 11;
		}
		panelLeft = x - 12;
		panelBottom = y + h;
		backdrop(g, x - 12, y - 6, x + width + 6, y + h + 1);

		g.text(font, header, x, y, fa(GOLD), false);
		int ry = y + 14;
		if (lead != null) {
			int col = color(lead);
			float flash = Mth.clamp(1 - (now - changedAt) / 1200.0F, 0, 1);
			int top = ry;
			for (FormattedCharSequence line : leadLines) {
				g.text(font, line, x, ry, fa(lead.done() ? DIM : CREAM), true);
				ry += 10;
			}
			if (chip) {
				chip(g, mc, font, lead, x, ry);
				ry += 12;
			}
			// The objective in hand is marked by a bar of its colour, bright for a moment when it changes.
			g.fill(x - 6, top - 1, x - 4, ry - 2, fa(ARGB.color((int) (170 + 85 * flash), col)));
			ry += 5;
		}
		for (int i = 0; i < rest.size(); i++) {
			Entry e = rest.get(i);
			int col = color(e);
			long done = doneFor(e, now);
			long seen = now - SEEN.getOrDefault(key(e), 0L);
			// New ones slide in from the right; finished ones glow, then fade away.
			float in = Mth.clamp(seen / 450.0F, 0, 1);
			int slide = Math.round((1 - in) * (1 - in) * 24);
			float alpha = done < 0 ? in : Mth.clamp((DONE_FOR - done) / 1500.0F, 0, 1);
			int ex = x + slide;
			boolean fresh = done < 0 && seen < NEW_FOR;
			if (done >= 0) {
				check(g, ex + 3, ry + 4, ARGB.color((int) (255 * fade * alpha), done < 900 ? 0xFFE8C873 : col));
			} else if (fresh) {
				// Newly given: its bullet glows gold for a few seconds.
				float glow = 0.6F + 0.4F * (float) Math.sin(now / 150.0);
				diamond(g, ex + 3, ry + 4, 3, ARGB.color((int) (255 * fade * alpha * glow), 0xE8C873));
			} else {
				hollowDiamond(g, ex + 3, ry + 4, 3, ARGB.color((int) (230 * fade * alpha), col));
			}
			int textCol = done >= 0 ? DIM : fresh ? CREAM : SOFT;
			for (FormattedCharSequence line : restLines.get(i)) {
				g.text(font, line, ex + 11, ry, ARGB.color((int) (ARGB.alpha(textCol) * fade * alpha), textCol), true);
				ry += 10;
			}
			ry += 2;
		}
		if (hint > 0) {
			Component keys = Component.translatable("hud.afterworld.quest.hint", AfterworldClient.JOURNAL.getTranslatedKeyMessage(),
				AfterworldClient.FOCUS.getTranslatedKeyMessage());
			g.text(font, keys, x + width - font.width(keys), ry + 1, ARGB.color((int) (150 * fade * hint), 0xC8BEB0), false);
		}
	}

	/** The chapter as one quiet line: "PROLOGUE · THE WAYSTONE CAMP". */
	private static Component header() {
		String h = head.getString();
		String t = title.getString();
		String line = h.isEmpty() ? t : t.isEmpty() ? h : h + "  ·  " + t;
		return Component.literal(line.toUpperCase(Locale.ROOT));
	}

	/** The way to the objective in hand: an arrow turned toward it and how far it is. */
	private static void chip(GuiGraphicsExtractor g, Minecraft mc, Font font, Entry e, int x, int y) {
		int col = ARGB.opaque(color(e));
		if (!sameDimension(e)) {
			g.text(font, Component.translatable("hud.afterworld.quest.elsewhere"), x, y, fa(ARGB.color(200, col)), true);
			return;
		}
		Vec3 at = target(e);
		double d = Math.hypot(at.x - mc.player.getX(), at.z - mc.player.getZ());
		if (d < 4) {
			g.text(font, Component.translatable("hud.afterworld.quest.here"), x, y, fa(col), true);
			return;
		}
		arrow(g, x + 4, y + 3.5F, 9, bearing(mc, at), fa(col));
		g.text(font, Component.literal(distanceText(d)), x + 12, y, fa(col), true);
	}

	/** The way-finding arrow, centred on (cx, cy) and turned {@code degrees} clockwise from up. */
	static void arrow(GuiGraphicsExtractor g, float cx, float cy, int size, float degrees, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate((float) Math.toRadians(degrees));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, -size / 2, -size / 2, size, size, color);
		pose.popMatrix();
	}

	/** A soft dark backdrop that fades in from its left edge rather than ending in a box. */
	private static void backdrop(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int body = (int) (132 * fade);
		for (int i = 0; i < 6; i++) {
			g.fill(x0 + i * 2, y0, x0 + i * 2 + 2, y1, ARGB.color(body * (i + 1) / 7, 0x0E0B14));
		}
		g.fill(x0 + 12, y0, x1, y1, ARGB.color(body, 0x0E0B14));
		g.fill(x0 + 12, y0, x1, y0 + 1, ARGB.color((int) (110 * fade), 0xE8C873));
	}

	private static List<FormattedCharSequence> cap(List<FormattedCharSequence> lines, int most) {
		return lines.size() <= most ? lines : lines.subList(0, most);
	}

	/** A colour at the panel's present fade. */
	private static int fa(int argb) {
		return ARGB.color((int) (ARGB.alpha(argb) * fade), argb);
	}

	/** An objective as the tracker writes it, with its count when it has one. */
	private static Component display(Entry e) {
		if (e.need() > 1 && !e.done()) {
			return e.text().copy().append(Component.literal("  " + e.have() + "/" + e.need()).withColor(0xE8C873));
		}
		return e.text();
	}

	static String distanceText(double d) {
		return d >= 1000 ? String.format(Locale.ROOT, "%.1f km", d / 1000) : (int) d + " m";
	}

	private static void hollowDiamond(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			g.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, color);
			g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	/** A small tick. */
	private static void check(GuiGraphicsExtractor g, int cx, int cy, int color) {
		g.fill(cx - 3, cy, cx - 2, cy + 1, color);
		g.fill(cx - 2, cy + 1, cx - 1, cy + 2, color);
		g.fill(cx - 1, cy + 2, cx, cy + 3, color);
		g.fill(cx, cy + 1, cx + 1, cy + 2, color);
		g.fill(cx + 1, cy, cx + 2, cy + 1, color);
		g.fill(cx + 2, cy - 1, cx + 3, cy, color);
		g.fill(cx + 3, cy - 2, cx + 4, cy - 1, color);
	}

	// ------------------------------------------------------------------ the marker

	private static void marker(GuiGraphicsExtractor g, Minecraft mc) {
		Entry e = focused();
		if (e == null || !sameDimension(e) || !cameraValid || mc.player == null) {
			return;
		}
		Vec3 base = target(e);
		double dist = Math.hypot(base.x - mc.player.getX(), base.z - mc.player.getZ());
		if (dist < 4) {
			// There: the tracker says so, and nothing need hang over the place.
			return;
		}
		// Close by, the diamond rises clear of a name plate over the place (a keeper's, say).
		Vec3 at = base.add(0, dist < 28 ? 3.4 : 2.2, 0);
		Vec3 rel = at.subtract(cameraPos);
		Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
		VIEW_PROJ.transform(clip);
		int w = HudScale.width(g);
		int h = HudScale.height(g);
		int col = color(e);
		boolean behind = clip.w <= 0.05F;
		float sx = (clip.x / Math.abs(clip.w) * 0.5F + 0.5F) * w;
		float sy = (1 - (clip.y / Math.abs(clip.w) * 0.5F + 0.5F)) * h;
		int margin = 24;
		Font font = mc.font;
		if (!behind && sx >= margin && sx <= w - margin && sy >= margin && sy <= h - margin) {
			float pulse = 0.75F + 0.25F * (float) Math.sin(Util.getMillis() / 260.0);
			int cx = Math.round(sx);
			int cy = Math.round(sy);
			diamond(g, cx, cy, 6, ARGB.color(170, 0x101216));
			diamond(g, cx, cy, 5, ARGB.color((int) (255 * pulse), col));
			diamond(g, cx, cy, 2, 0xFFFFF4DC);
			if (dist >= 4) {
				String label = distanceText(dist);
				int lw = font.width(label);
				g.text(font, label, cx - lw / 2 + 1, cy + 10, 0x90000000, false);
				g.text(font, label, cx - lw / 2, cy + 9, ARGB.opaque(col), false);
			}
			return;
		}
		// Off screen: an arrow on a ring round the crosshair, pointing the way to turn (stepping back
		// while a title card fills the middle of the screen).
		float clear = 1 - TitleCards.card();
		if (clear < 0.05F) {
			return;
		}
		float turn = bearing(mc, base);
		float rx = Math.min(w * 0.32F, 220);
		float ry = Math.min(h * 0.30F, 150);
		float ax = w / 2.0F + Mth.sin(turn * Mth.DEG_TO_RAD) * rx;
		float ay = h / 2.0F - Mth.cos(turn * Mth.DEG_TO_RAD) * ry;
		float pulse = 0.8F + 0.2F * (float) Math.sin(Util.getMillis() / 220.0);
		arrow(g, ax, ay, 15, turn, ARGB.color((int) (235 * pulse * clear), col));
		String label = distanceText(dist);
		int lw = font.width(label);
		float lx = w / 2.0F + Mth.sin(turn * Mth.DEG_TO_RAD) * (rx - 16);
		float ly = h / 2.0F - Mth.cos(turn * Mth.DEG_TO_RAD) * (ry - 14);
		g.text(font, label, Math.round(lx - lw / 2.0F), Math.round(ly - 4), ARGB.color((int) (255 * clear), col), true);
	}

	// ------------------------------------------------------------------ talking

	private static void talkPrompt(GuiGraphicsExtractor g, Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (!(hit instanceof EntityHitResult eh) || mc.gui.screen() != null) {
			return;
		}
		Entity e = eh.getEntity();
		String id = e.getAttached(AwAttachments.NPC);
		if (id == null || e.distanceToSqr(mc.player) > 36) {
			return;
		}
		Component key = mc.options.keyUse.getTranslatedKeyMessage();
		Component text = Component.translatable("hud.afterworld.talk", key, e.getDisplayName());
		int w = HudScale.width(g);
		int h = HudScale.height(g);
		int tw = mc.font.width(text);
		g.fill(w / 2 - tw / 2 - 5, h / 2 + 12, w / 2 + tw / 2 + 5, h / 2 + 25, 0x90101216);
		g.centeredText(mc.font, text, w / 2, h / 2 + 15, 0xFFE8DCC0);
	}
}
