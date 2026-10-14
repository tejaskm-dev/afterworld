package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Warden's Ladder's gate, carved in stone. On the left the Ladder itself, fifty rungs climbing
 * through its five lands, each band in its own colour: the floors climbed lit, the landings marked
 * with a fire and the Wardens' echoes with a skull, the traveller's best and the floor they may take
 * it up again from marked beside it. On the right, what matters before going in: the highest floor,
 * the next landing and the next echo (and whose), the Ladder's rules each with its sign, and who
 * climbs along. At the foot, the way in, from the start or from where the climb was left.
 */
public final class LadderScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	public static final int[] TIER_COLORS = {0x9FD86A, 0xE0485A, 0x9AD8F0, 0xF0C060, 0xE8C060};
	private static final String[] TIERS = {"overgrown", "crimson", "rime", "sun", "gilded"};
	private static final String[] ECHOES = {"ashen_shogun", "crimson_lich", "rimeback_colossus", "dune_sentinel", "gilded_warden"};
	private static final int ACCENT = 0x9C86C8;
	private static final int TEXT = 0xFFE8DCC0;
	private static final int SOFT = 0xFFC8BEB0;
	private static final int DIM = 0xFF8E8676;
	private static final int GOLD = 0xFFFFD27A;
	private final int best;
	private final int resume;
	private final int top;
	private final int party;
	private final BlockPos gate;
	private final long openedAt = Util.getMillis();

	public LadderScreen(CompoundTag data) {
		super(Component.translatable("ladder.afterworld.screen.title"));
		this.best = data.getIntOr("best", 0);
		this.resume = Math.max(1, data.getIntOr("resume", 1));
		this.top = Math.max(1, data.getIntOr("top", 50));
		this.party = data.getIntOr("party", 0);
		this.gate = BlockPos.of(data.getLongOr("gate", 0L));
	}

	@Override
	protected int prefWidth() {
		return 500;
	}

	@Override
	protected int prefHeight() {
		return 300;
	}

	@Override
	protected float widthShare() {
		return 0.82F;
	}

	@Override
	protected float heightShare() {
		return 0.86F;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 200.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 170), 0x07050C));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int pw = Math.min(prefWidth(), this.cw - 24);
		int ph = Math.min(prefHeight(), this.ch - 24);
		int px = (this.cw - pw) / 2;
		int py = (this.ch - ph) / 2;
		Frames.tablet(g, px, py, pw, ph);
		Component title = this.title.copy().withStyle(s -> s.withFont(DISPLAY));
		var pose = g.pose();
		pose.pushMatrix();
		float ts = Math.min(1.8F, (pw - 20) / (float) Math.max(1, this.font.width(title)));
		pose.translate(this.cw / 2.0F, py + 8);
		pose.scale(ts, ts);
		g.centeredText(this.font, title, 0, 0, TEXT);
		pose.popMatrix();
		g.centeredText(this.font, fit(Component.translatable("ladder.afterworld.screen.subtitle"), pw - 16), this.cw / 2, py + 8 + Math.round(10 * ts) + 3, 0xFF9C86C8);

		int top = py + 38;
		int bottom = py + ph - 40;
		int towerW = Math.max(110, pw / 4);
		ladder(g, px + 12, top, towerW, bottom - top);
		info(g, px + 12 + towerW + 14, top, px + pw - 12 - (px + 12 + towerW + 14), bottom - top);
		buttons(g, px, py, pw, ph, mx, my);
	}

	/** The Ladder: two rails, fifty rungs in five bands, its landings and echoes marked. */
	private void ladder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		long now = Util.getMillis();
		int railL = x + 6;
		int railR = x + 40;
		int band = h / 5;
		for (int t = 0; t < 5; t++) {
			int by = y + h - (t + 1) * band;
			// Each land's band, darkly in its colour, its name beside the rails.
			g.fill(x, by + 1, x + w, by + band, ARGB.color(t % 2 == 0 ? 46 : 32, TIER_COLORS[t]));
			Component name = Component.translatable("ladder.afterworld.tier_short." + TIERS[t]);
			g.text(this.font, fit(name, w - 70), railR + 26, by + band / 2 - 8, ARGB.opaque(TIER_COLORS[t]), false);
			Component floors = Component.literal((t * 10 + 1) + "–" + (t * 10 + 10));
			g.text(this.font, floors, railR + 26, by + band / 2 + 2, DIM, false);
		}
		g.fill(railL - 1, y, railL + 2, y + h, 0xFF5A4A6E);
		g.fill(railR - 2, y, railR + 1, y + h, 0xFF5A4A6E);
		for (int f = 1; f <= this.top; f++) {
			int ry = y + h - 2 - (int) ((f - 0.5F) / this.top * (h - 2));
			int tier = Math.min(4, (f - 1) / 10);
			boolean climbed = f <= this.best;
			int col = climbed ? ARGB.opaque(TIER_COLORS[tier]) : 0xFF3A3346;
			g.fill(railL + 2, ry, railR - 2, ry + 1, col);
			if (f % 10 == 0 || f % 10 == 5) {
				// Landings (a fire) and the Wardens' echoes (a skull), drawn small beside the rung.
				ItemStack sign = new ItemStack(f % 10 == 0 ? Items.WITHER_SKELETON_SKULL : Items.CAMPFIRE);
				smallItem(g, sign, railR + 3, ry - 5, 0.62F, climbed ? 1.0F : 0.45F);
			}
		}
		if (this.best > 0) {
			int ry = y + h - 2 - (int) ((this.best - 0.5F) / this.top * (h - 2));
			float pulse = 0.6F + 0.4F * Mth.sin(now / 260.0F);
			g.fill(railL + 2, ry - 1, railR - 2, ry + 2, ARGB.color((int) (255 * pulse), 0xFFF4DC));
		}
		if (this.resume > 1 && this.resume <= this.top) {
			int ry = y + h - 2 - (int) ((this.resume - 0.5F) / this.top * (h - 2));
			g.fill(x, ry - 2, x + 4, ry + 3, GOLD);
			g.fill(x + 4, ry - 1, x + 5, ry + 2, GOLD);
		}
	}

	/** An item drawn small (and dimmed when it is not reached yet). */
	private void smallItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale, float bright) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.item(stack, 0, 0);
		pose.popMatrix();
		if (bright < 1.0F) {
			g.fill(x, y, x + Math.round(16 * scale), y + Math.round(16 * scale), ARGB.color((int) (255 * (1 - bright) * 0.8F), 0x100C16));
		}
	}

	/** What to know before going in: three cards, the rules with their signs, and who climbs along. */
	private void info(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int from = this.resume > 1 && this.resume <= this.top ? this.resume : 1;
		int landing = from;
		while (landing % 10 != 5 && landing <= this.top) {
			landing++;
		}
		int echo = Math.min(this.top, ((from - 1) / 10 + 1) * 10);
		int cw = (w - 12) / 3;
		card(g, x, y, cw, Component.translatable("ladder.afterworld.screen.card.best"),
			this.best > 0 ? Component.literal(this.best + " / " + this.top) : Component.literal("—"), null, GOLD);
		card(g, x + cw + 6, y, cw, Component.translatable("ladder.afterworld.screen.card.landing"),
			landing <= this.top ? Component.translatable("ladder.afterworld.floor", landing) : Component.literal("—"), null, 0xFF7FE3D2);
		int tier = Math.min(4, (echo - 1) / 10);
		card(g, x + 2 * (cw + 6), y, cw, Component.translatable("ladder.afterworld.screen.card.echo"),
			Component.translatable("ladder.afterworld.floor", echo), Component.translatable("entity.afterworld." + ECHOES[tier]), 0xFFE0485A);

		int ry = y + 50;
		ItemStack[] signs = {new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.WITHER_SKELETON_SKULL), new ItemStack(Items.FEATHER)};
		String[] rules = {"waves", "landings", "echoes", "fall"};
		for (int i = 0; i < rules.length; i++) {
			int top = ry;
			g.item(signs[i], x, ry - 2);
			ry = wrap(g, Component.translatable("ladder.afterworld.screen.rule." + rules[i]), x + 22, ry + 2, w - 24, SOFT, 2, 10);
			ry = Math.max(ry, top + 18) + 4;
		}
		Component party = this.party <= 0 ? Component.translatable("ladder.afterworld.screen.alone")
			: this.party == 1 ? Component.translatable("ladder.afterworld.screen.party_one") : Component.translatable("ladder.afterworld.screen.party", this.party);
		int py = Math.min(ry + 2, y + h - 22);
		g.item(new ItemStack(Items.PLAYER_HEAD), x, py - 2);
		g.text(this.font, fit(party, w - 24), x + 22, py + 2, TEXT, false);
		g.text(this.font, fit(Component.translatable("ladder.afterworld.screen.gate_note"), w - 24), x + 22, py + 12, DIM, false);
	}

	private void card(GuiGraphicsExtractor g, int x, int y, int w, Component label, Component value, Component note, int color) {
		g.fill(x, y, x + w, y + 42, 0x66120E1A);
		g.fill(x, y, x + w, y + 1, ARGB.color(160, color));
		g.text(this.font, fit(label.copy(), w - 8), x + 5, y + 5, DIM, false);
		Component v = value.copy().withStyle(s -> s.withFont(DISPLAY));
		g.text(this.font, fit(v, w - 8), x + 5, y + 17, color, false);
		if (note != null) {
			g.text(this.font, fit(note.copy(), w - 8), x + 5, y + 30, SOFT, false);
		}
	}

	/** The way in. */
	private void buttons(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
		int bh = 24;
		int by = py + ph - bh - 10;
		boolean canResume = this.resume > 1 && this.resume <= this.top;
		int bw = canResume ? Math.min(190, (pw - 40) / 2) : Math.min(220, pw - 40);
		int bx1 = canResume ? this.cw / 2 - bw - 8 : (this.cw - bw) / 2;
		Component begin = Component.translatable("ladder.afterworld.screen.begin");
		boolean hot1 = mx >= bx1 && mx < bx1 + bw && my >= by && my < by + bh;
		Frames.button(g, this.font, bx1, by, bw, bh, fit(begin, bw - 8), hot1, true, ACCENT);
		this.hits.add(new Hit(bx1, by, bw, bh, () -> choose(AwPayloads.LadderAction.BEGIN)));
		if (canResume) {
			int bx2 = this.cw / 2 + 8;
			Component res = Component.translatable("ladder.afterworld.screen.resume", this.resume);
			boolean hot2 = mx >= bx2 && mx < bx2 + bw && my >= by && my < by + bh;
			Frames.button(g, this.font, bx2, by, bw, bh, fit(res, bw - 8), hot2, true, 0xFFD27A);
			this.hits.add(new Hit(bx2, by, bw, bh, () -> choose(AwPayloads.LadderAction.RESUME)));
		}
	}

	private void choose(int action) {
		sound(AwSounds.UI_SELECT, 0.7F, 0.7F);
		ClientPlayNetworking.send(new AwPayloads.LadderAction(action, this.gate));
		this.onClose();
	}

	/** For tests: presses Begin (0) or Resume (1). */
	public void chooseForTest(int action) {
		choose(action);
	}

	static String tierName(int tier) {
		return TIERS[Math.max(0, Math.min(4, tier))];
	}
}
