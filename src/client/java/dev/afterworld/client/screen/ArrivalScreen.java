package dev.afterworld.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.client.hud.TitleCards;
import dev.afterworld.client.render.GarbRender;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Garb;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The traveller's awakening. A dark void, a figure standing on rings of light, and a few questions
 * asked one at a time in large type: where you come from, how you meet trouble, which Edict lies
 * within you, your name and how you travel. W/S or the mouse to choose, Enter to answer, Esc to
 * go back; drag the figure to turn it. The wardrobe opens straight onto the garb question.
 */
public final class ArrivalScreen extends Screen {
	public static final int INTRO = 0;
	public static final int ORIGIN = 1;
	public static final int DISCIPLINE = 2;
	public static final int AFFINITY = 3;
	public static final int NAME = 4;
	public static final int GARB = 5;
	public static final int CONFIRM = 6;
	public static final int OUTRO = 7;

	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int VOID = 0xFF050608;
	private static final int BONE = 0xE8DCC0;
	private static final int AMBER = 0xE8A33D;
	private static final int DIM = 0x8A8478;
	private static final int FAINT = 0x45413A;
	private static final Identifier RING = Afterworld.id("textures/gui/arrival/ring.png");
	private static final Identifier FLOOR = Afterworld.id("textures/gui/arrival/floor.png");
	private static final Identifier RAYS = Afterworld.id("textures/gui/arrival/rays.png");
	private static final Identifier GLOW = Afterworld.id("textures/gui/arrival/spotlight.png");
	private static final int TYPE_MS = 32;
	private static final int OUTRO_MS = 4200;

	/** The garb question's rows: the Garb field each one changes (-1 is "surprise me"). */
	private static final String[] GARB_ROWS = {"head", "cloak", "accent", "arms", "feet", "cloth", "trim", "emblem", "wear", "random"};
	private static final int[] GARB_FIELD = {0, 1, 2, 3, 4, 5, 6, 7, 8, -1};
	private static final int[] GARB_COUNT = {Garb.HEADS, Garb.CLOAKS, Garb.ACCENTS, Garb.ARMS, Garb.FEET, Garb.PALETTE.length, Garb.PALETTE.length, Garb.EMBLEMS, 2, 1};
	/** Where the camera looks for each garb row: yaw (degrees, 0 = facing you), zoom, focus height (blocks). */
	private static final float[][] GARB_VIEW = {
		{14, 3.0F, 1.7F}, {160, 1.4F, 1.0F}, {28, 2.4F, 0.82F}, {50, 2.1F, 1.1F}, {20, 2.4F, 0.3F},
		{24, 1.35F, 0.95F}, {-30, 1.35F, 0.95F}, {165, 1.7F, 1.15F}, {20, 1.2F, 0.95F}, {20, 1.2F, 0.95F}
	};
	private static final Discipline[] DISCIPLINES = {Discipline.VANGUARD, Discipline.WAYFARER, Discipline.ARCANIST, Discipline.KEEPER};

	private record Hit(int x, int y, int w, int h, int index, int action) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private record Mote(float x, float y, float speed, float sway, int size, boolean hollow, float phase) {
	}

	private final boolean wardrobe;
	private final Discipline lockedDiscipline;
	private final Garb originalGarb;
	private int step;
	private final int[] cursor = new int[OUTRO + 1];
	private Origin origin = Origin.NONE;
	private Discipline discipline = Discipline.NONE;
	private boolean decideLater;
	private Edict affinity;
	private final StringBuilder name = new StringBuilder();
	private Garb garb;
	private boolean garbTouched;

	// Typewriter.
	private long typedAt;
	private int typedSounded;

	// Camera and figure.
	private float yaw = 18;
	private float yawVelocity;
	private float zoom = 1;
	private float focus = 0.95F;
	private boolean dragging;
	private long lastTouch;
	private int accent = AMBER;
	private float accentR = 0xE8;
	private float accentG = 0xA3;
	private float accentB = 0x3D;

	private final List<Hit> hits = new ArrayList<>();
	private final List<Mote> motes = new ArrayList<>();
	private double lastMouseX = -1;
	private double lastMouseY = -1;
	private long lastHoverSound;
	private long lastFrame;
	private float dt;
	private long openedAt;
	private long revealAt = -1;
	private long outroAt = -1;
	private int stageX0;
	private int stageY0;
	private int stageX1;
	private int stageY1;

	public ArrivalScreen(boolean wardrobe, Discipline lockedDiscipline) {
		super(Component.translatable(wardrobe ? "arrival.afterworld.wardrobe" : "arrival.afterworld.title"));
		this.wardrobe = wardrobe;
		this.lockedDiscipline = lockedDiscipline;
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		Garb current = player != null ? player.getAttached(AwAttachments.GARB) : null;
		this.originalGarb = current;
		this.garb = current != null ? current : Garb.DEFAULT;
		this.garbTouched = current != null;
		this.step = wardrobe ? GARB : INTRO;
		if (lockedDiscipline != Discipline.NONE) {
			this.discipline = lockedDiscipline;
		}
		RandomSource r = RandomSource.create(7);
		for (int i = 0; i < 70; i++) {
			this.motes.add(new Mote(r.nextFloat(), r.nextFloat(), 0.006F + r.nextFloat() * 0.018F, r.nextFloat() * 6.28F,
				r.nextInt(9) == 0 ? 3 : r.nextInt(3) == 0 ? 2 : 1, r.nextInt(6) == 0, r.nextFloat()));
		}
	}

