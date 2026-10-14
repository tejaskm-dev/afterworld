package dev.afterworld.entity.creature;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A spirit of the white wastes, crowned in icicles. It keeps its distance, gathers the cold for a
 * breath, and lets it go in a line of frost that bites and freezes.
 */
public class RimeWight extends Monster {
	/** Ticks left gathering cold before the beam (synced for the arms-raised pose). */
	private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(RimeWight.class, EntityDataSerializers.INT);

	public RimeWight(EntityType<? extends RimeWight> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.xpReward = 9;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.FLYING_SPEED, 0.4).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 28.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CHARGE, 0);
	}

	public int charge() {
		return this.entityData.get(CHARGE);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new FrostGoal());
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.4, this.getZ(), 1, 0.3, 0.3, 0.3, 0.01);
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dev.afterworld.registry.AwSounds.voice("wight").ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("wight").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("wight").death();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.55F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	/** Hover at a distance, gather the cold for a second, then breathe a line of frost. */
	private final class FrostGoal extends Goal {
		private int cooldown;

		FrostGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = RimeWight.this.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			RimeWight w = RimeWight.this;
			LivingEntity t = w.getTarget();
			if (t == null) {
				return;
			}
			w.getLookControl().setLookAt(t, 30, 30);
			double d = w.distanceToSqr(t);
			// Keep 7 to 11 blocks away, a little above.
			Vec3 away = w.position().subtract(t.position()).multiply(1, 0, 1).normalize();
			if (d < 7 * 7) {
				Vec3 to = t.position().add(away.scale(9)).add(0, 3, 0);
				w.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
			} else if (d > 12 * 12) {
				w.getMoveControl().setWantedPosition(t.getX(), t.getY() + 3, t.getZ(), 1.0);
			}
			int c = w.charge();
			if (c > 0) {
				w.entityData.set(CHARGE, c - 1);
				if (w.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, w.getX(), w.getEyeY(), w.getZ(), 3, 0.6, 0.6, 0.6, -0.05);
				}
				if (c == 1) {
					breathe(t);
				}
			} else if (--this.cooldown <= 0 && d < 16 * 16 && w.hasLineOfSight(t)) {
				w.entityData.set(CHARGE, 22);
				this.cooldown = 70 + w.random.nextInt(30);
				w.playSound(SoundEvents.POWDER_SNOW_STEP, 1.2F, 0.5F);
			}
		}

		private void breathe(LivingEntity t) {
			RimeWight w = RimeWight.this;
			if (!(w.level() instanceof ServerLevel level) || !w.hasLineOfSight(t)) {
				return;
			}
			Vec3 from = w.getEyePosition();
			Vec3 to = t.getEyePosition();
			int steps = (int) (from.distanceTo(to) * 3);
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.lerp(to, i / (double) steps);
				level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				if (i % 3 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
				}
			}
			level.playSound(null, w.getX(), w.getY(), w.getZ(), SoundEvents.PLAYER_HURT_FREEZE, w.getSoundSource(), 1.2F, 0.7F);
			if (t.hurtServer(level, w.damageSources().freeze(), 4.0F)) {
				t.setTicksFrozen(Math.min(t.getTicksRequiredToFreeze() + 60, t.getTicksFrozen() + 120));
				t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), w);
			}
		}
	}
}
