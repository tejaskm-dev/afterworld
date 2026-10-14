package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A four-legged Bureau construct with a clock for a face, still guarding whatever it was wound to
 * guard. It winds up, ticks, and fires a brass bolt. Poison and hunger mean nothing to it.
 */
public class ClockworkSentry extends Monster implements RangedAttackMob {
	/** Ticks left on the wind-up shown before each shot (synced for the head animation). */
	private static final EntityDataAccessor<Integer> WINDUP = SynchedEntityData.defineId(ClockworkSentry.class, EntityDataSerializers.INT);

	public ClockworkSentry(EntityType<? extends ClockworkSentry> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ARMOR, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(WINDUP, 0);
	}

	public int windup() {
		return this.entityData.get(WINDUP);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 16.0F));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			int w = this.windup();
			if (w > 0) {
				this.entityData.set(WINDUP, w - 1);
			} else if (this.getTarget() != null && this.random.nextInt(30) == 0) {
				this.entityData.set(WINDUP, 12);
				this.playSound(AwSounds.BOSS_TICK.value(), 0.8F, 1.6F);
			}
		}
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		ItemStack bolt = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, bolt, power, null);
		double xd = target.getX() - this.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - this.getZ();
		double dist = Math.sqrt(xd * xd + zd * zd);
		if (this.level() instanceof ServerLevel level) {
			Projectile.spawnProjectileUsingShoot(arrow, level, bolt, xd, yd + dist * 0.18F, zd, 1.8F, 6.0F);
		}
		this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.3F);
		this.entityData.set(WINDUP, 0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			level.sendParticles(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.GEAR, 0xC9962E),
				this.getX(), this.getY() + 0.8, this.getZ(), this.isDeadOrDying() ? 24 : 6, 0.35, 0.25, 0.35, 0.2);
		}
		return hurt;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.HUNGER) && !effect.is(MobEffects.REGENERATION) && super.canBeAffected(effect);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_SENTRY.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.4F, 1.5F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}
}