	@Override
	protected void init() {
		long now = Util.getMillis();
		if (this.openedAt == 0) {
			this.openedAt = now;
			this.typedAt = now + (this.wardrobe ? 150 : 900);
			this.lastTouch = now;
			if (this.wardrobe) {
				this.revealAt = now - 2000;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.outroAt >= 0) {
			return true;
		}
		int key = event.key();
		if (this.step == INTRO) {
			if (!typingDone()) {
				this.typedAt = Util.getMillis() - 100_000;
			} else {
				go(ORIGIN);
			}
			return true;
		}
		if (event.isEscape()) {
			back();
			return true;
		}
		if (this.step == NAME) {
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!this.name.isEmpty()) {
					this.name.setLength(this.name.length() - 1);
					sound(AwSounds.UI_TYPE, 0.8F, 0.4F);
				}
				return true;
			}
			if (event.isConfirmation()) {
				choose();
				return true;
			}
			return true;
		}
		if (event.isUp() || key == InputConstants.KEY_W) {
			move(-1);
			return true;
		}
		if (event.isDown() || key == InputConstants.KEY_S) {
			move(1);
			return true;
		}
		if (this.step == GARB && (event.isLeft() || key == InputConstants.KEY_A)) {
			cycleGarb(this.cursor[GARB], -1);
			return true;
		}
		if (this.step == GARB && (event.isRight() || key == InputConstants.KEY_D)) {
			cycleGarb(this.cursor[GARB], 1);
			return true;
		}
		if (event.isSelection()) {
			choose();
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			back();
			return true;
		}
		int digit = key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 ? key - InputConstants.KEY_1 : -1;
		if (digit >= 0 && digit < optionCount()) {
			this.cursor[this.step] = digit;
			choose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.step != NAME || this.outroAt >= 0) {
			return false;
		}
		int c = event.codepoint();
		boolean ok = Character.isLetterOrDigit(c) || c == '\'' || c == '-' || c == '.' || c == ' ' && !this.name.isEmpty() && this.name.charAt(this.name.length() - 1) != ' ';
		if (ok && this.name.length() < 16 && Character.isBmpCodePoint(c)) {
			this.name.append((char) c);
			sound(AwSounds.UI_TYPE, 0.9F + (this.name.length() % 4) * 0.06F, 0.45F);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.outroAt >= 0) {
			return true;
		}
		if (this.step == INTRO) {
			return keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
		}
		for (int i = this.hits.size() - 1; i >= 0; i--) {
			Hit h = this.hits.get(i);
			if (h.contains(event.x(), event.y())) {
				click(h);
				return true;
			}
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= this.stageX0 && event.x() < this.stageX1 && event.y() >= this.stageY0 && event.y() < this.stageY1) {
			this.dragging = true;
			this.yawVelocity = 0;
			this.lastTouch = Util.getMillis();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			float delta = (float) dx * 0.9F;
			this.yaw += delta;
			this.yawVelocity = delta / Math.max(0.016F, this.dt);
			this.lastTouch = Util.getMillis();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging) {
			this.dragging = false;
			this.yawVelocity = Mth.clamp(this.yawVelocity, -720, 720);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= this.stageX0 && x < this.stageX1) {
			this.zoom = Mth.clamp(this.zoom + (float) scrollY * 0.2F, 0.8F, 3.4F);
			this.lastTouch = Util.getMillis();
			return true;
		}
		if (this.step != NAME && scrollY != 0) {
			move(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/** Hovering an option highlights it, but only when the mouse actually moves, so W/S still work. */
	private void hover(int mx, int my) {
		if (mx == this.lastMouseX && my == this.lastMouseY) {
			return;
		}
		this.lastMouseX = mx;
		this.lastMouseY = my;
		for (Hit h : this.hits) {
			if (h.action == 0 && h.contains(mx, my) && this.cursor[this.step] != h.index) {
				this.cursor[this.step] = h.index;
				// The soft tick, and not too often: a sweep of the mouse down a list passes several
				// choices in a moment, and a full sound for each one made a rattle.
				long now = Util.getMillis();
				if (now - this.lastHoverSound > 70) {
					this.lastHoverSound = now;
					sound(AwSounds.UI_HOVER, 1.0F, 0.16F);
				}
				return;
			}
		}
	}

	private void click(Hit h) {
		this.cursor[this.step] = h.index;
		switch (h.action) {
			case 1 -> cycleGarb(h.index, -1);
			case 2 -> cycleGarb(h.index, 1);
			case 3 -> back();
			default -> choose();
		}
	}

	// ------------------------------------------------------------------ hooks (tests, shortcuts)

	public void skipIntro() {
		if (this.step == INTRO) {
			go(ORIGIN);
		}
	}

	public void selectOrigin(Origin o) {
		int i = Origin.CHOICES.indexOf(o);
		if (i >= 0) {
			this.cursor[ORIGIN] = i;
		}
	}

	public void selectDiscipline(Discipline d) {
		for (int i = 0; i < DISCIPLINES.length; i++) {
			if (DISCIPLINES[i] == d) {
				this.cursor[DISCIPLINE] = i;
			}
		}
	}

	public void selectAffinity(Edict e) {
		this.cursor[AFFINITY] = e.ordinal();
	}

	public void setName(String n) {
		this.name.setLength(0);
		this.name.append(n);
	}

	public void selectCategory(int row) {
		this.cursor[GARB] = Mth.clamp(row, 0, GARB_ROWS.length - 1);
		this.lastTouch = 0;
	}

	public void selectGarb(Garb g) {
		setGarb(g);
	}

	/** Enter: answers the current question. */
	public void confirm() {
		if (this.step == INTRO) {
			skipIntro();
		} else {
			choose();
		}
	}

	public int step() {
		return this.step;
	}

	// ------------------------------------------------------------------ flow

	private int optionCount() {
		return switch (this.step) {
			case ORIGIN -> Origin.CHOICES.size();
			case DISCIPLINE -> DISCIPLINES.length + 1;
			case AFFINITY -> Edict.COUNT;
			case GARB -> GARB_ROWS.length;
			case CONFIRM -> 2;
			default -> 0;
		};
	}

	private void move(int dir) {
		int n = optionCount();
		if (n == 0) {
			return;
		}
		this.cursor[this.step] = Math.floorMod(this.cursor[this.step] + dir, n);
		this.lastTouch = 0;
		sound(AwSounds.UI_MOVE, 1.0F, 0.45F);
	}

	private void choose() {
		int c = this.cursor[this.step];
		switch (this.step) {
			case ORIGIN -> {
				Origin o = Origin.CHOICES.get(c);
				if (this.origin != o && !this.garbTouched) {
					this.garb = o.garb;
				}
				this.origin = o;
				answered(this.lockedDiscipline != Discipline.NONE ? AFFINITY : DISCIPLINE);
			}
			case DISCIPLINE -> {
				this.decideLater = c >= DISCIPLINES.length;
				this.discipline = this.decideLater ? Discipline.NONE : DISCIPLINES[c];
				answered(AFFINITY);
			}
			case AFFINITY -> {
				this.affinity = Edict.byOrdinal(c);
				answered(NAME);
			}
			case NAME -> answered(GARB);
			case GARB -> {
				if (GARB_FIELD[c] == 8 || GARB_FIELD[c] < 0) {
					cycleGarb(c, 1);
				} else if (this.wardrobe) {
					ClientPlayNetworking.send(new AwPayloads.Arrive(-1, -1, -1, "", this.garb));
					sound(AwSounds.UI_CHOOSE, 1.0F, 0.6F);
					this.onClose();
				} else {
					answered(CONFIRM);
				}
			}
			case CONFIRM -> {
				if (c == 0) {
					arrive();
				} else {
					sound(AwSounds.UI_PAGE, 0.9F, 0.6F);
					go(ORIGIN);
				}
			}
			default -> {
			}
		}
	}

	private void answered(int next) {
		sound(AwSounds.UI_CHOOSE, 0.95F + this.step * 0.04F, 0.55F);
		go(next);
	}

	private void back() {
		if (this.wardrobe) {
			this.onClose();
			return;
		}
		int target = switch (this.step) {
			case DISCIPLINE -> ORIGIN;
			case AFFINITY -> this.lockedDiscipline != Discipline.NONE ? ORIGIN : DISCIPLINE;
			case NAME -> AFFINITY;
			case GARB -> NAME;
			case CONFIRM -> GARB;
			default -> this.step;
		};
		if (target != this.step) {
			sound(AwSounds.UI_PAGE, 0.9F, 0.6F);
			go(target);
		}
	}

	private void go(int target) {
		if (target == this.step) {
			return;
		}
		long now = Util.getMillis();
		if (this.step == INTRO) {
			this.revealAt = now;
			sound(AwSounds.UI_AWAKEN, 1.0F, 0.9F);
		} else {
			sound(AwSounds.UI_STEP, 1.0F, 0.55F);
		}
		this.step = target;
		this.typedAt = now + (target == ORIGIN && this.revealAt == now ? 700 : 120);
		this.typedSounded = 0;
		this.lastTouch = 0;
		// Land on what was chosen before, so going back and forth keeps your place.
		switch (target) {
			case ORIGIN -> this.cursor[ORIGIN] = Math.max(0, Origin.CHOICES.indexOf(this.origin));
			case DISCIPLINE -> {
				for (int i = 0; i < DISCIPLINES.length; i++) {
					if (DISCIPLINES[i] == this.discipline) {
						this.cursor[DISCIPLINE] = i;
					}
				}
				if (this.decideLater) {
					this.cursor[DISCIPLINE] = DISCIPLINES.length;
				}
			}
			case AFFINITY -> {
				if (this.affinity != null) {
					this.cursor[AFFINITY] = this.affinity.ordinal();
				} else if (this.discipline.starter != null) {
					this.cursor[AFFINITY] = this.discipline.starter.ordinal();
				}
			}
			case CONFIRM -> this.cursor[CONFIRM] = 0;
			default -> {
			}
		}
	}

	private void arrive() {
		this.outroAt = Util.getMillis();
		this.step = OUTRO;
		this.typedAt = this.outroAt + 500;
		this.typedSounded = 0;
		Discipline chosen = this.lockedDiscipline != Discipline.NONE ? Discipline.NONE : this.discipline;
		ClientPlayNetworking.send(new AwPayloads.Arrive(this.origin.ordinal(), chosen.ordinal(), this.affinity != null ? this.affinity.ordinal() : -1,
			this.name.toString().trim(), this.garb));
		sound(AwSounds.UI_AWAKEN, 1.0F, 1.0F);
	}

	private void cycleGarb(int row, int dir) {
		int field = GARB_FIELD[row];
		if (field < 0) {
			RandomSource r = RandomSource.create();
			setGarb(Garb.random(r).withHeart(heartShown()));
			sound(AwSounds.UI_PAGE, 0.8F + r.nextFloat() * 0.4F, 0.6F);
			return;
		}
		int value = Math.floorMod(this.garb.field(field) + dir, GARB_COUNT[row]);
		setGarb(this.garb.with(field, value));
		sound(AwSounds.UI_MOVE, 1.0F + dir * 0.06F, 0.5F);
	}

	private void setGarb(Garb g) {
		if (!g.equals(this.garb)) {
			this.garb = g;
			this.garbTouched = true;
		}
	}

	private void sound(Holder<SoundEvent> s, float pitch, float volume) {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s.value(), pitch, volume));
	}

