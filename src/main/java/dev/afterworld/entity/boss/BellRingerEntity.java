package dev.afterworld.entity.boss;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Bell-Ringer of the Hollow Cathedral. Each ring of its bell silences one of your Edicts.
 * It can be disarmed, toppled, crushed by its own chandelier, or reminded of its name.
 */
public class BellRingerEntity extends BossMob implements Enemy {
	@Override
	protected String voice() {
		return "bell";
	}

	@Override
	public dev.afterworld.aspect.Aspect aspect() {
		return dev.afterworld.aspect.Aspect.REQUIEM_TOLL;
	}

	public static final int ANIM_WALK = 0;
	public static final int ANIM_RING_WINDUP = 1;
	public static final int ANIM_RING = 2;
	public static final int ANIM_THROW = 3;
	public static final int ANIM_STUNNED = 4;
	public static final int ANIM_REMEMBER = 5;
	public static final int ANIM_SWING = 6;

	private static final EntityDataAccessor<Boolean> BELL_HELD = SynchedEntityData.defineId(BellRingerEntity.class, EntityDataSerializers.BOOLEAN);

	Vec3 home = Vec3.ZERO;
	int siteId = -1;
	private int stunned;
	private int ringTimer = 160;
	private int throwTimer = 260;
	private int swing;
	private int windup;
	private int specialTimer = 140;
	boolean enraged;
	boolean remembered;
	BlockPos landedBell;

