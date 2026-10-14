package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A spark of the blossom groves no bigger than a hand: a body of light with petals for wings. They
 * drift between flowers by day, chiming to each other, and scatter petals where they pass.
 */
public class PetalSprite extends PathfinderMob {
	public PetalSprite(EntityType<? extends PetalSprite> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.FLYING_SPEED, 0.35).add(Attributes.MOVEMENT_SPEED, 0.2);
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
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 3.0F, 0.9, 1.1));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 0.7));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % 10 == this.getId() % 10) {
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		}
		if (!this.level().isClientSide() && this.getY() > this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ()) + 6) {
			this.setDeltaMovement(this.getDeltaMovement().add(0, -0.02, 0));   // stay near the flowers
		}
	}

	/** On grass or leaves near flowers, by day. */
	public static boolean checkSpawnRules(EntityType<PetalSprite> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.ignoresLightRequirements(reason)) {
			return true;
		}
		BlockState below = level.getBlockState(pos.below());
		return level.getRawBrightness(pos, 0) > 8 && (below.is(BlockTags.DIRT) || below.is(BlockTags.LEAVES) || below.is(BlockTags.FLOWERS));
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dev.afterworld.registry.AwSounds.voice("sprite").ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("sprite").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("sprite").death();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 180;
	}
}
