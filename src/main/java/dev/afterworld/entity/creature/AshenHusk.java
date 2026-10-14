package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the moor's old fires left standing. A husk still smoulders: its touch catches, and when it
 * falls it comes apart in a gust of embers. Water hurts it; the sun does not.
 */
public class AshenHusk extends Monster {
	public AshenHusk(EntityType<? extends AshenHusk> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.ARMOR, 3.0).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			if (this.tickCount % 5 == 0) {
				level.sendParticles(AwParticles.of(AwParticles.EMBER, 0xFF8A3A), this.getX(), this.getY() + 1.2, this.getZ(), 1, 0.25, 0.5, 0.25, 0.01);
			}
			if (this.isInWaterOrRain() && this.tickCount % 20 == 0) {
				this.hurtServer(level, this.damageSources().drown(), 1.0F);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY() + 1, this.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.igniteForSeconds(2.0F);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(AwParticles.of(AwParticles.EMBER, 0xFFA040), this.getX(), this.getY() + 1, this.getZ(), 40, 0.4, 0.8, 0.4, 0.12);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1, this.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
		}
	}

	@Override
	public boolean isSensitiveToWater() {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dev.afterworld.registry.AwSounds.voice("husk").ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("husk").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("husk").death();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.HUSK_STEP, 0.15F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.75F;
	}
}
