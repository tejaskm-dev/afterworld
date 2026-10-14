package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Little fish with a lit stripe down each side; their schools turn the rivers into slow stars. */
public class Glimmerfin extends AbstractSchoolingFish {
	public Glimmerfin(EntityType<? extends Glimmerfin> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 3.0);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.isInWater() && this.tickCount % 40 == this.getId() % 40) {
			level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	@Override
	public int getMaxSchoolSize() {
		return 9;
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(AwItems.GLIMMERFIN_BUCKET);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.COD_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COD_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COD_HURT;
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}
}
