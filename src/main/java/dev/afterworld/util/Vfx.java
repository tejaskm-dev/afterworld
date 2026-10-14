package dev.afterworld.util;

import dev.afterworld.AwConfig;
import dev.afterworld.net.AwPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Shapes of light for attacks and powers, drawn by each nearby client for a moment (see
 * client.fx.VfxRenderer): a blade's arc, a shockwave, a beam, a flare, a pillar, a sigil on the
 * ground, a cone of force, a trail behind something moving, a helix of rising light, and a
 * flash at the edges of one player's screen. All of it is geometry, not particles, so a few
 * shapes say more than a hundred specks and cost less.
 */
public final class Vfx {
	public static final int SLASH = 0;
	public static final int RING = 1;
	public static final int BEAM = 2;
	public static final int FLARE = 3;
	public static final int PILLAR = 4;
	public static final int SIGIL = 5;
	public static final int WEDGE = 6;
	public static final int TRAIL = 7;
	public static final int HELIX = 8;
	public static final int FLASH = 9;
	public static final int MARK = 10;
	public static final int WAVE = 11;

	/** Sigil patterns. */
	public static final int SIGIL_EDICT = 0;
	public static final int SIGIL_CLOCK = 1;
	public static final int SIGIL_JUDGMENT = 2;
	public static final int SIGIL_STAR = 3;

	private static final double RANGE = 96;

	private Vfx() {
	}

	private static void send(ServerLevel level, Vec3 near, AwPayloads.Vfx v) {
		if (AwConfig.get().particleDensity <= 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(near) < RANGE * RANGE && ServerPlayNetworking.canSend(p, AwPayloads.Vfx.TYPE)) {
				ServerPlayNetworking.send(p, v);
			}
		}
	}

	/**
	 * A blade's arc: an arc of {@code span} degrees and {@code radius} blocks round {@code center},
	 * in the plane of the look ({@code yaw}, {@code pitch}) tipped by {@code roll}; it sweeps one
	 * way ({@code span} > 0) or the other, then fades. With {@code follow}, it moves with that entity.
	 */
	public static void slash(ServerLevel level, Vec3 center, float yaw, float pitch, float roll, float radius, float span, float width, int color, int life, Entity follow) {
		send(level, center, new AwPayloads.Vfx(SLASH, center, new Vec3(yaw, pitch, roll), radius, width, span, color, life, follow == null ? -1 : follow.getId()));
	}

	/** A slash from a player's chest along their gaze. */
	public static void slash(ServerLevel level, Entity by, float roll, float radius, float span, int color, int life) {
		Vec3 c = by.position().add(0, by.getBbHeight() * 0.62, 0);
		slash(level, c, by.getYRot(), by.getXRot() * 0.5F, roll, radius, span, 0.55F, color, life, by);
	}

	/** A shockwave: a ring on the ground running out to {@code radius}, with a wall of light {@code wall} high (0 for none). */
	public static void ring(ServerLevel level, Vec3 center, float radius, float thickness, float wall, int color, int life) {
		send(level, center, new AwPayloads.Vfx(RING, center, new Vec3(0, 1, 0), radius, thickness, wall, color, life, -1));
	}

	/** A ring in any plane (its axis {@code normal}): a halo, a bell's ripple in the air. */
	public static void ring(ServerLevel level, Vec3 center, Vec3 normal, float radius, float thickness, int color, int life) {
		send(level, center, new AwPayloads.Vfx(RING, center, normal, radius, thickness, 0, color, life, -1));
	}

	/** A beam between two points; {@code jag} > 0 makes it a crackling arc of lightning. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, float width, float jag, int color, int life) {
		send(level, from.add(to).scale(0.5), new AwPayloads.Vfx(BEAM, from, to, width, width, jag, color, life, -1));
	}

	/** A burst of light at a point, with a four-pointed star when {@code star}. */
	public static void flare(ServerLevel level, Vec3 at, float size, boolean star, int color, int life) {
		send(level, at, new AwPayloads.Vfx(FLARE, at, Vec3.ZERO, size, size, star ? 1 : 0, color, life, -1));
	}

	/** A column of light standing on {@code base}. */
	public static void pillar(ServerLevel level, Vec3 base, float radius, float height, int color, int life) {
		send(level, base, new AwPayloads.Vfx(PILLAR, base, Vec3.ZERO, radius, height, 0, color, life, -1));
	}

	/** A sigil drawn on the ground and turning ({@code spin} degrees a tick); with {@code follow}, it stays under that entity. */
	public static void sigil(ServerLevel level, Vec3 center, float radius, int pattern, float spin, int color, int life, Entity follow) {
		send(level, center, new AwPayloads.Vfx(SIGIL, center, new Vec3(spin, 0, 0), radius, 0, pattern, color, life, follow == null ? -1 : follow.getId()));
	}

	/** A cone of force on the ground, from {@code apex} along {@code dir}, out to {@code range}, {@code halfAngle} degrees either side. */
	public static void wedge(ServerLevel level, Vec3 apex, Vec3 dir, float range, float halfAngle, int color, int life) {
		send(level, apex, new AwPayloads.Vfx(WEDGE, apex, dir.multiply(1, 0, 1).normalize(), range, 0.7F, halfAngle, color, life, -1));
	}

	/** A ribbon of light behind a moving entity, for {@code life} ticks. */
	public static void trail(ServerLevel level, Entity e, float width, int color, int life) {
		send(level, e.position(), new AwPayloads.Vfx(TRAIL, e.position(), Vec3.ZERO, width, width, 0, color, life, e.getId()));
	}

	/** Strands of light rising round {@code base} (a mending, a blessing). */
	public static void helix(ServerLevel level, Vec3 base, float radius, float height, int color, int life) {
		send(level, base, new AwPayloads.Vfx(HELIX, base, Vec3.ZERO, radius, height, 0, color, life, -1));
	}

	/** A warning on the ground: a circle that fills over {@code ticks}, when something lands on it. */
	public static void mark(ServerLevel level, Vec3 center, float radius, int color, int ticks) {
		send(level, center, new AwPayloads.Vfx(MARK, center, Vec3.ZERO, radius, 0, 0, color, ticks, -1));
	}

	/** A shockwave rolling out along the ground at an even speed, reaching {@code radius} in {@code ticks}. */
	public static void wave(ServerLevel level, Vec3 center, float radius, float thickness, float wall, int color, int ticks) {
		send(level, center, new AwPayloads.Vfx(WAVE, center, new Vec3(0, 1, 0), radius, thickness, wall, color, ticks, -1));
	}

	/** A flash at the edges of this player's screen, {@code strength} 0..1. */
	public static void flash(ServerPlayer p, float strength, int color, int life) {
		if (ServerPlayNetworking.canSend(p, AwPayloads.Vfx.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.Vfx(FLASH, p.position(), Vec3.ZERO, strength, 0, 0, color, life, -1));
		}
	}

	/** Brightens a colour toward white by {@code k} (0..1). */
	public static int lighten(int rgb, float k) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
	}
}
