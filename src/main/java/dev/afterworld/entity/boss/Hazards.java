package dev.afterworld.entity.boss;

import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The great actors' attacks, as shapes on the ground. Every one is telegraphed first (a ring, a
 * line or a circle drawn in light) so a player who is watching can always get out of the way;
 * then it lands. They tick on the server and hurt only players who are actually playing.
 */
public final class Hazards {
	/** One running hazard; returns true when it is finished. */
	private interface Hazard {
		boolean tick(int age);
	}

	private record Live(Hazard hazard, int[] age) {
	}

	private static final List<Live> LIVE = new ArrayList<>();

	private Hazards() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = LIVE.size() - 1; i >= 0; i--) {
				Live l = LIVE.get(i);
				boolean done;
				try {
					done = l.hazard.tick(l.age[0]++);
				} catch (Exception e) {
					dev.afterworld.Afterworld.LOG.error("Afterworld hazard failed", e);
					done = true;
				}
				if (done) {
					LIVE.remove(i);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
	}

	private static void add(Hazard h) {
		LIVE.add(new Live(h, new int[1]));
	}

	/** Players a hazard can touch: alive, in the world, not watching from creative or spectator. */
	static List<ServerPlayer> targets(ServerLevel level, Vec3 at, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.position().distanceToSqr(at) < radius * radius) {
				out.add(p);
			}
		}
		return out;
	}

	private static void hit(ServerLevel level, LivingEntity source, ServerPlayer p, float damage, Vec3 push, double up) {
		if (p.hurtServer(level, source.damageSources().mobAttack(source), damage)) {
			p.push(push.x, up, push.z);
			p.syncVelocity = true;
		}
	}

	/** A dust ring drawn on the ground at {@code y}. */
	private static void groundRing(ServerLevel level, ParticleOptions p, Vec3 c, double r, int points) {
		Fx.ring(level, p, new Vec3(c.x, c.y + 0.15, c.z), r, points);
	}

	// ---------------------------------------------------------------- shapes

	/**
	 * A shockwave that rolls outward along the ground. Anyone standing on the ground as it passes
	 * is struck and thrown up; jumping over it is the answer.
	 */
	public static void wave(ServerLevel level, LivingEntity source, Vec3 center, double maxRadius, double speed, float damage, int color) {
		Set<UUID> struck = new HashSet<>();
		ParticleOptions dust = Fx.dust(color, 1.8F);
		dev.afterworld.util.Vfx.wave(level, center, (float) maxRadius, 0.9F, 1.3F, color, (int) Math.ceil((maxRadius - 1.0) / speed) + 1);
		add(age -> {
			double r = 1.0 + age * speed;
			int points = (int) Mth.clamp(r * 3, 8, 60);
			if (age % 2 == 0) {
				groundRing(level, dust, center, r, points);
				groundRing(level, ParticleTypes.CLOUD, center, r, points / 3);
			}
			for (ServerPlayer p : targets(level, center, r + 1.5)) {
				double d = Math.hypot(p.getX() - center.x, p.getZ() - center.z);
				if (Math.abs(d - r) < 0.9 && Math.abs(p.getY() - center.y) < 1.6 && p.onGround() && struck.add(p.getUUID())) {
					Vec3 out = new Vec3(p.getX() - center.x, 0, p.getZ() - center.z).normalize().scale(0.6);
					hit(level, source, p, damage, out, 0.55);
				}
			}
			return r >= maxRadius;
		});
	}

	/**
	 * Something falls from the sky onto a marked circle: the circle fills for {@code delay} ticks,
	 * then the strike lands, hurting everyone inside.
	 */
	public static void strike(ServerLevel level, LivingEntity source, Vec3 at, double radius, int delay, float damage, int color, ParticleOptions trail) {
		ParticleOptions dust = Fx.dust(color, 1.4F);
		dev.afterworld.util.Vfx.mark(level, at, (float) radius, color, delay);
		add(age -> {
			if (age < delay) {
				double fill = radius * (age + 1) / (double) delay;
				if (age % 6 == 0) {
					groundRing(level, dust, at, radius, (int) (radius * 5));
				}
				if (trail != null && age > delay - 12) {
					double h = (delay - age) * 1.6;
					Fx.burst(level, trail, at.add(0, h, 0), 4, 0.25, 0.02);
				}
				return false;
			}
			Fx.burst(level, ParticleTypes.POOF, at.add(0, 0.5, 0), 16, radius * 0.4, 0.05);
			Fx.burst(level, dust, at.add(0, 0.4, 0), 30, radius * 0.6, 0.2);
			dev.afterworld.util.Vfx.ring(level, at, (float) radius + 1.2F, 0.8F, 1.4F, color, 12);
			dev.afterworld.util.Vfx.pillar(level, at, (float) radius * 0.45F, 7.0F, color, 10);
			dev.afterworld.util.Vfx.flare(level, at.add(0, 0.6, 0), (float) radius * 0.9F, true, color, 9);
			Fx.hostileSound(level, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.3F);
			Fx.shake(level, at, 18, 2.5F, 8);
			for (ServerPlayer p : targets(level, at, radius + 0.4)) {
				if (Math.abs(p.getY() - at.y) < 3) {
					Vec3 out = new Vec3(p.getX() - at.x, 0, p.getZ() - at.z).normalize().scale(0.5);
					hit(level, source, p, damage, out, 0.4);
				}
			}
			return true;
		});
	}

	/** A line drawn on the ground from {@code from}, then a blast all along it. */
	public static void line(ServerLevel level, LivingEntity source, Vec3 from, Vec3 dir, double length, double width, int delay, float damage, int color) {
		Vec3 d = new Vec3(dir.x, 0, dir.z).normalize();
		Vec3 to = from.add(d.scale(length));
		Vec3 side = new Vec3(-d.z, 0, d.x).scale(width / 2);
		ParticleOptions dust = Fx.dust(color, 1.3F);
		add(age -> {
			if (age < delay) {
				if (age % 3 == 0) {
					Fx.line(level, dust, from.add(side).add(0, 0.15, 0), to.add(side).add(0, 0.15, 0), 0.6);
					Fx.line(level, dust, from.subtract(side).add(0, 0.15, 0), to.subtract(side).add(0, 0.15, 0), 0.6);
				}
				return false;
			}
			Fx.line(level, ParticleTypes.SONIC_BOOM, from.add(0, 0.8, 0), to.add(0, 0.8, 0), 2.0);
			Fx.line(level, dust, from.add(0, 0.5, 0), to.add(0, 0.5, 0), 0.4);
			Fx.hostileSound(level, from.lerp(to, 0.5), SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.3F);
			for (ServerPlayer p : targets(level, from.lerp(to, 0.5), length / 2 + width)) {
				Vec3 rel = p.position().subtract(from);
				double along = rel.x * d.x + rel.z * d.z;
				double across = Math.abs(rel.x * -d.z + rel.z * d.x);
				if (along > -0.5 && along < length + 0.5 && across < width / 2 + 0.3 && Math.abs(p.getY() - from.y) < 2.5) {
					hit(level, source, p, damage, d.scale(0.4), 0.3);
				}
			}
			return true;
		});
	}

	/**
	 * A marked circle that, once it settles, holds whatever stands in it for a while: {@code effect}
	 * runs on each player inside every ten ticks.
	 */
	public static void zone(ServerLevel level, Vec3 at, double radius, int delay, int duration, int color, Consumer<ServerPlayer> effect) {
		ParticleOptions dust = Fx.dust(color, 1.2F);
		add(age -> {
			if (age % 3 == 0) {
				groundRing(level, dust, at, radius, (int) (radius * 9));
			}
			if (age >= delay && age % 10 == 0) {
				Fx.burst(level, Fx.dust(color, 1.0F), at.add(0, 1, 0), (int) (radius * 6), radius * 0.5, 0.01);
				for (ServerPlayer p : targets(level, at, radius)) {
					if (Math.abs(p.getY() - at.y) < 3) {
						effect.accept(p);
					}
				}
			}
			return age >= delay + duration;
		});
	}

	/**
	 * Arms of light sweeping round a centre like the hands of a clock. Touching an arm hurts (once
	 * every half second); stepping or jumping over it is how to live.
	 */
	public static void blades(ServerLevel level, LivingEntity source, Vec3 center, double radius, int arms, double turnPerTick, int warmup, int duration,
			float damage, int color) {
		ParticleOptions dust = Fx.dust(color, 1.5F);
		Map<UUID, Integer> lastHit = new HashMap<>();
		double start = level.getRandom().nextDouble() * Math.PI * 2;
		add(age -> {
			boolean live = age >= warmup;
			double turn = start + Math.max(0, age - warmup) * turnPerTick;
			for (int a = 0; a < arms; a++) {
				double ang = turn + a * Math.PI * 2 / arms;
				Vec3 tip = center.add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
				if (live || age % 4 == 0) {
					Fx.line(level, live ? dust : Fx.dust(color, 0.7F), center.add(0, 0.5, 0), tip.add(0, 0.5, 0), live ? 0.7 : 1.4);
				}
				if (!live) {
					continue;
				}
				Vec3 d = tip.subtract(center).normalize();
				for (ServerPlayer p : targets(level, center, radius + 1)) {
					Vec3 rel = p.position().subtract(center);
					double along = rel.x * d.x + rel.z * d.z;
					double across = Math.abs(rel.x * -d.z + rel.z * d.x);
					boolean low = p.getY() - center.y < 1.0;
					if (along > 0 && along < radius && across < 0.75 && low && age - lastHit.getOrDefault(p.getUUID(), -100) > 10) {
						lastHit.put(p.getUUID(), age);
						hit(level, source, p, damage, new Vec3(-d.z, 0, d.x).scale(0.5), 0.35);
					}
				}
			}
			if (live && age % 20 == 0) {
				Fx.hostileSound(level, center, dev.afterworld.registry.AwSounds.BOSS_TICK, 1.5F, 0.7F);
			}
			return age >= warmup + duration;
		});
	}

	/**
	 * The ground erupts everywhere within {@code radius} except along a lit road. The road is drawn
	 * first; when the light runs out, anyone off it is struck.
	 */
	public static void road(ServerLevel level, LivingEntity source, Vec3 center, double radius, List<Vec3> path, double width, int delay, float damage,
			int roadColor, int dangerColor) {
		ParticleOptions road = Fx.dust(roadColor, 1.4F);
		ParticleOptions danger = Fx.dust(dangerColor, 1.0F);
		add(age -> {
			if (age < delay) {
				if (age % 3 == 0) {
					for (int i = 0; i + 1 < path.size(); i++) {
						Fx.line(level, road, path.get(i).add(0, 0.2, 0), path.get(i + 1).add(0, 0.2, 0), 0.5);
					}
					for (int k = 0; k < 24; k++) {
						double a = level.getRandom().nextDouble() * Math.PI * 2;
						double r = Math.sqrt(level.getRandom().nextDouble()) * radius;
						Vec3 p = center.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
						if (distanceToPath(p, path) > width) {
							level.sendParticles(danger, p.x, p.y, p.z, 1, 0.1, 0.0, 0.1, 0.0);
						}
					}
				}
				return false;
			}
			for (int k = 0; k < 90; k++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				double r = Math.sqrt(level.getRandom().nextDouble()) * radius;
				Vec3 p = center.add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
				if (distanceToPath(p, path) > width) {
					level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.2, 0.6, 0.2, 0.02);
				}
			}
			Fx.hostileSound(level, center, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
			Fx.shake(level, center, radius + 16, 3.0F, 10);
			for (ServerPlayer p : targets(level, center, radius)) {
				if (distanceToPath(p.position(), path) > width && Math.abs(p.getY() - center.y) < 4) {
					hit(level, source, p, damage, Vec3.ZERO, 0.6);
				}
			}
			return true;
		});
	}

	private static double distanceToPath(Vec3 p, List<Vec3> path) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < path.size(); i++) {
			Vec3 a = path.get(i);
			Vec3 b = path.get(i + 1);
			double abx = b.x - a.x;
			double abz = b.z - a.z;
			double len = abx * abx + abz * abz;
			double t = len == 0 ? 0 : Mth.clamp(((p.x - a.x) * abx + (p.z - a.z) * abz) / len, 0, 1);
			double dx = a.x + abx * t - p.x;
			double dz = a.z + abz * t - p.z;
			best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
		}
		return best;
	}
}
