package dev.afterworld.entity.creature;

import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.site.Sites;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A fen light. Some wisps only flee, but some know where something was left: come close and they
 * drift ahead of you, waiting, until you reach the spot. Then they go out, and there it is.
 */
public class Wisp extends PathfinderMob {
	private BlockPos treasure;
	private boolean leads;

	public Wisp(EntityType<? extends Wisp> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.leads = level.getRandom().nextFloat() < 0.5F;
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.4).add(Attributes.MOVEMENT_SPEED, 0.2);
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
		this.goalSelector.addGoal(1, new LeadGoal(this));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5.0F, 0.9, 1.1));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 0.8));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
			this.level().addParticle(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.MOTE, 0xBFF4FF),
				this.getRandomX(0.3), this.getY() + 0.2, this.getRandomZ(0.3), 0, 0.01, 0);
		}
	}

	/** Leads a nearby player to a spot 24-48 blocks away and leaves a cache there. */
	static final class LeadGoal extends Goal {
		private final Wisp wisp;
		private Player follower;

		LeadGoal(Wisp wisp) {
			this.wisp = wisp;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!this.wisp.leads || !(this.wisp.level() instanceof ServerLevel level)) {
				return false;
			}
			this.follower = level.getNearestPlayer(this.wisp, 10.0);
			if (this.follower == null || this.follower.isSpectator()) {
				return false;
			}
			if (this.wisp.treasure == null) {
				RandomSource r = this.wisp.getRandom();
				double a = r.nextDouble() * Math.PI * 2;
				int d = 24 + r.nextInt(25);
				int x = this.wisp.getBlockX() + (int) (Math.cos(a) * d);
				int z = this.wisp.getBlockZ() + (int) (Math.sin(a) * d);
				if (!level.hasChunk(x >> 4, z >> 4)) {
					return false;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				this.wisp.treasure = new BlockPos(x, y, z);
			}
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.wisp.treasure != null && this.follower != null && this.follower.isAlive() && this.follower.distanceTo(this.wisp) < 24;
		}

		@Override
		public void tick() {
			BlockPos t = this.wisp.treasure;
			double toTreasure = this.follower.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(t));
			if (toTreasure < 4.0 && this.wisp.level() instanceof ServerLevel level) {
				settle(level, t);
				return;
			}
			// Stay a few blocks ahead of the follower along the way, and wait when they fall behind.
			if (this.follower.distanceTo(this.wisp) < 9) {
				var dir = net.minecraft.world.phys.Vec3.atCenterOf(t).subtract(this.wisp.position()).normalize();
				var next = this.wisp.position().add(dir.scale(5));
				this.wisp.getNavigation().moveTo(next.x, Math.max(next.y, t.getY() + 1.5), next.z, 1.0);
			} else {
				this.wisp.getNavigation().stop();
			}
		}

		private void settle(ServerLevel level, BlockPos t) {
			BlockPos spot = t;
			while (spot.getY() > level.getMinY() && level.getBlockState(spot.below()).canBeReplaced()) {
				spot = spot.below();
			}
			BlockPos barrel = spot.below();
			level.setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), barrel, ResourceKey.create(Registries.LOOT_TABLE, Sites.lootId("old_cache")));
			level.sendParticles(ParticleTypes.END_ROD, barrel.getX() + 0.5, barrel.getY() + 1.2, barrel.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.04);
			level.playSound(null, barrel, AwSounds.UI_DISCOVERY.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.2F);
			if (this.follower instanceof ServerPlayer sp) {
				Progress.codex(sp, "creature.wisp");
			}
			this.wisp.treasure = null;
			this.wisp.leads = false;
			this.wisp.discard();
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
		return AwSounds.WORLD_WHISPER.value();
	}

	@Override
	protected float getSoundVolume() {
		return 0.3F;
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
		return 300;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Leads", this.leads);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.leads = input.getBooleanOr("Leads", false);
	}

	public static boolean checkSpawnRules(EntityType<Wisp> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.ignoresLightRequirements(reason)) {
			return true;
		}
		return level.getSkyDarken() >= 4 && level.getBrightness(LightLayer.BLOCK, pos) < 6;
	}
}
