package dev.afterworld.edict;

import dev.afterworld.player.Progress;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded areas where a rule is temporarily different: Weightlessness rifts and Stillness
 * bubbles. Each zone only touches entities inside its own small box. At Rank III a rift drags
 * foes into its eye and slams them down when it closes, and a Stillness bubble freezes creatures
 * outright, holding back the harm dealt them until the freeze ends.
 */
public final class Zones {
	public enum Kind {
		RIFT, STILL
	}

	public static final class Zone {
		public final Kind kind;
		public final ServerLevel level;
		public Vec3 center;
		public final double radius;
		public final long end;
		public final UUID owner;
		public final float power;
		public final boolean capstone;
		final Map<Projectile, Held> held = new IdentityHashMap<>();
		int age;

		Zone(Kind kind, ServerLevel level, Vec3 center, double radius, long end, UUID owner, float power, boolean capstone) {
			this.kind = kind;
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.end = end;
			this.owner = owner;
			this.power = power;
			this.capstone = capstone;
		}

		public boolean contains(Vec3 p) {
			return p.distanceToSqr(center) <= radius * radius;
		}

		AABB box() {
			return new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
		}
	}

	private record Held(boolean noGravity, Vec3 velocity, boolean ownedByCaster) {
	}

	/** A creature frozen by Stillness III, and the harm held back until it thaws. */
	private static final class Stilled {
		final ServerLevel level;
		final UUID owner;
		final long until;
		float stored;

		Stilled(ServerLevel level, UUID owner, long until) {
			this.level = level;
			this.owner = owner;
			this.until = until;
		}
	}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final Map<LivingEntity, Stilled> STILLED = new IdentityHashMap<>();
	/** Creatures slammed by a closing Weightlessness III rift: their next fall hurts twice as much. */
	private static final Map<LivingEntity, Long> SLAMMED = new IdentityHashMap<>();

	private Zones() {
	}

	public static Zone open(Kind kind, ServerLevel level, Vec3 center, double radius, int duration, UUID owner, float power) {
		return open(kind, level, center, radius, duration, owner, power, false);
	}

