package dev.afterworld.client.hud;

import dev.afterworld.net.AwPayloads;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * Numbers that rise from what you strike: white for blows, gold for criticals, violet for magic.
 * Each blow you land also flashes a small mark round the crosshair (gold on a critical, red on a
 * kill) and gives the view the smallest kick, so a hit is felt as well as heard.
 */
public final class DamageNumbers {
	private record Entry(Vec3 at, String text, int color, float scale, long born, float drift) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final long LIFE = 1100;
	private static long markAt = -1;
	private static int markColor;

	private DamageNumbers() {
	}

	public static void add(AwPayloads.DamageNumber d) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(d.entity());
		if (e == null) {
			return;
		}
		boolean self = e == mc.player;
		if (!self && (d.kind() == AwPayloads.DamageNumber.NORMAL || d.kind() == AwPayloads.DamageNumber.CRIT || d.kind() == AwPayloads.DamageNumber.MAGIC)) {
			boolean kill = e instanceof net.minecraft.world.entity.LivingEntity le && (le.isDeadOrDying() || le.getHealth() - d.amount() <= 0.01F);
			boolean crit = d.kind() == AwPayloads.DamageNumber.CRIT;
			markAt = Util.getMillis();
			markColor = kill ? 0xFF5A4A : crit ? 0xFFD24A : 0xF4ECDC;
			dev.afterworld.client.fx.Shake.kick(crit || kill ? 0.9F : 0.3F + Math.min(0.4F, d.amount() / 40.0F), 2);
		}
		if (!dev.afterworld.AwConfig.get().damageNumbers) {
			return;
		}
		String text;
		int color;
		float scale = 1.0F;
		switch (d.kind()) {
			case AwPayloads.DamageNumber.CRIT -> {
				text = fmt(d.amount()) + "!";
				color = 0xFFD24A;
				scale = 1.35F;
			}
			case AwPayloads.DamageNumber.MAGIC -> {
				text = fmt(d.amount());
				color = 0xC08AFF;
			}
			case AwPayloads.DamageNumber.HEAL -> {
				text = "+" + fmt(d.amount());
				color = 0x7FE3B0;
			}
			case AwPayloads.DamageNumber.PARRY -> {
				text = Component.translatable("hud.afterworld.parry").getString();
				color = 0xFFE8A0;
				scale = 1.3F;
			}
			case AwPayloads.DamageNumber.DODGE -> {
				text = Component.translatable("hud.afterworld.dodge").getString();
				color = 0xB8E8FF;
				scale = 1.2F;
			}
			default -> {
				text = fmt(d.amount());
				color = 0xF4ECDC;
			}
		}
		Vec3 at = self ? e.position().add(e.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)).add(0, 1.9, 0)
			: e.position().add(0, e.getBbHeight() + 0.25, 0);
		float drift = (mc.level.getRandom().nextFloat() - 0.5F) * 0.6F;
		ENTRIES.add(new Entry(at, text, color, scale, Util.getMillis(), drift));
		while (ENTRIES.size() > 40) {
			ENTRIES.remove(0);
		}
	}

	private static String fmt(float v) {
		return v < 10 ? String.format("%.1f", v) : String.valueOf(Math.round(v));
	}

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
		if (mc.gui.hud.isHidden()) {
			return;
		}
		mark(g);
		if (ENTRIES.isEmpty() || !QuestHud.cameraValid) {
			return;
		}
		long now = Util.getMillis();
		Iterator<Entry> it = ENTRIES.iterator();
		while (it.hasNext()) {
			Entry e = it.next();
			long age = now - e.born;
			if (age > LIFE) {
				it.remove();
				continue;
			}
			float t = age / (float) LIFE;
			Vec3 at = e.at.add(e.drift * t, 0.9 * (1 - (1 - t) * (1 - t)), 0);
			Vec3 rel = at.subtract(QuestHud.cameraPos);
			Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
			QuestHud.VIEW_PROJ.transform(clip);
			if (clip.w <= 0.05F) {
				continue;
			}
			float sx = (clip.x / clip.w * 0.5F + 0.5F) * HudScale.width(g);
			float sy = (1 - (clip.y / clip.w * 0.5F + 0.5F)) * HudScale.height(g);
			double dist = rel.length();
			float pop = t < 0.12F ? 1.0F + (0.12F - t) * 4.0F : 1.0F;
			float scale = (float) (e.scale * pop * Math.max(0.6, Math.min(1.2, 8.0 / Math.max(1.0, dist))));
			int alpha = (int) (255 * (t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F));
			if (alpha <= 4) {
				continue;
			}
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(sx, sy);
			pose.scale(scale, scale);
			int w = mc.font.width(e.text);
			g.text(mc.font, Component.literal(e.text), -w / 2, -4, ARGB.color(alpha, e.color), true);
			pose.popMatrix();
		}
	}

	/** Four short ticks round the crosshair that spring outward and fade. */
	private static void mark(GuiGraphicsExtractor g) {
		if (markAt < 0) {
			return;
		}
		long age = Util.getMillis() - markAt;
		long life = markColor == 0xFF5A4A ? 320 : 200;
		if (age > life) {
			markAt = -1;
			return;
		}
		float t = age / (float) life;
		int alpha = (int) (230 * (1 - t * t));
		int color = ARGB.color(alpha, markColor);
		int cx = HudScale.width(g) / 2;
		int cy = HudScale.height(g) / 2;
		int r = 4 + Math.round(t * 3);
		for (int i = 0; i < 3; i++) {
			int o = r + i;
			g.fill(cx + o, cy + o, cx + o + 1, cy + o + 1, color);
			g.fill(cx - o - 1, cy + o, cx - o, cy + o + 1, color);
			g.fill(cx + o, cy - o - 1, cx + o + 1, cy - o, color);
			g.fill(cx - o - 1, cy - o - 1, cx - o, cy - o, color);
		}
	}

	public static void reset() {
		ENTRIES.clear();
		markAt = -1;
	}
}
