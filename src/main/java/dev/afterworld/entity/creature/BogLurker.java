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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The fen's patient mouth. It lies still in the shallows with only its eyes above the water, then
 * lunges, bites, and drags what it caught back toward the mud.
 */
public class BogLurker extends Monster {
	/** Ticks left on the open-jaw lunge (synced for the bite animation). */
	private static final EntityDataAccessor<Integer> LUNGE = SynchedEntityData.defineId(BogLurker.class, EntityDataSerializers.INT);
	private int lungeCooldown;

	public BogLurker(EntityType<? extends BogLurker> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ARMOR, 4.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
			.add(Attributes.FOLLOW_RANGE, 20.0).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LUNGE, 0);
	}

	public int lunge() {
		return this.entityData.get(LUNGE);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LungeGoal());
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6, 240));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			int l = this.lunge();
			if (l > 0) {
				this.entityData.set(LUNGE, l - 1);
			}
			if (this.lungeCooldown > 0) {
				this.lungeCooldown--;
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			// Drag the prey back a little toward the lurker.
			Vec3 pull = this.position().subtract(living.position()).normalize().scale(0.55);
			living.push(pull.x, 0.15, pull.z);
			living.syncVelocity = true;
			level.sendParticles(ParticleTypes.SPLASH, living.getX(), living.getY() + 0.5, living.getZ(), 12, 0.4, 0.3, 0.4, 0.1);
		}
		return hit;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dev.afterworld.registry.AwSounds.voice("lurker").ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("lurker").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("lurker").death();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.5F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.FROG_STEP, 0.4F, 0.6F);
	}

	/** From up to eight blocks away: a short wind-up, then a leap with the jaw wide open. */
	private final class LungeGoal extends Goal {
		private int windup;

		LungeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = BogLurker.this.getTarget();
			if (t == null || BogLurker.this.lungeCooldown > 0 || !BogLurker.this.onGround() && !BogLurker.this.isInWater()) {
				return false;
			}
			double d = BogLurker.this.distanceToSqr(t);
			return d > 9 && d < 64 && BogLurker.this.hasLineOfSight(t);
		}

		@Override
		public void start() {
			this.windup = 12;
			BogLurker.this.getNavigation().stop();
			BogLurker.this.playSound(SoundEvents.FROG_LONG_JUMP, 1.0F, 0.5F);
		}

		@Override
		public boolean canContinueToUse() {
			return this.windup > 0 && BogLurker.this.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = BogLurker.this.getTarget();
			if (t == null) {
				return;
			}
			BogLurker.this.getLookControl().setLookAt(t, 30, 30);
			if (--this.windup == 0) {
				Vec3 dir = t.position().subtract(BogLurker.this.position());
				double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
				Vec3 leap = new Vec3(dir.x / horiz * 1.25, 0.45, dir.z / horiz * 1.25);
				BogLurker.this.setDeltaMovement(leap);
				BogLurker.this.entityData.set(LUNGE, 14);
				BogLurker.this.lungeCooldown = 80;
				BogLurker.this.playSound(SoundEvents.FROG_TONGUE, 1.2F, 0.6F);
				if (BogLurker.this.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SPLASH, BogLurker.this.getX(), BogLurker.this.getY() + 0.3, BogLurker.this.getZ(), 20, 0.6, 0.2, 0.6, 0.2);
				}
			}
		}
	}
}
