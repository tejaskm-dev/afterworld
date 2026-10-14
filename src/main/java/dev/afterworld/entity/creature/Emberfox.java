package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwParticles;
import dev.afterworld.registry.AwSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A fox of the ember steppe whose brush never quite stops smouldering. Shy of people, fond of
 * sweet berries, and at night the tail trails sparks behind it like a struck match.
 */
public class Emberfox extends Animal {
	public Emberfox(EntityType<? extends Emberfox> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, true));
		this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.2, 1.5, p -> !p.isShiftKeyDown()));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % (level.isBrightOutside() ? 20 : 4) == 0) {
			double yaw = Math.toRadians(this.yBodyRot);
			level.sendParticles(AwParticles.of(AwParticles.EMBER, 0xFFA040), this.getX() + Math.sin(yaw) * 0.6, this.getY() + 0.6, this.getZ() - Math.cos(yaw) * 0.6,
				1, 0.05, 0.05, 0.05, 0.01);
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AwEntities.EMBERFOX.create(level, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_EMBERFOX.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}
}
