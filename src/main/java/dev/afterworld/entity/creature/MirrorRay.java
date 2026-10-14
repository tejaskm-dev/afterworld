package dev.afterworld.entity.creature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A great flat ray whose back holds the sky like a mirror. They glide the open sea in slow beats
 * and, now and then, break the surface in a leap that throws spray into the light.
 */
public class MirrorRay extends WaterAnimal {
	private int leapIn = 400;

	public MirrorRay(EntityType<? extends MirrorRay> type, Level level) {
		super(type, level);
		this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 10);
	}

	public static AttributeSupplier.Builder attributes() {
		return WaterAnimal.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 1.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.9, 40));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.isInWater() && --this.leapIn <= 0) {
			this.leapIn = 500 + this.random.nextInt(900);
			// Only leap from near the surface.
			if (level.getBlockState(this.blockPosition().above(2)).isAir() || level.getBlockState(this.blockPosition().above()).isAir()) {
				Vec3 look = this.getLookAngle().multiply(1, 0, 1).normalize();
				this.setDeltaMovement(look.x * 0.7, 0.95, look.z * 0.7);
				level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 40, 1.0, 0.3, 1.0, 0.3);
				this.playSound(SoundEvents.DOLPHIN_JUMP, 1.4F, 0.6F);
			}
		}
		if (!this.isInWater() && this.onGround()) {
			// Stranded: flop back toward the water.
			this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() - 0.5) * 0.2, 0.3, (this.random.nextFloat() - 0.5) * 0.2));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.DOLPHIN_AMBIENT_WATER;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DOLPHIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DOLPHIN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}
}
