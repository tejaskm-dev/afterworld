package dev.afterworld.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Character portraits for conversations: the speaker's own Minecraft avatar, drawn live from
 * their model (skin, clothes, whatever they're holding), framed from the chest up and turned
 * toward the words. While they talk, the head moves with the speech.
 */
public final class Portraits {
	/** Room above the crown of the head, as a share of the body's height. */
	private static final float HEADROOM = 0.08F;

	private Portraits() {
	}

	/**
	 * Draws {@code e} as a bust in the box. {@code turn} faces it left (negative) or right
	 * (positive); {@code talk} (0..1) is how much it is speaking right now; {@code t} is seconds.
	 */
	public static void bust(GuiGraphicsExtractor g, LivingEntity e, int x0, int y0, int x1, int y1, float turn, float talk, float t) {
		if (e == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		EntityRenderState rs;
		try {
			rs = mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e, partial);
		} catch (RuntimeException ex) {
			return;
		}
		rs.shadowPieces.clear();
		rs.outlineColor = 0;
		rs.nameTag = null;
		rs.scoreText = null;
		rs.leashStates = null;
		float h = rs.boundingBoxHeight;
		if (rs instanceof LivingEntityRenderState l) {
			float nod = talk * (Mth.sin(t * 9.0F) * 3.5F + Mth.sin(t * 5.3F) * 2.0F);
			float sway = Mth.sin(t * 0.9F) * 2.0F;
			l.bodyRot = 180.0F + turn;
			l.yRot = turn * 0.35F + sway;
			l.xRot = -3.0F + nod;
			l.walkAnimationSpeed = 0;
			l.walkAnimationPos = 0;
			l.deathTime = 0;
			l.hasRedOverlay = false;
			h = l.boundingBoxHeight / Math.max(0.01F, l.scale);
			l.boundingBoxWidth = l.boundingBoxWidth / Math.max(0.01F, l.scale);
			l.boundingBoxHeight = h;
			l.scale = 1.0F;
		}
		// Frame head, shoulders and chest: the top 62% of the body fills the box, with a little
		// headroom above the crown (hair and hats included), and it breathes. The box centre sits at
		// this height on the body, so the crown is HEADROOM above the top 62%.
		float breathe = Mth.sin(t * 1.6F) * 0.012F;
		float span = 0.62F;
		float scale = (y1 - y0) / (h * span);
		Vector3f translation = new Vector3f(0.0F, h * (1.0F + HEADROOM - span / 2.0F + breathe), 0.0F);
		entity(g, rs, scale, translation, new Quaternionf().rotateZ((float) Math.PI), null, x0, y0, x1, y1);
	}

	/**
	 * {@link GuiGraphicsExtractor#entity}, but in the current pose's coordinates: the game draws
	 * entities in a box given in GUI pixels and ignores the pose, so a scaled screen has to map
	 * the box (and the model's scale) itself.
	 */
	public static void entity(GuiGraphicsExtractor g, EntityRenderState rs, float scale, Vector3fc translation, Quaternionfc rotation, Quaternionfc tilt,
		int x0, int y0, int x1, int y1) {
		var m = g.pose();
		Vector2f a = m.transformPosition(new Vector2f(x0, y0));
		Vector2f b = m.transformPosition(new Vector2f(x1, y1));
		float s = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
		g.entity(rs, scale * s, translation, rotation, tilt, Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y));
	}

	/** A soft round glow behind a portrait, in the speaker's colour. */
	public static void halo(GuiGraphicsExtractor g, int cx, int cy, int radius, int rgb, float alpha) {
		int steps = 8;
		for (int k = 0; k < steps; k++) {
			int r = radius * (steps - k) / steps;
			int a = (int) (alpha * 22);
			if (a <= 0 || r <= 0) {
				continue;
			}
			for (int dy = -r; dy <= r; dy += 2) {
				int half = (int) Math.sqrt(r * r - dy * dy);
				g.fill(cx - half, cy + dy, cx + half, cy + dy + 2, ARGB.color(a, rgb));
			}
		}
	}
}