	public static Zone open(Kind kind, ServerLevel level, Vec3 center, double radius, int duration, UUID owner, float power, boolean capstone) {
		Zone z = new Zone(kind, level, center, radius, level.getGameTime() + duration, owner, power, capstone);
		ZONES.add(z);
		if (kind == Kind.STILL && capstone) {
			long until = level.getGameTime() + 60;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, z.box(), e -> e.isAlive() && z.contains(e.position()) && !(e instanceof Player)
				&& !e.getUUID().equals(owner) && !(e instanceof dev.afterworld.entity.boss.BossMob))) {
				STILLED.put(e, new Stilled(level, owner, until));
				Fx.burst(level, ParticleTypes.SNOWFLAKE, e.getBoundingBox().getCenter(), 10, 0.3, 0.02);
			}
		}
		return z;
	}

	/** Whether a creature is frozen by Stillness III (it can neither move nor strike). */
	public static boolean isStilled(Entity e) {
		return e instanceof LivingEntity l && STILLED.containsKey(l);
	}

	/** Stillness III: harm dealt to a frozen creature is held back. Returns true when it was stored. */
	public static boolean storeStilled(LivingEntity victim, float amount) {
		Stilled st = STILLED.get(victim);
		if (st == null || amount <= 0) {
			return false;
		}
		st.stored += amount;
		Fx.burst(st.level, Fx.dust(Edict.STILLNESS.color, 0.9F), victim.getBoundingBox().getCenter(), 6, 0.25, 0.02);
		Fx.sound(st.level, victim.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 0.6F + Math.min(1.2F, st.stored / 20.0F));
		return true;
	}

	/** Weightlessness III doubles the fall that follows its slam. */
	public static float fallScale(LivingEntity victim) {
		Long until = SLAMMED.get(victim);
		if (until == null) {
			return 1.0F;
		}
		SLAMMED.remove(victim);
		return until >= victim.level().getGameTime() ? 2.0F : 1.0F;
	}

	public static Zone riftAt(ServerLevel level, Vec3 pos) {
		for (Zone z : ZONES) {
			if (z.kind == Kind.RIFT && z.level == level && z.contains(pos)) {
				return z;
			}
		}
		return null;
	}

	public static boolean anyRift(ServerLevel level, Vec3 pos) {
		return riftAt(level, pos) != null;
	}

	public static void clear() {
		for (Zone z : ZONES) {
			release(z);
		}
		ZONES.clear();
		STILLED.clear();
		SLAMMED.clear();
	}

	public static void tick(MinecraftServer server) {
		if (!STILLED.isEmpty()) {
			tickStilled();
		}
		if (!SLAMMED.isEmpty() && server.getTickCount() % 20 == 0) {
			SLAMMED.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getValue() < e.getKey().level().getGameTime());
		}
		if (ZONES.isEmpty()) {
			return;
		}
		Iterator<Zone> it = ZONES.iterator();
		while (it.hasNext()) {
			Zone z = it.next();
			long now = z.level.getGameTime();
			if (now >= z.end) {
				release(z);
				if (z.kind == Kind.RIFT && z.capstone) {
					slam(z, now);
				}
				it.remove();
				Fx.sound(z.level, z.center, z.kind == Kind.STILL ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.4F);
				continue;
			}
			z.age++;
			if (z.kind == Kind.STILL) {
				tickStill(z, now);
			} else if (z.age % 2 == 0) {
				tickRift(z, now);
			}
		}
	}

	private static void tickRift(Zone z, long now) {
		ServerLevel level = z.level;
		for (Entity e : level.getEntities((Entity) null, z.box(), Entity::isAlive)) {
			if (!z.contains(e.position())) {
				if (e instanceof Projectile p) {
					restore(z, p);
				}
				continue;
			}
			if (e instanceof Player player) {
				if (!player.isSpectator()) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
					player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 10, 1, false, false));
				}
			} else if (e instanceof LivingEntity living) {
				if (isHeavy(living)) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 1, false, false));
					EdictSystem.onRiftTouchHeavy(living, z);
				} else {
					living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 8, z.power > 1.2F ? 1 : 0, false, false));
					living.resetFallDistance();
					if (z.capstone && !e.getUUID().equals(z.owner) && !MobMark.of(e).ownedBy(z.owner)) {
						// Drawn toward the eye of the rift.
						Vec3 pull = z.center.subtract(e.position());
						if (pull.lengthSqr() > 0.25) {
							living.setDeltaMovement(living.getDeltaMovement().scale(0.8).add(pull.normalize().scale(0.22)));
							living.syncVelocity = true;
						}
					}
				}
				if (inStill(level, e.position())) {
					suspend(living, now, z.owner);
				}
			} else if (e instanceof Projectile p) {
				if (!z.held.containsKey(p)) {
					z.held.put(p, new Held(p.isNoGravity(), Vec3.ZERO, false));
					p.setNoGravity(true);
				}
			} else if (e instanceof ItemEntity item) {
				Vec3 v = item.getDeltaMovement();
				item.setDeltaMovement(v.x * 0.9, 0.05, v.z * 0.9);
			}
		}
		if (z.age % 6 == 0) {
			Fx.ring(level, Fx.dust(Edict.WEIGHTLESSNESS.color, 1.1F), z.center, z.radius, 18);
			Fx.burst(level, ParticleTypes.END_ROD, z.center, 3, z.radius * 0.5, 0.01);
		}
	}

	private static void tickStill(Zone z, long now) {
		ServerLevel level = z.level;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(z.owner);
		if (owner != null && owner.level() == level) {
			z.center = owner.position().add(0, 1, 0);
		}
		for (Entity e : level.getEntities((Entity) null, z.box(), Entity::isAlive)) {
			boolean inside = z.contains(e.position());
			if (e instanceof Projectile p) {
				if (inside) {
					if (!z.held.containsKey(p)) {
						boolean mine = p.getOwner() != null && p.getOwner().getUUID().equals(z.owner);
						z.held.put(p, new Held(p.isNoGravity(), p.getDeltaMovement(), mine));
						p.setNoGravity(true);
						if (mine && owner != null && z.held.size() >= 3) {
							Progress.codex(owner, "combo.frozen_volley");
						}
					}
					p.setDeltaMovement(Vec3.ZERO);
				}
				continue;
			}
			if (!inside || e.getUUID().equals(z.owner) || !(e instanceof LivingEntity living) || e instanceof Player) {
				continue;
			}
			if (z.age % 5 == 0) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 3, false, false));
			}
			Vec3 v = living.getDeltaMovement();
			living.setDeltaMovement(v.x * 0.25, Math.min(v.y, 0.0) * 0.5, v.z * 0.25);
			if (anyRift(level, e.position())) {
				suspend(living, now, z.owner);
			}
		}
		if (z.age % 5 == 0) {
			Fx.ring(level, Fx.dust(Edict.STILLNESS.color, 1.0F), z.center.subtract(0, 0.9, 0), z.radius, 20);
		}
		if (z.age % 20 == 0) {
			Fx.sound(level, z.center, SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 0.6F);
		}
	}

	/** A closing Weightlessness III rift throws everything it held into the ground. */
	private static void slam(Zone z, long now) {
		ServerLevel level = z.level;
		AABB box = z.box().inflate(z.radius * 0.3);
		int slammed = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Player) && !e.getUUID().equals(z.owner)
			&& !MobMark.of(e).ownedBy(z.owner) && !isHeavy(e))) {
			e.removeEffect(MobEffects.LEVITATION);
			e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -2.4, e.getDeltaMovement().z * 0.2);
			e.syncVelocity = true;
			SLAMMED.put(e, now + 80);
			slammed++;
		}
		if (slammed > 0) {
			Fx.burst(level, ParticleTypes.REVERSE_PORTAL, z.center, 40, z.radius * 0.5, 0.2);
			Fx.sound(level, z.center, SoundEvents.MACE_SMASH_AIR, 1.0F, 0.6F);
		}
	}

	/** Stillness III: the frozen stay frozen, then thaw and take what was held back, half again as hard. */
	private static void tickStilled() {
		Iterator<Map.Entry<LivingEntity, Stilled>> it = STILLED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Stilled> entry = it.next();
			LivingEntity e = entry.getKey();
			Stilled st = entry.getValue();
			long now = st.level.getGameTime();
			if (!e.isAlive() || e.level() != st.level) {
				it.remove();
				continue;
			}
			if (now < st.until) {
				e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
				if (e instanceof net.minecraft.world.entity.Mob mob) {
					mob.getNavigation().stop();
				}
				if (now % 5 == 0) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8, 9, false, false));
					Fx.burst(st.level, Fx.dust(Edict.STILLNESS.color, 0.7F), e.getBoundingBox().getCenter(), 2, 0.3, 0);
				}
				continue;
			}
			it.remove();
			if (st.stored > 0) {
				ServerPlayer owner = st.level.getServer().getPlayerList().getPlayer(st.owner);
				var source = owner != null ? st.level.damageSources().indirectMagic(owner, owner) : st.level.damageSources().magic();
				e.hurtServer(st.level, source, st.stored * 1.5F);
				Fx.burst(st.level, ParticleTypes.CRIT, e.getBoundingBox().getCenter(), 16, 0.4, 0.4);
				Fx.burst(st.level, Fx.dust(Edict.STILLNESS.color, 1.4F), e.getBoundingBox().getCenter(), 20, 0.5, 0.1);
				Fx.sound(st.level, e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.7F);
			}
		}
	}

	private static void suspend(LivingEntity living, long now, UUID owner) {
		MobMark m = MobMark.of(living);
		if (!m.has(MobMark.SUSPENDED) || m.expires() < now + 40) {
			MobMark.set(living, m.with(MobMark.SUSPENDED).withExpires(Math.max(m.expires(), now + 80)));
			Fx.burst((ServerLevel) living.level(), ParticleTypes.ENCHANTED_HIT, living.position().add(0, living.getBbHeight() / 2, 0), 8, 0.3, 0.05);
			ServerPlayer p = ((ServerLevel) living.level()).getServer().getPlayerList().getPlayer(owner);
			if (p != null) {
				Progress.codex(p, "combo.suspended_sentence");
			}
		}
	}

	private static boolean inStill(ServerLevel level, Vec3 pos) {
		for (Zone z : ZONES) {
			if (z.kind == Kind.STILL && z.level == level && z.contains(pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isHeavy(LivingEntity e) {
		return e.getMaxHealth() >= 80 || MobMark.has(e, MobMark.ANCHORED);
	}

	private static void restore(Zone z, Projectile p) {
		Held h = z.held.remove(p);
		if (h != null && p.isAlive()) {
			p.setNoGravity(h.noGravity);
			if (h.ownedByCaster) {
				p.setDeltaMovement(h.velocity);
			}
		}
	}

	private static void release(Zone z) {
		for (Map.Entry<Projectile, Held> entry : z.held.entrySet()) {
			Projectile p = entry.getKey();
			Held h = entry.getValue();
			if (p.isAlive()) {
				p.setNoGravity(h.noGravity);
				p.setDeltaMovement(h.ownedByCaster ? h.velocity : new Vec3(0, -0.1, 0));
			}
		}
		z.held.clear();
	}
}
