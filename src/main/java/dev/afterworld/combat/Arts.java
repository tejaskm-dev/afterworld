package dev.afterworld.combat;

import dev.afterworld.entity.CrystalShard;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.item.WeaponGrowth;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwEffects;
import dev.afterworld.registry.AwParticles;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Acts;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Scheduler;
import dev.afterworld.util.Vfx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Arts of Afterworld's weapons. Each is one press of the use key: big, readable and quick to
 * learn, with a cooldown so it punctuates a fight rather than replacing it.
 */
public final class Arts {
	public enum Art {
		CINDER_CLEAVE("cinder_cleave", 160, 0xFF7A2E),
		CRYSTAL_FAN("crystal_fan", 100, 0xD8B8FF),
		REEL("reel", 120, 0xA8C070),
		STEP_BEHIND("step_behind", 120, 0x9A78F0),
		COIL_SLAM("coil_slam", 180, 0xE8C060),
		FROST_LANCE("frost_lance", 140, 0x9FE6FF),
		TOLL("toll", 240, 0xE8A33D),
		STOPPED_MOMENT("stopped_moment", 400, 0xF2E3A0),
		WAY_OF_LIGHT("way_of_light", 300, 0xFFF0B0),
		RIPOSTE("riposte", 100, 0xE0E8FF),
		FANG_FLURRY("fang_flurry", 120, 0xC8E8A0),
		CHAKRAM("chakram", 80, 0xBFE8FF),
		RALLY("rally", 400, 0xFF8A3D),
		SPORE_LASH("spore_lash", 100, 0x9AD050),
		HARPOON("harpoon", 100, 0xB8C8D8),
		THUNDERCLAP("thunderclap", 140, 0x9FD8FF),
		LANTERN_CUTS("lantern_cuts", 180, 0x7AFFD8),
		CRIMSON_REAP("crimson_reap", 200, 0xE0203A),
		AVALANCHE("avalanche", 220, 0xBFF0FF),
		CHAIN_PULL("chain_pull", 140, 0xE8C080),
		GLYPH_BEAM("glyph_beam", 240, 0xFFD060);

		public final String id;
		public final int cooldown;
		/** The colour of its light: the arcs, flares and rings it makes. */
		public final int color;

		Art(String id, int cooldown, int color) {
			this.id = id;
			this.cooldown = cooldown;
			this.color = color;
		}
	}

	/** Targets marked by Step Behind: their next hit from the marker lands twice as hard. */
	private static final Map<UUID, Long> MARKED = new HashMap<>();

	private Arts() {
	}

	/** Performs an Art. Returns false (and costs nothing) when it has nothing to act on. */
	public static boolean perform(Art art, ServerLevel level, ServerPlayer p, ItemStack stack) {
		int tier = WeaponGrowth.tier(stack);
		return switch (art) {
			case CINDER_CLEAVE -> cinderCleave(level, p);
			case CRYSTAL_FAN -> crystalFan(level, p);
			case REEL -> reel(level, p);
			case STEP_BEHIND -> stepBehind(level, p);
			case COIL_SLAM -> coilSlam(level, p);
			case FROST_LANCE -> frostLance(level, p);
			case TOLL -> toll(level, p);
			case STOPPED_MOMENT -> stoppedMoment(level, p);
			case WAY_OF_LIGHT -> wayOfLight(level, p);
			case RIPOSTE -> riposteStance(level, p, tier);
			case FANG_FLURRY -> fangFlurry(level, p, tier);
			case CHAKRAM -> chakram(level, p, tier);
			case RALLY -> rally(level, p, tier);
			case SPORE_LASH -> sporeLash(level, p, tier);
			case HARPOON -> harpoon(level, p, tier);
			case THUNDERCLAP -> thunderclap(level, p, tier);
			case LANTERN_CUTS -> lanternCuts(level, p);
			case CRIMSON_REAP -> crimsonReap(level, p);
			case AVALANCHE -> avalanche(level, p);
			case CHAIN_PULL -> chainPull(level, p);
			case GLYPH_BEAM -> glyphBeam(level, p);
		};
	}

