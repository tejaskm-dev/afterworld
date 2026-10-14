package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

/** A night moth with a lantern for an abdomen. Harmless; drifts toward any light it can find. */
public class LanternMoth extends PathfinderMob {
	public LanternMoth(EntityType<? extends LanternMoth> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.FLYING_SPEED, 0.5).add(Attributes.MOVEMENT_SPEED, 0.25);
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
		this.goalSelector.addGoal(1, new SeekLightGoal(this));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0));
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.MOTE, 0xFFD27A), this.getX(), this.getY() + 0.2, this.getZ(), 16, 0.3, 0.2, 0.3, 0.02);
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_MOTH.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("moth").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("moth").death();
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	/** Spawns in open air after dark. */
	public static boolean checkSpawnRules(EntityType<LanternMoth> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.ignoresLightRequirements(reason)) {
			return true;
		}
		return level.getBrightness(LightLayer.SKY, pos) > 8 && level.getSkyDarken() >= 4
			&& pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
	}

	/** Flutter toward the brightest block light nearby, the way moths do. */
	static final class SeekLightGoal extends Goal {
		private final LanternMoth moth;
		private BlockPos target;
		private int cooldown;

		SeekLightGoal(LanternMoth moth) {
			this.moth = moth;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (--this.cooldown > 0) {
				return false;
			}
			this.cooldown = 60 + this.moth.getRandom().nextInt(60);
			Level level = this.moth.level();
			BlockPos origin = this.moth.blockPosition();
			BlockPos best = null;
			int bestLight = 9;
			for (int i = 0; i < 24; i++) {
				BlockPos p = origin.offset(this.moth.getRandom().nextInt(21) - 10, this.moth.getRandom().nextInt(9) - 4, this.moth.getRandom().nextInt(21) - 10);
				int light = level.getBrightness(LightLayer.BLOCK, p);
				if (light > bestLight && level.isEmptyBlock(p)) {
					bestLight = light;
					best = p;
				}
			}
			this.target = best;
			return best != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && !this.moth.getNavigation().isDone();
		}

		@Override
		public void start() {
			this.moth.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5, 1.0);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return true;
	}
}
