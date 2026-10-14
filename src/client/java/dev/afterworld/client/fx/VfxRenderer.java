package dev.afterworld.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.afterworld.AwConfig;
import dev.afterworld.combat.Arts;
import dev.afterworld.item.ArtWeapon;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.util.Vfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Afterworld's shapes of light (see {@link Vfx}): blade arcs, shockwaves, beams and
 * lightning, flares, pillars, sigils, cones of force, trails and rising helices. Everything is
 * soft-edged ribbons (strong down the middle, fading to nothing at the edges), built fresh each
 * frame from a handful of numbers, so no textures are needed and a whole fight's worth costs a
 * few hundred quads. Each shape is drawn twice: a body in its true colour, blended over the
 * world so it reads the same on grass, sand or sky, and a thin hot core of additive light that
 * glows at night without washing out the day. Also gives every swing of a blade its arc.
 */
public final class VfxRenderer {
	private static final List<Effect> LIVE = new ArrayList<>();
	private static final int MAX = 320;
	/** Each swinger's last swing (game time) and which way it went, so a string of swings alternates. */
	private static final Map<Integer, long[]> SWINGS = new HashMap<>();

	private VfxRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			Minecraft mc = Minecraft.getInstance();
			if (LIVE.isEmpty()) {
				return;
			}
			if (mc.level == null) {
				LIVE.clear();
				return;
			}
			float k = (float) Mth.clamp(AwConfig.get().abilityEffects, 0, 1.5);
			float now = now(mc);
			LIVE.removeIf(e -> now - e.start > e.life + 1);
			if (k <= 0 || LIVE.isEmpty()) {
				return;
			}
			Effect[] draw = LIVE.toArray(new Effect[0]);
			Vec3 cam = ctx.levelState().cameraRenderState.pos;
			// The bodies first (blended, true colour), then the hot cores over them (added light).
			// Under a shader pack only lightning reaches the pack's own programs (plain colour drawn
			// any other way skips its colour handling and comes out pale), so both layers go that
			// way, told apart by alpha: Afterworld Shaders' gbuffers_lightning reads it back.
			boolean shaders = shaderPackInUse();
			ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), shaders ? RenderTypes.lightning() : RenderTypes.debugQuads(),
				(pose, vc) -> drawAll(draw, new Draw(pose, vc, cam, k * 0.85F, BODY, shaders), now));
			ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lightning(),
				(pose, vc) -> drawAll(draw, new Draw(pose, vc, cam, k * 0.8F, GLOW, shaders), now));
		});
	}

	/** The two layers every shape is drawn in. */
	static final int BODY = 0;
	static final int GLOW = 1;

	private static java.lang.reflect.Method irisInUse;
	private static Object iris;
	private static boolean irisLooked;

	/** Whether Iris is drawing with a shader pack (asked by name, so Iris need not be present). */
	static boolean shaderPackInUse() {
		if (!irisLooked) {
			irisLooked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				iris = api.getMethod("getInstance").invoke(null);
				irisInUse = api.getMethod("isShaderPackInUse");
			} catch (Throwable t) {
				irisInUse = null;
			}
		}
		if (irisInUse == null) {
			return false;
		}
		try {
			return (Boolean) irisInUse.invoke(iris);
		} catch (Throwable t) {
			return false;
		}
	}

	private static void drawAll(Effect[] draw, Draw d, float now) {
		for (Effect e : draw) {
			float age = now - e.start;
			if (age >= 0 && age <= e.life) {
				e.draw(d, age, age / e.life);
			}
		}
	}

	static float now(Minecraft mc) {
		return mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	public static void clear() {
		LIVE.clear();
		SWINGS.clear();
	}

	private static void add(Effect e) {
		if (LIVE.size() >= MAX) {
			LIVE.remove(0);
		}
		LIVE.add(e);
	}

	/** A shape sent by the server. */
	public static void receive(AwPayloads.Vfx v) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (v.kind() == Vfx.FLASH) {
			ScreenFlash.start(v.size(), v.color(), v.life());
			return;
		}
		float start = now(mc);
		Entity follow = v.entity() >= 0 ? mc.level.getEntity(v.entity()) : null;
		int life = Math.max(1, v.life());
		switch (v.kind()) {
			case Vfx.SLASH -> {
				if (follow == mc.player && mc.options.getCameraType().isFirstPerson()) {
					// Seen from one's own eyes, an arc round the body is edge-on: draw it across the view instead.
					add(screenSlash(start, life, v.color(), (float) v.b().z, Math.min(1.1F, v.size() * 0.5F), v.width() * 0.8F, v.extra()));
				} else {
					add(new Slash(start, life, v.color(), v.a(), follow, (float) v.b().x, (float) v.b().y, (float) v.b().z, v.size(), v.width(), v.extra(), false));
				}
			}
			case Vfx.RING -> add(new Ring(start, life, v.color(), v.a(), v.b(), v.size(), v.width(), v.extra(), 0, 0));
			case Vfx.WAVE -> {
				Ring r = new Ring(start, life, v.color(), v.a(), v.b(), v.size(), v.width(), v.extra(), 0, 0);
				r.linear = true;
				add(r);
			}
			case Vfx.MARK -> add(new Mark(start, life, v.color(), v.a(), v.size()));
			case Vfx.BEAM -> add(new Beam(start, life, v.color(), v.a(), v.b(), v.size(), v.extra()));
			case Vfx.FLARE -> add(new Flare(start, life, v.color(), v.a(), v.size(), v.extra() > 0));
			case Vfx.PILLAR -> add(new Pillar(start, life, v.color(), v.a(), v.size(), v.width()));
			case Vfx.SIGIL -> add(new Sigil(start, life, v.color(), v.a(), follow, v.size(), (int) v.extra(), (float) v.b().x));
			case Vfx.WEDGE -> {
				double yaw = Math.atan2(v.b().z, v.b().x);
				double half = Math.toRadians(v.extra());
				add(new Ring(start, life, v.color(), v.a(), new Vec3(0, 1, 0), v.size(), v.width(), 0, yaw - half, yaw + half));
			}
			case Vfx.TRAIL -> {
				if (follow != null) {
					add(new Trail(start, life, v.color(), follow, v.size()));
				}
			}
			case Vfx.HELIX -> add(new Helix(start, life, v.color(), v.a(), v.size(), v.width()));
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ swings

	/** A creature's swing has begun (client side): give a blade its arc. */
	public static void onSwing(LivingEntity e, InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || hand != InteractionHand.MAIN_HAND || !(e instanceof Player) || !AwConfig.get().swingTrails) {
			return;
		}
		ItemStack held = e.getMainHandItem();
		if (!isBlade(held)) {
			return;
		}
		long t = mc.level.getGameTime();
		long[] last = SWINGS.computeIfAbsent(e.getId(), id -> new long[] {-100, 0});
		int style = t - last[0] < 30 ? (int) ((last[1] + 1) % 3) : 0;
		last[0] = t;
		last[1] = style;
		if (SWINGS.size() > 64) {
			SWINGS.clear();
		}
		boolean crit = !e.onGround() && e.fallDistance > 0 && !e.isInWater();
		boolean heavy = held.is(ItemTags.AXES) || held.is(Items.MACE);
		float radius = heavy ? 2.0F : 1.75F;
		float roll;
		float span;
		if (crit) {
			roll = 88;
			span = 150;
			radius += 0.25F;
		} else {
			roll = switch (style) {
				case 0 -> 28;
				case 1 -> -28;
				default -> heavy ? 80 : 4;
			};
			span = switch (style) {
				case 0 -> 150;
				case 1 -> -150;
				default -> heavy ? 140 : 160;
			};
		}
		boolean me = e == mc.player && mc.options.getCameraType().isFirstPerson();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int color = colorOf(held);
		if (me) {
			add(screenSlash(now(mc), crit ? 9 : 7, color, roll, crit ? 1.05F : 0.95F, crit ? 0.55F : 0.42F, span));
			return;
		}
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.62, 0);
		add(new Slash(now(mc), crit ? 9 : 7, color, c, e, e.getYRot(), e.getXRot() * 0.6F, roll, radius, crit ? 0.7F : 0.5F, span, false));
	}

	/** An arc drawn across one's own view, a little ahead of the eyes, tipped by {@code roll}. */
	private static Slash screenSlash(float start, float life, int color, float roll, float radius, float width, float span) {
		Minecraft mc = Minecraft.getInstance();
		Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eye = cam.getEyePosition(partial);
		Vec3 look = cam.getViewVector(partial);
		Vec3 c = eye.add(look.scale(1.25)).add(0, 0.28, 0);
		return new Slash(start, life, color, c, null, cam.getViewYRot(partial), cam.getViewXRot(partial), roll, radius, width, span, true);
	}

	private static boolean isBlade(ItemStack s) {
		return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(Items.TRIDENT) || s.is(Items.MACE)
			|| s.getItem() instanceof ArtWeapon);
	}

	/** The colour of a weapon's arc: its Art's, or its metal's. */
	public static int colorOf(ItemStack s) {
		if (s.getItem() instanceof ArtWeapon w) {
			return w.art().color;
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (id.startsWith("golden")) {
			return 0xFFD27A;
		}
		if (id.startsWith("diamond")) {
			return 0x8FF0F0;
		}
		if (id.startsWith("netherite")) {
			return 0xC0A8FF;
		}
		if (id.startsWith("copper")) {
			return 0xF0A070;
		}
		if (s.is(Items.TRIDENT)) {
			return 0x7FE0D0;
		}
		return 0xDCE6FF;
	}

	/** Spawns a slash from this client (for tests and previews). */
	public static void localSlash(Vec3 c, float yaw, float pitch, float roll, float radius, float span, int color, int life) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			add(new Slash(now(mc), life, color, c, null, yaw, pitch, roll, radius, 0.55F, span, false));
		}
	}

	// ------------------------------------------------------------------ drawing

	/** Writes soft-edged geometry, camera-relative, scaled by the effects setting. */
	static final class Draw {
		final PoseStack.Pose pose;
		final VertexConsumer vc;
		final double cx;
		final double cy;
		final double cz;
		final float k;
		/** Which layer this pass draws; everything asked of the other is skipped. */
		final int pass;
		/** Under a shader pack: both layers share one pipeline, the body in alpha 0..0.5, the core from 0.52 up. */
		final boolean encode;

		Draw(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float k, int pass, boolean encode) {
			this.pose = pose;
			this.vc = vc;
			this.cx = cam.x;
			this.cy = cam.y;
			this.cz = cam.z;
			this.k = k;
			this.pass = pass;
			this.encode = encode;
		}

		void v(double x, double y, double z, int rgb, float a) {
			float f = Mth.clamp(a * this.k, 0, 1);
			if (this.encode) {
				f = this.pass == BODY ? f * 0.5F : 0.52F + f * 0.48F;
			}
			int alpha = Mth.clamp((int) (f * 255), 0, 255);
			this.vc.addVertex(this.pose, (float) (x - this.cx), (float) (y - this.cy), (float) (z - this.cz)).setColor((alpha << 24) | (rgb & 0xFFFFFF));
		}

		/**
		 * A quad seen from both sides; vertices as {x, y, z} rows. (The glow's pipeline culls back
		 * faces, so it gets both windings; the body's draws both sides of one.)
		 */
		void quad(int layer, double[] p0, double[] p1, double[] p2, double[] p3, int c0, int c1, int c2, int c3, float a0, float a1, float a2, float a3) {
			if (layer != this.pass) {
				return;
			}
			v(p0[0], p0[1], p0[2], c0, a0);
			v(p1[0], p1[1], p1[2], c1, a1);
			v(p2[0], p2[1], p2[2], c2, a2);
			v(p3[0], p3[1], p3[2], c3, a3);
			if (this.pass == GLOW || this.encode) {
				v(p3[0], p3[1], p3[2], c3, a3);
				v(p2[0], p2[1], p2[2], c2, a2);
				v(p1[0], p1[1], p1[2], c1, a1);
				v(p0[0], p0[1], p0[2], c0, a0);
			}
		}

		/**
		 * A ribbon down a line of points, bright along it and fading to nothing at its edges:
		 * {@code side} gives each point's half-width (as a vector), {@code out} how far past the
		 * points the outer edge reaches as a fraction of it (1 = symmetric).
		 */
		void ribbon(int layer, double[][] pts, double[][] side, float[] alpha, int[] color, float out) {
			if (layer != this.pass) {
				return;
			}
			for (int i = 0; i + 1 < pts.length; i++) {
				double[] a = pts[i];
				double[] b = pts[i + 1];
				double[] sa = side[i];
				double[] sb = side[i + 1];
				double[] aIn = {a[0] - sa[0], a[1] - sa[1], a[2] - sa[2]};
				double[] bIn = {b[0] - sb[0], b[1] - sb[1], b[2] - sb[2]};
				double[] aOut = {a[0] + sa[0] * out, a[1] + sa[1] * out, a[2] + sa[2] * out};
				double[] bOut = {b[0] + sb[0] * out, b[1] + sb[1] * out, b[2] + sb[2] * out};
				quad(layer, aIn, a, b, bIn, color[i], color[i], color[i + 1], color[i + 1], 0, alpha[i], alpha[i + 1], 0);
				quad(layer, a, aOut, bOut, b, color[i], color[i], color[i + 1], color[i + 1], alpha[i], 0, 0, alpha[i + 1]);
			}
		}

		/** A camera-facing ribbon through {@code pts}, {@code width[i]} wide at each. */
		void facing(int layer, double[][] pts, float[] width, float[] alpha, int[] color) {
			if (layer != this.pass) {
				return;
			}
			int n = pts.length;
			double[][] side = new double[n][];
			for (int i = 0; i < n; i++) {
				double[] p = pts[i];
				double[] q = pts[Math.min(n - 1, i + 1)];
				double[] o = pts[Math.max(0, i - 1)];
				double tx = q[0] - o[0];
				double ty = q[1] - o[1];
				double tz = q[2] - o[2];
				double vx = p[0] - this.cx;
				double vy = p[1] - this.cy;
				double vz = p[2] - this.cz;
				double sx = ty * vz - tz * vy;
				double sy = tz * vx - tx * vz;
				double sz = tx * vy - ty * vx;
				double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
				if (len < 1e-6) {
					side[i] = new double[] {0, width[i] * 0.5, 0};
				} else {
					double s = width[i] * 0.5 / len;
					side[i] = new double[] {sx * s, sy * s, sz * s};
				}
			}
			ribbon(layer, pts, side, alpha, color, 1);
		}

		/** A soft disc facing the camera: bright in the middle, gone at the rim. */
		void disc(int layer, double x, double y, double z, double r, int rgb, float a) {
			if (layer != this.pass) {
				return;
			}
			double vx = x - this.cx;
			double vy = y - this.cy;
			double vz = z - this.cz;
			double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (len < 1e-4) {
				return;
			}
			vx /= len;
			vy /= len;
			vz /= len;
			// right = v x up, up' = right x v
			double rx = -vz;
			double rz = vx;
			double rl = Math.sqrt(rx * rx + rz * rz);
			double ry = 0;
			if (rl < 1e-4) {
				rx = 1;
				rz = 0;
				rl = 1;
			}
			rx /= rl;
			rz /= rl;
			double ux = ry * vz - rz * vy;
			double uy = rz * vx - rx * vz;
			double uz = rx * vy - ry * vx;
			int n = 16;
			double[] c = {x, y, z};
			for (int i = 0; i < n; i++) {
				double a0 = i * Math.PI * 2 / n;
				double a1 = (i + 1) * Math.PI * 2 / n;
				double[] p0 = {x + (rx * Math.cos(a0) + ux * Math.sin(a0)) * r, y + (ry * Math.cos(a0) + uy * Math.sin(a0)) * r, z + (rz * Math.cos(a0) + uz * Math.sin(a0)) * r};
				double[] p1 = {x + (rx * Math.cos(a1) + ux * Math.sin(a1)) * r, y + (ry * Math.cos(a1) + uy * Math.sin(a1)) * r, z + (rz * Math.cos(a1) + uz * Math.sin(a1)) * r};
				quad(layer, c, c, p0, p1, rgb, rgb, rgb, rgb, a, a, 0, 0);
			}
		}

		/** A soft line lying in the plane with normal {@code n} (a stroke of a sigil). */
		void stroke(int layer, double[] p0, double[] p1, double[] n, double width, int rgb, float a) {
			if (layer != this.pass) {
				return;
			}
			double dx = p1[0] - p0[0];
			double dy = p1[1] - p0[1];
			double dz = p1[2] - p0[2];
			double sx = n[1] * dz - n[2] * dy;
			double sy = n[2] * dx - n[0] * dz;
			double sz = n[0] * dy - n[1] * dx;
			double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (len < 1e-6) {
				return;
			}
			double s = width * 0.5 / len;
			double[] side = {sx * s, sy * s, sz * s};
			ribbon(layer, new double[][] {p0, p1}, new double[][] {side, side}, new float[] {a, a}, new int[] {rgb, rgb}, 1);
		}

		/** A circle in the plane through {@code c} with axes {@code u}, {@code w}, as a soft ring of {@code width}. */
		void circle(int layer, double[] c, double[] u, double[] w, double r, double width, int rgb, float a, double from, double to) {
			if (layer != this.pass) {
				return;
			}
			int n = Mth.clamp((int) (r * 10 * (to - from) / (Math.PI * 2)), 10, 120);
			double[][] pts = new double[n + 1][];
			double[][] side = new double[n + 1][];
			float[] al = new float[n + 1];
			int[] col = new int[n + 1];
			for (int i = 0; i <= n; i++) {
				double ang = from + (to - from) * i / n;
				double ca = Math.cos(ang);
				double sa = Math.sin(ang);
				double dx = u[0] * ca + w[0] * sa;
				double dy = u[1] * ca + w[1] * sa;
				double dz = u[2] * ca + w[2] * sa;
				pts[i] = new double[] {c[0] + dx * r, c[1] + dy * r, c[2] + dz * r};
				side[i] = new double[] {dx * width * 0.5, dy * width * 0.5, dz * width * 0.5};
				al[i] = a;
				col[i] = rgb;
			}
			ribbon(layer, pts, side, al, col, 1);
		}
	}

	/** Two unit vectors at right angles to {@code n} and to each other. */
	static double[][] basis(double nx, double ny, double nz) {
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6) {
			ny = 1;
			len = 1;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		double[] a = Math.abs(ny) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
		double ux = a[1] * nz - a[2] * ny;
		double uy = a[2] * nx - a[0] * nz;
		double uz = a[0] * ny - a[1] * nx;
		double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux /= ul;
		uy /= ul;
		uz /= ul;
		double wx = ny * uz - nz * uy;
		double wy = nz * ux - nx * uz;
		double wz = nx * uy - ny * ux;
		return new double[][] {{ux, uy, uz}, {wx, wy, wz}, {nx, ny, nz}};
	}

	static float ease(float t) {
		t = Mth.clamp(t, 0, 1);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	static float rnd(int i, int seed) {
		int h = i * 374761393 + seed * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
	}

	// ------------------------------------------------------------------ the shapes

	abstract static class Effect {
		final float start;
		final float life;
		final int color;

		Effect(float start, float life, int color) {
			this.start = start;
			this.life = life;
			this.color = color & 0xFFFFFF;
		}

		abstract void draw(Draw d, float age, float t);
	}

	/** Something that may ride along with an entity. */
	abstract static class Anchored extends Effect {
		final Vec3 at;
		final Entity follow;
		final Vec3 offset;

		Anchored(float start, float life, int color, Vec3 at, Entity follow) {
			super(start, life, color);
			this.at = at;
			this.follow = follow;
			this.offset = follow == null ? Vec3.ZERO : at.subtract(follow.position());
		}

		Vec3 where() {
			if (this.follow == null || this.follow.isRemoved()) {
				return this.at;
			}
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			return this.follow.getPosition(partial).add(this.offset);
		}
	}

	/**
	 * A blade's arc: sweeps through its span, the head bright and the tail thinning, then fades.
	 * Round the body it lies in the plane of the look; {@code screen} arcs stand across the view
	 * instead, curving down like a smile (tipped by the roll), for one's own swings.
	 */
	static final class Slash extends Anchored {
		final double[] f;
		final double[] r;
		final float radius;
		final float width;
		final float span;
		final boolean screen;

		Slash(float start, float life, int color, Vec3 at, Entity follow, float yaw, float pitch, float roll, float radius, float width, float span, boolean screen) {
			super(start, life, color, at, follow);
			double y = Math.toRadians(yaw);
			double p = Math.toRadians(pitch);
			double fx = -Math.sin(y) * Math.cos(p);
			double fy = -Math.sin(p);
			double fz = Math.cos(y) * Math.cos(p);
			// right = f x up; up' = right x f; the arc's second axis is right turned by roll about f.
			double rx = -fz;
			double rz = fx;
			double rl = Math.sqrt(rx * rx + rz * rz);
			if (rl < 1e-4) {
				rx = 1;
				rz = 0;
				rl = 1;
			}
			rx /= rl;
			rz /= rl;
			double ux = -rz * fy;
			double uy = rz * fx - rx * fz;
			double uz = rx * fy;
			double ro = Math.toRadians(roll);
			double[] rr = {rx * Math.cos(ro) + ux * Math.sin(ro), Math.sin(ro) * uy, rz * Math.cos(ro) + uz * Math.sin(ro)};
			this.r = rr;
			this.screen = screen;
			if (screen) {
				// The arc's other axis points down the view (the rolled "down"), so it bows below its middle.
				double dx = -(rr[1] * fz - rr[2] * fy);
				double dy = -(rr[2] * fx - rr[0] * fz);
				double dz = -(rr[0] * fy - rr[1] * fx);
				this.f = new double[] {dx, dy, dz};
			} else {
				this.f = new double[] {fx, fy, fz};
			}
			this.radius = radius;
			this.width = width;
			this.span = span;
		}

		@Override
		void draw(Draw d, float age, float t) {
			float sweep = 0.4F;
			float head = ease(t / sweep);
			float tail = (float) Math.pow(Mth.clamp((t - sweep * 0.35F) / (1 - sweep * 0.35F), 0, 1), 1.2);
			float fade = t < sweep ? 1 : 1 - smooth((t - sweep) / (1 - sweep));
			double half = Math.toRadians(Math.abs(this.span)) / 2;
			double from = this.span > 0 ? half : -half;
			double to = -from;
			double ah = from + (to - from) * head;
			double at = from + (to - from) * Math.min(head, tail);
			Vec3 c = where();
			int n = 22;
			double[][] pts = new double[n + 1][];
			double[][] side = new double[n + 1][];
			double[][] core = new double[n + 1][];
			float[] al = new float[n + 1];
			float[] alc = new float[n + 1];
			int[] col = new int[n + 1];
			int[] colc = new int[n + 1];
			int hot = Vfx.lighten(this.color, 0.75F);
			for (int i = 0; i <= n; i++) {
				float s = i / (float) n;
				double ang = at + (ah - at) * s;
				double dx = this.f[0] * Math.cos(ang) + this.r[0] * Math.sin(ang);
				double dy = this.f[1] * Math.cos(ang) + this.r[1] * Math.sin(ang);
				double dz = this.f[2] * Math.cos(ang) + this.r[2] * Math.sin(ang);
				pts[i] = new double[] {c.x + dx * this.radius, c.y + dy * this.radius, c.z + dz * this.radius};
				double w = this.width * (0.2 + 0.8 * s) * 0.5;
				side[i] = new double[] {dx * w, dy * w, dz * w};
				core[i] = new double[] {dx * w * 0.22, dy * w * 0.22, dz * w * 0.22};
				al[i] = fade * (0.1F + 0.9F * (float) Math.pow(s, 1.3));
				alc[i] = fade * (float) Math.pow(s, 2.0);
				col[i] = this.color;
				colc[i] = hot;
			}
			// The glow reaches further inward than out, as a blade's trail does.
			d.ribbon(BODY, pts, side, al, col, 0.55F);
			d.ribbon(GLOW, pts, core, alc, colc, 1);
			if (t < sweep) {
				d.disc(GLOW, pts[n][0], pts[n][1], pts[n][2], this.width * 0.45, hot, 0.7F * fade);
			}
		}
	}

	/** A shockwave (or, with an arc of angles, a cone of force): runs out, thins and fades, with an optional wall of light. */
	static final class Ring extends Effect {
		final Vec3 c;
		final double[][] b;
		final float radius;
		final float thick;
		final float wall;
		final double from;
		final double to;
		/** A hazard's wave: out at an even speed, keeping its strength until near the end. */
		boolean linear;

		Ring(float start, float life, int color, Vec3 c, Vec3 normal, float radius, float thick, float wall, double from, double to) {
			super(start, life, color);
			this.c = c;
			this.b = basis(normal.x, normal.y, normal.z);
			this.radius = radius;
			this.thick = thick;
			this.wall = wall;
			if (from == to) {
				this.from = 0;
				this.to = Math.PI * 2;
			} else {
				this.from = from;
				this.to = to;
			}
		}

		@Override
		void draw(Draw d, float age, float t) {
			float e = this.linear ? t : ease(t * 1.15F);
			double r = this.radius * (0.06 + 0.94 * e);
			float a = this.linear ? (t < 0.8F ? 1 : 1 - (t - 0.8F) / 0.2F) : (float) Math.pow(1 - t, 1.5);
			double th = Math.max(0.12, this.thick * (this.linear ? 1 : 1 - 0.55 * t));
			boolean full = this.to - this.from >= Math.PI * 2 - 1e-3;
			// Axes on the ground (or in the ring's own plane): u, w; the axis: n.
			double[] u = this.b[0];
			double[] w = this.b[1];
			double[] n = this.b[2];
			// For the cone, angles are measured on the ground from +x toward +z.
			if (!full) {
				u = new double[] {1, 0, 0};
				w = new double[] {0, 0, 1};
			}
			double[] c = {this.c.x, this.c.y + 0.06, this.c.z};
			int seg = Mth.clamp((int) (r * 9 * (this.to - this.from) / (Math.PI * 2)), 12, 140);
			double[][] pts = new double[seg + 1][];
			double[][] side = new double[seg + 1][];
			float[] al = new float[seg + 1];
			int[] col = new int[seg + 1];
			double[][] core = new double[seg + 1][];
			int[] colc = new int[seg + 1];
			int hot = Vfx.lighten(this.color, 0.6F);
			for (int i = 0; i <= seg; i++) {
				double ang = this.from + (this.to - this.from) * i / seg;
				double ca = Math.cos(ang);
				double sa = Math.sin(ang);
				double dx = u[0] * ca + w[0] * sa;
				double dy = u[1] * ca + w[1] * sa;
				double dz = u[2] * ca + w[2] * sa;
				pts[i] = new double[] {c[0] + dx * r, c[1] + dy * r, c[2] + dz * r};
				side[i] = new double[] {dx * th, dy * th, dz * th};
				core[i] = new double[] {dx * th * 0.25, dy * th * 0.25, dz * th * 0.25};
				// A cone's edges fade, so it reads as a sweep rather than a slice.
				float edge = full ? 1 : (float) Math.sin(Math.PI * i / seg);
				al[i] = a * (full ? 1 : 0.35F + 0.65F * edge);
				col[i] = this.color;
				colc[i] = hot;
			}
			d.ribbon(BODY, pts, side, al, col, 0.45F);
			d.ribbon(GLOW, pts, core, al, colc, 1);
			if (this.wall > 0) {
				double h = this.wall * (1 - 0.5 * t);
				for (int i = 0; i < seg; i++) {
					double[] p0 = pts[i];
					double[] p1 = pts[i + 1];
					double[] q0 = {p0[0] + n[0] * h, p0[1] + n[1] * h, p0[2] + n[2] * h};
					double[] q1 = {p1[0] + n[0] * h, p1[1] + n[1] * h, p1[2] + n[2] * h};
					d.quad(BODY, p0, p1, q1, q0, this.color, this.color, this.color, this.color, al[i] * 0.3F, al[i + 1] * 0.3F, 0, 0);
				}
			}
			if (!full) {
				// The cone's floor: a faint wash from the apex to the front.
				for (int i = 0; i < seg; i++) {
					d.quad(BODY, c, c, pts[i], pts[i + 1], this.color, this.color, this.color, this.color, 0, 0, al[i] * 0.22F, al[i + 1] * 0.22F);
				}
			}
		}
	}

	/** A warning on the ground: a ring of the danger's size, and a disc filling it until the blow lands. */
	static final class Mark extends Effect {
		final Vec3 c;
		final float radius;

		Mark(float start, float life, int color, Vec3 c, float radius) {
			super(start, life, color);
			this.c = c;
			this.radius = radius;
		}

		@Override
		void draw(Draw d, float age, float t) {
			double[] c = {this.c.x, this.c.y + 0.07, this.c.z};
			double[] u = {1, 0, 0};
			double[] w = {0, 0, 1};
			float pulse = t > 0.75F ? 0.75F + 0.25F * Mth.sin(age * 1.6F) : 0.75F;
			float in = Math.min(1, t * 6);
			d.circle(BODY, c, u, w, this.radius, 0.22, this.color, pulse * in, 0, Math.PI * 2);
			d.circle(GLOW, c, u, w, this.radius, 0.1, Vfx.lighten(this.color, 0.4F), 0.5F * pulse * in, 0, Math.PI * 2);
			double fill = this.radius * t;
			if (fill > 0.05) {
				d.circle(BODY, c, u, w, fill, 0.32, Vfx.lighten(this.color, 0.3F), 0.9F * in, 0, Math.PI * 2);
				int n = Mth.clamp((int) (fill * 8), 12, 96);
				for (int i = 0; i < n; i++) {
					double a0 = i * Math.PI * 2 / n;
					double a1 = (i + 1) * Math.PI * 2 / n;
					double[] p0 = {c[0] + Math.cos(a0) * fill, c[1], c[2] + Math.sin(a0) * fill};
					double[] p1 = {c[0] + Math.cos(a1) * fill, c[1], c[2] + Math.sin(a1) * fill};
					d.quad(BODY, c, c, p0, p1, this.color, this.color, this.color, this.color, 0.05F * in, 0.05F * in, 0.22F * in, 0.22F * in);
				}
			}
		}
	}

	/** A beam, or with {@code jag} a crackle of lightning, between two points. */
	static final class Beam extends Effect {
		final Vec3 a;
		final Vec3 b;
		final float width;
		final float jag;
		final int seed;

		Beam(float start, float life, int color, Vec3 a, Vec3 b, float width, float jag) {
			super(start, life, color);
			this.a = a;
			this.b = b;
			this.width = width;
			this.jag = jag;
			this.seed = (int) (start * 7919);
		}

		@Override
		void draw(Draw d, float age, float t) {
			float grow = ease(t / 0.15F);
			float al = t < 0.15F ? 1 : (float) Math.pow(1 - (t - 0.15F) / 0.85F, 1.2);
			Vec3 dir = this.b.subtract(this.a);
			double len = dir.length() * grow;
			if (len < 1e-3) {
				return;
			}
			Vec3 unit = dir.normalize();
			double[][] bs = basis(unit.x, unit.y, unit.z);
			int n = this.jag > 0 ? Mth.clamp((int) (len / 0.5), 4, 48) : 2;
			int frame = (int) (age / 1.5F);
			double[][] pts = new double[n + 1][];
			float[] w = new float[n + 1];
			float[] wc = new float[n + 1];
			float[] alpha = new float[n + 1];
			int[] col = new int[n + 1];
			int[] colc = new int[n + 1];
			int hot = Vfx.lighten(this.color, 0.8F);
			for (int i = 0; i <= n; i++) {
				float s = i / (float) n;
				double ox = 0;
				double oy = 0;
				double oz = 0;
				if (this.jag > 0 && i > 0 && i < n) {
					double amp = this.jag * Math.sin(Math.PI * s);
					double j1 = (rnd(i, this.seed + frame) - 0.5) * 2 * amp;
					double j2 = (rnd(i + 97, this.seed + frame) - 0.5) * 2 * amp;
					ox = bs[0][0] * j1 + bs[1][0] * j2;
					oy = bs[0][1] * j1 + bs[1][1] * j2;
					oz = bs[0][2] * j1 + bs[1][2] * j2;
				}
				pts[i] = new double[] {this.a.x + unit.x * len * s + ox, this.a.y + unit.y * len * s + oy, this.a.z + unit.z * len * s + oz};
				w[i] = this.width * (1 - 0.35F * t);
				wc[i] = w[i] * 0.3F;
				alpha[i] = al;
				col[i] = this.color;
				colc[i] = hot;
			}
			d.facing(BODY, pts, w, alpha, col);
			d.facing(GLOW, pts, wc, alpha, colc);
			d.disc(GLOW, this.a.x, this.a.y, this.a.z, this.width * 1.1, hot, al * 0.6F);
			double[] end = pts[n];
			d.disc(GLOW, end[0], end[1], end[2], this.width * 1.4, hot, al * 0.8F);
			d.disc(BODY, end[0], end[1], end[2], this.width * 1.6, this.color, al * 0.45F);
		}
	}

	/** A burst of light, with an optional star of four rays. */
	static final class Flare extends Effect {
		final Vec3 at;
		final float size;
		final boolean star;

		Flare(float start, float life, int color, Vec3 at, float size, boolean star) {
			super(start, life, color);
			this.at = at;
			this.size = size;
			this.star = star;
		}

		@Override
		void draw(Draw d, float age, float t) {
			float s = this.size * (0.5F + 0.5F * ease(t * 3));
			float a = (float) Math.pow(1 - t, 2);
			d.disc(BODY, this.at.x, this.at.y, this.at.z, s, this.color, a * 0.5F);
			d.disc(GLOW, this.at.x, this.at.y, this.at.z, s * 0.45, Vfx.lighten(this.color, 0.85F), a);
			if (this.star) {
				// Rays across and up the view, turning slightly as they fade.
				double vx = this.at.x - d.cx;
				double vy = this.at.y - d.cy;
				double vz = this.at.z - d.cz;
				double[][] bs = basis(vx, vy, vz);
				double spin = age * 0.05;
				for (int k = 0; k < 2; k++) {
					double ang = spin + k * Math.PI / 2;
					double dx = bs[0][0] * Math.cos(ang) + bs[1][0] * Math.sin(ang);
					double dy = bs[0][1] * Math.cos(ang) + bs[1][1] * Math.sin(ang);
					double dz = bs[0][2] * Math.cos(ang) + bs[1][2] * Math.sin(ang);
					double l = s * 2.4;
					double[][] pts = {{this.at.x - dx * l, this.at.y - dy * l, this.at.z - dz * l}, {this.at.x, this.at.y, this.at.z},
						{this.at.x + dx * l, this.at.y + dy * l, this.at.z + dz * l}};
					d.facing(GLOW, pts, new float[] {0, s * 0.22F, 0}, new float[] {0, a, 0}, new int[] {this.color, Vfx.lighten(this.color, 0.7F), this.color});
				}
			}
		}
	}

	/** A column of light rising from the ground, fading toward its top. */
	static final class Pillar extends Effect {
		final Vec3 base;
		final float radius;
		final float height;

		Pillar(float start, float life, int color, Vec3 base, float radius, float height) {
			super(start, life, color);
			this.base = base;
			this.radius = radius;
			this.height = height;
		}

		@Override
		void draw(Draw d, float age, float t) {
			float rise = ease(t / 0.18F);
			float a = t < 0.18F ? 1 : (float) Math.pow(1 - (t - 0.18F) / 0.82F, 1.3);
			double h = this.height * rise;
			int n = 8;
			double[][] pts = new double[n + 1][];
			float[] w = new float[n + 1];
			float[] wc = new float[n + 1];
			float[] al = new float[n + 1];
			int[] col = new int[n + 1];
			int[] colc = new int[n + 1];
			int hot = Vfx.lighten(this.color, 0.7F);
			for (int i = 0; i <= n; i++) {
				float s = i / (float) n;
				pts[i] = new double[] {this.base.x, this.base.y + h * s, this.base.z};
				w[i] = this.radius * 2 * (1 - 0.25F * s) * (1 - 0.3F * t);
				wc[i] = w[i] * 0.3F;
				al[i] = a * (float) Math.pow(1 - s, 1.1);
				col[i] = this.color;
				colc[i] = hot;
			}
			d.facing(BODY, pts, w, al, col);
			d.facing(GLOW, pts, wc, al, colc);
			double[] c = {this.base.x, this.base.y + 0.05, this.base.z};
			d.circle(BODY, c, new double[] {1, 0, 0}, new double[] {0, 0, 1}, this.radius * (1.2 + 0.8 * rise), this.radius * 0.5, this.color, a * 0.7F, 0, Math.PI * 2);
		}
	}

	/** A sigil on the ground: rings, ticks and a turning figure, drawn in light. */
	static final class Sigil extends Anchored {
		final float radius;
		final int pattern;
		final float spin;

		Sigil(float start, float life, int color, Vec3 at, Entity follow, float radius, int pattern, float spin) {
			super(start, life, color, at, follow);
			this.radius = radius;
			this.pattern = pattern;
			this.spin = spin;
		}

		@Override
		void draw(Draw d, float age, float t) {
			float appear = ease(t / 0.15F);
			float a = appear * (t > 0.7F ? 1 - smooth((t - 0.7F) / 0.3F) : 1);
			double r = this.radius * (0.82 + 0.18 * appear);
			Vec3 at = where();
			double[] c = {at.x, at.y + 0.05, at.z};
			double[] u = {1, 0, 0};
			double[] w = {0, 0, 1};
			double[] n = {0, 1, 0};
			double rot = Math.toRadians(this.spin * age);
			int hot = Vfx.lighten(this.color, 0.55F);
			double lw = Math.max(0.05, r * 0.035);
			d.circle(BODY, c, u, w, r, lw * 2.2, this.color, a, 0, Math.PI * 2);
			d.circle(GLOW, c, u, w, r, lw * 0.9, hot, a * 0.6F, 0, Math.PI * 2);
			d.circle(BODY, c, u, w, r * 0.8, lw * 1.4, hot, a * 0.9F, 0, Math.PI * 2);
			// Ticks between the rings, long and short in turn, turning with the sigil.
			int ticks = this.pattern == Vfx.SIGIL_CLOCK ? 12 : 24;
			for (int i = 0; i < ticks; i++) {
				double ang = rot + i * Math.PI * 2 / ticks;
				double in = (i % 2 == 0 || ticks == 12) ? 0.82 : 0.88;
				double[] p0 = {c[0] + Math.cos(ang) * r * in, c[1], c[2] + Math.sin(ang) * r * in};
				double[] p1 = {c[0] + Math.cos(ang) * r * 0.97, c[1], c[2] + Math.sin(ang) * r * 0.97};
				d.stroke(BODY, p0, p1, n, lw, this.color, a);
			}
			switch (this.pattern) {
				case Vfx.SIGIL_CLOCK -> {
					// Two hands, one slow and one quick.
					double h1 = -rot * 0.5;
					double h2 = -rot * 3;
					d.stroke(BODY, c, new double[] {c[0] + Math.cos(h1) * r * 0.45, c[1], c[2] + Math.sin(h1) * r * 0.45}, n, lw * 2.4, hot, a);
					d.stroke(BODY, c, new double[] {c[0] + Math.cos(h2) * r * 0.68, c[1], c[2] + Math.sin(h2) * r * 0.68}, n, lw * 1.6, hot, a);
					d.circle(BODY, c, u, w, r * 0.1, lw * 2, hot, a, 0, Math.PI * 2);
				}
				case Vfx.SIGIL_JUDGMENT -> {
					// Two squares, turning against each other, round an eye.
					polygon(d, c, n, r * 0.74, 4, -rot * 1.3, lw * 1.5, hot, a);
					polygon(d, c, n, r * 0.74, 4, -rot * 1.3 + Math.PI / 4, lw * 1.5, hot, a);
					d.circle(BODY, c, u, w, r * 0.3, lw * 1.6, this.color, a, 0, Math.PI * 2);
					d.circle(BODY, c, u, w, r * 0.12, lw * 2.2, hot, a, 0, Math.PI * 2);
				}
				case Vfx.SIGIL_STAR -> {
					// A five-pointed star drawn in one line.
					double[][] pts = new double[6][];
					for (int i = 0; i <= 5; i++) {
						double ang = -rot * 1.2 + i * Math.PI * 4 / 5 - Math.PI / 2;
						pts[i] = new double[] {c[0] + Math.cos(ang) * r * 0.76, c[1], c[2] + Math.sin(ang) * r * 0.76};
					}
					for (int i = 0; i < 5; i++) {
						d.stroke(BODY, pts[i], pts[i + 1], n, lw * 1.5, hot, a);
					}
				}
				default -> {
					// Two triangles, a six-pointed star, with a dot at each point.
					polygon(d, c, n, r * 0.76, 3, -rot * 1.5, lw * 1.5, hot, a);
					polygon(d, c, n, r * 0.76, 3, -rot * 1.5 + Math.PI, lw * 1.5, hot, a);
					for (int i = 0; i < 6; i++) {
						double ang = -rot * 1.5 + i * Math.PI / 3;
						double[] p = {c[0] + Math.cos(ang) * r * 0.76, c[1], c[2] + Math.sin(ang) * r * 0.76};
						d.circle(BODY, p, u, w, r * 0.06, lw * 1.2, this.color, a, 0, Math.PI * 2);
					}
				}
			}
			if (t < 0.2F) {
				// The moment it is drawn, a flash in its middle.
				d.disc(GLOW, c[0], c[1] + 0.3, c[2], r * 0.5 * (1 - t / 0.2F), hot, 0.6F * (1 - t / 0.2F));
			}
		}

		private static void polygon(Draw d, double[] c, double[] n, double r, int sides, double rot, double lw, int rgb, float a) {
			double[] prev = null;
			for (int i = 0; i <= sides; i++) {
				double ang = rot + i * Math.PI * 2 / sides;
				double[] p = {c[0] + Math.cos(ang) * r, c[1], c[2] + Math.sin(ang) * r};
				if (prev != null) {
					d.stroke(BODY, prev, p, n, lw, rgb, a);
				}
				prev = p;
			}
		}
	}

	/** A ribbon of light behind something moving, sampled from where it has been. */
	static final class Trail extends Effect {
		final Entity e;
		final float width;
		final List<double[]> samples = new ArrayList<>();

		Trail(float start, float life, int color, Entity e, float width) {
			super(start, life, color);
			this.e = e;
			this.width = width;
		}

		@Override
		void draw(Draw d, float age, float t) {
			Minecraft mc = Minecraft.getInstance();
			float now = this.start + age;
			if (!this.e.isRemoved() && t < 0.85F) {
				float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				Vec3 p = this.e.getPosition(partial).add(0, this.e.getBbHeight() * 0.55, 0);
				double[] last = this.samples.isEmpty() ? null : this.samples.get(this.samples.size() - 1);
				if (last == null || (p.x - last[0]) * (p.x - last[0]) + (p.y - last[1]) * (p.y - last[1]) + (p.z - last[2]) * (p.z - last[2]) > 0.01) {
					this.samples.add(new double[] {p.x, p.y, p.z, now});
				}
			}
			this.samples.removeIf(s -> now - s[3] > 7);
			int n = this.samples.size();
			if (n < 2) {
				return;
			}
			float fade = t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
			double[][] pts = new double[n][];
			float[] w = new float[n];
			float[] wc = new float[n];
			float[] al = new float[n];
			int[] col = new int[n];
			int[] colc = new int[n];
			int hot = Vfx.lighten(this.color, 0.7F);
			for (int i = 0; i < n; i++) {
				double[] s = this.samples.get(i);
				float k = 1 - (float) ((now - s[3]) / 7.0);
				pts[i] = s;
				w[i] = this.width * (0.2F + 0.8F * k);
				wc[i] = w[i] * 0.3F;
				al[i] = fade * k * k;
				col[i] = this.color;
				colc[i] = hot;
			}
			d.facing(BODY, pts, w, al, col);
			d.facing(GLOW, pts, wc, al, colc);
		}
	}

	/** Strands of light winding upward round a point. */
	static final class Helix extends Effect {
		final Vec3 base;
		final float radius;
		final float height;

		Helix(float start, float life, int color, Vec3 base, float radius, float height) {
			super(start, life, color);
			this.base = base;
			this.radius = radius;
			this.height = height;
		}

		@Override
		void draw(Draw d, float age, float t) {
			float rise = ease(t / 0.45F);
			float fade = t > 0.6F ? 1 - smooth((t - 0.6F) / 0.4F) : 1;
			int n = 28;
			int hot = Vfx.lighten(this.color, 0.6F);
			for (int strand = 0; strand < 3; strand++) {
				double[][] pts = new double[n + 1][];
				float[] w = new float[n + 1];
				float[] al = new float[n + 1];
				int[] col = new int[n + 1];
				for (int i = 0; i <= n; i++) {
					float s = i / (float) n;
					double ang = s * Math.PI * 3 + age * 0.22 + strand * Math.PI * 2 / 3;
					double r = this.radius * (1 - 0.35 * s);
					pts[i] = new double[] {this.base.x + Math.cos(ang) * r, this.base.y + this.height * rise * s, this.base.z + Math.sin(ang) * r};
					w[i] = 0.14F * (1 - 0.5F * s);
					al[i] = fade * (float) Math.sin(Math.PI * s);
					col[i] = strand == 0 ? hot : this.color;
				}
				d.facing(strand == 0 ? GLOW : BODY, pts, w, al, col);
			}
		}
	}
}
