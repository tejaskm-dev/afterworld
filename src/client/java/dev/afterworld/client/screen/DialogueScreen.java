package dev.afterworld.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A conversation, staged like a story game's: letterbox bars slide in and the view turns to the
 * speaker; their portrait (their own avatar, from the chest up) stands at the left of a framed
 * box, with their name on a tab and how they say it ("softly") beside it; their words type out
 * exactly as the voice speaks them, pausing where the voice pauses. When it's your turn, your
 * own portrait steps in at the right with the answers beside it.
 *
 * <p>Space or click: hurry the line, then go on. A: let lines play on their own. L: look back over
 * what's been said. 1-9: answer.
 *
 * <p>The box grows to hold the whole line (up to about two fifths of the screen; a longer line
 * scrolls as it is spoken), and long answers wrap rather than being cut.
 */
public final class DialogueScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int TEXT = 0xFFF0E8D4;
	private static final int DIM = 0xFF9A9384;
	/** Lines play on by themselves (kept between conversations). */
	private static boolean auto;
	/** What's been said in this conversation, for the log. */
	private final List<Component> history = new ArrayList<>();
	private AwPayloads.DialogueShow page;
	private int line;
	private long lineStart;
	private long openedAt = Util.getMillis();
	private long answerAt = -1;
	private SoundInstance voice;
	private boolean closing;
	private boolean showLog;
	private int logScroll;
	private int lastBlip;

	public DialogueScreen(AwPayloads.DialogueShow page) {
		super(Component.literal(page.name()));
		this.page = page;
	}

	public int entity() {
		return this.page.entity();
	}

	/** The next page of the same conversation. */
	public void show(AwPayloads.DialogueShow next) {
		this.page = next;
		this.line = 0;
		this.answerAt = -1;
		startLine();
	}

	@Override
	protected int prefWidth() {
		return 584;
	}

	@Override
	protected int prefHeight() {
		return 300;
	}

	/** Conversations are the main thing to read: the box spans most of the screen, as in story games. */
	@Override
	protected float widthShare() {
		return 0.9F;
	}

	@Override
	protected float heightShare() {
		return 0.9F;
	}

	@Override
	protected void init() {
		super.init();
		if (this.lineStart == 0) {
			startLine();
		}
	}

	private void startLine() {
		this.lineStart = Util.getMillis();
		this.lastBlip = 0;
		stopVoice();
		AwPayloads.DialogueShow.Line l = current();
		if (l == null) {
			return;
		}
		this.history.add(Component.literal(this.page.name() + ": ").withColor(this.page.color()).append(Component.literal(l.text()).withColor(0xE8DCC0)));
		// The conversation has the floor: the narrator or a great power mid-line waits until it ends.
		dev.afterworld.client.hud.Voices.hush();
		if (!l.sound().isEmpty() && dev.afterworld.AwConfig.get().voices) {
			this.voice = new SimpleSoundInstance(Identifier.parse(l.sound()), SoundSource.VOICE, 1.0F, 1.0F,
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
			this.minecraft.getSoundManager().play(this.voice);
			dev.afterworld.client.hud.Voices.conversationSpeaks();
		}
	}

	private void stopVoice() {
		if (this.voice != null) {
			this.minecraft.getSoundManager().stop(this.voice);
			this.voice = null;
		}
	}

	@Override
	public void removed() {
		stopVoice();
		super.removed();
	}

	@Override
	public void onClose() {
		if (!this.closing) {
			this.closing = true;
			ClientPlayNetworking.send(new AwPayloads.DialogueChoose(this.page.entity(), -1));
		}
		super.onClose();
	}

	/** Called when the server ends the conversation: close without answering. */
	public void closeQuietly() {
		this.closing = true;
		super.onClose();
	}

	// ------------------------------------------------------------------ timing

	private AwPayloads.DialogueShow.Line current() {
		List<AwPayloads.DialogueShow.Line> lines = this.page.lines();
		return this.line < lines.size() ? lines.get(this.line) : null;
	}

	private long elapsed() {
		return Util.getMillis() - this.lineStart;
	}

	/** How long the line's words take to appear: in step with the voice, or at a reading pace. */
	private long typeMillis() {
		AwPayloads.DialogueShow.Line l = current();
		if (l == null) {
			return 0;
		}
		if (l.ticks() > 0) {
			return !l.beats().isEmpty() ? l.beats().get(l.beats().size() - 1) : Math.max(300, (long) (l.ticks() * 50 * 0.92));
		}
		return Math.max(300, l.text().length() * 24L);
	}

	/** How long the line lasts in all: the voice's own length, or time to read it. */
	private long lineMillis() {
		AwPayloads.DialogueShow.Line l = current();
		if (l == null) {
			return 0;
		}
		return l.ticks() > 0 ? l.ticks() * 50L : typeMillis() + 900 + l.text().length() * 12L;
	}

	private float revealed() {
		AwPayloads.DialogueShow.Line l = current();
		return l == null ? 1 : l.revealed(elapsed(), typeMillis());
	}

	private boolean typed() {
		return revealed() >= 1.0F || elapsed() >= typeMillis();
	}

	private boolean speaking() {
		return this.voice != null && elapsed() < lineMillis() - 150;
	}

	private boolean lastLine() {
		return this.line >= this.page.lines().size() - 1;
	}

	private boolean answering() {
		return lastLine() && typed();
	}

	private void advance() {
		if (!typed()) {
			// Hurry the words; the voice carries on.
			this.lineStart = Util.getMillis() - Math.max(typeMillis(), 1) - 1;
			return;
		}
		if (!lastLine()) {
			this.line++;
			startLine();
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_SELECT.value(), 1.5F, 0.15F));
			return;
		}
		if (this.page.options().isEmpty()) {
			onClose();
		}
	}

	private void choose(int i) {
		if (i < 0 || i >= this.page.options().size()) {
			return;
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_SELECT.value(), 1.0F, 0.4F));
		this.history.add(Component.literal("» " + this.page.options().get(i)).withColor(0xE8A33D));
		stopVoice();
		ClientPlayNetworking.send(new AwPayloads.DialogueChoose(this.page.entity(), i));
	}

	// ------------------------------------------------------------------ the view

	@Override
	public void tick() {
		// Turn to face whoever is speaking.
		Entity speaker = speaker();
		var player = this.minecraft.player;
		if (speaker != null && player != null) {
			Vec3 to = speaker.getEyePosition().subtract(player.getEyePosition());
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
			float dy = Mth.wrapDegrees(yaw - player.getYRot());
			player.setYRot(player.getYRot() + dy * 0.25F);
			player.setXRot(player.getXRot() + (pitch + 4.0F - player.getXRot()) * 0.25F);
			player.setYHeadRot(player.getYRot());
		}
		// Unvoiced lines get a soft typing blip as their words appear.
		AwPayloads.DialogueShow.Line l = current();
		if (l != null && l.sound().isEmpty() && !typed()) {
			int shown = (int) (l.text().length() * revealed());
			if (shown - this.lastBlip >= 3) {
				this.lastBlip = shown;
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_TYPE.value(), 1.3F, 0.18F));
			}
		}
		// Auto: once the line is said (and a breath has passed), go on.
		if (auto && !lastLine() && typed() && elapsed() > lineMillis() + 650) {
			advance();
		}
		if (answering() && this.answerAt < 0) {
			this.answerAt = Util.getMillis();
		}
	}

	private Entity speaker() {
		return this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.page.entity());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		// No blur: the world is the backdrop.
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int w = this.cw;
		int h = this.ch;
		long now = Util.getMillis();
		float t = now / 1000.0F;
		float open = ease(Math.min(1.0F, (now - this.openedAt) / 450.0F));
		int color = this.page.color();
		// Letterbox.
		int bar = (int) (h * 0.075F * open);
		g.fill(0, 0, w, bar, 0xFF050506);
		g.fill(0, h - bar, w, h, 0xFF050506);
		g.fillGradient(0, (int) (h * 0.55F), w, h - bar, ARGB.color(0, 0x07080A), ARGB.color((int) (190 * open), 0x07080A));

		// The box: tall enough for the whole line, up to about two fifths of the screen.
		int boxW = Math.min(560, w - 24);
		int boxX = (w - boxW) / 2;
		// The portrait's size follows the screen: about a third of its height, never wider than a quarter of the box.
		int portraitH = Mth.clamp((int) (h * 0.36F), 84, 132);
		int portraitW = Math.min((int) (portraitH * 0.82F), boxW / 4);
		int textX = boxX + portraitW + 16;
		int textW = boxX + boxW - 14 - textX;
		if (answering() && !this.page.options().isEmpty()) {
			// Your own portrait steps in at the right: the words make room for it.
			textW -= Math.min(96, boxW / 5) + 4;
		}
		AwPayloads.DialogueShow.Line l = current();
		List<int[]> spans = new ArrayList<>();
		if (l != null) {
			this.font.getSplitter().splitLines(l.text(), textW, Style.EMPTY, false, (st, from, to) -> spans.add(new int[] {from, to}));
		}
		int rows = Mth.clamp(spans.size(), 4, Math.max(4, (int) ((h * 0.42F - 20) / 11)));
		int boxH = Math.max(74, 20 + rows * 11);
		int boxY = h - bar - boxH - 10 + (int) ((1 - open) * 30);
		frame(g, boxX, boxY, boxW, boxH, color, open);

		// The speaker's portrait, standing at the left of the box.
		Entity sp = speaker();
		if (sp instanceof LivingEntity le) {
			int px0 = boxX + 6;
			int py1 = boxY + boxH - 4;
			Portraits.halo(g, px0 + portraitW / 2, py1 - portraitH / 2 - 6, portraitW / 2 + 6, color, open * (answering() ? 0.4F : 0.9F));
			Portraits.bust(g, le, px0, py1 - portraitH, px0 + portraitW, py1, 24.0F, speaking() ? 1.0F : 0.0F, t);
		}

		// The name on its tab, the delivery cue beside it.
		Component name = Component.literal(this.page.name()).withStyle(s -> s.withFont(DISPLAY));
		int nameW = (int) (this.font.width(name) * 1.35F);
		int tabX = textX - 4;
		int tabY = boxY - 15;
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, Frames.INSET, tabX, tabY - 2, nameW + 10, 17, open);
		g.fill(tabX + 3, tabY + 13, tabX + nameW + 7, tabY + 14, ARGB.color((int) (255 * open), color));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(tabX + 5, tabY + 3);
		pose.scale(1.35F, 1.35F);
		g.text(this.font, name, 0, 0, ARGB.color((int) (255 * open), color), true);
		pose.popMatrix();
		int roleX = tabX + nameW + 16;
		int roleRoom = boxX + boxW - 6 - roleX;
		if (roleRoom >= 30) {
			g.text(this.font, fit(Component.literal(this.page.role()).withStyle(s -> s.withItalic(true)), roleRoom), roleX, tabY + 5, ARGB.color((int) (200 * open), 0xA89E8A), true);
		}
		if (l != null && !l.cue().isEmpty() && !answering()) {
			// Like a stage direction, by the speaker's shoulder.
			cue(g, l.cue(), boxX + 8 + portraitW - 10, boxY + boxH - 4 - portraitH + 10, open);
		}

		// The words, appearing as they are spoken: laid out for the whole line first, so a word never
		// jumps to the next line half-typed; a line longer than the box scrolls with the voice.
		if (l != null) {
			float frac = revealed();
			int shown = (int) Math.ceil(l.text().length() * frac);
			int cursorRow = 0;
			for (int i = 0; i < spans.size(); i++) {
				if (spans.get(i)[0] < shown) {
					cursorRow = i;
				}
			}
			int first = Math.max(0, Math.min(cursorRow - rows + 1, spans.size() - rows));
			int ty = boxY + 10;
			int alpha = answering() && !this.page.options().isEmpty() ? 150 : 255;
			for (int i = first; i < Math.min(spans.size(), first + rows); i++) {
				int[] span = spans.get(i);
				int end = Math.min(span[1], shown);
				if (end > span[0]) {
					g.text(this.font, l.text().substring(span[0], end), textX, ty, ARGB.color(alpha, Frames.PAPER_INK), false);
				}
				ty += 11;
			}
			if (typed() && !lastLine()) {
				// The "more" mark, bobbing.
				int mx0 = boxX + boxW - 14;
				int my0 = boxY + boxH - 12 + (int) (Mth.sin(t * 6) * 1.5F);
				for (int i = 0; i < 4; i++) {
					g.fill(mx0 - 3 + i, my0 + i, mx0 + 4 - i, my0 + i + 1, ARGB.color(230, color));
				}
			}
		}

		// Your turn: your own portrait steps in at the right, the answers beside it.
		if (answering() && !this.showLog) {
			float in = this.answerAt < 0 ? 0 : ease(Math.min(1.0F, (now - this.answerAt) / 300.0F));
			List<String> opts = this.page.options();
			if (opts.isEmpty()) {
				Component bye = fit(Component.translatable("dialogue.afterworld.farewell"), textW);
				g.text(this.font, bye, boxX + boxW - 12 - this.font.width(bye), boxY + boxH - 13, 0xFF9A4A10, false);
			} else {
				var me = this.minecraft.player;
				int myH = (int) (portraitH * 0.9F);
				int pw = Math.min((int) (myH * 0.82F), boxW / 5);
				int px1 = boxX + boxW - 6 + (int) ((1 - in) * 40);
				if (me != null) {
					Portraits.bust(g, me, px1 - pw, boxY + boxH - 4 - myH, px1, boxY + boxH - 4, -24.0F, 0.0F, t);
				}
				// Each answer wraps onto a second (or third) line rather than being cut.
				int optMax = boxW - pw - 30;
				List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
				int optW = 0;
				int total = 0;
				for (String o : opts) {
					List<FormattedCharSequence> ls = this.font.split(Component.literal(o), optMax - 24);
					if (ls.size() > 3) {
						ls = new ArrayList<>(ls.subList(0, 3));
					}
					wrapped.add(ls);
					for (FormattedCharSequence seq : ls) {
						optW = Math.max(optW, this.font.width(seq) + 24);
					}
					total += ls.size() * 10 + 5;
				}
				optW = Math.min(optW, optMax);
				int ox = px1 - pw - 8 - optW;
				int bottom = boxY - 21;
				int oy = Math.max(bar + 8, bottom - 2 - total);
				if (in > 0.05F) {
					Frames.panel(g, ox - 4, oy - 4, optW + 6, bottom - oy + 2, 0xE8A33D);
				}
				int rowY = oy;
				for (int i = 0; i < opts.size(); i++) {
					List<FormattedCharSequence> ls = wrapped.get(i);
					int rh = ls.size() * 10 + 5;
					boolean hot = mx >= ox - 6 && mx < ox + optW + 4 && my >= rowY - 3 && my < rowY + rh - 3;
					if (hot) {
						g.fill(ox - 6, rowY - 3, ox + optW + 4, rowY + rh - 3, 0x603A2A12);
						g.fill(ox - 6, rowY - 3, ox - 4, rowY + rh - 3, 0xFFE8A33D);
					}
					g.text(this.font, Component.literal((i + 1) + "."), ox, rowY, ARGB.color((int) (255 * in), hot ? 0xFFD27A : 0xB8935A), true);
					for (int k = 0; k < ls.size(); k++) {
						g.text(this.font, ls.get(k), ox + 14, rowY + k * 10, ARGB.color((int) (255 * in), hot ? 0xFFF4DC : 0xE0D6C0), true);
					}
					int choice = i;
					this.hits.add(new Hit(ox - 6, rowY - 3, optW + 10, rh, () -> choose(choice)));
					rowY += rh;
				}
			}
		}

		// The keys, quietly, under the box.
		Component hint = fit(Component.translatable("dialogue.afterworld.hint", Component.translatable(auto ? "dialogue.afterworld.auto_on" : "dialogue.afterworld.auto_off")), boxW - 8);
		g.text(this.font, hint, boxX + boxW - this.font.width(hint) - 4, boxY + boxH + 3, ARGB.color((int) (120 * open), 0xB8B0A0), false);

		if (this.showLog) {
			log(g, w, h);
		}
	}

	/** The box: dark glass, a fine border in the speaker's colour, and a brighter top edge. */
	/** The words are written on a scroll of parchment, its rolls above and below. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, float open) {
		g.fill(x + 3, y + 3, x + w + 3, y + h + 5, ARGB.color((int) (80 * open), 0x000000));
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, Frames.PARCHMENT, x - 4, y - 6, w + 8, h + 12, open);
	}

	/** The delivery cue, like a stage direction: "[softly]". */
	private void cue(GuiGraphicsExtractor g, String cue, int x, int y, float open) {
		Component c = Component.literal("[" + cue + "]").withStyle(s -> s.withItalic(true));
		int cw = this.font.width(c);
		g.fill(x - 3, y - 3, x + cw + 3, y + 10, ARGB.color((int) (200 * open), 0x101216));
		g.fill(x - 3, y + 10, x + cw + 3, y + 11, ARGB.color((int) (160 * open), 0xB8B0A0));
		g.text(this.font, c, x, y, ARGB.color((int) (235 * open), 0xE8E0D0), false);
	}

	/** Everything said so far in this conversation. */
	private void log(GuiGraphicsExtractor g, int w, int h) {
		int lw = Math.min(460, w - 40);
		int lx = (w - lw) / 2;
		int ly = Math.max(12, (int) (h * 0.075F) + 6);
		int lh = Math.max(60, h - ly * 2 - 70);
		Frames.panel(g, lx, ly, lw, lh, 0xE8C873);
		g.text(this.font, Component.translatable("dialogue.afterworld.log"), lx + 8, ly + 6, 0xFFE8C873, false);
		List<FormattedCharSequence> rows = new ArrayList<>();
		for (Component c : this.history) {
			rows.addAll(this.font.split(c, lw - 16));
			rows.add(FormattedCharSequence.EMPTY);
		}
		int visible = (lh - 24) / 10;
		int max = Math.max(0, rows.size() - visible);
		this.logScroll = Mth.clamp(this.logScroll, 0, max);
		int start = max - this.logScroll;
		for (int i = 0; i < visible && start + i < rows.size(); i++) {
			g.text(this.font, rows.get(start + i), lx + 8, ly + 20 + i * 10, 0xFFE8DCC0, false);
		}
	}

	private static float ease(float x) {
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.showLog) {
			this.showLog = false;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A click anywhere but an answer: hurry the line, or go on. */
	@Override
	protected boolean clicked(double x, double y, int button) {
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			advance();
			return true;
		}
		return false;
	}

	@Override
	protected boolean scrolled(double x, double y, double dy) {
		if (this.showLog) {
			this.logScroll += (int) Math.signum(dy) * 2;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (this.showLog && (key == InputConstants.KEY_L || key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_TAB)) {
			this.showLog = false;
			return true;
		}
		if (key == InputConstants.KEY_L || key == InputConstants.KEY_TAB) {
			this.showLog = true;
			this.logScroll = 0;
			return true;
		}
		if (key == InputConstants.KEY_A) {
			auto = !auto;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_SELECT.value(), auto ? 1.4F : 1.0F, 0.3F));
			return true;
		}
		if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (answering() && this.page.options().size() == 1) {
				choose(0);
			} else {
				advance();
			}
			return true;
		}
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 && answering()) {
			choose(key - InputConstants.KEY_1);
			return true;
		}
		return super.keyPressed(event);
	}

	/** For tests. */
	public int lineForTest() {
		return this.line;
	}

	public List<String> optionsForTest() {
		return this.page.options();
	}

	public void advanceForTest() {
		this.lineStart = 1;
		advance();
	}

	public void chooseForTest(int i) {
		this.line = this.page.lines().size() - 1;
		this.lineStart = 1;
		choose(i);
	}
}