	// ------------------------------------------------------------------ what is shown

	/** The heart the figure wears this frame: the Edict under the cursor while choosing it. */
	private Edict heartShown() {
		if (this.step == AFFINITY) {
			return Edict.byOrdinal(this.cursor[AFFINITY]);
		}
		if (this.wardrobe) {
			return this.originalGarb != null ? this.originalGarb.heartEdict() : null;
		}
		return this.affinity;
	}

	private Garb garbShown() {
		Garb g = this.garb;
		if (this.step == ORIGIN && !this.garbTouched) {
			g = Origin.CHOICES.get(this.cursor[ORIGIN]).garb;
		}
		return g.withHeart(heartShown());
	}

	private Discipline disciplineShown() {
		if (this.step == DISCIPLINE) {
			int c = this.cursor[DISCIPLINE];
			return c < DISCIPLINES.length ? DISCIPLINES[c] : Discipline.NONE;
		}
		return this.step >= CONFIRM ? this.discipline : Discipline.NONE;
	}

	private int targetAccent() {
		return switch (this.step) {
			case INTRO -> 0x8A8478;
			case ORIGIN -> Origin.CHOICES.get(this.cursor[ORIGIN]).color;
			case DISCIPLINE -> disciplineShown() != Discipline.NONE ? disciplineShown().color : 0x9AA3A8;
			case AFFINITY -> Edict.byOrdinal(this.cursor[AFFINITY]).color;
			case NAME -> BONE;
			case GARB -> this.wardrobe ? AMBER : this.affinity != null ? this.affinity.color : AMBER;
			default -> this.affinity != null ? this.affinity.color : AMBER;
		};
	}

	private String headingKey() {
		return switch (this.step) {
			case INTRO -> "arrival.afterworld.q.intro";
			case ORIGIN -> "arrival.afterworld.q.origin";
			case DISCIPLINE -> "arrival.afterworld.q.discipline";
			case AFFINITY -> "arrival.afterworld.q.affinity";
			case NAME -> "arrival.afterworld.q.name";
			case GARB -> "arrival.afterworld.q.garb";
			case CONFIRM -> "arrival.afterworld.q.confirm";
			default -> "arrival.afterworld.q.outro";
		};
	}

	private String heading() {
		return Component.translatable(headingKey()).getString().toUpperCase(java.util.Locale.ROOT);
	}

