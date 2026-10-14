package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A slow, patient beast with a garden growing on its shell. Shear the moss; it grows back. */
public class Mossback extends Animal implements Shearable {
	private static final EntityDataAccessor<Boolean> MOSSY = SynchedEntityData.defineId(Mossback.class, EntityDataSerializers.BOOLEAN);

	public Mossback(EntityType<? extends Mossback> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.14)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ARMOR, 6.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOSSY, true);
	}

	public boolean isMossy() {
		return this.entityData.get(MOSSY);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.3));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.15, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AwEntities.MOSSBACK.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Items.SHEARS)) {
			if (this.level() instanceof ServerLevel level && this.readyForShearing()) {
				this.shear(level, SoundSource.PLAYERS, stack);
				this.gameEvent(GameEvent.SHEAR, player);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.CONSUME;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void shear(ServerLevel level, SoundSource source, ItemStack tool) {
		level.playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 0.8F);
		int moss = 2 + this.random.nextInt(2);
		this.spawnAtLocation(level, new ItemStack(Items.MOSS_BLOCK, moss), 1.4F);
		if (this.random.nextFloat() < 0.5F) {
			this.spawnAtLocation(level, new ItemStack(this.random.nextBoolean() ? Items.RED_MUSHROOM : Items.BROWN_MUSHROOM), 1.4F);
		}
		this.entityData.set(MOSSY, false);
	}

	@Override
	public boolean readyForShearing() {
		return this.isAlive() && this.isMossy() && !this.isBaby();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		// The garden regrows slowly while it stands on living ground.
		if (!this.level().isClientSide() && !this.isMossy() && this.random.nextInt(4000) == 0) {
			BlockState below = this.level().getBlockState(this.blockPosition().below());
			if (below.is(BlockTags.DIRT) || below.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)) {
				this.entityData.set(MOSSY, true);
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_MOSSBACK.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.TURTLE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.TURTLE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.CAMEL_STEP, 0.5F, 0.6F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 320;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Mossy", this.isMossy());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(MOSSY, input.getBooleanOr("Mossy", true));
	}

	public static boolean checkSpawnRules(EntityType<Mossback> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(BlockTags.DIRT) || below.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || below.is(net.minecraft.world.level.block.Blocks.MUD))
			&& level.getRawBrightness(pos, 0) > 7;
	}
}
