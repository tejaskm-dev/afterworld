package dev.afterworld.combat;

import dev.afterworld.entity.MagicProjectile;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.item.gear.ConsumableItem;
import dev.afterworld.registry.AwEffects;
import dev.afterworld.registry.AwGear;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ranged weapons' shots: Moonshot arrows that curve toward foes, venom bolts whose poison
 * spreads, star bolts, the Repeater's thunderous hitscan, and bombs.
 */
public final class Projectiles {
	private record Tracked(AbstractArrow arrow, int tier, boolean venom) {
	}

	private static final List<Tracked> ARROWS = new ArrayList<>();

	private Projectiles() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Projectiles::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!(source.getDirectEntity() instanceof AbstractArrow arrow) || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			for (Tracked t : ARROWS) {
				if (t.arrow == arrow && t.venom) {
					LivingEntity owner = arrow.getOwner() instanceof LivingEntity l ? l : null;
					victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80 + 20 * t.tier, t.tier - 1), owner);
					for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(2.5),
						e -> e != victim && e != owner && (!(owner instanceof ServerPlayer p) || Combat.isFoe(p, e)))) {
						near.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), owner);
					}
					Fx.burst(level, Fx.dust(0x7AB040, 1.4F), victim.position().add(0, 1, 0), 24, 1.2, 0.02);
					Fx.sound(level, victim.position(), SoundEvents.SLIME_SQUISH, 1.0F, 0.8F);
				}
			}
		});
	}

	/** A fully drawn Duskpine arrow: it glows and curves toward the nearest foe in front of it. */
	public static void moonshot(AbstractArrow arrow, int tier) {
		ARROWS.add(new Tracked(arrow, tier, false));
	}

	/** A Bog-iron bolt: its poison spreads to whatever stands beside the one it hits. */
	public static void venom(AbstractArrow arrow, int tier) {
		ARROWS.add(new Tracked(arrow, tier, true));
	}

	private static void tick(MinecraftServer server) {
		if (ARROWS.isEmpty()) {
			return;
		}
		ARROWS.removeIf(t -> t.arrow.isRemoved() || t.arrow.tickCount > 200 || t.arrow.tickCount > 2 && t.arrow.getDeltaMovement().lengthSqr() < 0.01);
		for (Tracked t : ARROWS) {
			AbstractArrow a = t.arrow;
			if (!(a.level() instanceof ServerLevel level)) {
				continue;
			}
			if (t.venom) {
				if (a.tickCount % 2 == 0) {
					level.sendParticles(Fx.dust(0x7AB040, 0.9F), a.getX(), a.getY(), a.getZ(), 1, 0, 0, 0, 0);
				}
				continue;
			}
			level.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY(), a.getZ(), 1, 0, 0, 0, 0);
			if (!(a.getOwner() instanceof ServerPlayer p)) {
				continue;
			}
			Vec3 v = a.getDeltaMovement();
			double speed = v.length();
			Vec3 dir = v.normalize();
			LivingEntity best = null;
			double bestScore = 0.6;
			for (LivingEntity e : Combat.foes(level, p, a.position(), 10)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(a.position());
				double d = to.length();
				double dot = to.normalize().dot(dir);
				double score = dot - d * 0.01;
				if (dot > 0.6 && score > bestScore) {
					bestScore = score;
					best = e;
				}
			}
			if (best != null) {
				Vec3 to = best.position().add(0, best.getBbHeight() * 0.5, 0).subtract(a.position()).normalize();
				Vec3 nv = dir.add(to.scale(0.12 + 0.06 * t.tier)).normalize().scale(speed);
				a.setDeltaMovement(nv);
				a.needsSync = true;
			}
		}
	}

	// ---------------------------------------------------------------- the wand

	public static void starBolt(ServerLevel level, ServerPlayer p, int tier) {
		MagicProjectile bolt = new MagicProjectile(level, p, new ItemStack(AwGear.STAR_FRAGMENT), MagicProjectile.STAR, tier, 4.0F + 1.5F * tier);
		bolt.setPos(p.getX(), p.getEyeY() - 0.2, p.getZ());
		bolt.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, 1.6F, 0.4F);
		level.addFreshEntity(bolt);
		Fx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
		Fx.sound(level, p.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.5F, 1.8F);
	}

	/** Every tick of a magic projectile's flight. */
	public static void flight(ServerLevel level, MagicProjectile m) {
		if (m.kind == MagicProjectile.STAR || m.kind == MagicProjectile.SPLINTER) {
			level.sendParticles(Fx.dust(0xC08AFF, m.kind == MagicProjectile.STAR ? 1.1F : 0.7F), m.getX(), m.getY(), m.getZ(), 2, 0.05, 0.05, 0.05, 0);
			if (m.kind == MagicProjectile.STAR && m.getOwner() instanceof ServerPlayer p && m.tickCount > 3) {
				// Star bolts lean gently toward a foe close to their path.
				Vec3 v = m.getDeltaMovement();
				Vec3 dir = v.normalize();
				for (LivingEntity e : Combat.foes(level, p, m.position(), 5)) {
					Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(m.position()).normalize();
					if (to.dot(dir) > 0.5) {
						m.setDeltaMovement(dir.add(to.scale(0.2)).normalize().scale(v.length()));
						break;
					}
				}
			}
		} else if (m.fromCreature()) {
			switch (m.kind) {
				case MagicProjectile.FIRE_SPIT -> level.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY(), m.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
				case MagicProjectile.WEB -> level.sendParticles(Fx.dust(0xE8E8F0, 0.8F), m.getX(), m.getY(), m.getZ(), 1, 0.05, 0.05, 0.05, 0);
				case MagicProjectile.CHILL -> level.sendParticles(ParticleTypes.SNOWFLAKE, m.getX(), m.getY(), m.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
				default -> {
				}
			}
		} else if (m.tickCount % 2 == 0) {
			level.sendParticles(m.kind == MagicProjectile.FROST_BOMB ? ParticleTypes.SNOWFLAKE : m.kind == MagicProjectile.THUNDER_BOMB
				? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE, m.getX(), m.getY(), m.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/** Where a magic projectile lands. */
	public static void burst(ServerLevel level, MagicProjectile m, Vec3 at, Entity hit) {
		ServerPlayer p = m.getOwner() instanceof ServerPlayer sp ? sp : null;
		switch (m.kind) {
			case MagicProjectile.STAR -> {
				Fx.burst(level, Fx.dust(0xE0C8FF, 1.3F), at, 14, 0.2, 0.1);
				Fx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.5F);
				if (m.tier >= 3 && p != null) {
					// Tier III: the star splinters into three.
					for (int i = 0; i < 3; i++) {
						MagicProjectile s = new MagicProjectile(level, p, new ItemStack(AwGear.STAR_FRAGMENT), MagicProjectile.SPLINTER, 1, 3.0F);
						Vec3 back = m.getDeltaMovement().normalize().scale(-0.4);
						s.setPos(at.x + back.x, at.y + 0.3, at.z + back.z);
						double ang = i * Math.PI * 2 / 3 + level.getRandom().nextDouble();
						s.shoot(Math.cos(ang), 0.25, Math.sin(ang), 0.8F, 0.0F);
						level.addFreshEntity(s);
					}
				}
			}
			case MagicProjectile.SPLINTER -> Fx.burst(level, Fx.dust(0xE0C8FF, 0.8F), at, 6, 0.1, 0.05);
			case MagicProjectile.FIRE_SPIT -> {
				Fx.burst(level, ParticleTypes.FLAME, at, 12, 0.2, 0.05);
				Fx.sound(level, at, SoundEvents.FIRECHARGE_USE, 0.6F, 1.4F);
			}
			case MagicProjectile.WEB -> Fx.burst(level, Fx.dust(0xE8E8F0, 1.2F), at, 16, 0.4, 0.02);
			case MagicProjectile.CHILL -> {
				Fx.burst(level, ParticleTypes.SNOWFLAKE, at, 14, 0.3, 0.05);
				Fx.sound(level, at, SoundEvents.GLASS_BREAK, 0.5F, 1.6F);
			}
			case MagicProjectile.ROCK -> {
				Fx.burst(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()),
					at, 20, 0.4, 0.15);
				Fx.sound(level, at, SoundEvents.GLASS_BREAK, 0.8F, 0.8F);
			}
			case MagicProjectile.HARPOON -> Fx.sound(level, at, SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
			case MagicProjectile.SMOKE_BOMB -> {
				Fx.burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 60, 2.0, 0.02);
				Fx.burst(level, ParticleTypes.LARGE_SMOKE, at, 40, 2.0, 0.05);
				Fx.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.6F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5))) {
					if (p != null && Combat.isFoe(p, e)) {
						e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), p);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), p);
						if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == p) {
							mob.setTarget(null);
						}
					}
				}
				if (p != null && p.position().distanceTo(at) < 6) {
					p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0));
					p.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1));
				}
			}
			case MagicProjectile.FROST_BOMB -> {
				Fx.burst(level, ParticleTypes.SNOWFLAKE, at, 80, 2.0, 0.1);
				Fx.burst(level, ParticleTypes.ITEM_SNOWBALL, at, 30, 1.5, 0.2);
				Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1.5F, 0.7F);
				Fx.sound(level, at, SoundEvents.POWDER_SNOW_BREAK, 1.5F, 0.6F);
				if (p != null) {
					for (LivingEntity e : Combat.foes(level, p, at, 4.5)) {
						e.hurtServer(level, level.damageSources().freeze(), 4.0F);
						e.setTicksFrozen(e.getTicksRequiredToFreeze() + 140);
						e.addEffect(new MobEffectInstance(e instanceof BossMob ? MobEffects.SLOWNESS : AwEffects.STOPPED, e instanceof BossMob ? 60 : 40, 1), p);
					}
				}
			}
			case MagicProjectile.THUNDER_BOMB -> {
				var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setVisualOnly(true);
					bolt.snapTo(at.x, at.y, at.z, 0, 0);
					level.addFreshEntity(bolt);
				}
				Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, at, 60, 1.5, 0.3);
				Fx.shake(level, at, 14, 2.5F, 8);
				if (p != null) {
					for (LivingEntity e : Combat.foes(level, p, at, 4.0)) {
						e.hurtServer(level, level.damageSources().playerAttack(p), 9.0F);
						if (!(e instanceof BossMob)) {
							e.addEffect(new MobEffectInstance(AwEffects.STOPPED, 30, 0), p);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the repeater

	/** One round from the Brassmere Repeater: hitscan, loud, and it shoves what it hits. */
	public static void shot(ServerLevel level, ServerPlayer p, int tier) {
		Vec3 eye = p.getEyePosition();
		float spread = 0.02F;
		Vec3 dir = p.getLookAngle().add(level.getRandom().triangle(0, spread), level.getRandom().triangle(0, spread), level.getRandom().triangle(0, spread)).normalize();
		Vec3 end = eye.add(dir.scale(48));
		var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable() && e != p, 48 * 48);
		if (hit != null) {
			end = hit.getLocation();
			LivingEntity t = (LivingEntity) hit.getEntity();
			t.hurtServer(level, level.damageSources().playerAttack(p), 9.0F + 2.5F * tier);
			Vec3 push = dir.multiply(1, 0, 1).normalize().scale(0.8);
			t.push(push.x, 0.15, push.z);
			Fx.burst(level, ParticleTypes.CRIT, end, 14, 0.2, 0.3);
		} else if (block.getType() != HitResult.Type.MISS) {
			Fx.burst(level, ParticleTypes.SMOKE, end, 8, 0.1, 0.05);
		}
		Fx.line(level, ParticleTypes.SMOKE, eye.add(dir.scale(1.2)).add(0, -0.15, 0), end, 0.8);
		Fx.burst(level, ParticleTypes.FLAME, eye.add(dir.scale(1.0)).add(0, -0.15, 0), 4, 0.05, 0.05);
		Fx.sound(level, p.position(), SoundEvents.GENERIC_EXPLODE, 0.6F, 1.8F);
		Fx.sound(level, p.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.2F, 0.6F);
		// The kick.
		Vec3 back = dir.scale(-0.25);
		p.push(back.x, 0.02, back.z);
		p.syncVelocity = true;
	}

	// ---------------------------------------------------------------- bombs

	public static void bomb(ServerLevel level, ServerPlayer p, ConsumableItem.Kind kind) {
		int k = switch (kind) {
			case FROST_BOMB -> MagicProjectile.FROST_BOMB;
			case THUNDER_BOMB -> MagicProjectile.THUNDER_BOMB;
			default -> MagicProjectile.SMOKE_BOMB;
		};
		ItemStack look = new ItemStack(kind == ConsumableItem.Kind.FROST_BOMB ? AwGear.FROST_BOMB : kind == ConsumableItem.Kind.THUNDER_BOMB ? AwGear.THUNDER_BOMB : AwGear.SMOKE_BOMB);
		MagicProjectile b = new MagicProjectile(level, p, look, k, 1, 0);
		b.setPos(p.getX(), p.getEyeY() - 0.1, p.getZ());
		b.shootFromRotation(p, p.getXRot(), p.getYRot(), -10.0F, 1.1F, 0.5F);
		level.addFreshEntity(b);
		Fx.sound(level, p.position(), SoundEvents.SNOWBALL_THROW, 1.0F, 0.6F);
	}
}