	private int typed() {
		long el = Util.getMillis() - this.typedAt;
		return el < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, el / TYPE_MS);
	}

	private boolean typingDone() {
		return typed() >= heading().length();
	}

	// ------------------------------------------------------------------ frame

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		g.fill(0, 0, this.width, this.height, VOID);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		long now = Util.getMillis();
		this.dt = this.lastFrame == 0 ? 0 : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;
		hover(mx, my);
		syncTextInput();
		this.hits.clear();
		super.extractRenderState(g, mx, my, a);
		float t = (now % 3_600_000L) / 1000.0F;
		blendAccent();

		drawMotes(g, t, mx, my);
		int margin = Math.max(14, this.width / 22);
		int stageX = (int) (this.width * 0.5F);
		this.stageX0 = stageX;
		this.stageY0 = 4;
		this.stageX1 = this.width - margin / 2;
		this.stageY1 = this.height - 24;
		float reveal = this.revealAt < 0 ? 0 : ease(Mth.clamp((now - this.revealAt) / 1600.0F, 0, 1));
		if (reveal > 0) {
			drawStage(g, t, now, reveal);
		}
		drawVignette(g);
		typeSounds();
		if (this.step == INTRO) {
			drawIntro(g, now);
		} else if (this.step == OUTRO) {
			drawOutro(g, now);
		} else {
			drawQuestion(g, margin, stageX - 8, now);
			drawHints(g, margin);
			if (!this.wardrobe) {
				drawTrail(g, margin, now);
			}
		}
		if (this.step == OUTRO && now - this.outroAt > OUTRO_MS) {
			TitleCards.dawn(this.name.toString().trim(), this.origin, this.discipline, this.affinity);
			this.onClose();
		}
	}

	private void blendAccent() {
		int target = targetAccent();
		float k = Math.min(1, this.dt * 5.0F);
		this.accentR += (ARGB.red(target) - this.accentR) * k;
		this.accentG += (ARGB.green(target) - this.accentG) * k;
		this.accentB += (ARGB.blue(target) - this.accentB) * k;
		this.accent = ARGB.color(255, (int) this.accentR, (int) this.accentG, (int) this.accentB) & 0xFFFFFF;
	}

	/** Squares of dust drifting up through the dark, some of them outlines, the odd one in the accent colour. */
	private void drawMotes(GuiGraphicsExtractor g, float t, int mx, int my) {
		float px = (mx - this.width / 2.0F) / this.width;
		float py = (my - this.height / 2.0F) / this.height;
		boolean outro = this.step == OUTRO;
		for (int i = 0; i < this.motes.size(); i++) {
			Mote m = this.motes.get(i);
			float speed = m.speed * (outro ? 3.0F : 1.0F);
			float yy = 1.0F - ((m.y + t * speed) % 1.0F);
			float depth = m.size / 3.0F;
			float x = m.x * this.width + Mth.sin(t * 0.4F + m.sway) * 6 - px * 10 * depth;
			float y = yy * (this.height + 10) - 5 - py * 6 * depth;
			float life = Mth.sin(yy * Mth.PI);
			float twinkle = 0.6F + 0.4F * Mth.sin(t * 1.7F + m.phase * 20);
			int alpha = (int) (life * twinkle * (90 + m.size * 40));
			if (alpha < 4) {
				continue;
			}
			int color = i % 5 == 0 ? this.accent : BONE;
			int x0 = (int) x;
			int y0 = (int) y;
			if (m.hollow && m.size > 1) {
				g.outline(x0, y0, m.size + 2, m.size + 2, ARGB.color(alpha, color));
			} else {
				g.fill(x0, y0, x0 + m.size, y0 + m.size, ARGB.color(alpha, color));
			}
		}
	}

	private void drawVignette(GuiGraphicsExtractor g) {
		int band = this.height / 5;
		g.fillGradient(0, 0, this.width, band, 0xC0050608, 0x00050608);
		g.fillGradient(0, this.height - band, this.width, this.height, 0x00050608, 0xD0050608);
	}

	// ------------------------------------------------------------------ the stage

	private void drawStage(GuiGraphicsExtractor g, float t, long now, float reveal) {
		int x0 = this.stageX0;
		int x1 = this.stageX1;
		int y0 = this.stageY0;
		int y1 = this.stageY1;
		int cx = (x0 + x1) / 2;
		float outro = this.outroAt < 0 ? 0 : Mth.clamp((now - this.outroAt) / (float) OUTRO_MS, 0, 1);
		float strength = reveal * (1 + outro * 1.5F);

		// The camera: pixels per block, and where the feet land on screen. Rings and light follow the feet.
		animateCamera();
		int floorY = y1 - (y1 - y0) / 9;
		float base = (floorY - y0) * 0.34F;
		float size = base * this.zoom;
		float zoomT = Mth.clamp((this.zoom - 1) / 1.8F, 0, 1);
		float focusY = Mth.lerp(zoomT, floorY - this.focus * base, (y0 + floorY) / 2.0F - 4);
		float feet = focusY + this.focus * size - ease(outro) * 16;
		int feetY = Math.round(feet);
		float spread = Math.min(this.zoom, 2.2F);
		int rw = Math.round(Math.min((x1 - x0) * 0.95F, 260) * spread);
		int rh = rw / 4;

		// Light falling from above, and a pool of it on the floor.
		int shaftW = Math.round(rw * 0.6F / spread * Math.min(this.zoom, 1.4F));
		blit(g, RAYS, cx - shaftW / 2, y0 - 10, shaftW, Math.max(10, feetY - y0 + 10), 0, 0, 128, 160, 128, 160, ARGB.color(clampA(70 * strength), 0xFFF0D2));
		boolean floorVisible = feetY - rh < y1 + 20;
		if (floorVisible) {
			blit(g, GLOW, cx - rw / 2, feetY - rw / 2, rw, rw, 0, 0, 128, 128, 128, 128, ARGB.color(clampA(55 * strength), this.accent));
			blit(g, FLOOR, cx - rw * 3 / 5, feetY - rh * 2 / 5, rw * 6 / 5, rh * 4 / 5, 0, 0, 128, 32, 128, 32, ARGB.color(clampA(150 * strength), this.accent));
			rings(g, t, cx, feetY, rw, rh, strength, outro, true);
		}
		drawFigure(g, x0, y0, x1, y1, size, feet, t);
		if (floorVisible) {
			rings(g, t, cx, feetY, rw, rh, strength, outro, false);
		}

		// The figure steps out of the dark when the first question is asked, and dissolves into light at the end.
		if (reveal < 1) {
			g.fill(x0, y0, x1, y1, ARGB.color(clampA(255 * (1 - reveal)), 0x050608));
		}
		if (outro > 0.3F) {
			float w = ease(Mth.clamp((outro - 0.3F) / 0.5F, 0, 1));
			int mid = Math.round(feet - base * 0.95F);
			int r = Math.round((y1 - y0) * (0.35F + w * 0.9F));
			blit(g, GLOW, cx - r, mid - r, r * 2, r * 2, 0, 0, 128, 128, 128, 128, ARGB.color(clampA(255 * w), 0xFFF6E2));
			int core = r / 2;
			blit(g, GLOW, cx - core, mid - core, core * 2, core * 2, 0, 0, 128, 128, 128, 128, ARGB.color(clampA(255 * w), 0xFFFFFF));
		}
	}

	private void rings(GuiGraphicsExtractor g, float t, int cx, int cy, int rw, int rh, float strength, float outro, boolean back) {
		// Three still rings breathing slowly, and ripples running outward every couple of seconds.
		float[] still = {0.42F, 0.68F, 0.95F};
		for (int i = 0; i < still.length; i++) {
			float breathe = 0.85F + 0.15F * Mth.sin(t * 1.3F + i * 1.7F);
			ring(g, cx, cy, rw * still[i], rh * still[i], clampA((170 - i * 45) * breathe * strength), back);
		}
		float period = outro > 0 ? 0.55F : 2.4F;
		for (int k = 0; k < 3; k++) {
			float age = ((t / period) + k / 3.0F) % 1.0F;
			float s = 0.3F + age * (1.25F + outro * 2.0F);
			float alpha = (1 - age) * (1 - age) * 200 * strength;
			ring(g, cx, cy, rw * s, rh * s, clampA(alpha), back);
		}
	}

	private void ring(GuiGraphicsExtractor g, int cx, int cy, float w, float h, int alpha, boolean back) {
		if (alpha < 3 || w < 4) {
			return;
		}
		int iw = Math.round(w);
		int ih = Math.max(2, Math.round(h) & ~1);
		int color = ARGB.color(alpha, this.accent);
		if (back) {
			blit(g, RING, cx - iw / 2, cy - ih / 2, iw, ih / 2, 0, 0, 256, 32, 256, 64, color);
		} else {
			blit(g, RING, cx - iw / 2, cy, iw, ih / 2, 0, 32, 256, 32, 256, 64, color);
		}
	}

	private void animateCamera() {
		long now = Util.getMillis();
		boolean idle = !this.dragging && now - this.lastTouch > 3500;
		float[] view = switch (this.step) {
			case AFFINITY -> new float[] {12, 2.3F, 1.15F};
			case NAME -> new float[] {6, 3.1F, 1.62F};
			case GARB -> GARB_VIEW[this.cursor[GARB]];
			default -> null;
		};
		float targetFocus = view != null ? view[2] : 0.95F;
		if (view != null) {
			if (idle) {
				this.yaw = approachAngle(this.yaw, view[0], this.dt * 3.0F);
				this.zoom = Mth.lerp(Math.min(1, this.dt * 3.0F), this.zoom, view[1]);
			}
		} else if (idle) {
			this.yaw += this.dt * (this.step == OUTRO ? 40.0F : 11.0F);
			this.zoom = Mth.lerp(Math.min(1, this.dt * 2.5F), this.zoom, 1.0F);
		}
		if (!this.dragging && Math.abs(this.yawVelocity) > 1) {
			this.yaw += this.yawVelocity * this.dt;
			this.yawVelocity *= (float) Math.pow(0.04, this.dt);
		}
		this.focus = Mth.lerp(Math.min(1, this.dt * 3.0F), this.focus, targetFocus);
		this.yaw = Mth.wrapDegrees(this.yaw);
	}

	private static float approachAngle(float from, float to, float rate) {
		return from + Mth.wrapDegrees(to - from) * Math.min(1, rate);
	}

	private void drawFigure(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float size, float feet, float t) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		EntityRenderState raw = this.minecraft.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, 1.0F);
		if (!(raw instanceof AvatarRenderState s)) {
			return;
		}
		Garb shown = garbShown();
		s.shadowPieces.clear();
		s.outlineColor = 0;
		s.nameTag = null;
		s.scoreText = null;
		s.isCrouching = false;
		s.headEquipment = ItemStack.EMPTY;
		s.chestEquipment = ItemStack.EMPTY;
		s.legsEquipment = ItemStack.EMPTY;
		s.feetEquipment = ItemStack.EMPTY;
		s.rightHandItemState.clear();
		s.leftHandItemState.clear();
		s.rightHandItemStack = ItemStack.EMPTY;
		s.leftHandItemStack = ItemStack.EMPTY;
		s.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		hold(s, player, disciplineShown());
		s.setData(GarbRender.GARB, shown);
		if (shown.visible() && shown.cloak() != 0 && shown.cloak() != 4) {
			s.showCape = false;
		}
		s.ageInTicks = t * 20.0F;
		s.bodyRot = 180.0F + this.yaw;
		s.yRot = Mth.sin(t * 0.45F) * 9.0F;
		s.xRot = Mth.sin(t * 0.31F) * 4.0F - (this.step == NAME ? 0 : 3.0F);
		s.walkAnimationPos = 0;
		s.walkAnimationSpeed = 0;
		s.capeFlap = Mth.sin(t * 2.2F) * 2.5F;
		s.capeLean = 6.0F + Mth.sin(t * 0.9F) * 4.0F;
		s.capeLean2 = Mth.sin(t * 0.6F) * 6.0F;
		s.boundingBoxWidth = s.boundingBoxWidth / s.scale;
		s.boundingBoxHeight = s.boundingBoxHeight / s.scale;
		s.scale = 1.0F;

		float cy = (y0 + y1) / 2.0F;
		Vector3f translation = new Vector3f(0, (feet - cy) / size, 0);
		Quaternionf tilt = new Quaternionf().rotateX(-6.0F * Mth.DEG_TO_RAD);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		Portraits.entity(g, s, size, translation, rotation, tilt, x0, y0, x1, y1);
	}

	/** Each discipline's figure carries something that says how it meets trouble. */
	private void hold(AvatarRenderState s, LocalPlayer player, Discipline d) {
		ItemStack stack = switch (d) {
			case VANGUARD -> new ItemStack(Items.IRON_SWORD);
			case WAYFARER -> new ItemStack(Items.WIND_CHARGE);
			case ARCANIST -> new ItemStack(Items.ENCHANTED_BOOK);
			case KEEPER -> new ItemStack(Items.LANTERN);
			default -> ItemStack.EMPTY;
		};
		if (stack.isEmpty()) {
			return;
		}
		boolean left = d == Discipline.KEEPER || d == Discipline.ARCANIST;
		var resolver = this.minecraft.getItemModelResolver();
		if (left) {
			resolver.updateForTopItem(s.leftHandItemState, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, player.level(), player, 0);
			s.leftHandItemStack = stack;
			s.leftArmPose = HumanoidModel.ArmPose.ITEM;
		} else {
			resolver.updateForTopItem(s.rightHandItemState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player.level(), player, 0);
			s.rightHandItemStack = stack;
			s.rightArmPose = HumanoidModel.ArmPose.ITEM;
		}
	}

	// ------------------------------------------------------------------ type

	private static MutableComponent display(String s) {
		return Component.literal(s).withStyle(st -> st.withFont(DISPLAY));
	}

	/** A pose scale that lands display-font pixels on whole screen pixels. */
	private float crisp(float desired) {
		int gs = Math.max(1, this.minecraft.getWindow().getGuiScale());
		return Math.max(1, Math.round(desired * gs)) / (float) gs;
	}

	/** Word-wraps display text to a width in unscaled pixels. */
	private List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && this.font.width(display(next)) > width) {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(next);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/**
	 * Draws display text scaled, revealing the first {@code shown} characters. Returns the height used.
	 * The largest scale that keeps it to {@code maxLines} lines wins.
	 */
	private int bigText(GuiGraphicsExtractor g, String text, int x, int y, int width, int shown, float[] scales, int maxLines, int color, boolean center) {
		float scale = crisp(scales[scales.length - 1]);
		List<String> lines = null;
		for (float s : scales) {
			float cs = crisp(s);
			List<String> l = wrap(text, (int) (width / cs));
			if (l.size() <= maxLines) {
				scale = cs;
				lines = l;
				break;
			}
		}
		if (lines == null) {
			lines = wrap(text, (int) (width / scale));
		}
		int lineH = Math.round(13 * scale);
		int used = 0;
		int left = shown;
		var pose = g.pose();
		for (String line : lines) {
			if (left <= 0) {
				break;
			}
			String part = line.length() <= left ? line : line.substring(0, left);
			left -= line.length() + 1;
			pose.pushMatrix();
			float lw = this.font.width(display(line)) * scale;
			pose.translate(center ? x + (width - lw) / 2.0F : x, y + used);
			pose.scale(scale, scale);
			g.text(this.font, display(part), 1, 1, ARGB.color(ARGB.alpha(color) * 2 / 5, 0x000000), false);
			g.text(this.font, display(part), 0, 0, color, false);
			pose.popMatrix();
			used += lineH;
		}
		return Math.max(used, lines.size() * lineH);
	}

	private void typeSounds() {
		if (this.step == OUTRO && Util.getMillis() - this.outroAt > OUTRO_MS - 600) {
			return;
		}
		int n = Math.min(typed(), heading().length());
		String h = heading();
		while (this.typedSounded < n) {
			char c = h.charAt(this.typedSounded);
			if (c != ' ' && this.typedSounded % 2 == 0) {
				sound(AwSounds.UI_TYPE, 0.92F + ((this.typedSounded * 7) % 5) * 0.04F, 0.4F);
			}
			this.typedSounded++;
		}
	}

	// ------------------------------------------------------------------ intro and outro

	private void drawIntro(GuiGraphicsExtractor g, long now) {
		int w = Math.min(this.width - 40, 360);
		int h = bigText(g, heading(), (this.width - w) / 2, this.height / 2 - 24, w, typed(), new float[] {1.5F, 1.0F}, 3, ARGB.opaque(BONE), true);
		if (typingDone()) {
			float pulse = 0.5F + 0.5F * Mth.sin((now - this.typedAt) / 320.0F);
			int alpha = clampA(90 + 140 * pulse);
			String prompt = Component.translatable("arrival.afterworld.press_any_key").getString().toUpperCase(java.util.Locale.ROOT);
			bigText(g, prompt, 0, this.height / 2 - 24 + h + 18, this.width, prompt.length(), new float[] {1.0F}, 1, ARGB.color(alpha, AMBER), true);
		}
	}

	private void drawOutro(GuiGraphicsExtractor g, long now) {
		float el = now - this.outroAt;
		int w = Math.min(this.width - 40, 380);
		float textAlpha = el > OUTRO_MS - 900 ? Mth.clamp((OUTRO_MS - el) / 700.0F, 0, 1) : 1;
		bigText(g, heading(), (this.width - w) / 2, (int) (this.height * 0.2F), w, typed(), new float[] {1.5F, 1.0F}, 3,
			ARGB.color(clampA(255 * textAlpha), BONE), true);
		float white = Mth.clamp((el - (OUTRO_MS - 1100)) / 1000.0F, 0, 1);
		if (white > 0) {
			g.fill(0, 0, this.width, this.height, ARGB.color(clampA(255 * ease(white)), 0xFFF6E2));
		}
	}

	// ------------------------------------------------------------------ the question

	private void drawQuestion(GuiGraphicsExtractor g, int x, int right, long now) {
		int w = right - x;
		int y = this.wardrobe ? 18 : 30;
		int headH = bigText(g, heading(), x, y, w, typed(), new float[] {2.0F, 1.5F, 1.0F}, 2, ARGB.opaque(BONE), false);
		y += headH + 8;
		// The options appear as the question finishes typing.
		float in = ease(Mth.clamp((now - this.typedAt - heading().length() * (long) TYPE_MS * 3 / 4) / 260.0F, 0, 1));
		if (in <= 0) {
			return;
		}
		int bottom = this.height - 30;
		switch (this.step) {
			case ORIGIN -> {
				List<String> labels = new ArrayList<>();
				for (Origin o : Origin.CHOICES) {
					labels.add(o.displayName().getString());
				}
				y = list(g, labels, x, y, w, in, bottom - 52, i -> Origin.CHOICES.get(i).color);
				Origin o = Origin.CHOICES.get(this.cursor[ORIGIN]);
				y = describe(g, Component.translatable("origin.afterworld." + o.id + ".tagline").withStyle(s -> s.withItalic(true)), x, y + 4, w, bottom, in, 0xB8AE98);
				Component standing = o.faction != null
					? Component.translatable("arrival.afterworld.standing.one", o.faction.displayName(), o.repWith(o.faction)).withColor(o.faction.color)
					: Component.translatable("arrival.afterworld.standing.all", o.repWith(Faction.CROWN)).withColor(BONE);
				y = describe(g, Component.translatable("arrival.afterworld.standing").append(": ").append(standing), x, y + 2, w, bottom, in, DIM);
				kit(g, o, x, y + 3, w, bottom, in);
			}
			case DISCIPLINE -> {
				List<String> labels = new ArrayList<>();
				for (Discipline d : DISCIPLINES) {
					labels.add(d.displayName().getString());
				}
				labels.add(Component.translatable("arrival.afterworld.later").getString());
				y = list(g, labels, x, y, w, in, bottom - 46, i -> i < DISCIPLINES.length ? DISCIPLINES[i].color : 0x9AA3A8);
				Discipline d = disciplineShown();
				if (d == Discipline.NONE) {
					describe(g, Component.translatable("arrival.afterworld.later_help"), x, y + 4, w, bottom, in, 0xB8AE98);
				} else {
					y = describe(g, Component.translatable("discipline.afterworld." + d.id + ".style"), x, y + 4, w, bottom, in, BONE);
					if (d.starter != null) {
						y = describe(g, Component.translatable("arrival.afterworld.first_edict").append(": ")
							.append(d.starter.displayName().copy().withColor(d.starter.color)), x, y + 2, w, bottom, in, DIM);
					}
					describe(g, Component.translatable("arrival.afterworld.signature", d.signatureName().copy().withColor(0xFFD27A)), x, y, w, bottom, in, DIM);
				}
			}
			case AFFINITY -> {
				List<String> labels = new ArrayList<>();
				for (Edict e : Edict.VALUES) {
					labels.add(e.displayName().getString());
				}
				y = list(g, labels, x, y, w, in, bottom - 56, i -> Edict.VALUES[i].color);
				Edict e = Edict.byOrdinal(this.cursor[AFFINITY]);
				y = describe(g, e.rule().copy().withStyle(s -> s.withItalic(true)), x, y + 4, w, bottom, in, 0xB8AE98);
				describe(g, Component.translatable("arrival.afterworld.affinity_gift", e.displayName().copy().withColor(e.color)), x, y + 2, w, bottom, in, DIM);
			}
			case NAME -> nameField(g, x, y, w, in, now);
			case GARB -> garbRows(g, x, y, w, in, bottom);
			case CONFIRM -> confirmPage(g, x, y, w, in, bottom);
			default -> {
			}
		}
	}

	/**
	 * A column of choices in display type: the highlighted one bright with a marker and a rule, the
	 * rest dim. Long lists scroll to keep the cursor in view. Returns the y below the list.
	 */
	private int list(GuiGraphicsExtractor g, List<String> labels, int x, int y, int w, float in, int maxBottom, java.util.function.IntUnaryOperator color) {
		float scale = crisp(1.0F);
		int rowH = Math.round(14 * scale);
		int visible = Math.max(3, Math.min(labels.size(), (maxBottom - y) / rowH));
		int cur = this.cursor[this.step];
		int first = Mth.clamp(cur - visible / 2, 0, Math.max(0, labels.size() - visible));
		var pose = g.pose();
		for (int i = first; i < Math.min(labels.size(), first + visible); i++) {
			int row = i - first;
			int ry = y + row * rowH;
			boolean on = i == cur;
			float delay = Mth.clamp(in * 1.6F - row * 0.08F, 0, 1);
			if (delay <= 0) {
				continue;
			}
			int slide = (int) ((1 - ease(delay)) * 10);
			int c = color.applyAsInt(i);
			int alpha = clampA(255 * delay);
			String label = labels.get(i).toUpperCase(java.util.Locale.ROOT);
			pose.pushMatrix();
			pose.translate(x + 12 + slide + (on ? 3 : 0), ry);
			pose.scale(scale, scale);
			g.text(this.font, display(label), 0, 0, on ? ARGB.color(alpha, BONE) : ARGB.color(alpha * 2 / 3, DIM), false);
			pose.popMatrix();
			if (on) {
				float pulse = 0.6F + 0.4F * Mth.sin(Util.getMillis() / 240.0F);
				pose.pushMatrix();
				pose.translate(x + slide, ry);
				pose.scale(scale, scale);
				g.text(this.font, display("▸"), 0, 0, ARGB.color(clampA(alpha * pulse), c), false);
				pose.popMatrix();
				int lw = (int) (this.font.width(display(label)) * scale);
				g.fillGradient(x + 12 + slide + 3, ry + (int) (11 * scale), x + 15 + slide + lw, ry + (int) (11 * scale) + 1, ARGB.color(alpha, c), ARGB.color(alpha, c));
			} else {
				// A small mark in the option's colour, so the list reads at a glance.
				g.fill(x + 3 + slide, ry + (int) (4 * scale), x + 5 + slide, ry + (int) (6 * scale), ARGB.color(alpha / 2, c));
			}
			this.hits.add(new Hit(x, ry - 2, w, rowH, i, 0));
		}
		if (first > 0) {
			g.text(this.font, display("↑"), x + w - 10, y, ARGB.color(clampA(150 * in), DIM), false);
		}
		if (first + visible < labels.size()) {
			g.text(this.font, display("↓"), x + w - 10, y + (visible - 1) * rowH, ARGB.color(clampA(150 * in), DIM), false);
		}
		return y + visible * rowH + 2;
	}

	private int describe(GuiGraphicsExtractor g, Component text, int x, int y, int w, int bottom, float in, int color) {
		for (FormattedCharSequence line : this.font.split(text, w)) {
			if (y + 9 > bottom) {
				break;
			}
			g.text(this.font, line, x, y, ARGB.color(clampA(235 * in), color), false);
			y += 10;
		}
		return y;
	}

	private void kit(GuiGraphicsExtractor g, Origin o, int x, int y, int w, int bottom, float in) {
		if (y + 16 > bottom) {
			return;
		}
		Component label = Component.translatable("arrival.afterworld.carries");
		g.text(this.font, label, x, y + 4, ARGB.color(clampA(235 * in), DIM), false);
		int ix = x + this.font.width(label) + 6;
		for (ItemStack stack : o.kit()) {
			if (ix + 16 > x + w) {
				break;
			}
			g.item(stack, ix, y);
			g.itemDecorations(this.font, stack, ix, y);
			if (this.lastMouseX >= ix && this.lastMouseX < ix + 16 && this.lastMouseY >= y && this.lastMouseY < y + 16) {
				g.setTooltipForNextFrame(this.font, stack, (int) this.lastMouseX, (int) this.lastMouseY);
			}
			ix += 18;
		}
	}

	private void nameField(GuiGraphicsExtractor g, int x, int y, int w, float in, long now) {
		String typedName = this.name.toString();
		boolean empty = typedName.isEmpty();
		String account = this.minecraft.getUser().getName();
		String shown = (empty ? account : typedName).toUpperCase(java.util.Locale.ROOT);
		float scale = crisp(2.0F);
		while (scale > crisp(1.0F) && this.font.width(display(shown + "_")) * scale > w) {
			scale = crisp(scale - 0.5F);
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y + 4);
		pose.scale(scale, scale);
		int textColor = empty ? ARGB.color(clampA(120 * in), DIM) : ARGB.color(clampA(255 * in), BONE);
		g.text(this.font, display(shown), 0, 0, textColor, false);
		if ((now / 450) % 2 == 0) {
			int cx = empty ? 0 : this.font.width(display(shown)) + 1;
			g.fill(cx, 10, cx + 7, 11, ARGB.color(clampA(255 * in), AMBER));
		}
		pose.popMatrix();
		int lineY = y + 4 + Math.round(14 * scale);
		g.fill(x, lineY, x + w, lineY + 1, ARGB.color(clampA(70 * in), BONE));
		describe(g, Component.translatable(empty ? "arrival.afterworld.name_help" : "arrival.afterworld.name_keep"), x, lineY + 6, w, this.height - 30, in, DIM);
	}

	private void garbRows(GuiGraphicsExtractor g, int x, int y, int w, float in, int bottom) {
		float scale = crisp(1.0F);
		int rowH = Math.max(11, Math.round(12 * scale));
		int visible = Math.max(4, Math.min(GARB_ROWS.length, (bottom - y - 14) / rowH));
		int cur = this.cursor[GARB];
		int first = Mth.clamp(cur - visible / 2, 0, Math.max(0, GARB_ROWS.length - visible));
		int valueX = x + Math.min(w / 2, 86);
		var pose = g.pose();
		for (int i = first; i < Math.min(GARB_ROWS.length, first + visible); i++) {
			int ry = y + (i - first) * rowH;
			boolean on = i == cur;
			int alpha = clampA(255 * in);
			String label = Component.translatable("arrival.afterworld.garb." + GARB_ROWS[i]).getString().toUpperCase(java.util.Locale.ROOT);
			pose.pushMatrix();
			pose.translate(x + 12 + (on ? 2 : 0), ry);
			pose.scale(scale * 0.75F, scale * 0.75F);
			g.text(this.font, display(label), 0, 1, on ? ARGB.color(alpha, BONE) : ARGB.color(alpha * 2 / 3, DIM), false);
			pose.popMatrix();
			if (on) {
				pose.pushMatrix();
				pose.translate(x, ry);
				pose.scale(scale * 0.75F, scale * 0.75F);
				g.text(this.font, display("▸"), 0, 1, ARGB.color(alpha, this.accent), false);
				pose.popMatrix();
			}
			// The value, between arrows you can click.
			int field = GARB_FIELD[i];
			Component value;
			int swatch = -1;
			if (field < 0) {
				value = Component.translatable("arrival.afterworld.garb.random_value");
			} else if (field == 8) {
				value = Component.translatable(this.garb.visible() ? "arrival.afterworld.garb.wear_yes" : "arrival.afterworld.garb.wear_no");
			} else if (field == 5 || field == 6) {
				int idx = this.garb.field(field);
				value = Component.translatable("garb.afterworld.colour." + Garb.PALETTE_NAMES[idx]);
				swatch = Garb.PALETTE[idx];
			} else {
				value = Component.translatable("garb.afterworld." + GARB_ROWS[i] + "." + this.garb.field(field));
			}
			int vy = ry + 1;
			int vColor = on ? ARGB.color(alpha, 0xFFD27A) : ARGB.color(alpha * 3 / 4, BONE);
			int vx = valueX;
			if (field >= 0) {
				g.text(this.font, "◂", vx, vy, ARGB.color(on ? alpha : alpha / 3, AMBER), false);
				this.hits.add(new Hit(vx - 3, ry - 1, 10, rowH, i, 1));
				vx += 9;
			}
			if (swatch >= 0) {
				g.fill(vx, vy, vx + 7, vy + 7, ARGB.opaque(swatch));
				g.outline(vx - 1, vy - 1, 9, 9, ARGB.color(alpha / 2, BONE));
				vx += 11;
			}
			g.text(this.font, value, vx, vy, vColor, false);
			vx += this.font.width(value) + 3;
			if (field >= 0) {
				g.text(this.font, "▸", vx, vy, ARGB.color(on ? alpha : alpha / 3, AMBER), false);
				this.hits.add(new Hit(vx - 2, ry - 1, 10, rowH, i, 2));
			}
			this.hits.add(new Hit(x, ry - 1, valueX - x - 4, rowH, i, 0));
		}
		int hy = y + visible * rowH + 4;
		describe(g, Component.translatable("arrival.afterworld.garb_help." + GARB_ROWS[cur]), x, hy, w, bottom, in, DIM);
	}

	private void confirmPage(GuiGraphicsExtractor g, int x, int y, int w, float in, int bottom) {
		String nm = this.name.toString().trim();
		if (nm.isEmpty()) {
			nm = this.minecraft.getUser().getName();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("arrival.afterworld.summary.name", Component.literal(nm).withColor(BONE)));
		lines.add(Component.translatable("arrival.afterworld.summary.origin", this.origin.displayName().copy().withColor(this.origin.color)));
		lines.add(this.discipline != Discipline.NONE
			? Component.translatable("arrival.afterworld.summary.discipline", this.discipline.displayName().copy().withColor(this.discipline.color))
			: Component.translatable("arrival.afterworld.summary.undecided"));
		if (this.affinity != null) {
			lines.add(Component.translatable("arrival.afterworld.summary.affinity", this.affinity.displayName().copy().withColor(this.affinity.color)));
		}
		for (Component line : lines) {
			y = describe(g, line, x, y, w, bottom, in, 0xB8AE98) + 2;
		}
		y += 6;
		List<String> labels = List.of(Component.translatable("arrival.afterworld.yes").getString(), Component.translatable("arrival.afterworld.not_yet").getString());
		list(g, labels, x, y, w, in, bottom, i -> i == 0 ? AMBER : 0x9AA3A8);
	}

	// ------------------------------------------------------------------ chrome

	/** Squares in the corner: answered, current (pulsing), still to come. */
	private void drawTrail(GuiGraphicsExtractor g, int x, long now) {
		int y = 14;
		int n = 6;
		int size = 5;
		for (int i = 0; i < n; i++) {
			int q = ORIGIN + i;
			int sx = x + i * (size + 4);
			boolean current = q == this.step;
			boolean done = q < this.step || q == DISCIPLINE && this.lockedDiscipline != Discipline.NONE && this.step > DISCIPLINE;
			if (current) {
				float pulse = 0.5F + 0.5F * Mth.sin(now / 260.0F);
				g.fill(sx - 1, y - 1, sx + size + 1, y + size + 1, ARGB.color(clampA(60 + 80 * pulse), this.accent));
				g.fill(sx, y, sx + size, y + size, ARGB.opaque(BONE));
			} else if (done) {
				g.fill(sx, y, sx + size, y + size, ARGB.opaque(AMBER));
			} else {
				g.outline(sx, y, size, size, ARGB.opaque(FAINT));
			}
		}
		Component label = Component.translatable("arrival.afterworld.step", this.step, n);
		g.text(this.font, label, x + n * (size + 4) + 4, y - 1, ARGB.opaque(0x5C574E), false);
	}

	/** Key hints along the bottom edge, as little keycaps. */
	private void drawHints(GuiGraphicsExtractor g, int x) {
		int y = this.height - 17;
		List<String[]> hints = new ArrayList<>();
		if (this.step == NAME) {
			hints.add(new String[] {"←", "arrival.afterworld.hint.erase"});
		} else {
			hints.add(new String[] {"W S", "arrival.afterworld.hint.choose"});
		}
		if (this.step == GARB) {
			hints.add(new String[] {"A D", "arrival.afterworld.hint.change"});
		}
		hints.add(new String[] {"ENTER", this.wardrobe ? "arrival.afterworld.hint.done" : "arrival.afterworld.hint.confirm"});
		if (this.wardrobe || this.step > ORIGIN) {
			hints.add(new String[] {"ESC", this.wardrobe ? "arrival.afterworld.hint.cancel" : "arrival.afterworld.hint.back"});
		}
		hints.add(new String[] {"↔", "arrival.afterworld.hint.turn"});
		int hx = x;
		for (String[] h : hints) {
			Component cap = Component.literal(h[0]);
			int cw = this.font.width(cap) + 6;
			g.fill(hx, y - 2, hx + cw, y + 10, 0x55141218);
			g.outline(hx, y - 2, cw, 12, 0x88E8DCC0);
			g.fill(hx + 1, y + 9, hx + cw - 1, y + 10, 0x55E8DCC0);
			g.text(this.font, cap, hx + 3, y, 0xFFE8DCC0, false);
			Component what = Component.translatable(h[1]);
			g.text(this.font, what, hx + cw + 4, y, 0xFF8A8478, false);
			int used = cw + 4 + this.font.width(what) + 12;
			if (h[1].endsWith("back") || h[1].endsWith("cancel")) {
				this.hits.add(new Hit(hx, y - 2, used - 8, 12, this.cursor[this.step], 3));
			}
			hx += used;
			if (hx > this.stageX0 + 40) {
				break;
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private static int clampA(float a) {
		return Mth.clamp((int) a, 0, 255);
	}

	private static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int uw, int vh, int texW, int texH, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, texW, texH, color);
	}

	private static float ease(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	/** For tests: where the figure is turned, what has been typed, and the middle of the stage. */
	public float yawForTest() {
		return this.yaw;
	}

	public String nameForTest() {
		return this.name.toString();
	}

	public int stepForTest() {
		return this.step;
	}

	public int[] stageCenterForTest() {
		return new int[] {(this.stageX0 + this.stageX1) / 2, (this.stageY0 + this.stageY1) / 2};
	}

	/** Letters only arrive while the window is taking text, so take it on the name step and only there. */
	private void syncTextInput() {
		var input = this.minecraft.textInputManager();
		if (this.step == NAME && this.outroAt < 0) {
			input.startTextInput(this);
		} else {
			input.stopTextInput(this);
		}
	}

	@Override
	public void removed() {
		this.minecraft.textInputManager().stopTextInput(this);
		super.removed();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}
}
