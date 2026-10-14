package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** A stag whose antlers glow like moonlit glass. It bolts from anyone who does not creep. */
public class HollowStag extends Animal {
	private static final EntityDataAccessor<Boolean> GRAZING = SynchedEntityData.defineId(HollowStag.class, EntityDataSerializers.BOOLEAN);

	public HollowStag(EntityType<? extends HollowStag> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 18.0).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GRAZING, false);
	}

	public boolean isGrazing() {
		return this.entityData.get(GRAZING);
	}

	/** Client-side easing of the graze pose, so the head lowers and lifts smoothly. */
	public float grazeAnim;
	public float grazeAnimO;

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			this.grazeAnimO = this.grazeAnim;
			this.grazeAnim = net.minecraft.util.Mth.approach(this.grazeAnim, this.isGrazing() ? 1.0F : 0.0F, 0.12F);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, p -> !p.isShiftKeyDown(), 10.0F, 1.3, 1.8,
			e -> e instanceof Player player && !player.isCreative() && !player.isSpectator()));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.15));
		this.goalSelector.addGoal(6, new GrazeGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AwEntities.HOLLOW_STAG.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_STAG.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	/** Head down, nibbling, for a few seconds at a time. */
	static final class GrazeGoal extends Goal {
		private final HollowStag stag;
		private int ticks;

		GrazeGoal(HollowStag stag) {
			this.stag = stag;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return this.stag.getRandom().nextInt(this.stag.isBaby() ? 300 : 600) == 0 && this.stag.onGround();
		}

		@Override
		public void start() {
			this.ticks = 60 + this.stag.getRandom().nextInt(60);
			this.stag.getNavigation().stop();
			this.stag.entityData.set(GRAZING, true);
		}

		@Override
		public boolean canContinueToUse() {
			return this.ticks > 0;
		}

		@Override
		public void tick() {
			this.ticks--;
		}

		@Override
		public void stop() {
			this.stag.entityData.set(GRAZING, false);
		}
	}

	public static boolean checkSpawnRules(EntityType<HollowStag> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(BlockTags.DIRT) || below.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)) && level.getRawBrightness(pos, 0) > 7;
	}
}