	public BellRingerEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 650.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.SCALE, 1.2)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 13.0)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BELL_HELD, true);
	}

	public boolean bellHeld() {
		return this.entityData.get(BELL_HELD);
	}

	void setBellHeld(boolean held) {
		this.entityData.set(BELL_HELD, held);
	}

	public boolean stunned() {
		return stunned > 0;
	}

	public void stun(int ticks) {
		if (remembered) {
			return;
		}
		stunned = Math.max(stunned, ticks);
		windup = 0;
		setAnim(ANIM_STUNNED);
		getNavigation().stop();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == Vec3.ZERO) {
			home = position();
		}
		if (remembered) {
			getNavigation().stop();
			return;
		}
		if (stunned > 0) {
			stunned--;
			if (stunned % 10 == 0) {
				Fx.burst(level, ParticleTypes.CRIT, position().add(0, getBbHeight() + 0.3, 0), 6, 0.4, 0.05);
			}
			if (stunned == 0) {
				setAnim(ANIM_WALK);
			}
			return;
		}
		if (inTransition()) {
			getNavigation().stop();
			if (transition % 10 == 0) {
				setAnim(ANIM_RING_WINDUP);
				Fx.hostileSound(level, position(), SoundEvents.BELL_RESONATE, 2.0F, 0.4F + phase * 0.1F);
			}
			if (transition == 1) {
				setAnim(ANIM_WALK);
			}
			return;
		}
		ServerPlayer target = nearestPlayer(30);
		if (swing > 0) {
			swing--;
		}
		// From the second toll on: the great attacks.
		if (target != null && phase >= 2 && windup == 0 && --specialTimer <= 0) {
			specialTimer = phase >= 3 ? 100 : 150;
			switch (random.nextInt(phase >= 3 ? 3 : 2)) {
				case 0 -> tollWaves(level);
				case 1 -> bellRain(level, target);
				default -> rush(level, target);
			}
		}
		if (windup > 0) {
			windup--;
			if (anim() == ANIM_RING_WINDUP && windup % 6 == 0) {
				Fx.hostileSound(level, position(), SoundEvents.BELL_RESONATE, 1.4F, 0.5F + (30 - windup) * 0.03F);
				Fx.ring(level, Fx.dust(0xE8A33D, 1.2F), position().add(0, 0.2, 0), 3 + (30 - windup) * 0.15, 20);
			}
			if (windup == 0) {
				if (anim() == ANIM_RING_WINDUP) {
					ring(level);
				} else if (anim() == ANIM_THROW && target != null) {
					throwBell(level, target);
				}
			}
			return;
		}
		if (!bellHeld() && landedBell != null && !BellRingerSystem.bellLost(level)) {
			// Go and fetch the bell.
			if (position().distanceToSqr(Vec3.atCenterOf(landedBell)) < 6.5) {
				BellRingerSystem.pickUpBell(level, this);
			} else if (tickCount % 10 == 0) {
				getNavigation().moveTo(landedBell.getX() + 0.5, landedBell.getY(), landedBell.getZ() + 0.5, 1.2);
			}
		} else if (target != null) {
			getLookControl().setLookAt(target, 30, 30);
			double dist = Math.sqrt(distanceToSqr(target));
			if (tickCount % 10 == 0 && dist > 2.6) {
				getNavigation().moveTo(target, enraged ? 1.35 : 1.0);
			}
			if (dist < 3.4 && swing == 0) {
				swing = enraged ? 18 : 26;
				setAnim(ANIM_SWING);
				this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				doHurtTarget(level, target);
			}
			if (bellHeld() && !BellRingerSystem.bellLost(level)) {
				if (--ringTimer <= 0) {
					ringTimer = enraged ? 150 : 200;
					windup = 30;
					setAnim(ANIM_RING_WINDUP);
					getNavigation().stop();
				} else if (--throwTimer <= 0 && dist > 9) {
					throwTimer = 320;
					windup = 18;
					setAnim(ANIM_THROW);
					getNavigation().stop();
				}
			}
		} else if (position().distanceToSqr(home) > 9 && tickCount % 20 == 0) {
			getNavigation().moveTo(home.x, home.y, home.z, 0.8);
			if (getHealth() < getMaxHealth()) {
				heal(4.0F);
			}
		}
	}

	private void ring(ServerLevel level) {
		setAnim(ANIM_RING);
		Vec3 pos = position();
		Fx.hostileSound(level, pos, AwSounds.BOSS_BELL, 3.0F, 1.0F);
		Fx.hostileSound(level, pos, SoundEvents.BELL_RESONATE, 2.0F, 0.6F);
		Fx.ring(level, Fx.dust(0xE8A33D, 1.6F), pos.add(0, 1, 0), 6, 36);
		Fx.ring(level, ParticleTypes.SONIC_BOOM, pos.add(0, 1.5, 0), 2.5, 6);
		long now = level.getGameTime();
		for (ServerPlayer p : playersNear(24)) {
			PlayerState s = PlayerState.of(p);
			int slot = -1;
			for (int i = 0; i < s.slotCount(); i++) {
				if (s.slots[i] != null && s.slots[i] == s.lastCast) {
					slot = i;
				}
			}
			if (slot < 0) {
				int count = 0;
				for (int i = 0; i < s.slotCount(); i++) {
					if (s.slots[i] != null && random.nextInt(++count) == 0) {
						slot = i;
					}
				}
			}
			if (slot >= 0) {
				s.suppressedMask |= 1 << slot;
				s.suppressedUntil = now + 240;
				s.hudDirty = true;
				Edict e = s.slots[slot];
				p.sendOverlayMessage(Component.translatable("message.afterworld.bell_silences", e.displayName()).withColor(0xE8A33D));
			}
			Progress.codex(p, "boss.bell_ringer");
			if (p.distanceToSqr(this) < 36) {
				Vec3 d = p.position().subtract(pos).normalize();
				p.knockback(1.2, -d.x, -d.z, level.damageSources().mobAttack(this), 0);
			}
		}
	}

	/** Three shockwaves roll out from the bell, one after another. Jump them. */
	private void tollWaves(ServerLevel level) {
		setAnim(ANIM_RING_WINDUP);
		windup = 24;
		getNavigation().stop();
		Fx.hostileSound(level, position(), SoundEvents.BELL_RESONATE, 2.5F, 0.5F);
		for (int i = 0; i < 3; i++) {
			int n = i;
			dev.afterworld.util.Scheduler.after(20 + i * 16, () -> {
				if (!isAlive()) {
					return;
				}
				setAnim(ANIM_RING);
				Fx.hostileSound(level, position(), AwSounds.BOSS_BELL, 3.0F, 0.8F + n * 0.12F);
				Fx.shake(level, position(), 32, 2.5F, 10);
				Hazards.wave(level, this, position(), 16, 0.55 + n * 0.1, 9.0F, n == 2 ? 0xFF7A3A : 0xE8A33D);
			});
		}
	}

	/** Bells fall from the dark roof onto marked circles around the target. */
	private void bellRain(ServerLevel level, ServerPlayer target) {
		setAnim(ANIM_RING_WINDUP);
		windup = 20;
		getNavigation().stop();
		Fx.hostileSound(level, position(), AwSounds.BOSS_ROAR, 2.0F, 1.1F);
		int count = phase >= 3 ? 8 : 6;
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = i == 0 ? 0 : 1.5 + random.nextDouble() * 5;
			Vec3 at = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
			int delay = 40 + i * 6;
			Hazards.strike(level, this, at, 1.8, delay, 11.0F, 0xE8A33D, null);
			// The bell itself, timed to land as the circle fills.
			dev.afterworld.util.Scheduler.after(Math.max(1, delay - 31), () -> {
				HeavyProjectile bell = new HeavyProjectile(level, this, new ItemStack(Items.BELL), 0).asProp();
				bell.setPos(at.x, at.y + 20, at.z);
				bell.setDeltaMovement(0, 0, 0);
				level.addFreshEntity(bell);
			});
		}
	}

	/** A charge down a marked lane, bell first. */
	private void rush(ServerLevel level, ServerPlayer target) {
		Vec3 from = position();
		Vec3 dir = target.position().subtract(from).multiply(1, 0, 1).normalize();
		double length = Math.min(18, Math.max(8, target.position().distanceTo(from) + 4));
		setAnim(ANIM_SWING);
		windup = 22;
		getNavigation().stop();
		Hazards.line(level, this, from, dir, length, 3.0, 22, 12.0F, 0xFF7A3A);
		Fx.hostileSound(level, from, AwSounds.BOSS_ROAR, 2.0F, 0.9F);
		// The dash itself: a few quick strides down the lane, stopping at a wall.
		int steps = 7;
		for (int k = 1; k <= steps; k++) {
			int n = k;
			dev.afterworld.util.Scheduler.after(21 + k, () -> {
				if (!isAlive()) {
					return;
				}
				Vec3 to = from.add(dir.scale((length - 1) * n / steps));
				if (level.noCollision(this, getBoundingBox().move(to.subtract(position())))) {
					setPos(to.x, to.y, to.z);
					Fx.burst(level, Fx.dust(0xFF7A3A, 1.6F), to.add(0, 1, 0), 12, 0.6, 0.05);
					Fx.burst(level, ParticleTypes.CLOUD, to.add(0, 0.2, 0), 4, 0.5, 0.02);
				}
				if (n == steps) {
					Fx.shake(level, position(), 24, 3.0F, 8);
				}
			});
		}
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		if (phase == 3) {
			enraged = true;
			// The last toll calls up the rule-eaters that live in the bell's silence.
			for (int i = 0; i < 3; i++) {
				var w = dev.afterworld.registry.AwEntities.RULE_WRAITH.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
				if (w != null) {
					double a = i * Math.PI * 2 / 3;
					w.snapTo(getX() + Math.cos(a) * 4, getY() + 2, getZ() + Math.sin(a) * 4, 0, 0);
					level.addFreshEntity(w);
					Fx.burst(level, ParticleTypes.SOUL, w.position().add(0, 1, 0), 20, 0.5, 0.05);
				}
			}
		}
	}

	@Override
	protected void onReset(ServerLevel level) {
		enraged = BellRingerSystem.bellLost(level);
		specialTimer = 140;
	}

	private void throwBell(ServerLevel level, ServerPlayer target) {
		setAnim(ANIM_WALK);
		HeavyProjectile bell = new HeavyProjectile(level, this, new ItemStack(Items.BELL), 12.0F);
		bell.setPos(getX(), getY() + 3.0, getZ());
		Vec3 to = target.position().subtract(bell.position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		bell.shoot(to.x, to.y + horizontal * 0.25, to.z, 1.2F, 1.0F);
		level.addFreshEntity(bell);
		setBellHeld(false);
		Fx.hostileSound(level, position(), AwSounds.BOSS_ROAR, 1.2F, 1.25F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (remembered) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			BellRingerSystem.died(level, this, source);
			if (source.getEntity() instanceof net.minecraft.world.entity.player.Player) {
				this.spawnAtLocation(level, new ItemStack(dev.afterworld.registry.AwItems.BELLHAMMER), 1.0F);
			}
		}
	}

	void remember(ServerLevel level, ServerPlayer by) {
		remembered = true;
		setAnim(ANIM_REMEMBER);
		getNavigation().stop();
		setPermanentlyInvulnerable(true);
	}

	@Override
	public void travel(Vec3 input) {
		if (stunned > 0 || remembered) {
			super.travel(Vec3.ZERO);
			return;
		}
		super.travel(input);
	}

	@Override
	protected void registerGoals() {
	}

	static boolean isRinger(LivingEntity e) {
		return e instanceof BellRingerEntity;
	}
}