	/** The small thing each weapon does on every hit. */
	public static void passive(Art art, ServerLevel level, ServerPlayer p, LivingEntity target) {
		switch (art) {
			case CINDER_CLEAVE -> target.igniteForSeconds(3.0F);
			case CRYSTAL_FAN -> {
				if (p.getRandom().nextFloat() < 0.25F) {
					for (LivingEntity e : foes(level, p, target.position(), 2.2)) {
						if (e != target) {
							e.hurtServer(level, level.damageSources().playerAttack(p), 2.0F);
						}
					}
					Fx.burst(level, AwParticles.of(AwParticles.SHARD, 0xE0C8FF), target.position().add(0, 1, 0), 12, 0.4, 0.2);
					Vfx.flare(level, target.position().add(0, 1, 0), 0.7F, true, Art.CRYSTAL_FAN.color, 8);
					Vfx.ring(level, target.position().add(0, 0.1, 0), 2.2F, 0.35F, 0, Art.CRYSTAL_FAN.color, 10);
				}
			}
			case REEL -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), p);
			case STEP_BEHIND -> {
				Long until = MARKED.remove(target.getUUID());
				float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
				if (until != null && until > level.getGameTime()) {
					Scheduler.after(1, () -> {
						if (target.isAlive()) {
							target.setInvulnerableTime(0);
							target.hurtServer(level, level.damageSources().playerAttack(p), base);
							Fx.burst(level, ParticleTypes.CRIT, target.position().add(0, 1, 0), 20, 0.4, 0.3);
							Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
						}
					});
				}
				// The echo: a fraction of the blow lands again a moment later.
				Scheduler.after(20, () -> {
					if (target.isAlive()) {
						target.setInvulnerableTime(0);
						target.hurtServer(level, level.damageSources().magic(), base * 0.3F);
						Fx.burst(level, AwParticles.of(AwParticles.INK, 0x6A4AB0), target.position().add(0, 1, 0), 10, 0.3, 0.05);
						Vfx.flare(level, target.position().add(0, target.getBbHeight() * 0.6, 0), 0.6F, false, Art.STEP_BEHIND.color, 8);
					}
				});
			}
			case COIL_SLAM -> {
				Vec3 d = target.position().subtract(p.position()).multiply(1, 0, 1).normalize();
				target.push(d.x * 0.6, 0.15, d.z * 0.6);
			}
			case FROST_LANCE -> {
				target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 40));
				Fx.burst(level, ParticleTypes.SNOWFLAKE, target.position().add(0, 1, 0), 8, 0.3, 0.05);
			}
			case TOLL -> {
				if (p.getRandom().nextFloat() < 0.15F && !(target instanceof BossMob)) {
					target.addEffect(new MobEffectInstance(AwEffects.STOPPED, 15, 0), p);
					Fx.sound(level, target.position(), SoundEvents.BELL_BLOCK, 0.8F, 1.4F);
					Vfx.ring(level, target.position().add(0, target.getBbHeight() + 0.2, 0), new Vec3(0, 1, 0), 0.7F, 0.18F, Art.TOLL.color, 15);
				}
			}
			case STOPPED_MOMENT -> {
				if (target.hasEffect(AwEffects.STOPPED)) {
					float bonus = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F;
					Scheduler.after(1, () -> {
						if (target.isAlive()) {
							target.setInvulnerableTime(0);
							target.hurtServer(level, level.damageSources().playerAttack(p), bonus);
						}
					});
				}
			}
			case WAY_OF_LIGHT -> p.heal(1.0F);
			case RIPOSTE -> {
				// Every third cut in a row on the same foe flourishes into a second, lighter one.
				if (PlayerState.of(p).combo % 3 == 0) {
					echoHit(level, p, target, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F, ParticleTypes.ENCHANTED_HIT);
				}
			}
			case FANG_FLURRY -> {
				// From behind, the fang bites deeper.
				Vec3 facing = Vec3.directionFromRotation(0, target.getYRot()).multiply(1, 0, 1).normalize();
				Vec3 toMe = p.position().subtract(target.position()).multiply(1, 0, 1).normalize();
				if (facing.dot(toMe) < -0.3) {
					echoHit(level, p, target, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F, ParticleTypes.CRIT);
				}
			}
			case CHAKRAM -> {
				if (p.getRandom().nextFloat() < 0.25F) {
					for (LivingEntity e : foes(level, p, target.position(), 5)) {
						if (e != target) {
							Vfx.beam(level, target.position().add(0, 1, 0), e.position().add(0, 1, 0), 0.16F, 0, Art.CHAKRAM.color, 6);
							e.hurtServer(level, level.damageSources().playerAttack(p), 3.0F);
							break;
						}
					}
				}
			}
			case RALLY -> p.setAbsorptionAmount(Math.min(4.0F, p.getAbsorptionAmount() + 1.0F));
			case SPORE_LASH -> {
				if (p.getRandom().nextFloat() < 0.25F) {
					target.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0), p);
				}
			}
			case HARPOON -> {
				Vec3 d = p.position().subtract(target.position()).multiply(1, 0, 1).normalize();
				if (!(target instanceof BossMob)) {
					target.push(d.x * 0.35, 0.05, d.z * 0.35);
				}
			}
			case THUNDERCLAP -> {
				if (PlayerState.of(p).combo % 4 == 0) {
					for (LivingEntity e : foes(level, p, target.position(), 6)) {
						if (e != target) {
							Fx.line(level, ParticleTypes.ELECTRIC_SPARK, target.position().add(0, 1, 0), e.position().add(0, 1, 0), 0.6);
							Vfx.beam(level, target.position().add(0, 1, 0), e.position().add(0, 1, 0), 0.14F, 0.45F, Art.THUNDERCLAP.color, 6);
							e.hurtServer(level, level.damageSources().playerAttack(p), 4.0F);
							Fx.sound(level, e.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3F, 2.0F);
							break;
						}
					}
				}
			}
			case LANTERN_CUTS -> {
				target.igniteForSeconds(3.0F);
				Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, target.position().add(0, 1, 0), 6, 0.3, 0.02);
			}
			case CRIMSON_REAP -> p.heal((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.15F);
			case AVALANCHE -> target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 50));
			case CHAIN_PULL -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), p);
			case GLYPH_BEAM -> {
				if (p.getRandom().nextFloat() < 0.25F) {
					Fx.burst(level, Fx.dust(0xFFD060, 1.2F), target.position().add(0, 1, 0), 10, 0.3, 0.05);
					echoHit(level, p, target, 3.0F, ParticleTypes.WAX_OFF);
				}
			}
		}
	}

	/** A second blow a tick after the first, through the invulnerability window. */
	private static void echoHit(ServerLevel level, ServerPlayer p, LivingEntity target, float amount, net.minecraft.core.particles.SimpleParticleType fx) {
		Scheduler.after(1, () -> {
			if (target.isAlive()) {
				target.setInvulnerableTime(0);
				target.hurtServer(level, level.damageSources().playerAttack(p), amount);
				Fx.burst(level, fx, target.position().add(0, target.getBbHeight() * 0.6, 0), 10, 0.3, 0.2);
			}
		});
	}

	// ---------------------------------------------------------------- helpers

	/** Who an Art may hit: monsters, and anything that is hunting the wielder. Never players or pets. */
	static boolean isFoe(ServerPlayer p, Entity e) {
		return Combat.isFoe(p, e);
	}

	static List<LivingEntity> foes(ServerLevel level, ServerPlayer p, Vec3 at, double radius) {
		return Combat.foes(level, p, at, radius);
	}

	/** The first living thing along the wielder's gaze, within {@code range}. */
	static LivingEntity sighted(ServerLevel level, ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var block = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
			net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable() && e != p, range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}

	private static void launch(ServerPlayer p, Vec3 v) {
		p.setDeltaMovement(v);
		p.syncVelocity = true;
		p.fallDistance = 0;
	}

	/** Runs {@code step} on each of the next {@code ticks} ticks. */
	private static void every(int ticks, java.util.function.IntConsumer step) {
		for (int i = 1; i <= ticks; i++) {
			int n = i;
			Scheduler.after(i, () -> step.accept(n));
		}
	}

	/** An Art's force: its base, half the weapon's damage, and a quarter more for each tier the weapon has grown. */
	private static float strength(ServerPlayer p, float base) {
		ItemStack held = p.getMainHandItem();
		int tier = WeaponGrowth.grows(held) ? WeaponGrowth.tier(held) : 1;
		return (base + (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F) * (1.0F + 0.25F * (tier - 1));
	}

	// ---------------------------------------------------------------- the arts

	/** Emberbrand: leap, then come down in a ring of fire. */
	private static boolean cinderCleave(ServerLevel level, ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		launch(p, new Vec3(look.x * 0.7, 0.62, look.z * 0.7));
		Fx.sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.7F);
		Acts.play(p, Acts.LEAP, 16);
		Vfx.trail(level, p, 0.7F, Art.CINDER_CLEAVE.color, 24);
		Vfx.ring(level, p.position(), 1.8F, 0.4F, 0, Art.CINDER_CLEAVE.color, 8);
		boolean[] done = {false};
		every(30, n -> {
			if (done[0] || !p.isAlive()) {
				return;
			}
			p.fallDistance = 0;
			Fx.burst(level, ParticleTypes.FLAME, p.position(), 3, 0.2, 0.02);
			if (n > 4 && (p.onGround() || n == 30)) {
				done[0] = true;
				Vec3 at = p.position();
				Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.9F, 1.2F);
				Fx.sound(level, at, SoundEvents.BLAZE_SHOOT, 1.2F, 0.6F);
				Fx.shake(level, at, 16, 3.0F, 10);
				Acts.play(p, Acts.SLAM, 10);
				Vfx.slash(level, p, 88, 2.3F, 150, Art.CINDER_CLEAVE.color, 8);
				Vfx.ring(level, at, 5.0F, 1.0F, 1.6F, Art.CINDER_CLEAVE.color, 16);
				Vfx.ring(level, at, 2.8F, 0.5F, 0, 0xFFD27A, 10);
				Vfx.flare(level, at.add(0, 0.6, 0), 2.2F, true, 0xFFB060, 10);
				for (int r = 1; r <= 4; r++) {
					double rr = r;
					Scheduler.after(r, () -> Fx.ring(level, ParticleTypes.FLAME, at.add(0, 0.2, 0), rr, (int) (rr * 10)));
				}
				for (LivingEntity e : foes(level, p, at, 4.5)) {
					if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 8.0F))) {
						e.igniteForSeconds(4.0F);
						e.push(0, 0.55, 0);
					}
				}
			}
		});
		return true;
	}

	/** Shardsong Glaive: a fan of crystal shards. */
	private static boolean crystalFan(ServerLevel level, ServerPlayer p) {
		for (int i = -2; i <= 2; i++) {
			CrystalShard shard = new CrystalShard(level, p, strength(p, 3.0F));
			shard.setPos(p.getX(), p.getEyeY() - 0.15, p.getZ());
			shard.shootFromRotation(p, p.getXRot(), p.getYRot() + i * 9.0F, 0.0F, 1.9F, 0.5F);
			level.addFreshEntity(shard);
			Vfx.trail(level, shard, 0.28F, Art.CRYSTAL_FAN.color, 16);
		}
		Acts.play(p, Acts.THROW, 10);
		Vfx.wedge(level, p.position(), p.getLookAngle(), 7.0F, 24.0F, Art.CRYSTAL_FAN.color, 10);
		Vfx.flare(level, p.getEyePosition().add(p.getLookAngle().scale(0.8)), 0.8F, true, Art.CRYSTAL_FAN.color, 6);
		Fx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
		Fx.sound(level, p.position(), SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.4F);
		return true;
	}

	/** Lurker's Hook: hook what you look at and haul it in; sneak to haul yourself to it instead. */
	private static boolean reel(ServerLevel level, ServerPlayer p) {
		LivingEntity t = sighted(level, p, 16);
		if (t == null) {
			return false;
		}
		Vec3 from = p.position().add(0, 1, 0);
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
		Vfx.beam(level, from.add(0, 0.3, 0), to, 0.12F, 0, Art.REEL.color, 9);
		Vfx.flare(level, to, 0.7F, false, Art.REEL.color, 8);
		Acts.play(p, Acts.LASH, 10);
		Fx.sound(level, t.position(), SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
		Fx.sound(level, p.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 1.2F, 0.6F);
		Vec3 d = to.subtract(from);
		double dist = d.length();
		if (p.isShiftKeyDown() || t instanceof BossMob || t.getBbWidth() > 2.0) {
			launch(p, d.normalize().scale(Math.min(2.4, 0.4 + dist * 0.16)).add(0, 0.35, 0));
			Vfx.trail(level, p, 0.5F, Art.REEL.color, 14);
		} else {
			Vec3 pull = d.normalize().scale(-Math.min(2.0, 0.3 + dist * 0.14));
			t.setDeltaMovement(pull.x, 0.35 + Math.min(0.4, dist * 0.02), pull.z);
			t.syncVelocity = true;
			if (isFoe(p, t)) {
				t.hurtServer(level, level.damageSources().playerAttack(p), 4.0F);
				t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), p);
			}
		}
		return true;
	}

	/** Echo Edge: step out of an echo behind your target; the next blow lands twice. */
	private static boolean stepBehind(ServerLevel level, ServerPlayer p) {
		LivingEntity t = sighted(level, p, 14);
		if (t == null) {
			return false;
		}
		Vec3 facing = Vec3.directionFromRotation(0, t.getYRot()).multiply(1, 0, 1).normalize();
		Vec3 behind = t.position().subtract(facing.scale(t.getBbWidth() * 0.5 + 1.6));
		Vec3 from = p.position();
		if (!level.noCollision(p, p.getBoundingBox().move(behind.subtract(from)))) {
			behind = t.position().add(from.subtract(t.position()).normalize().scale(t.getBbWidth() * 0.5 + 1.2));
		}
		p.teleportTo(behind.x, behind.y, behind.z);
		Vec3 look = t.getEyePosition().subtract(p.getEyePosition());
		float yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		p.connection.teleport(behind.x, behind.y, behind.z, yaw, pitch);
		Fx.line(level, AwParticles.of(AwParticles.INK, 0x6A4AB0), from.add(0, 1, 0), behind.add(0, 1, 0), 0.8);
		Fx.burst(level, ParticleTypes.REVERSE_PORTAL, from.add(0, 1, 0), 30, 0.3, 0.05);
		Vfx.beam(level, from.add(0, 1, 0), behind.add(0, 1, 0), 0.22F, 0.12F, Art.STEP_BEHIND.color, 8);
		Vfx.pillar(level, from, 0.45F, 2.2F, Art.STEP_BEHIND.color, 14);
		Vfx.flare(level, behind.add(0, 1, 0), 1.0F, true, Art.STEP_BEHIND.color, 8);
		Acts.play(p, Acts.SWEEP, 9);
		Fx.sound(level, behind, SoundEvents.ENDERMAN_TELEPORT, 0.9F, 1.4F);
		MARKED.put(t.getUUID(), level.getGameTime() + 60);
		MARKED.values().removeIf(until -> until < level.getGameTime());
		return true;
	}

	/** Gearspring Maul: the spring lets go and everything nearby is thrown into the air. */
	private static boolean coilSlam(ServerLevel level, ServerPlayer p) {
		Fx.sound(level, p.position(), AwSounds.BOSS_TICK, 1.0F, 1.6F);
		Fx.sound(level, p.position(), SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 0.6F);
		Acts.play(p, Acts.SLAM, 14);
		Vfx.sigil(level, p.position(), 2.2F, Vfx.SIGIL_CLOCK, -9.0F, Art.COIL_SLAM.color, 12, p);
		Scheduler.after(6, () -> {
			if (!p.isAlive()) {
				return;
			}
			Vec3 at = p.position();
			Fx.ring(level, AwParticles.of(AwParticles.GEAR, 0xC9962E), at.add(0, 0.3, 0), 3.0, 24);
			Fx.ring(level, AwParticles.of(AwParticles.GEAR, 0xE8C060), at.add(0, 0.3, 0), 5.0, 36);
			Fx.burst(level, ParticleTypes.POOF, at.add(0, 0.3, 0), 14, 0.9, 0.05);
			Vfx.ring(level, at, 5.5F, 1.0F, 1.3F, Art.COIL_SLAM.color, 14);
			Vfx.ring(level, at, 3.0F, 0.5F, 0, 0xFFF0C0, 9);
			Vfx.flare(level, at.add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)).add(0, 0.3, 0), 1.8F, true, Art.COIL_SLAM.color, 9);
			Fx.sound(level, at, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
			Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.7F, 1.4F);
			Fx.shake(level, at, 16, 3.5F, 10);
			for (LivingEntity e : foes(level, p, at, 5.0)) {
				if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 6.0F))) {
					double up = e instanceof BossMob ? 0.0 : 1.0;
					Vec3 out = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.4);
					e.setDeltaMovement(out.x, up, out.z);
					e.syncVelocity = true;
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
				}
			}
		});
		return true;
	}

	/** Rimefang Spear: a freezing lunge that runs through everything in its way. */
	private static boolean frostLance(ServerLevel level, ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		launch(p, new Vec3(look.x * 1.7, 0.12, look.z * 1.7));
		Fx.sound(level, p.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 1.4F);
		Acts.play(p, Acts.THRUST, 12);
		Vfx.trail(level, p, 0.8F, Art.FROST_LANCE.color, 14);
		Vfx.flare(level, p.getEyePosition().add(look.scale(1.0)), 0.9F, true, Art.FROST_LANCE.color, 7);
		Fx.sound(level, p.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.2F);
		Set<UUID> struck = new HashSet<>();
		every(9, n -> {
			if (!p.isAlive()) {
				return;
			}
			p.fallDistance = 0;
			Fx.burst(level, ParticleTypes.SNOWFLAKE, p.position().add(0, 1, 0), 6, 0.3, 0.02);
			for (LivingEntity e : foes(level, p, p.position(), 2.0)) {
				if (struck.add(e.getUUID()) && e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 6.0F))) {
					e.setTicksFrozen(e.getTicksRequiredToFreeze() + 140);
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), p);
					Fx.burst(level, ParticleTypes.ITEM_SNOWBALL, e.position().add(0, 1, 0), 16, 0.3, 0.1);
					Vfx.flare(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.1F, true, Art.FROST_LANCE.color, 9);
				}
			}
		});
		return true;
	}

	/** Bellhammer: one toll, and everything around you stops to listen. */
	private static boolean toll(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position();
		Fx.sound(level, at, AwSounds.BOSS_BELL, 2.5F, 1.1F);
		Fx.sound(level, at, SoundEvents.BELL_RESONATE, 1.5F, 0.8F);
		Fx.shake(level, at, 20, 3.0F, 12);
		Acts.play(p, Acts.SLAM, 12);
		for (int i = 0; i < 3; i++) {
			int k = i;
			Scheduler.after(2 + i * 4, () -> {
				Vfx.ring(level, at, 7.5F, 0.8F - k * 0.15F, k == 0 ? 1.2F : 0, Art.TOLL.color, 16);
				Vfx.ring(level, at.add(0, 1.3, 0), new Vec3(0, 1, 0), 5.0F + k, 0.25F, Vfx.lighten(Art.TOLL.color, 0.4F), 12);
			});
		}
		Vfx.flare(level, at.add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)).add(0, 0.4, 0), 2.0F, true, Art.TOLL.color, 10);
		Vfx.flash(p, 0.22F, Art.TOLL.color, 10);
		for (LivingEntity e : foes(level, p, at, 7.0)) {
			if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 5.0F))) {
				Vec3 out = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.8);
				e.push(out.x, 0.3, out.z);
				if (!(e instanceof BossMob)) {
					e.addEffect(new MobEffectInstance(AwEffects.STOPPED, 40, 0), p);
				}
			}
		}
		return true;
	}

	/** The Second Hand: everything hostile around you is caught in a stopped moment. */
	private static boolean stoppedMoment(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position();
		for (int i = 0; i < 3; i++) {
			int n = i;
			Scheduler.after(i * 3, () -> Fx.sound(level, at, AwSounds.BOSS_TICK, 1.5F, 0.6F + n * 0.25F));
		}
		Fx.runeRing(level, at.add(0, 0.2, 0), 0xE8D7A0, 10, 30);
		Acts.play(p, Acts.INVOKE, 18);
		Vfx.sigil(level, at, 10.0F, Vfx.SIGIL_CLOCK, 1.5F, Art.STOPPED_MOMENT.color, 80, null);
		Vfx.ring(level, at, 10.0F, 0.6F, 1.5F, Art.STOPPED_MOMENT.color, 14);
		Vfx.flash(p, 0.35F, Art.STOPPED_MOMENT.color, 16);
		for (LivingEntity e : foes(level, p, at, 10.0)) {
			if (e instanceof BossMob) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), p);
			} else {
				e.addEffect(new MobEffectInstance(AwEffects.STOPPED, 80, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), p);
			}
			Fx.burst(level, ParticleTypes.END_ROD, e.position().add(0, e.getBbHeight() * 0.6, 0), 6, 0.3, 0.0);
			Vfx.ring(level, e.position().add(0, e.getBbHeight() + 0.25, 0), new Vec3(0, 1, 0), Math.max(0.5F, e.getBbWidth() * 0.6F), 0.12F, Art.STOPPED_MOMENT.color, 80);
			Vfx.beam(level, at.add(0, 0.2, 0), e.position().add(0, e.getBbHeight() * 0.5, 0), 0.08F, 0, Art.STOPPED_MOMENT.color, 10);
		}
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1), p);
		return true;
	}

	/** The Pilgrim's Staff: glide along a road of light, scattering foes; it mends you when it ends. */
	private static boolean wayOfLight(ServerLevel level, ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		double y = p.getY() + 0.4;
		Fx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.4F);
		Acts.play(p, Acts.POINT, 40);
		Vfx.trail(level, p, 1.0F, Art.WAY_OF_LIGHT.color, 42);
		Vfx.beam(level, p.position().add(0, 0.15, 0), p.position().add(look.scale(30)).add(0, 0.15, 0), 0.9F, 0, Art.WAY_OF_LIGHT.color, 40);
		Set<UUID> struck = new HashSet<>();
		every(40, n -> {
			if (!p.isAlive()) {
				return;
			}
			p.fallDistance = 0;
			double lift = Math.max(-0.08, (y - p.getY()) * 0.4);
			launch(p, new Vec3(look.x * 0.75, lift, look.z * 0.75));
			Fx.burst(level, ParticleTypes.END_ROD, p.position().add(0, 0.1, 0), 4, 0.3, 0.0);
			Fx.burst(level, Fx.dust(0xFFF0B0, 1.2F), p.position().add(0, 0.05, 0), 3, 0.4, 0.0);
			for (LivingEntity e : foes(level, p, p.position(), 2.2)) {
				if (struck.add(e.getUUID()) && e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 5.0F))) {
					Vec3 side = new Vec3(-look.z, 0, look.x).scale(e.position().subtract(p.position()).dot(new Vec3(-look.z, 0, look.x)) > 0 ? 0.9 : -0.9);
					e.push(side.x, 0.35, side.z);
				}
			}
			if (n == 40) {
				Fx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
				Vfx.helix(level, p.position(), 1.3F, 2.6F, Art.WAY_OF_LIGHT.color, 30);
				Vfx.ring(level, p.position(), 6.0F, 0.4F, 0, Art.WAY_OF_LIGHT.color, 14);
				for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6))) {
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), p);
				}
			}
		});
		return true;
	}

	// ---------------------------------------------------------------- the second armoury

	/** Moonsilver Rapier: a heartbeat of stillness. Strike me now and I strike back three times as hard; wait, and I lunge. */
	private static boolean riposteStance(ServerLevel level, ServerPlayer p, int tier) {
		PlayerState s = PlayerState.of(p);
		long until = level.getGameTime() + 12 + 2 * tier;
		s.riposteUntil = until;
		Fx.sound(level, p.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 1.6F);
		Fx.ring(level, Fx.dust(0xD8E0F0, 0.9F), p.position().add(0, 1, 0), 1.2, 8);
		int stance = (int) (until - level.getGameTime());
		Acts.play(p, Acts.BRACE, stance);
		Vfx.ring(level, p.position().add(0, 1.0, 0), new Vec3(0, 1, 0), 1.3F, 0.18F, Art.RIPOSTE.color, stance);
		Scheduler.after(stance, () -> {
			if (s.riposteUntil != until || !p.isAlive()) {
				return;
			}
			s.riposteUntil = 0;
			Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
			launch(p, new Vec3(look.x * 1.4, 0.1, look.z * 1.4));
			Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.7F);
			Acts.play(p, Acts.THRUST, 10);
			Vfx.trail(level, p, 0.5F, Art.RIPOSTE.color, 10);
			Set<UUID> struck = new HashSet<>();
			every(6, n -> {
				Fx.burst(level, Fx.dust(0xD8E0F0, 0.8F), p.position().add(0, 1, 0), 3, 0.2, 0.0);
				for (LivingEntity e : foes(level, p, p.position(), 2.0)) {
					if (struck.add(e.getUUID())) {
						e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 4.0F));
						Fx.burst(level, ParticleTypes.ENCHANTED_HIT, e.position().add(0, 1, 0), 12, 0.3, 0.2);
					}
				}
			});
		});
		return true;
	}

	/** The counter: called when a blow lands inside the Riposte stance. */
	public static void riposte(ServerLevel level, ServerPlayer p, LivingEntity enemy) {
		float dmg = strength(p, 8.0F) * 1.5F;
		Vec3 at = enemy.position().add(0, enemy.getBbHeight() * 0.6, 0);
		Scheduler.after(2, () -> {
			if (!enemy.isAlive()) {
				return;
			}
			enemy.setInvulnerableTime(0);
			enemy.hurtServer(level, level.damageSources().playerAttack(p), dmg);
			if (!(enemy instanceof BossMob)) {
				enemy.addEffect(new MobEffectInstance(AwEffects.STOPPED, 20, 0), p);
			}
			Fx.burst(level, ParticleTypes.ENCHANTED_HIT, at, 30, 0.3, 0.4);
			Vfx.flare(level, at, 1.6F, true, Art.RIPOSTE.color, 10);
		});
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Acts.play(p, Acts.SWEEP, 9);
		Vfx.slash(level, p, 32, 2.4F, 170, Art.RIPOSTE.color, 9);
		Vfx.flash(p, 0.25F, 0xF0F4FF, 8);
		Fx.sound(level, p.position(), SoundEvents.ANVIL_PLACE, 0.5F, 2.0F);
		Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.2F);
		dev.afterworld.aspect.Aspects.addResonance(p, 12);
		dev.afterworld.quest.Quests.onParry(p);
	}

	/** Wayfarer's Fang: dart forward and open whatever you reach with a flurry of quick cuts. */
	private static boolean fangFlurry(ServerLevel level, ServerPlayer p, int tier) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		launch(p, new Vec3(look.x * 1.3, 0.08, look.z * 1.3));
		Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.9F);
		int cuts = 3 + 2 * tier;
		Acts.play(p, Acts.THRUST, 6);
		Vfx.trail(level, p, 0.5F, Art.FANG_FLURRY.color, 8);
		Scheduler.after(5, () -> Acts.play(p, Acts.FLURRY, cuts * 2 + 2));
		Scheduler.after(5, () -> {
			if (!p.isAlive()) {
				return;
			}
			List<LivingEntity> near = foes(level, p, p.position().add(look), 2.8);
			for (int i = 0; i < cuts; i++) {
				int n = i;
				Scheduler.after(i * 2, () -> {
					for (LivingEntity e : near) {
						if (e.isAlive()) {
							e.setInvulnerableTime(0);
							e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 1.0F) * 0.45F);
							Fx.burst(level, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 1, 0.2, 0);
						}
					}
					Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.7F, 1.4F + n * 0.08F);
					Vfx.slash(level, p, n % 2 == 0 ? 38 : -38, 1.6F, n % 2 == 0 ? 130 : -130, Art.FANG_FLURRY.color, 6);
				});
			}
		});
		return true;
	}

	/** Mirrorglass Chakram: thrown, it leaps from foe to foe and comes home. */
	private static boolean chakram(ServerLevel level, ServerPlayer p, int tier) {
		LivingEntity first = sighted(level, p, 18);
		if (first == null || !isFoe(p, first)) {
			List<LivingEntity> ahead = Combat.cone(level, p, 14, 25);
			first = ahead.isEmpty() ? null : ahead.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).get();
		}
		List<LivingEntity> chain = new java.util.ArrayList<>();
		if (first != null) {
			chain.add(first);
			int bounces = 3 + 2 * (tier - 1);
			while (chain.size() < bounces) {
				LivingEntity last = chain.get(chain.size() - 1);
				LivingEntity next = null;
				double best = 64;
				for (LivingEntity e : foes(level, p, last.position(), 8)) {
					double d = e.distanceToSqr(last);
					if (!chain.contains(e) && d < best) {
						best = d;
						next = e;
					}
				}
				if (next == null) {
					break;
				}
				chain.add(next);
			}
		}
		Fx.sound(level, p.position(), SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.6F);
		Acts.play(p, Acts.THROW, 10);
		Vec3[] prev = {p.position().add(0, 1.2, 0)};
		for (int i = 0; i < chain.size(); i++) {
			LivingEntity e = chain.get(i);
			Scheduler.after(1 + i * 3, () -> {
				if (!e.isAlive()) {
					return;
				}
				Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
				Fx.line(level, AwParticles.of(AwParticles.SHARD, 0xBFE8FF), prev[0], at, 0.7);
				Vfx.beam(level, prev[0], at, 0.2F, 0, Art.CHAKRAM.color, 6);
				Vfx.ring(level, at, at.subtract(prev[0]), 0.55F, 0.16F, Art.CHAKRAM.color, 6);
				Vfx.flare(level, at, 0.8F, true, Art.CHAKRAM.color, 7);
				prev[0] = at;
				e.setInvulnerableTime(0);
				e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 4.0F));
				Fx.sound(level, at, SoundEvents.GLASS_BREAK, 0.5F, 1.8F);
			});
		}
		if (chain.isEmpty()) {
			// Nothing to strike: it sails out and back along your gaze, cutting anything in the way.
			Vec3 from = p.position().add(0, 1.2, 0);
			Vec3 to = from.add(p.getLookAngle().scale(12));
			Fx.line(level, AwParticles.of(AwParticles.SHARD, 0xBFE8FF), from, to, 1.0);
			Vfx.beam(level, from, to, 0.2F, 0, Art.CHAKRAM.color, 8);
			for (LivingEntity e : foes(level, p, from.add(to).scale(0.5), 6.5)) {
				e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 3.0F));
			}
		}
		Scheduler.after(2 + chain.size() * 3, () -> {
			Vfx.beam(level, prev[0], p.position().add(0, 1.2, 0), 0.16F, 0, Art.CHAKRAM.color, 6);
			Fx.sound(level, p.position(), SoundEvents.ITEM_PICKUP, 0.8F, 1.4F);
		});
		return true;
	}

	/** Ashen Warbanner: plant it, and for a while everyone fighting beside you fights better. */
	private static boolean rally(ServerLevel level, ServerPlayer p, int tier) {
		Vec3 at = p.position();
		int ticks = 200 + 60 * (tier - 1);
		Fx.sound(level, at, SoundEvents.RAID_HORN.value(), 1.2F, 1.3F);
		Fx.sound(level, at, SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
		Fx.burst(level, ParticleTypes.LAVA, at, 12, 0.3, 0.1);
		Acts.play(p, Acts.PLANT, 16);
		Scheduler.after(8, () -> {
			Vfx.pillar(level, at, 0.55F, 6.0F, Art.RALLY.color, ticks);
			Vfx.ring(level, at, 8.0F, 0.6F, 1.0F, Art.RALLY.color, 16);
			Vfx.flare(level, at.add(0, 0.5, 0), 1.6F, true, Art.RALLY.color, 10);
		});
		for (int t = 0; t < ticks; t += 10) {
			int k = t;
			Scheduler.after(t, () -> {
				Fx.column(level, AwParticles.of(AwParticles.EMBER, 0xE87A3D), at, 6, 0.15, 3.0);
				if (k % 40 == 0 && k > 0) {
					Vfx.ring(level, at, 8.0F, 0.35F, 0.5F, Art.RALLY.color, 18);
				}
				if (k % 20 != 0) {
					return;
				}
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(8))) {
					if (e.position().distanceToSqr(at) > 64) {
						continue;
					}
					if (e instanceof Player || Combat.isSummonOf(e, p) || e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() == p) {
						e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, tier - 1, true, true), p);
						e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, true, true), p);
						e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, true), p);
					} else if (isFoe(p, e)) {
						e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, tier - 1), p);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), p);
					}
				}
			});
		}
		return true;
	}

	/** Sporelash Whip: a crack that bites everything in a line, drags it closer and leaves spores behind. */
	private static boolean sporeLash(ServerLevel level, ServerPlayer p, int tier) {
		Vec3 eye = p.position().add(0, 1.0, 0);
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 end = eye.add(dir.scale(8));
		Fx.line(level, Fx.dust(0x8AB04A, 1.1F), eye, end, 0.6);
		Acts.play(p, Acts.LASH, 10);
		Scheduler.after(3, () -> {
			Vfx.beam(level, eye.add(0, 0.2, 0), end, 0.24F, 0.35F, Art.SPORE_LASH.color, 7);
			Vfx.flare(level, end, 1.0F, true, Art.SPORE_LASH.color, 8);
		});
		Fx.sound(level, p.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2F, 0.6F);
		Fx.sound(level, end, SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
		for (LivingEntity e : foes(level, p, eye.add(dir.scale(4)), 5)) {
			Vec3 rel = e.position().add(0, 1, 0).subtract(eye);
			double along = rel.dot(dir);
			if (along < 0 || along > 8.5 || rel.subtract(dir.scale(along)).length() > 1.6) {
				continue;
			}
			if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 4.0F))) {
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, tier - 1), p);
				if (!(e instanceof BossMob)) {
					Vec3 pull = dir.scale(-0.7);
					e.setDeltaMovement(pull.x, 0.25, pull.z);
					e.syncVelocity = true;
				}
			}
		}
		Vec3 cloud = end.add(0, -0.8, 0);
		for (int t = 0; t < 100; t += 10) {
			Scheduler.after(t, () -> {
				Fx.burst(level, Fx.dust(0x8AB04A, 1.6F), cloud, 10, 1.4, 0.0);
				Vfx.ring(level, cloud.add(0, -0.1, 0), 2.6F, 0.3F, 0.4F, Art.SPORE_LASH.color, 12);
				for (LivingEntity e : foes(level, p, cloud, 2.6)) {
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), p);
				}
			});
		}
		return true;
	}

	/** Skyhook Harpoon: spear a creature and haul it in, or spear a wall and haul yourself there. */
	private static boolean harpoon(ServerLevel level, ServerPlayer p, int tier) {
		double range = 24 + 8 * (tier - 1);
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var block = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
			net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		LivingEntity t = sighted(level, p, range);
		Fx.sound(level, p.position(), SoundEvents.CROSSBOW_SHOOT, 1.2F, 0.6F);
		Acts.play(p, Acts.THROW, 8);
		if (t != null) {
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vfx.beam(level, eye.add(0, -0.3, 0), to, 0.1F, 0, Art.HARPOON.color, 12);
			Vfx.flare(level, to, 0.8F, true, Art.HARPOON.color, 7);
			Fx.sound(level, to, SoundEvents.CHAIN_PLACE, 1.2F, 0.8F);
			if (isFoe(p, t)) {
				t.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 3.0F));
			}
			if (t instanceof BossMob || t.getBbWidth() > 1.5 || p.isShiftKeyDown()) {
				yank(level, p, to, 14);
			} else {
				Vec3 d = eye.subtract(to);
				Vec3 pull = d.normalize().scale(Math.min(2.2, 0.4 + d.length() * 0.14));
				t.setDeltaMovement(pull.x, 0.35 + Math.min(0.4, d.length() * 0.02), pull.z);
				t.syncVelocity = true;
			}
			return true;
		}
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			Vec3 to = block.getLocation();
			Vfx.beam(level, eye.add(0, -0.3, 0), to, 0.1F, 0, Art.HARPOON.color, 16);
			Vfx.flare(level, to, 0.7F, true, Art.HARPOON.color, 8);
			Fx.burst(level, ParticleTypes.CRIT, to, 8, 0.1, 0.1);
			Fx.sound(level, to, SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
			yank(level, p, to, 20);
			return true;
		}
		return false;
	}

	/** Pulls the player toward {@code to} over a few ticks; no fall damage for a while after. */
	public static void yank(ServerLevel level, ServerPlayer p, Vec3 to, int maxTicks) {
		PlayerState.of(p).safeFallUntil = level.getGameTime() + maxTicks + 60;
		Vfx.trail(level, p, 0.5F, Art.HARPOON.color, maxTicks);
		boolean[] done = {false};
		every(maxTicks, n -> {
			if (done[0] || !p.isAlive()) {
				return;
			}
			Vec3 d = to.subtract(p.position().add(0, 0.9, 0));
			if (d.length() < 1.6) {
				done[0] = true;
				launch(p, p.getDeltaMovement().scale(0.2).add(0, 0.42, 0));
				return;
			}
			launch(p, d.normalize().scale(Math.min(1.5, 0.5 + d.length() * 0.12)).add(0, 0.08, 0));
			if (n % 3 == 0) {
				Vfx.beam(level, p.position().add(0, 1, 0), to, 0.08F, 0, Art.HARPOON.color, 3);
			}
		});
	}

	/** Thunderdrum Gauntlets: clap, and thunder rolls out in front of you. */
	private static boolean thunderclap(ServerLevel level, ServerPlayer p, int tier) {
		Vec3 at = p.position();
		Fx.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.4F);
		Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.6F);
		Fx.shake(level, at, 18, 3.0F, 10);
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		Acts.play(p, Acts.CLAP, 10);
		Vfx.wedge(level, at, look, 7.5F, 35.0F, Art.THUNDERCLAP.color, 10);
		Vfx.flare(level, p.getEyePosition().add(look.scale(0.7)).add(0, -0.3, 0), 1.5F, true, Art.THUNDERCLAP.color, 8);
		Vfx.flash(p, 0.2F, Art.THUNDERCLAP.color, 8);
		for (int r = 1; r <= 7; r++) {
			double rr = r;
			Scheduler.after(r, () -> {
				Vec3 c = at.add(look.scale(rr)).add(0, 1, 0);
				Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, c, (int) (rr * 2), rr * 0.3, 0.1);
				Fx.burst(level, ParticleTypes.CLOUD, c, 3, rr * 0.2, 0.02);
			});
		}
		int bolts = 1 + tier;
		for (LivingEntity e : Combat.cone(level, p, 7.5, 35)) {
			if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 6.0F))) {
				Vfx.beam(level, p.getEyePosition().add(look.scale(0.7)).add(0, -0.3, 0), e.position().add(0, e.getBbHeight() * 0.6, 0), 0.16F, 0.5F, Art.THUNDERCLAP.color, 7);
				Vec3 out = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(1.1);
				e.push(out.x, 0.35, out.z);
				if (!(e instanceof BossMob)) {
					e.addEffect(new MobEffectInstance(AwEffects.STOPPED, 15 + 10 * tier, 0), p);
				}
				if (bolts-- > 0) {
					var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setVisualOnly(true);
						bolt.snapTo(e.getX(), e.getY(), e.getZ(), 0, 0);
						level.addFreshEntity(bolt);
					}
				}
			}
		}
		return true;
	}

	/** Hitodama: three draws too quick to see; each leaves a ghost lantern that bursts a heartbeat later. */
	private static boolean lanternCuts(ServerLevel level, ServerPlayer p) {
		List<LivingEntity> targets = new java.util.ArrayList<>(Combat.cone(level, p, 11, 40));
		targets.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		Fx.sound(level, p.position(), SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.6F);
		if (targets.isEmpty()) {
			launch(p, new Vec3(look.x * 1.6, 0.05, look.z * 1.6));
			Acts.play(p, Acts.SWEEP, 10);
			Vfx.trail(level, p, 0.6F, Art.LANTERN_CUTS.color, 12);
			Vec3 from = p.position();
			for (int i = 1; i <= 3; i++) {
				lantern(level, p, from.add(look.scale(i * 2.0)).add(0, 1, 0), 20 + i * 4);
			}
			return true;
		}
		int n = Math.min(3, targets.size());
		for (int i = 0; i < n; i++) {
			LivingEntity e = targets.get(i);
			int cut = i;
			Scheduler.after(i * 4, () -> {
				if (!e.isAlive() || !p.isAlive()) {
					return;
				}
				Vec3 facing = Vec3.directionFromRotation(0, e.getYRot()).multiply(1, 0, 1).normalize();
				Vec3 behind = e.position().subtract(facing.scale(e.getBbWidth() * 0.5 + 1.2));
				if (!level.noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) {
					behind = p.position();
				}
				Fx.line(level, ParticleTypes.SOUL_FIRE_FLAME, p.position().add(0, 1, 0), behind.add(0, 1, 0), 1.0);
				Vfx.beam(level, p.position().add(0, 1, 0), behind.add(0, 1, 0), 0.3F, 0, Art.LANTERN_CUTS.color, 7);
				Vfx.pillar(level, p.position(), 0.4F, 2.0F, Art.LANTERN_CUTS.color, 10);
				p.teleportTo(level, behind.x, behind.y, behind.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
				Acts.play(p, Acts.SWEEP, 8);
				Vfx.slash(level, e.position().add(0, e.getBbHeight() * 0.55, 0), p.getYRot() + 180, 0, cut % 2 == 0 ? 30 : -30, 1.6F, 160, 0.6F, Art.LANTERN_CUTS.color, 7, null);
				e.setInvulnerableTime(0);
				e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 7.0F));
				Fx.burst(level, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 2, 0.3, 0);
				Fx.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
				lantern(level, p, e.position().add(0, e.getBbHeight() * 0.7, 0), 20);
			});
		}
		return true;
	}

	private static void lantern(ServerLevel level, ServerPlayer p, Vec3 at, int fuse) {
		for (int t = 0; t < fuse; t += 3) {
			Scheduler.after(t, () -> Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, at, 2, 0.1, 0.01));
		}
		Vfx.flare(level, at, 0.45F, false, Art.LANTERN_CUTS.color, fuse);
		Scheduler.after(fuse, () -> {
			Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, at, 20, 0.6, 0.12);
			Fx.burst(level, ParticleTypes.SOUL, at, 8, 0.4, 0.05);
			Vfx.flare(level, at, 1.6F, true, Art.LANTERN_CUTS.color, 10);
			Vfx.ring(level, at.add(0, -0.6, 0), 2.6F, 0.45F, 0.6F, Art.LANTERN_CUTS.color, 12);
			Fx.sound(level, at, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 1.4F);
			for (LivingEntity e : foes(level, p, at, 2.6)) {
				e.setInvulnerableTime(0);
				e.hurtServer(level, level.damageSources().magic(), strength(p, 3.0F));
				e.igniteForSeconds(3.0F);
			}
		});
	}

	/** Crimson Requiem: one red sweep. It drinks what it cuts, and what it kills gets up to fight for you. */
	private static boolean crimsonReap(ServerLevel level, ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 at = p.position();
		Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
		Fx.sound(level, at, SoundEvents.WITHER_SHOOT, 0.5F, 1.4F);
		Acts.play(p, Acts.SWEEP, 12);
		Scheduler.after(3, () -> {
			Vfx.slash(level, at.add(0, 1.0, 0), p.getYRot(), 0, 4, 4.2F, 185, 1.3F, Art.CRIMSON_REAP.color, 11, null);
			Vfx.slash(level, at.add(0, 1.05, 0), p.getYRot(), 0, 4, 2.6F, 185, 0.7F, 0xFF6070, 9, null);
			Vfx.wedge(level, at, look, 5.5F, 90.0F, Art.CRIMSON_REAP.color, 10);
		});
		for (int i = -6; i <= 6; i += 2) {
			double a = Math.atan2(look.z, look.x) + i * Math.PI / 12;
			for (double r = 1.5; r <= 5; r += 1.4) {
				Fx.burst(level, Fx.dust(0xC0203A, 1.3F), at.add(Math.cos(a) * r, 1.0, Math.sin(a) * r), 1, 0.05, 0);
			}
		}
		float drunk = 0;
		for (LivingEntity e : Combat.cone(level, p, 5.5, 90)) {
			float before = e.getHealth();
			if (e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 9.0F))) {
				drunk += Math.max(0, before - e.getHealth());
				if (!e.isAlive() && !(e instanceof BossMob)) {
					raiseThrall(level, p, e.position());
				}
			}
		}
		if (drunk > 0) {
			p.heal(drunk * 0.3F);
			Fx.burst(level, Fx.dust(0xC0203A, 1.0F), p.position().add(0, 1, 0), 16, 0.4, 0.05);
			Vfx.helix(level, p.position(), 0.9F, 2.2F, Art.CRIMSON_REAP.color, 20);
		}
		return true;
	}

	private static void raiseThrall(ServerLevel level, ServerPlayer p, Vec3 at) {
		var thrall = net.minecraft.world.entity.EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (thrall == null) {
			return;
		}
		thrall.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
		thrall.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		thrall.setCustomName(net.minecraft.network.chat.Component.translatable("art.afterworld.crimson_reap.thrall").withColor(0xC0203A));
		Combat.bindSummon(thrall, p, 400);
		level.addFreshEntity(thrall);
		Fx.burst(level, Fx.dust(0xC0203A, 1.5F), at.add(0, 1, 0), 24, 0.4, 0.1);
		Vfx.pillar(level, at, 0.5F, 3.0F, Art.CRIMSON_REAP.color, 16);
		Vfx.sigil(level, at, 1.4F, Vfx.SIGIL_STAR, 4.0F, Art.CRIMSON_REAP.color, 20, null);
		Fx.sound(level, at, SoundEvents.SKELETON_AMBIENT, 1.0F, 0.6F);
	}

	/** Rimeback Cleaver: leap, and come down as an avalanche of ice that runs on ahead of you. */
	private static boolean avalanche(ServerLevel level, ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		launch(p, new Vec3(look.x * 0.4, 0.9, look.z * 0.4));
		Fx.sound(level, p.position(), SoundEvents.POWDER_SNOW_STEP, 1.5F, 0.6F);
		Acts.play(p, Acts.LEAP, 18);
		Vfx.trail(level, p, 0.7F, Art.AVALANCHE.color, 26);
		boolean[] done = {false};
		every(30, n -> {
			if (done[0] || !p.isAlive()) {
				return;
			}
			p.fallDistance = 0;
			Fx.burst(level, ParticleTypes.SNOWFLAKE, p.position(), 3, 0.2, 0.02);
			if (n > 5 && (p.onGround() || n == 30)) {
				done[0] = true;
				Vec3 at = p.position();
				Fx.shake(level, at, 20, 4.0F, 12);
				Acts.play(p, Acts.SLAM, 10);
				Vfx.slash(level, p, 88, 2.4F, 150, Art.AVALANCHE.color, 8);
				Vfx.ring(level, at, 3.5F, 0.7F, 0.8F, Art.AVALANCHE.color, 12);
				Vfx.wedge(level, at, look, 12.5F, 14.0F, Art.AVALANCHE.color, 18);
				Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1.5F, 0.5F);
				Fx.sound(level, at, AwSounds.BOSS_STOMP, 1.0F, 1.1F);
				var ice = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState());
				Set<UUID> struck = new HashSet<>();
				for (int row = 0; row < 6; row++) {
					int k = row;
					Scheduler.after(row * 2, () -> {
						Vec3 c = at.add(look.scale(1.5 + k * 1.8));
						Fx.burst(level, ice, c.add(0, 0.6, 0), 20, 0.9, 0.3);
						Vfx.ring(level, c, 2.2F, 0.5F, 1.4F, Art.AVALANCHE.color, 12);
						Vfx.flare(level, c.add(0, 0.8, 0), 1.2F, k % 2 == 0, Art.AVALANCHE.color, 8);
						Fx.burst(level, ParticleTypes.SNOWFLAKE, c.add(0, 1, 0), 10, 0.8, 0.1);
						Fx.sound(level, c, SoundEvents.POWDER_SNOW_BREAK, 1.0F, 0.6F + k * 0.1F);
						for (LivingEntity e : foes(level, p, c, 2.2)) {
							if (struck.add(e.getUUID()) && e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 7.0F))) {
								e.setTicksFrozen(e.getTicksRequiredToFreeze() + 160);
								if (!(e instanceof BossMob)) {
									e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
									e.syncVelocity = true;
								}
							}
						}
					});
				}
			}
		});
		return true;
	}

	/** Dunewalker's Chain: a sweeping cast that drags every foe in front of you into one heap, then blinds it with sand. */
	private static boolean chainPull(ServerLevel level, ServerPlayer p) {
		List<LivingEntity> caught = Combat.cone(level, p, 10, 40);
		if (caught.isEmpty()) {
			return false;
		}
		Vec3 heap = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2));
		Fx.sound(level, p.position(), SoundEvents.CHAIN_PLACE, 1.4F, 0.6F);
		Acts.play(p, Acts.LASH, 12);
		for (LivingEntity e : caught) {
			Vfx.beam(level, p.position().add(0, 1.2, 0), e.position().add(0, 1, 0), 0.12F, 0.06F, Art.CHAIN_PULL.color, 9);
		}
		every(6, n -> {
			for (LivingEntity e : caught) {
				if (!e.isAlive() || e instanceof BossMob) {
					continue;
				}
				Vec3 d = heap.subtract(e.position());
				if (d.length() > 1.0) {
					Vec3 v = d.normalize().scale(Math.min(1.2, d.length() * 0.35));
					e.setDeltaMovement(v.x, 0.08, v.z);
					e.syncVelocity = true;
				}
			}
		});
		Scheduler.after(7, () -> {
			var sand = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
			Fx.burst(level, sand, heap.add(0, 0.8, 0), 40, 1.4, 0.2);
			Vfx.ring(level, heap, 3.5F, 0.6F, 1.0F, Art.CHAIN_PULL.color, 14);
			Vfx.flare(level, heap.add(0, 0.8, 0), 1.6F, true, Art.CHAIN_PULL.color, 9);
			Fx.sound(level, heap, SoundEvents.SAND_BREAK, 1.5F, 0.6F);
			Fx.sound(level, heap, SoundEvents.CHAIN_BREAK, 1.0F, 0.8F);
			for (LivingEntity e : foes(level, p, heap, 3.5)) {
				e.setInvulnerableTime(0);
				e.hurtServer(level, level.damageSources().playerAttack(p), strength(p, 5.0F));
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 2), p);
			}
		});
		for (LivingEntity e : caught) {
			if (e instanceof BossMob) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), p);
			}
		}
		return true;
	}

	/** Glyphwarden Aegis: brace behind it and pour out a beam of judgment for two seconds. */
	private static boolean glyphBeam(ServerLevel level, ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		s.guardUntil = level.getGameTime() + 40;
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, false, false));
		Fx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.5F);
		Acts.play(p, Acts.BRACE, 40);
		Vfx.sigil(level, p.position(), 2.2F, Vfx.SIGIL_JUDGMENT, 4.0F, Art.GLYPH_BEAM.color, 42, p);
		for (int t = 0; t < 40; t += 4) {
			int k = t;
			Scheduler.after(t, () -> {
				if (!p.isAlive()) {
					return;
				}
				Vec3 eye = p.getEyePosition().add(0, -0.3, 0);
				Vec3 dir = p.getLookAngle();
				Vec3 end = eye.add(dir.scale(20));
				var block = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, p));
				if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
					end = block.getLocation();
				}
				Vfx.beam(level, eye.add(dir), end, 0.6F, 0, Art.GLYPH_BEAM.color, 5);
				Vfx.flare(level, end, 0.9F, k % 8 == 0, Art.GLYPH_BEAM.color, 5);
				Fx.burst(level, ParticleTypes.WAX_OFF, end, 3, 0.2, 0.1);
				if (k % 8 == 0) {
					Fx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
				}
				double len = end.subtract(eye).length();
				for (LivingEntity e : foes(level, p, eye.add(dir.scale(len / 2)), len / 2 + 1.5)) {
					Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
					double along = rel.dot(dir);
					if (along > 0 && along < len + 0.5 && rel.subtract(dir.scale(along)).length() < 1.3 + e.getBbWidth() * 0.5) {
						e.setInvulnerableTime(0);
						e.hurtServer(level, level.damageSources().indirectMagic(p, p), strength(p, 2.5F));
					}
				}
			});
		}
		return true;
	}
}
