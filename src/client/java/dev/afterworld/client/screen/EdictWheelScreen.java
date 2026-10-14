package dev.afterworld.client.screen;

import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.ClientState;
import dev.afterworld.client.hud.EdictHud;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The Edict wheel: hold the wheel key and your equipped Edicts (and your signature) fan out
 * around the cursor. Point at one and let go, or click, to cast it.
 */
public final class EdictWheelScreen extends AwScreen {
	private record Entry(int slot, Identifier icon, Component name, Component rule, int color, int remain, int total, boolean silenced, Component key) {
	}

	private final long openedAt = Util.getMillis();
	private final List<Entry> entries = new ArrayList<>();
	private int hovered = -1;
	private int lastHovered = -1;

	public EdictWheelScreen() {
		super(Component.translatable("screen.afterworld.wheel"));
	}

	@Override
	protected int prefWidth() {
		return 300;
	}

	@Override
	protected int prefHeight() {
		return 300;
	}

	@Override
	protected void init() {
		super.init();
		this.entries.clear();
		for (int i = 0; i < ClientState.slotCount; i++) {
			int e = ClientState.slots[i];
			if (e < 0) {
				continue;
			}
			Edict edict = Edict.byOrdinal(e);
			boolean silenced = ((ClientState.flags >> AwPayloads.Hud.SUPPRESS_SHIFT) & (1 << i)) != 0;
			this.entries.add(new Entry(i, EdictHud.edictIcon(e), edict.displayName(), Component.translatable("edict.afterworld." + edict.id + ".rule"),
				edict.color, ClientState.cdRemain[i], ClientState.cdTotal[i], silenced, AfterworldClient.SLOT_KEYS[i].getTranslatedKeyMessage()));
		}
		Discipline d = Discipline.byOrdinal(ClientState.discipline);
		if (d != Discipline.NONE) {
			this.entries.add(new Entry(AwPayloads.Cast.SIGNATURE, EdictHud.disciplineIcon(d.ordinal()), d.signatureName(),
				Component.translatable("discipline.afterworld." + d.id + ".signature.desc"), d.color, ClientState.sigRemain, ClientState.sigTotal, false,
				AfterworldClient.SIGNATURE.getTranslatedKeyMessage()));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 160.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 110), 0x05080B));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int cx = this.cw / 2;
		int cy = this.ch / 2;
		float open = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 160.0F);
		open = 1 - (1 - open) * (1 - open);
		int ringR = (int) (Math.min(this.cw, this.ch) * 0.26F);
		int n = this.entries.size();
		if (n == 0) {
			wrapCentred(g, Component.translatable("wheel.afterworld.empty"), cx, cy - 4, Math.min(this.cw - 20, 240), 0xFFE8DCC0, 3, 10);
			return;
		}
		// Choose by direction from the centre.
		double dx = mx - cx;
		double dy = my - cy;
		this.hovered = -1;
		if (dx * dx + dy * dy > 14 * 14) {
			double ang = Math.atan2(dy, dx);
			double best = Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				double ea = -Math.PI / 2 + i * Math.PI * 2 / n;
				double diff = Math.abs(Math.atan2(Math.sin(ang - ea), Math.cos(ang - ea)));
				if (diff < best) {
					best = diff;
					this.hovered = i;
				}
			}
		}
		if (this.hovered != this.lastHovered && this.hovered >= 0) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_SELECT.value(), 1.3F + this.hovered * 0.08F, 0.25F));
		}
		this.lastHovered = this.hovered;
		// A dark disc behind the wheel, so chat and the world don't show through the text.
		int back = (int) ((ringR + 30) * open);
		for (int yy = -back; yy <= back; yy++) {
			int half = (int) Math.sqrt((double) back * back - (double) yy * yy);
			float edge = 1.0F - Math.abs(yy) / (float) Math.max(1, back);
			g.fill(cx - half, cy + yy, cx + half + 1, cy + yy + 1, ARGB.color((int) (open * (195 + 45 * edge)), 0x07090C));
		}
		// A dotted ring.
		for (int i = 0; i < 90; i++) {
			double ang = i * Math.PI * 2 / 90;
			int x = cx + (int) Math.round(Math.cos(ang) * ringR * open);
			int y = cy + (int) Math.round(Math.sin(ang) * ringR * open);
			g.fill(x, y, x + 1, y + 1, ARGB.color((int) (open * 90), 0xE8A33D));
		}
		for (int i = 0; i < n; i++) {
			Entry e = this.entries.get(i);
			double ang = -Math.PI / 2 + i * Math.PI * 2 / n;
			int ex = cx + (int) Math.round(Math.cos(ang) * ringR * open);
			int ey = cy + (int) Math.round(Math.sin(ang) * ringR * open);
			boolean hot = i == this.hovered;
			int r = hot ? 20 : 16;
			boolean ready = e.remain <= 0 && !e.silenced;
			medallion(g, ex, ey, r, hot ? e.color : ARGB.scaleRGB(e.color, 0.7F), ready);
			if (!ready && e.total > 0) {
				// Cooldown: a dark veil that drains from the top as it recovers.
				float frac = Mth.clamp(e.remain / (float) e.total, 0, 1);
				int veil = (int) (frac * r * 2);
				for (int yy = -r; yy < -r + veil; yy++) {
					int half = (int) Math.sqrt(Math.max(0, (r - 2) * (r - 2) - yy * yy));
					g.fill(ex - half, ey + yy, ex + half, ey + yy + 1, 0x99000000);
				}
			}
			var pose = g.pose();
			pose.pushMatrix();
			float s = hot ? 1.5F : 1.2F;
			pose.translate(ex - 8 * s, ey - 8 * s);
			pose.scale(s, s);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, e.icon, 0, 0, 16, 16);
			pose.popMatrix();
			if (e.silenced) {
				for (int k = -6; k <= 6; k++) {
					g.fill(ex + k, ey + k, ex + k + 1, ey + k + 1, 0xFFA8432E);
					g.fill(ex + k, ey - k, ex + k + 1, ey - k + 1, 0xFFA8432E);
				}
			}
			g.centeredText(this.font, e.key, ex, ey + r + 3, hot ? 0xFFFFD27A : 0xFFB8AE98);
		}
		// The centre: what you are pointing at.
		if (this.hovered >= 0) {
			Entry e = this.entries.get(this.hovered);
			int inner = ringR * 2 - 36;
			g.centeredText(this.font, fit(e.name, inner), cx, cy - 18, ARGB.opaque(e.color));
			Component state = e.silenced ? Component.translatable("wheel.afterworld.silenced")
				: e.remain > 0 ? Component.translatable("wheel.afterworld.cooldown", (e.remain + 19) / 20) : Component.translatable("wheel.afterworld.ready");
			g.centeredText(this.font, fit(state, inner), cx, cy - 7, e.remain > 0 || e.silenced ? 0xFF9A9384 : 0xFFE8A33D);
			int wrapW = Math.max(80, ringR * 2 - 60);
			int y = cy + 6;
			for (FormattedCharSequence line : this.font.split(e.rule, wrapW)) {
				if (y > cy + ringR - 26) {
					break;
				}
				g.centeredText(this.font, line, cx, y, 0xFFD8CCB0);
				y += 10;
			}
		} else {
			wrapCentred(g, Component.translatable("wheel.afterworld.hint"), cx, cy - 4, ringR * 2 - 30, 0xFF9A9384, 2, 10);
		}
	}

	private static void medallion(GuiGraphicsExtractor g, int cx, int cy, int r, int rim, boolean ready) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt(r * r - dy * dy);
			int inner = dy * dy <= (r - 2) * (r - 2) ? (int) Math.sqrt((r - 2) * (r - 2) - dy * dy) : 0;
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, ARGB.opaque(rim));
			if (inner > 0) {
				g.fill(cx - inner, cy + dy, cx + inner + 1, cy + dy + 1, ready ? 0xF01A1612 : 0xF0100E0C);
			}
		}
	}

	private void cast() {
		if (this.hovered >= 0 && this.hovered < this.entries.size()) {
			Entry e = this.entries.get(this.hovered);
			ClientPlayNetworking.send(new AwPayloads.Cast(e.slot));
		}
		this.onClose();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (AfterworldClient.WHEEL.matches(event)) {
			cast();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		cast();
		return true;
	}

	/** For tests: point the wheel at an entry without a mouse. */
	public int entryCount() {
		return this.entries.size();
	}
}
