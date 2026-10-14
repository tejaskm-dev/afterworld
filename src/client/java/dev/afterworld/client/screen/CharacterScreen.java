package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.client.ClientState;
import dev.afterworld.combat.SetBonuses;
import dev.afterworld.edict.Edict;
import dev.afterworld.movement.Gifts;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.CharacterServer;
import dev.afterworld.player.Skills;
import dev.afterworld.registry.AwGear;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The Character screen: who you have become. Renown and Insight at the top; tabs for the
 * Aspects you've bound, the skill trees, your Edicts' ranks, and your relics, gifts and armour.
 * The tabs run down the left on a wide window and across the top on a narrow one.
 */
public final class CharacterScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private static final int BG = 0xF0121417;
	private static final int PANEL = 0xFF1A1E23;
	private static final int LINE = 0xFF2C333B;
	private static final int TEXT = 0xFFE8DCC0;
	private static final int DIM = 0xFF8E8676;
	private static final int TEAL = 0xFF7FE3D2;
	private static final int GOLD = 0xFFE8C873;
	private static final int RED = 0xFFC86B5A;
	private static final String[] TABS = {"aspects", "skills", "edicts", "relics"};
	private static CompoundTag data = new CompoundTag();
	private static int lastTab;

	/** Something with a tooltip, on the canvas. */
	private record Tip(int x, int y, int w, int h, List<Component> lines) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private final List<Tip> tips = new ArrayList<>();
	private final long openedAt = Util.getMillis();
	private int tab = lastTab;
	private int selectedAspect = -1;

	private CharacterScreen() {
		super(Component.translatable("screen.afterworld.character"));
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (ClientPlayNetworking.canSend(AwPayloads.CharacterAction.TYPE)) {
			ClientPlayNetworking.send(new AwPayloads.CharacterAction(AwPayloads.CharacterAction.OPEN, 0, ""));
		}
		mc.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_OPEN.value(), 1.0F, 0.6F));
		mc.gui.setScreen(new CharacterScreen());
	}

	public static void apply(AwPayloads.CharacterView view) {
		data = view.data();
	}

	/** What the server last told us about the traveller (the skill tree reads it too). */
	static CompoundTag data() {
		return data;
	}

	private void openTree() {
		sound(AwSounds.UI_OPEN, 1.1F, 0.5F);
		this.minecraft.gui.setScreen(new SkillTreeScreen(this));
	}

	@Override
	protected int prefWidth() {
		return 384;
	}

	@Override
	protected int prefHeight() {
		return 240;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 200.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 160), 0x05080B));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		this.tips.clear();
		int w = Math.min(prefWidth(), this.cw - 8);
		int h = Math.min(prefHeight(), this.ch - 8);
		int x = (this.cw - w) / 2;
		int y = (this.ch - h) / 2;
		Frames.panel(g, x, y, w, h, TEAL & 0xFFFFFF);
		this.header(g, x, y, w);
		boolean side = w >= 350;
		int cx;
		int cy;
		for (int i = 0; i < TABS.length; i++) {
			int tx = side ? x + 6 : x + 6 + i * ((w - 12 + 2) / TABS.length);
			int ty = side ? y + 50 + i * 24 : y + 49;
			int tw = side ? 84 : (w - 12 + 2) / TABS.length - 2;
			int th = side ? 20 : 15;
			boolean on = i == this.tab;
			boolean hot = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
			Frames.tab(g, tx, ty, tw, th, on || hot);
			if (side) {
				g.text(this.font, fit(Component.translatable("character.afterworld.tab." + TABS[i]), tw - 12), tx + 8, ty + (th - 8) / 2, on ? Frames.INK : Frames.INK_DIM, false);
			} else {
				Component label = fit(Component.translatable("character.afterworld.tab." + TABS[i]), tw - 8);
				g.text(this.font, label, tx + (tw - this.font.width(label)) / 2, ty + (th - 8) / 2, on ? Frames.INK : Frames.INK_DIM, false);
			}
			int idx = i;
			this.hits.add(new Hit(tx, ty, tw, th, () -> {
				if (this.tab != idx) {
					this.tab = idx;
					lastTab = idx;
					sound(AwSounds.UI_TAB, 1.0F, 0.5F);
				}
				if (idx == 1) {
					openTree();
				}
			}));
		}
		if (side) {
			cx = x + 96;
			cy = y + 50;
		} else {
			cx = x + 6;
			cy = y + 67;
		}
		int pw = x + w - 6 - cx;
		int ph = y + h - 6 - cy;
		Frames.inset(g, cx, cy, pw, ph);
		switch (this.tab) {
			case 0 -> this.aspects(g, cx, cy, pw, ph, mx, my);
			case 1 -> this.skills(g, cx, cy, pw, ph, mx, my);
			case 2 -> this.edicts(g, cx, cy, pw, ph, mx, my);
			default -> this.relics(g, cx, cy, pw, ph, mx, my);
		}
		for (Tip t : this.tips) {
			if (t.contains(mx, my)) {
				tip(g, t.lines, mx, my);
				break;
			}
		}
	}

	// ------------------------------------------------------------------ header

	private void header(GuiGraphicsExtractor g, int x, int y, int w) {
		Minecraft mc = Minecraft.getInstance();
		// The right column first (Insight, Marks, discipline); the name and Renown get the rest.
		Component insight = Component.translatable("character.afterworld.insight", data.getIntOr("insight", 0));
		Component marks = Component.translatable("character.afterworld.marks", String.format("%,d", data.getLongOr("marks", 0)));
		Component disc = Component.translatable("discipline.afterworld." + data.getStringOr("discipline", "none"));
		int rightW = Math.min(w / 2 - 10, Math.max(this.font.width(insight), Math.max(this.font.width(marks), this.font.width(disc))));
		int rx = x + w - 10;
		Component ins = fit(insight, rightW);
		Component mk = fit(marks, rightW);
		Component ds = fit(disc, rightW);
		g.text(this.font, ins, rx - this.font.width(ins), y + 10, TEAL, false);
		g.text(this.font, mk, rx - this.font.width(mk), y + 22, 0xFFFFD27A, false);
		g.text(this.font, ds, rx - this.font.width(ds), y + 34, DIM, false);
		int room = w - 20 - rightW - 10;
		Component name = (mc.player == null ? Component.empty() : mc.player.getDisplayName()).copy().withStyle(s -> s.withFont(DISPLAY));
		float ns = Mth.clamp(room / (float) Math.max(1, this.font.width(name)), 1.0F, 1.4F);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 10, y + 8);
		pose.scale(ns, ns);
		g.text(this.font, fit(name, (int) (room / ns)), 0, 0, TEXT, false);
		pose.popMatrix();
		int level = data.getIntOr("level", 1);
		float prog = data.getFloatOr("progress", 0);
		Component renown = fit(Component.translatable("character.afterworld.renown", level), room);
		g.text(this.font, renown, x + 10, y + 26, GOLD, false);
		int bx = x + 14 + this.font.width(renown);
		int bw = Math.min(120, x + 10 + room - bx);
		if (bw >= 24) {
			g.fill(bx, y + 28, bx + bw, y + 32, 0xFF262B31);
			g.fill(bx, y + 28, bx + (int) (bw * prog), y + 32, GOLD);
		}
		g.fill(x + 6, y + 44, x + w - 6, y + 45, LINE);
	}

	// ------------------------------------------------------------------ aspects

	private void aspects(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		int owned = ClientState.aspectsOwned;
		int cardW = (w - 10) / 4;
		int cardH = 44;
		int rows = (Aspect.VALUES.length + 3) / 4;
		for (int i = 0; i < Aspect.VALUES.length; i++) {
			Aspect asp = Aspect.VALUES[i];
			boolean has = (owned & (1 << i)) != 0;
			boolean equipped = ClientState.aspect == i;
			int cx = x + 4 + (i % 4) * (cardW + 1);
			int cy = y + 4 + (i / 4) * (cardH + 2);
			boolean hot = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
			boolean sel = this.selectedAspect == i;
			g.fill(cx, cy, cx + cardW, cy + cardH, sel ? 0xFF2A333C : hot ? 0xFF222A31 : 0xFF181C21);
			g.fill(cx, cy + cardH - 2, cx + cardW, cy + cardH, has ? ARGB.opaque(asp.color) : LINE);
			if (equipped) {
				g.fill(cx, cy, cx + cardW, cy + 1, ARGB.opaque(asp.color));
			}
			if (has) {
				g.item(new ItemStack(AwGear.HEARTS.get(asp)), cx + cardW / 2 - 8, cy + 4);
			} else {
				g.centeredText(this.font, Component.literal("?"), cx + cardW / 2, cy + 8, DIM);
			}
			Component nm = has ? Component.translatable("aspect.afterworld." + asp.id) : Component.translatable("character.afterworld.unknown");
			wrapCentred(g, nm, cx + cardW / 2, cy + 23, cardW - 4, has ? (equipped ? ARGB.opaque(asp.color) : TEXT) : DIM, 2, 9);
			int idx = i;
			this.hits.add(new Hit(cx, cy, cardW, cardH, () -> {
				this.selectedAspect = idx;
				sound(AwSounds.UI_SELECT, 1.2F, 0.4F);
			}));
		}
		int dy = y + 4 + rows * (cardH + 2) + 4;
		g.fill(x + 4, dy - 2, x + w - 4, dy - 1, LINE);
		Aspect sel = Aspect.byOrdinal(this.selectedAspect >= 0 ? this.selectedAspect : ClientState.aspect);
		if (sel == null) {
			wrap(g, Component.translatable("character.afterworld.aspects_intro"), x + 8, dy + 2, w - 16, DIM, Math.max(1, (y + h - 4 - dy) / 10), 10);
			return;
		}
		boolean has = (owned & (1 << sel.ordinal())) != 0;
		g.text(this.font, fit(has ? Component.translatable("aspect.afterworld." + sel.id) : Component.translatable("character.afterworld.unknown"), w - 16),
			x + 8, dy + 2, ARGB.opaque(sel.color), false);
		int buttonY = y + h - 20;
		int descLines = Math.max(1, ((has ? buttonY - 2 : y + h - 12) - (dy + 14)) / 10);
		int ty = wrap(g, has ? Component.translatable("aspect.afterworld." + sel.id + ".desc") : Component.translatable("aspect.afterworld." + sel.id + ".hint"),
			x + 8, dy + 14, w - 16, has ? TEXT : DIM, descLines, 10);
		if (has) {
			boolean equipped = ClientState.aspect == sel.ordinal();
			Component label = equipped ? Component.translatable("character.afterworld.equipped") : Component.translatable("character.afterworld.equip");
			int bw = Math.min(w / 2, this.font.width(label) + 12);
			int bx = x + w - 8 - bw;
			this.button(g, bx, buttonY, bw, label, !equipped, mx, my, () -> this.send(AwPayloads.CharacterAction.EQUIP_ASPECT, sel.ordinal(), ""));
			if (!equipped) {
				Component cost = Component.translatable("character.afterworld.equip_cost");
				Component shown = fit(cost, bx - 6 - (x + 8));
				g.text(this.font, shown, x + 8, buttonY + 4, DIM, false);
				if (shown != cost) {
					this.tips.add(new Tip(x + 8, buttonY, bx - 6 - (x + 8), 15, List.of(cost)));
				}
			}
		}
		// The Resonance bar.
		int by = y + h - 8;
		float frac = Mth.clamp(ClientState.resonance / 100.0F, 0, 1);
		Aspect eq = Aspect.byOrdinal(ClientState.aspect);
		// (Not under the line about what changing Aspects costs.)
		if (eq != null && ty < by - 14 && !(has && ClientState.aspect != sel.ordinal())) {
			g.fill(x + 8, by, x + w / 2, by + 3, 0xFF262B31);
			g.fill(x + 8, by, x + 8 + (int) ((w / 2 - 8) * frac), by + 3, ARGB.opaque(eq.color));
		}
	}

	// ------------------------------------------------------------------ skills

	private void skills(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		// The tree has a screen of its own (the Skills tab opens it); here, what has grown, and the way back to it.
		ListTag ownedTag = data.getListOrEmpty("skills");
		int insight = data.getIntOr("insight", 0);
		int total = Skills.TREE.size() - 1;
		Component head = Component.translatable("skilltree.afterworld.learned_count", ownedTag.size(), total);
		g.text(this.font, head, x + 8, y + 8, TEXT, false);
		Component ins = Component.translatable("skilltree.afterworld.insight", insight);
		g.text(this.font, ins, x + w - 8 - this.font.width(ins), y + 8, insight > 0 ? TEAL : DIM, false);
		int ly = y + 24;
		for (int i = 0; i < ownedTag.size() && ly < y + h - 40; i++) {
			Skills.Node n = Skills.byId(ownedTag.getStringOr(i, ""));
			if (n == null || n.kind() == Skills.Kind.MINOR) {
				continue;
			}
			int col = switch (n.branch()) {
				case "blade" -> 0xFFE07A5A;
				case "wayfarer" -> 0xFF7FE3B0;
				case "arcanum" -> 0xFFB08AFF;
				default -> GOLD;
			};
			g.fill(x + 10, ly + 2, x + 14, ly + 6, col);
			g.text(this.font, fit(Component.translatable("skill.afterworld." + n.id()), w - 28), x + 18, ly, TEXT, false);
			ly += 11;
		}
		Component open = Component.translatable("skilltree.afterworld.title");
		int bw = Math.min(w - 20, this.font.width(open) + 40);
		int bx = x + (w - bw) / 2;
		int by = y + h - 30;
		boolean hot = mx >= bx && mx < bx + bw && my >= by && my < by + 20;
		Frames.button(g, this.font, bx, by, bw, 20, open, hot, true, TEAL & 0xFFFFFF);
		this.hits.add(new Hit(bx, by, bw, 20, this::openTree));
	}

	// ------------------------------------------------------------------ edicts

	private void edicts(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		CompoundTag ranks = data.getCompoundOrEmpty("ranks");
		int shards = data.getIntOr("shards", 0);
		long marks = data.getLongOr("marks", 0);
		int top = wrap(g, Component.translatable("character.afterworld.edict_intro", shards), x + 8, y + 6, w - 16, DIM, 2, 10) + 4;
		int row = 0;
		Component raise = Component.translatable("character.afterworld.raise");
		int bw = this.font.width(raise) + 10;
		for (Edict e : Edict.VALUES) {
			if (!ranks.contains(e.id)) {
				continue;
			}
			int rank = ranks.getIntOr(e.id, 1);
			int ry = top + row * 19;
			if (ry > y + h - 18) {
				break;
			}
			row++;
			g.fill(x + 6, ry, x + w - 6, ry + 17, 0xFF181C21);
			g.fill(x + 6, ry, x + 8, ry + 17, ARGB.opaque(e.color));
			// From the right: the button (or "Mastered"), the cost if there's room for it, the pips, then the name.
			int bx = x + w - 10 - bw;
			int costRight = bx - 6;
			Component cost = null;
			boolean can = false;
			if (rank < 3) {
				int needS = CharacterServer.RANK_SHARDS[rank + 1];
				int needM = CharacterServer.RANK_MARKS[rank + 1];
				can = shards >= needS && marks >= needM;
				cost = Component.translatable("character.afterworld.rank_cost", needS, needM);
				this.button(g, bx, ry + 1, bw, raise, can, mx, my, () -> this.send(AwPayloads.CharacterAction.RANK_EDICT, 0, e.id));
			} else {
				cost = Component.translatable("character.afterworld.rank_max");
				costRight = x + w - 10;
			}
			int costW = this.font.width(cost);
			boolean costFits = costRight - costW - 8 - 27 - 70 >= x + 13;
			int pipsX = costFits ? costRight - costW - 8 - 27 : costRight - 27;
			if (costFits) {
				g.text(this.font, cost, costRight - costW, ry + 5, rank >= 3 ? GOLD : can ? DIM : RED, false);
			}
			for (int r = 1; r <= 3; r++) {
				int px = pipsX + (r - 1) * 9;
				g.fill(px, ry + 5, px + 6, ry + 11, r <= rank ? ARGB.opaque(e.color) : 0xFF2C333B);
			}
			g.text(this.font, fit(e.displayName(), pipsX - 6 - (x + 13)), x + 13, ry + 5, TEXT, false);
			List<Component> tip = new ArrayList<>(List.of(e.displayName().copy().withColor(e.color), Component.translatable("character.afterworld.rank_effect", rank).withColor(TEXT),
				Component.translatable("edict.afterworld." + e.id + ".capstone").withColor(rank >= 3 ? GOLD : DIM)));
			if (!costFits) {
				tip.add(cost.copy().withColor(rank >= 3 ? GOLD : can ? TEAL : RED));
			}
			this.tips.add(new Tip(x + 6, ry, pipsX + 27 - (x + 6), 17, tip));
		}
		if (row == 0) {
			wrap(g, Component.translatable("character.afterworld.no_edicts"), x + 8, top + 4, w - 16, DIM, 4, 10);
		}
	}

	// ------------------------------------------------------------------ relics, gifts and armour

	private void relics(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		g.text(this.font, fit(Component.translatable("character.afterworld.relics"), w - 16), x + 8, y + 6, GOLD, false);
		ListTag relics = data.getListOrEmpty("relics");
		for (int i = 0; i < 3; i++) {
			int sx = x + 8 + i * 30;
			int sy = y + 18;
			g.fill(sx - 1, sy - 1, sx + 25, sy + 25, LINE);
			g.fill(sx, sy, sx + 24, sy + 24, 0xFF14181C);
			ItemStack st = i < relics.size() ? decode(relics.getCompoundOrEmpty(i)) : ItemStack.EMPTY;
			if (!st.isEmpty()) {
				g.item(st, sx + 4, sy + 4);
				int idx = i;
				List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(mc, st));
				tip.add(Component.translatable("character.afterworld.relic_remove").withColor(DIM));
				this.hits.add(new Hit(sx, sy, 24, 24, () -> this.send(AwPayloads.CharacterAction.UNEQUIP_RELIC, idx, "")));
				this.tips.add(new Tip(sx, sy, 24, 24, tip));
			}
		}
		wrap(g, Component.translatable("character.afterworld.relics_how"), x + 104, y + 18, w - 112, DIM, 3, 9);
		// Gifts.
		int gy = y + 50;
		g.fill(x + 6, gy - 4, x + w - 6, gy - 3, LINE);
		g.text(this.font, fit(Component.translatable("character.afterworld.gifts"), w - 16), x + 8, gy, GOLD, false);
		int gifts = data.getIntOr("gifts", ClientState.gifts);
		int colW = (w - 16) / 2;
		for (Gifts.Gift gift : Gifts.Gift.values()) {
			int i = gift.ordinal();
			int gx = x + 8 + (i % 2) * colW;
			int gyy = gy + 12 + (i / 2) * 22;
			boolean has = (gifts & gift.bit()) != 0;
			g.fill(gx, gyy, gx + 3, gyy + 20, has ? TEAL : LINE);
			g.text(this.font, fit(Component.translatable("gift.afterworld." + gift.id), colW - 12), gx + 7, gyy + 1, has ? TEXT : DIM, false);
			Component sub = has ? Component.translatable("gift.afterworld." + gift.id + ".short") : Component.translatable("character.afterworld.gift_unknown");
			Component shown = fit(sub, colW - 12);
			g.text(this.font, shown, gx + 7, gyy + 11, DIM, false);
			if (shown != sub) {
				this.tips.add(new Tip(gx, gyy, colW - 4, 20, List.of(sub)));
			}
		}
		// Armour sets.
		int ay = gy + 12 + ((Gifts.Gift.values().length + 1) / 2) * 22 + 6;
		if (ay > y + h - 14) {
			return;
		}
		g.fill(x + 6, ay - 4, x + w - 6, ay - 3, LINE);
		g.text(this.font, fit(Component.translatable("character.afterworld.armour"), w - 16), x + 8, ay, GOLD, false);
		if (mc.player == null) {
			return;
		}
		int line = 0;
		for (AwGear.Set set : AwGear.Set.values()) {
			int n = SetBonuses.count(mc.player, set);
			if (n == 0) {
				continue;
			}
			int ly = ay + 12 + line * 20;
			if (ly > y + h - 18) {
				break;
			}
			line++;
			g.text(this.font, fit(Component.translatable("set.afterworld.label", Component.translatable("set.afterworld." + set.id), n), w - 16), x + 8, ly, n == 4 ? TEAL : TEXT, false);
			Component bonus = Component.translatable("set.afterworld." + set.id + ".bonus");
			Component shown = fit(bonus, w - 16);
			g.text(this.font, shown, x + 8, ly + 9, n == 4 ? TEXT : DIM, false);
			if (shown != bonus) {
				this.tips.add(new Tip(x + 8, ly, w - 16, 18, List.of(bonus)));
			}
		}
		if (line == 0) {
			g.text(this.font, fit(Component.translatable("character.afterworld.no_set"), w - 16), x + 8, ay + 12, DIM, false);
		}
	}

	private static ItemStack decode(CompoundTag t) {
		Minecraft mc = Minecraft.getInstance();
		if (t.isEmpty() || mc.level == null) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), t).result().orElse(ItemStack.EMPTY);
	}

	// ------------------------------------------------------------------ bits

	private void button(GuiGraphicsExtractor g, int x, int y, int w, Component label, boolean enabled, int mx, int my, Runnable action) {
		boolean hot = enabled && mx >= x && mx < x + w && my >= y && my < y + 15;
		Frames.button(g, this.font, x, y, w, 15, fit(label, w - 6), hot, enabled, TEAL & 0xFFFFFF);
		if (enabled) {
			this.hits.add(new Hit(x, y, w, 15, action));
		}
	}

	private void send(int action, int a, String s) {
		ClientPlayNetworking.send(new AwPayloads.CharacterAction(action, a, s));
		sound(AwSounds.UI_SELECT, 1.0F, 0.7F);
	}

	/** For tests: open a tab. */
	public void tabForTest(int t) {
		this.tab = t;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (dev.afterworld.client.AfterworldClient.CHARACTER.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
