package dev.afterworld.client.minigame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.client.screen.AwScreen;
import dev.afterworld.net.AwPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bench trials, each its own little screen: the anvil (strike in the gold), the stew pot (keep
 * the heat and add what the broth asks for), Mab's cauldron (drop each thing in on its colour, stir
 * with the bubbles), the star dials (turn each star home) and the chapel bells (ring back the
 * apprentice's song). They keep their own score and tell the server when they are done.
 */
public final class TrialScreens {
	static final int BG = 0xF0121418;
	static final int FRAME = 0xFF3A2E1E;
	static final int INK = 0xFFE8DCC0;
	static final int DIM = 0xFFA89A7C;
	static final int GOLD = 0xFFFFD27A;
	static final int RED = 0xFFD8505A;
	static final int GREEN = 0xFF7FD27A;

	private TrialScreens() {
	}

	public static Screen create(String id, CompoundTag data) {
		long seed = data.getIntOr("seed", 0);
		return switch (id) {
			case "smithing" -> new Smithing(seed);
			case "cooking" -> new Cooking(seed);
			case "brewing" -> new Brewing(seed);
			case "stars" -> new Stars(seed);
			default -> new Melody(seed);
		};
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Afterworld.id(id));
	}

	static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int hw = (int) Math.round(Math.sqrt(r * r - dy * dy));
			g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
		}
	}

	static void ellipse(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color) {
		for (int dy = -ry; dy <= ry; dy++) {
			int hw = (int) Math.round(rx * Math.sqrt(1 - (dy / (double) ry) * (dy / (double) ry)));
			g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
		}
	}

	static int hsv(float h, float s, float v) {
		h = (h % 1 + 1) % 1;
		int i = (int) (h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		float r, gg, b;
		switch (i) {
			case 0 -> { r = v; gg = t; b = p; }
			case 1 -> { r = q; gg = v; b = p; }
			case 2 -> { r = p; gg = v; b = t; }
			case 3 -> { r = p; gg = q; b = v; }
			case 4 -> { r = t; gg = p; b = v; }
			default -> { r = v; gg = p; b = q; }
		}
		return ARGB.color(255, (int) (r * 255), (int) (gg * 255), (int) (b * 255));
	}

	/** What every bench trial shares: the frame, the title and hint, quitting, and reporting. */
	abstract static class Base extends AwScreen {
		final String id;
		final Random rand;
		int ticks;
		boolean done;
		int doneAt = -1;
		Component verdict = Component.empty();
		final List<float[]> sparks = new ArrayList<>();
		int px;
		int py;
		int pw = 300;
		int ph = 204;

		Base(String id, long seed) {
			super(Component.translatable("trial.afterworld." + id));
			this.id = id;
			this.rand = new Random(seed);
		}

		@Override
		protected int prefWidth() {
			return this.pw + 24;
		}

		@Override
		protected int prefHeight() {
			return this.ph + 24;
		}

		@Override
		protected void init() {
			super.init();
			this.px = this.cw / 2 - this.pw / 2;
			this.py = this.ch / 2 - this.ph / 2;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
			g.fill(0, 0, this.width, this.height, 0x90050709);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
			dev.afterworld.client.screen.Frames.panel(g, this.px, this.py, this.pw, this.ph, GOLD & 0xFFFFFF);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(this.cw / 2F, this.py + 7);
			pose.scale(1.4F, 1.4F);
			g.centeredText(this.font, this.title, 0, 0, GOLD);
			pose.popMatrix();
			var hint = this.font.split(Component.translatable("trial.afterworld." + this.id + ".hint"), this.pw - 24);
			int hy = this.py + this.ph - 12 - hint.size() * 10;
			for (int i = 0; i < hint.size(); i++) {
				g.centeredText(this.font, hint.get(i), this.cw / 2, hy + i * 10, DIM);
			}
			g.centeredText(this.font, Component.translatable("trial.afterworld.quit"), this.cw / 2, this.py + this.ph - 11, 0xFF6A6458);
			draw(g, a);
			// Sparks and motes.
			for (float[] s : this.sparks) {
				float life = s[4];
				g.fill((int) s[0], (int) s[1], (int) s[0] + 2, (int) s[1] + 2, ARGB.color((int) (255 * Mth.clamp(life, 0, 1)), (int) s[5]));
			}
			if (this.done) {
				g.centeredText(this.font, this.verdict, this.cw / 2, this.py + this.ph - 46, GOLD);
			}
		}

		abstract void draw(GuiGraphicsExtractor g, float a);

		abstract void step();

		@Override
		public void tick() {
			super.tick();
			this.ticks++;
			for (int i = this.sparks.size() - 1; i >= 0; i--) {
				float[] s = this.sparks.get(i);
				s[0] += s[2];
				s[1] += s[3];
				s[3] += 0.25F;
				s[4] -= 0.06F;
				if (s[4] <= 0) {
					this.sparks.remove(i);
				}
			}
			if (!this.done) {
				step();
			} else if (this.doneAt >= 0 && this.ticks - this.doneAt > 60) {
				// The server answers with the verdict; if it never does, let the traveller go.
				closeQuietly();
			}
		}

		void burst(int x, int y, int n, int color) {
			for (int i = 0; i < n; i++) {
				float ang = this.rand.nextFloat() * Mth.TWO_PI;
				float sp = 1 + this.rand.nextFloat() * 3;
				this.sparks.add(new float[] {x, y, Mth.cos(ang) * sp, Mth.sin(ang) * sp - 2, 1, color});
			}
		}

		void finish(int score, Component verdict) {
			if (this.done) {
				return;
			}
			this.done = true;
			this.doneAt = this.ticks;
			this.verdict = verdict;
			ClientPlayNetworking.send(new AwPayloads.TrialReply(this.id, AwPayloads.TrialReply.RESULT, score));
		}

		void closeQuietly() {
			this.done = true;
			super.onClose();
		}

		@Override
		public void onClose() {
			if (!this.done) {
				this.done = true;
				ClientPlayNetworking.send(new AwPayloads.TrialReply(this.id, AwPayloads.TrialReply.QUIT, 0));
			}
			super.onClose();
		}
	}

	// ------------------------------------------------------------------ the anvil

	/** Five strikes. The marker sweeps the glowing bar; strike while it sits in the gold. */
	static final class Smithing extends Base {
		float m = 0.1F;
		float v = 0.02F;
		float zone;
		float half;
		int strike;
		int points;
		final int[] results = new int[5];
		int flash;
		Component last = Component.empty();

		Smithing(long seed) {
			super("smithing", seed);
			next();
		}

		void next() {
			this.zone = 0.2F + this.rand.nextFloat() * 0.6F;
			this.half = 0.11F - this.strike * 0.012F;
			this.v = Math.copySign(0.018F + this.strike * 0.006F, this.v);
		}

		@Override
		void step() {
			this.m += this.v;
			if (this.m > 1 || this.m < 0) {
				this.v = -this.v;
				this.m = Mth.clamp(this.m, 0, 1);
			}
			if (this.flash > 0) {
				this.flash--;
			}
		}

		void hit() {
			if (this.done || this.strike >= 5) {
				return;
			}
			float d = Math.abs(this.m - this.zone);
			int r = d <= this.half * 0.35F ? 2 : d <= this.half ? 1 : 0;
			this.results[this.strike] = r + 1;
			this.points += r;
			this.strike++;
			this.flash = 6;
			int bx = this.px + 40 + (int) ((this.pw - 80) * this.m);
			int by = this.py + 72;
			if (r == 2) {
				this.last = Component.translatable("trial.afterworld.smithing.perfect");
				play(SoundEvents.ANVIL_USE, 1.2F, 0.7F);
				burst(bx, by, 22, 0xFFE8A0);
			} else if (r == 1) {
				this.last = Component.translatable("trial.afterworld.smithing.good");
				play(SoundEvents.ANVIL_USE, 1.0F, 0.6F);
				burst(bx, by, 12, 0xFFB050);
			} else {
				this.last = Component.translatable("trial.afterworld.smithing.miss");
				play(SoundEvents.ANVIL_LAND, 0.7F, 0.4F);
				burst(bx, by, 5, 0x8A5A3A);
			}
			if (this.strike >= 5) {
				finish(this.points, Component.translatable(this.points >= 6 ? "trial.afterworld.smithing.sings" : "trial.afterworld.smithing.cooled"));
			} else {
				next();
			}
		}

		@Override
		void draw(GuiGraphicsExtractor g, float a) {
			int x0 = this.px + 40;
			int x1 = this.px + this.pw - 40;
			int y = this.py + 64;
			// The bar: cool iron at the ends, white heat at the heart.
			for (int x = x0; x < x1; x++) {
				float t = (x - x0) / (float) (x1 - x0);
				float heat = 1 - Math.abs(t - 0.5F) * 1.4F;
				int col = hsv(0.02F + 0.09F * heat, 0.9F - 0.4F * heat * heat, 0.45F + 0.5F * heat);
				g.fill(x, y, x + 1, y + 16, col);
			}
			int zx0 = x0 + (int) ((x1 - x0) * (this.zone - this.half));
			int zx1 = x0 + (int) ((x1 - x0) * (this.zone + this.half));
			g.fill(zx0, y - 3, zx1, y + 19, 0x70FFD27A);
			g.outline(zx0, y - 3, zx1 - zx0, 22, GOLD);
			int px0 = x0 + (int) ((x1 - x0) * (this.zone - this.half * 0.35F));
			int px1 = x0 + (int) ((x1 - x0) * (this.zone + this.half * 0.35F));
			g.fill(px0, y - 3, px1, y - 1, 0xFFFFFFFF);
			float mm = Mth.clamp(this.m + this.v * a, 0, 1);
			int mx = x0 + (int) ((x1 - x0) * mm);
			g.fill(mx - 1, y - 8, mx + 2, y + 24, this.flash > 0 ? 0xFFFFFFFF : 0xFFEDE6D6);
			g.fill(mx - 5, y - 12, mx + 6, y - 8, 0xFF8A8F98);
			// The strikes so far.
			for (int i = 0; i < 5; i++) {
				int cx = this.cw / 2 - 40 + i * 20;
				int col = this.results[i] == 3 ? GOLD : this.results[i] == 2 ? 0xFFE8A050 : this.results[i] == 1 ? RED : 0xFF3A3F46;
				disc(g, cx, this.py + 110, 5, col);
			}
			g.centeredText(this.font, this.last, this.cw / 2, this.py + 124, INK);
			g.centeredText(this.font, Component.translatable("trial.afterworld.smithing.score", this.points), this.cw / 2, this.py + 40, DIM);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.key() == InputConstants.KEY_SPACE) {
				hit();
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			hit();
			return true;
		}
	}

	// ------------------------------------------------------------------ the stew pot

	/** Keep the broth in the green by stirring (hold Space); add what it asks for (1-4) in time. */
	static final class Cooking extends Base {
		static final int PROMPTS = 8;
		final ItemStack[] food = {new ItemStack(Items.BEEF), new ItemStack(Items.CARROT), new ItemStack(Items.POTATO), new ItemStack(item("ember_core"))};
		float heat = 0.5F;
		boolean stirring;
		int inBand;
		int prompt = -1;
		int promptAt;
		int asked;
		int points;
		int scorch;
		Component last = Component.empty();
		int lastCol = INK;

		Cooking(long seed) {
			super("cooking", seed);
		}

		@Override
		void step() {
			this.heat += 0.0055F + (this.rand.nextFloat() - 0.5F) * 0.004F - (this.stirring ? 0.016F : 0);
			this.heat = Mth.clamp(this.heat, 0, 1);
			if (this.heat >= 0.97F) {
				this.points -= 1;
				this.scorch++;
				this.heat = 0.6F;
				say("trial.afterworld.cooking.scorched", RED);
				play(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
			}
			if (this.heat >= 0.35F && this.heat <= 0.7F) {
				this.inBand++;
			}
			if (this.stirring && this.ticks % 6 == 0) {
				burst(this.cw / 2 + this.rand.nextInt(40) - 20, this.py + 92, 1, 0xC8B080);
			}
			if (this.prompt >= 0 && this.ticks - this.promptAt > 40) {
				this.prompt = -1;
				say("trial.afterworld.cooking.late", DIM);
			}
			if (this.prompt < 0 && this.ticks >= 40 + this.asked * 70) {
				if (this.asked >= PROMPTS) {
					int total = this.points + Math.round(6F * this.inBand / Math.max(1, this.ticks));
					finish(total, Component.translatable(total >= 14 ? "trial.afterworld.cooking.tasty" : "trial.afterworld.cooking.bland"));
					return;
				}
				this.prompt = this.rand.nextInt(4);
				this.promptAt = this.ticks;
				this.asked++;
				play(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2F, 0.6F);
			}
		}

		void say(String key, int col) {
			this.last = Component.translatable(key);
			this.lastCol = col;
		}

		void add(int k) {
			if (this.prompt < 0 || this.done) {
				return;
			}
			if (k == this.prompt) {
				this.points += 2;
				say("trial.afterworld.cooking.right", GREEN);
				play(SoundEvents.GENERIC_SPLASH, 1.3F, 0.5F);
				burst(this.cw / 2, this.py + 92, 14, 0xE8A050);
			} else {
				this.points -= 1;
				say("trial.afterworld.cooking.wrong", RED);
				play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
			}
			this.prompt = -1;
		}

		@Override
		void draw(GuiGraphicsExtractor g, float a) {
			int cx = this.cw / 2;
			int cy = this.py + 100;
			// The pot and the broth, its colour following the heat.
			ellipse(g, cx, cy + 10, 52, 30, 0xFF2A2C30);
			int broth = this.heat > 0.7F ? hsv(0.02F, 0.8F, 0.55F - (this.heat - 0.7F)) : hsv(0.07F, 0.75F, 0.45F + this.heat * 0.4F);
			ellipse(g, cx, cy, 46, 14, broth);
			for (int i = 0; i < 5; i++) {
				int bx = cx - 34 + (int) ((i * 17 + this.ticks * (0.4F + this.heat)) % 68);
				disc(g, bx, cy - 2 + (i % 2) * 4, 2, ARGB.color(160, 255, 220, 160));
			}
			// The heat gauge, with its green band.
			int gx = this.px + this.pw - 34;
			int gy0 = this.py + 34;
			int gy1 = this.py + 140;
			g.fill(gx, gy0, gx + 10, gy1, 0xFF262B31);
			int b0 = gy1 - (int) ((gy1 - gy0) * 0.7F);
			int b1 = gy1 - (int) ((gy1 - gy0) * 0.35F);
			g.fill(gx, b0, gx + 10, b1, 0xFF2E5A34);
			int hy = gy1 - (int) ((gy1 - gy0) * this.heat);
			g.fill(gx - 3, hy - 1, gx + 13, hy + 2, this.heat > 0.7F ? RED : this.heat < 0.35F ? 0xFF7FB8E8 : GREEN);
			// What the broth wants, and the shelf of ingredients.
			if (this.prompt >= 0) {
				float left = 1 - (this.ticks - this.promptAt + a) / 40F;
				g.fill(cx - 12, cy - 54, cx + 12, cy - 30, 0xFF1E232A);
				g.fill(cx - 12, cy - 30, cx - 12 + (int) (24 * left), cy - 28, GOLD);
				g.item(this.food[this.prompt], cx - 8, cy - 50);
			}
			for (int i = 0; i < 4; i++) {
				int ix = cx - 60 + i * 32;
				int iy = this.py + 140;
				g.fill(ix - 2, iy - 2, ix + 18, iy + 18, this.prompt == i ? 0xFF4A3E22 : 0xFF1E232A);
				g.item(this.food[i], ix, iy);
				g.text(this.font, Component.literal(String.valueOf(i + 1)), ix + 14, iy + 10, GOLD, true);
			}
			g.centeredText(this.font, this.last, cx, this.py + 30, this.lastCol);
			g.text(this.font, Component.translatable("trial.afterworld.cooking.stir"), this.px + 10, this.py + 34, this.stirring ? GOLD : DIM, false);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			int k = event.key();
			if (k == InputConstants.KEY_SPACE) {
				this.stirring = true;
				return true;
			}
			if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_4) {
				add(k - InputConstants.KEY_1);
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public boolean keyReleased(KeyEvent event) {
			if (event.key() == InputConstants.KEY_SPACE) {
				this.stirring = false;
				return true;
			}
			return super.keyReleased(event);
		}
	}

	// ------------------------------------------------------------------ Mab's cauldron

	/** Drop each ingredient in when the brew turns its colour (Space); stir with the bubbles (A / D). */
	static final class Brewing extends Base {
		final ItemStack[] what = {new ItemStack(item("lurker_fang")), new ItemStack(item("bog_amber")), new ItemStack(item("moth_dust"))};
		final float[] hue = {0.0F, 0.11F, 0.78F};
		final int[] order = new int[6];
		int stepIdx;
		int stirDir;
		int stirAt = -1;
		int stirs;
		int points;
		float tint;
		Component last = Component.empty();
		int lastCol = INK;

		Brewing(long seed) {
			super("brewing", seed);
			for (int i = 0; i < 6; i++) {
				this.order[i] = this.rand.nextInt(3);
			}
		}

		float hueNow(float a) {
			return ((this.ticks + a) * 0.006F) % 1F;
		}

		@Override
		void step() {
			if (this.stirAt >= 0 && this.ticks - this.stirAt > 50) {
				this.stirAt = -1;
				say("trial.afterworld.brewing.unstirred", DIM);
			}
			if (this.stepIdx >= 6 && this.stirAt < 0 && this.stirs >= 3) {
				finish(this.points, Component.translatable(this.points >= 10 ? "trial.afterworld.brewing.good" : "trial.afterworld.brewing.murky"));
			}
		}

		void say(String key, int col) {
			this.last = Component.translatable(key);
			this.lastCol = col;
		}

		void drop() {
			if (this.done || this.stepIdx >= 6 || this.stirAt >= 0) {
				return;
			}
			int k = this.order[this.stepIdx];
			float d = Math.abs(hueNow(0) - this.hue[k]);
			d = Math.min(d, 1 - d);
			if (d <= 0.05F) {
				this.points += 2;
				say("trial.afterworld.brewing.drop_good", GREEN);
				play(SoundEvents.BREWING_STAND_BREW, 1.2F, 0.6F);
				burst(this.cw / 2, this.py + 92, 16, hsv(this.hue[k], 0.8F, 1F) & 0xFFFFFF);
			} else {
				say("trial.afterworld.brewing.drop_bad", RED);
				play(SoundEvents.FIRE_EXTINGUISH, 1.4F, 0.5F);
			}
			this.stepIdx++;
			if (this.stepIdx % 2 == 0) {
				this.stirDir = this.rand.nextBoolean() ? 1 : -1;
				this.stirAt = this.ticks;
				this.stirs++;
			}
		}

		void stir(int dir) {
			if (this.stirAt < 0 || this.done) {
				return;
			}
			if (dir == this.stirDir) {
				this.points += 1;
				say("trial.afterworld.brewing.stir_good", GREEN);
				play(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.2F, 0.6F);
			} else {
				say("trial.afterworld.brewing.stir_bad", RED);
				play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
			}
			this.stirAt = -1;
		}

		@Override
		void draw(GuiGraphicsExtractor g, float a) {
			int cx = this.cw / 2;
			int cy = this.py + 100;
			ellipse(g, cx, cy + 12, 50, 32, 0xFF33363C);
			int brew = hsv(hueNow(a), 0.75F, 0.85F);
			ellipse(g, cx, cy, 44, 13, brew);
			// Bubbles circling the way the brew wants stirring.
			for (int i = 0; i < 8; i++) {
				float ang = (this.ticks + a) * 0.08F * (this.stirAt >= 0 ? this.stirDir : 1) + i * Mth.TWO_PI / 8;
				disc(g, cx + (int) (Mth.cos(ang) * 34), cy + (int) (Mth.sin(ang) * 9), 2, ARGB.color(170, 255, 255, 255));
			}
			if (this.stirAt >= 0) {
				Component arrow = Component.translatable(this.stirDir > 0 ? "trial.afterworld.brewing.clockwise" : "trial.afterworld.brewing.counter");
				g.centeredText(this.font, arrow, cx, cy - 34, GOLD);
			}
			// The recipe card: each ingredient and the colour it goes in on.
			int rx = this.px + 12;
			int ry = this.py + 36;
			for (int i = 0; i < 3; i++) {
				g.item(this.what[i], rx, ry + i * 22);
				g.fill(rx + 20, ry + i * 22 + 4, rx + 32, ry + i * 22 + 12, hsv(this.hue[i], 0.75F, 0.85F));
			}
			// The steps: what goes in next.
			for (int i = 0; i < 6; i++) {
				int sx = cx - 60 + i * 22;
				int sy = this.py + 142;
				g.fill(sx - 2, sy - 2, sx + 18, sy + 18, i == this.stepIdx ? 0xFF4A3E22 : i < this.stepIdx ? 0xFF1A1E24 : 0xFF1E232A);
				g.item(this.what[this.order[i]], sx, sy);
			}
			g.centeredText(this.font, this.last, cx, this.py + 30, this.lastCol);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			int k = event.key();
			if (k == InputConstants.KEY_SPACE) {
				drop();
				return true;
			}
			if (k == InputConstants.KEY_A || k == InputConstants.KEY_LEFT) {
				stir(-1);
				return true;
			}
			if (k == InputConstants.KEY_D || k == InputConstants.KEY_RIGHT) {
				stir(1);
				return true;
			}
			return super.keyPressed(event);
		}
	}

	// ------------------------------------------------------------------ the star dials

	/** Five stars of the Lost Moth on their dials. Turn each until it sits on its mark; it settles. */
	static final class Stars extends Base {
		static final float[][] TARGET = {{0.18F, 0.34F}, {0.36F, 0.56F}, {0.5F, 0.42F}, {0.64F, 0.56F}, {0.82F, 0.34F}};
		static final int[][] LINES = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {1, 3}};
		final float[] angle = new float[5];
		final float[] goal = new float[5];
		final float[] radius = new float[5];
		final boolean[] locked = new boolean[5];
		int sel;
		int turning;

		Stars(long seed) {
			super("stars", seed);
			this.ph = 210;
			for (int i = 0; i < 5; i++) {
				this.goal[i] = this.rand.nextFloat() * 360;
				this.angle[i] = (this.goal[i] + 90 + this.rand.nextFloat() * 180) % 360;
				this.radius[i] = 9 + this.rand.nextInt(6);
			}
		}

		float[] pos(int i, float ang) {
			float cxf = TARGET[i][0] - (float) Math.cos(Math.toRadians(this.goal[i])) * this.radius[i] / 220F;
			float cyf = TARGET[i][1] - (float) Math.sin(Math.toRadians(this.goal[i])) * this.radius[i] / 120F;
			return new float[] {cxf + (float) Math.cos(Math.toRadians(ang)) * this.radius[i] / 220F, cyf + (float) Math.sin(Math.toRadians(ang)) * this.radius[i] / 120F};
		}

		@Override
		void step() {
			if (this.turning != 0) {
				turn(this.turning * 2.5F);
				if (this.ticks % 4 == 0) {
					play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
				}
			}
		}

		/** Turns the chosen star; close enough to its mark, it settles there for good. */
		void turn(float deg) {
			if (this.locked[this.sel] || this.done) {
				return;
			}
			this.angle[this.sel] = (this.angle[this.sel] + deg + 360) % 360;
			float d = Math.abs(Mth.wrapDegrees(this.angle[this.sel] - this.goal[this.sel]));
			if (d >= 4) {
				return;
			}
			this.locked[this.sel] = true;
			this.angle[this.sel] = this.goal[this.sel];
			this.turning = 0;
			play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F + this.sel * 0.15F, 1.0F);
			float[] p = pos(this.sel, this.goal[this.sel]);
			burst(this.px + 10 + (int) ((this.pw - 20) * p[0]), this.py + 30 + (int) (120 * p[1]), 14, 0xE8F0FF);
			for (int k = 1; k <= 5; k++) {
				if (!this.locked[(this.sel + k) % 5]) {
					this.sel = (this.sel + k) % 5;
					return;
				}
			}
			play(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.7F);
			finish(5, Component.translatable("trial.afterworld.stars.home"));
		}

		@Override
		void draw(GuiGraphicsExtractor g, float a) {
			int x0 = this.px + 10;
			int y0 = this.py + 30;
			int w = this.pw - 20;
			int h = 120;
			for (int y = 0; y < h; y++) {
				g.fill(x0, y0 + y, x0 + w, y0 + y + 1, ARGB.color(255, 10 + y / 8, 14 + y / 6, 34 + y / 3));
			}
			Random dust = new Random(7);
			for (int i = 0; i < 70; i++) {
				int sx = x0 + dust.nextInt(w);
				int sy = y0 + dust.nextInt(h);
				int tw = (int) (90 + 80 * Math.sin((this.ticks + i * 13) * 0.07));
				g.fill(sx, sy, sx + 1, sy + 1, ARGB.color(tw, 220, 225, 255));
			}
			// The chart: where the stars belong.
			for (int[] l : LINES) {
				float[] p = TARGET[l[0]];
				float[] q = TARGET[l[1]];
				int n = 30;
				for (int k = 0; k <= n; k++) {
					float t = k / (float) n;
					int lx = x0 + (int) (w * (p[0] + (q[0] - p[0]) * t));
					int ly = y0 + (int) (h * (p[1] + (q[1] - p[1]) * t));
					g.fill(lx, ly, lx + 1, ly + 1, 0x506A86C8);
				}
			}
			for (int i = 0; i < 5; i++) {
				int tx = x0 + (int) (w * TARGET[i][0]);
				int ty = y0 + (int) (h * TARGET[i][1]);
				g.outline(tx - 4, ty - 4, 9, 9, this.locked[i] ? 0xFFB9A4FF : 0x806A86C8);
				float[] p = pos(i, this.angle[i] + (i == this.sel && !this.locked[i] ? this.turning * 2.5F * a : 0));
				int sx = x0 + (int) (w * p[0]);
				int sy = y0 + (int) (h * p[1]);
				int col = this.locked[i] ? 0xFFFFFFFF : i == this.sel ? GOLD : 0xFFCFD8FF;
				disc(g, sx, sy, this.locked[i] ? 3 : 2, col);
				if (i == this.sel && !this.locked[i]) {
					g.outline(sx - 6, sy - 6, 13, 13, ARGB.color(140, 255, 210, 122));
				}
			}
			// The dials.
			for (int i = 0; i < 5; i++) {
				int dx = this.cw / 2 - 80 + i * 40;
				int dy = this.py + 168;
				disc(g, dx, dy, 11, i == this.sel ? 0xFF4A3E22 : 0xFF22272E);
				disc(g, dx, dy, 9, this.locked[i] ? 0xFF3A3266 : 0xFF16191E);
				float ang = (float) Math.toRadians(this.angle[i]);
				g.fill(dx + (int) (Math.cos(ang) * 7) - 1, dy + (int) (Math.sin(ang) * 7) - 1, dx + (int) (Math.cos(ang) * 7) + 2, dy + (int) (Math.sin(ang) * 7) + 2,
					this.locked[i] ? 0xFFB9A4FF : GOLD);
				g.centeredText(this.font, Component.literal(String.valueOf(i + 1)), dx, dy + 14, i == this.sel ? GOLD : DIM);
			}
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			int k = event.key();
			if (k == InputConstants.KEY_A || k == InputConstants.KEY_LEFT) {
				this.turning = -1;
				return true;
			}
			if (k == InputConstants.KEY_D || k == InputConstants.KEY_RIGHT) {
				this.turning = 1;
				return true;
			}
			if (k == InputConstants.KEY_W || k == InputConstants.KEY_UP || k == InputConstants.KEY_TAB) {
				this.sel = (this.sel + 1) % 5;
				return true;
			}
			if (k == InputConstants.KEY_S || k == InputConstants.KEY_DOWN) {
				this.sel = (this.sel + 4) % 5;
				return true;
			}
			if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_5) {
				this.sel = k - InputConstants.KEY_1;
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public boolean keyReleased(KeyEvent event) {
			int k = event.key();
			if (k == InputConstants.KEY_A || k == InputConstants.KEY_LEFT || k == InputConstants.KEY_D || k == InputConstants.KEY_RIGHT) {
				this.turning = 0;
				return true;
			}
			return super.keyReleased(event);
		}

		@Override
		public boolean mouseScrolled(double x, double y, double dx, double dy) {
			turn((float) dy * 3F);
			return true;
		}
	}

	// ------------------------------------------------------------------ the chapel bells

	/** The apprentice hums; ring the bells back in the same order (1-5). Three verses. */
	static final class Melody extends Base {
		static final float[] PITCH = {0.75F, 0.84F, 0.94F, 1.12F, 1.26F};
		final int[] song = new int[6];
		int verse;
		int heard;
		int playing = -1;
		int playAt;
		int lit = -1;
		int litAt;
		Component last = Component.empty();
		int lastCol = INK;

		Melody(long seed) {
			super("melody", seed);
			for (int i = 0; i < 6; i++) {
				this.song[i] = this.rand.nextInt(5);
			}
			hum();
		}

		int length() {
			return 4 + this.verse;
		}

		void hum() {
			this.playing = 0;
			this.playAt = this.ticks + 20;
			this.heard = 0;
			this.last = Component.translatable("trial.afterworld.melody.listen");
			this.lastCol = DIM;
		}

		@Override
		void step() {
			if (this.playing >= 0 && this.ticks >= this.playAt) {
				if (this.playing < length()) {
					ring(this.song[this.playing]);
					this.playing++;
					this.playAt = this.ticks + 14;
				} else {
					this.playing = -1;
					this.last = Component.translatable("trial.afterworld.melody.your_turn");
					this.lastCol = GOLD;
				}
			}
		}

		void ring(int b) {
			this.lit = b;
			this.litAt = this.ticks;
			play(SoundEvents.NOTE_BLOCK_BELL.value(), PITCH[b], 0.9F);
			burst(bellX(b), this.py + 80, 6, 0xE8F4FF);
		}

		int bellX(int b) {
			return this.cw / 2 - 100 + b * 50;
		}

		void press(int b) {
			if (this.playing >= 0 || this.done) {
				return;
			}
			ring(b);
			if (this.song[this.heard] == b) {
				this.heard++;
				if (this.heard >= length()) {
					this.verse++;
					if (this.verse >= 3) {
						play(SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
						finish(3, Component.translatable("trial.afterworld.melody.finished"));
					} else {
						this.last = Component.translatable("trial.afterworld.melody.verse");
						this.lastCol = GREEN;
						this.playing = 0;
						this.playAt = this.ticks + 30;
						this.heard = 0;
					}
				}
			} else {
				play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
				this.last = Component.translatable("trial.afterworld.melody.again");
				this.lastCol = RED;
				this.playing = 0;
				this.playAt = this.ticks + 30;
				this.heard = 0;
			}
		}

		@Override
		void draw(GuiGraphicsExtractor g, float a) {
			for (int b = 0; b < 5; b++) {
				int x = bellX(b);
				int y = this.py + 70;
				boolean on = this.lit == b && this.ticks - this.litAt < 10;
				int col = on ? 0xFFFFE8A0 : 0xFFB89A50;
				int sway = on ? (int) (Mth.sin((this.ticks - this.litAt + a) * 1.2F) * 2) : 0;
				g.fill(x - 1 + sway, y - 22, x + 1 + sway, y - 12, 0xFF6A5A40);
				for (int k = 0; k < 18; k++) {
					int hw = 5 + k / 2;
					g.fill(x - hw + sway, y - 12 + k, x + hw + sway, y - 11 + k, col);
				}
				disc(g, x + sway, y + 8, 3, 0xFF7A6430);
				g.centeredText(this.font, Component.literal(String.valueOf(b + 1)), x, y + 18, on ? GOLD : DIM);
			}
			for (int i = 0; i < length(); i++) {
				int dx = this.cw / 2 - length() * 6 + i * 12;
				disc(g, dx, this.py + 122, 3, i < this.heard ? GOLD : 0xFF3A3F46);
			}
			for (int v = 0; v < 3; v++) {
				disc(g, this.cw / 2 - 14 + v * 14, this.py + 136, 4, v < this.verse ? 0xFFB9A4FF : 0xFF2A2E36);
			}
			g.centeredText(this.font, this.last, this.cw / 2, this.py + 30, this.lastCol);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			int k = event.key();
			if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_5) {
				press(k - InputConstants.KEY_1);
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			double x = event.x() / this.ui;
			double y = event.y() / this.ui;
			for (int b = 0; b < 5; b++) {
				if (Math.abs(x - bellX(b)) < 14 && y > this.py + 44 && y < this.py + 96) {
					press(b);
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		}
	}
}
