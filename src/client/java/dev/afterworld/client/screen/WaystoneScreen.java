package dev.afterworld.client.screen;

import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/** The road between waystones: every stone you have touched, nearest first. Click one to go. */
public final class WaystoneScreen extends AwScreen {
	private static final FontDescription DISPLAY = new FontDescription.Resource(Afterworld.id("display"));
	private final AwPayloads.WaystoneList list;
	private final List<AwPayloads.WaystoneList.Entry> rows = new ArrayList<>();
	private final long openedAt = Util.getMillis();
	private double scroll;

	public WaystoneScreen(AwPayloads.WaystoneList list) {
		super(Component.translatable("screen.afterworld.waystones"));
		this.list = list;
	}

	@Override
	protected int prefWidth() {
		return 300;
	}

	@Override
	protected int prefHeight() {
		return 52 + Math.max(3, Math.min(this.list.entries().size(), 8)) * 22;
	}

	@Override
	protected void init() {
		super.init();
		this.rows.clear();
		this.rows.addAll(this.list.entries());
		var p = this.minecraft.player;
		if (p != null) {
			this.rows.sort(Comparator.comparingDouble(e -> e.camp() ? -1 : Math.hypot(e.x() - p.getX(), e.z() - p.getZ())));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float t = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 200.0F);
		g.fill(0, 0, this.width, this.height, ARGB.color((int) (t * 150), 0x05080B));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
		int pw = Math.min(300, this.cw - 20);
		int px = (this.cw - pw) / 2;
		int rowH = 22;
		int visible = Math.max(2, Math.min(Math.max(3, this.rows.size()), (this.ch - 76) / rowH));
		int ph = 52 + visible * rowH;
		int py = (this.ch - ph) / 2;
		Frames.panel(g, px, py, pw, ph, 0x7FE3D2);
		Component title = this.title.copy().withStyle(s -> s.withFont(DISPLAY));
		var pose = g.pose();
		pose.pushMatrix();
		float ts = Math.min(1.5F, (pw - 16) / (float) Math.max(1, this.font.width(title)));
		pose.translate(this.cw / 2.0F, py + 8);
		pose.scale(ts, ts);
		g.centeredText(this.font, title, 0, 0, 0xFFE8DCC0);
		pose.popMatrix();
		int waitS = (int) Math.max(0, (this.list.waitTicks() - (Util.getMillis() - this.openedAt) / 50 + 19) / 20);
		Component sub = this.list.fare() > 0 ? Component.translatable("screen.afterworld.waystones.charter", this.list.fare())
			: waitS > 0 ? Component.translatable("screen.afterworld.waystones.wait", waitS) : Component.translatable("screen.afterworld.waystones.hint");
		g.centeredText(this.font, fit(sub, pw - 12), this.cw / 2, py + 26, waitS > 0 ? 0xFFC86B5A : 0xFF9A9384);
		int maxScroll = Math.max(0, this.rows.size() - visible);
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll);
		int first = (int) this.scroll;
		var p = this.minecraft.player;
		if (this.rows.isEmpty()) {
			wrapCentred(g, Component.translatable("screen.afterworld.waystones.none"), this.cw / 2, py + 48, pw - 20, 0xFF9A9384, 3, 10);
		}
		for (int i = 0; i < visible && first + i < this.rows.size(); i++) {
			AwPayloads.WaystoneList.Entry e = this.rows.get(first + i);
			int ry = py + 42 + i * rowH;
			boolean here = e.id() == this.list.from();
			boolean hot = !here && mx >= px + 6 && mx < px + pw - 6 && my >= ry && my < ry + rowH - 2;
			if (!here) {
				this.hits.add(new Hit(px + 6, ry, pw - 12, rowH - 2, () -> travel(e)));
			}
			Frames.inset(g, px + 6, ry, pw - 12, rowH - 2);
			if (hot) {
				g.fill(px + 8, ry + 2, px + pw - 8, ry + rowH - 4, 0x403A8A80);
			}
			g.fill(px + 8, ry + 3, px + 10, ry + rowH - 5, here ? 0xFF6A6A6A : e.camp() ? 0xFFE8A33D : 0xFF7FE3D2);
			g.text(this.font, fit(Component.literal(e.name()), pw - 26), px + 14, ry + 3, here ? 0xFF8A8478 : hot ? 0xFFFFF4DC : 0xFFE8DCC0, false);
			String info;
			if (here) {
				info = Component.translatable("screen.afterworld.waystones.here").getString();
			} else if (p != null && p.level().dimension().identifier().toString().equals(e.dim())) {
				double d = Math.hypot(e.x() - p.getX(), e.z() - p.getZ());
				info = (d >= 1000 ? String.format("%.1f km", d / 1000) : (int) d + " m") + "  " + compass(e.x() - p.getX(), e.z() - p.getZ());
			} else {
				info = Component.translatable("screen.afterworld.waystones.elsewhere").getString();
			}
			g.text(this.font, fit(Component.literal(info), pw - 26), px + 14, ry + 12, 0xFF8E8676, false);
		}
		if (this.rows.size() > visible) {
			int top = py + 42;
			int h = visible * rowH - 2;
			int bh = Math.max(8, h * visible / this.rows.size());
			int by = top + (int) ((h - bh) * this.scroll / Math.max(1, maxScroll));
			g.fill(px + pw - 4, top, px + pw - 2, top + h, 0x30FFFFFF);
			g.fill(px + pw - 4, by, px + pw - 2, by + bh, 0xFF7FE3D2);
		}
	}

	private void travel(AwPayloads.WaystoneList.Entry e) {
		sound(AwSounds.UI_SELECT, 0.8F, 0.6F);
		ClientPlayNetworking.send(new AwPayloads.WaystoneTravel(e.id()));
		this.onClose();
	}

	private static String compass(double dx, double dz) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		double ang = Math.toDegrees(Math.atan2(-dx, dz));
		return names[Math.floorMod((int) Math.round(ang / 45.0), 8)];
	}

	@Override
	protected boolean scrolled(double x, double y, double dy) {
		this.scroll -= dy;
		return true;
	}

	/** For tests. */
	public int rowsForTest() {
		return this.rows.size();
	}
}
