package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The shaggy, ice-horned herd beast of the Whitewaste. Gentle unless its calf is threatened; it
 * can be milked, and shorn for a thick white fleece that grows back over a few days' walking.
 */
public class Rimehorn extends Animal implements Shearable {
	private int fleeceIn;

	public Rimehorn(EntityType<? extends Rimehorn> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.18)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.15, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.fleeceIn > 0) {
			this.fleeceIn--;
		}
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
		if (stack.is(Items.BUCKET) && !this.isBaby()) {
			player.playSound(SoundEvents.COW_MILK, 1.0F, 0.8F);
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, Items.MILK_BUCKET.getDefaultInstance()));
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void shear(ServerLevel level, SoundSource source, ItemStack tool) {
		level.playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 0.8F);
		int n = 2 + this.random.nextInt(3);
		for (int i = 0; i < n; i++) {
			ItemEntity drop = this.spawnAtLocation(level, new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)), 1.0F);
			if (drop != null) {
				drop.setDeltaMovement(drop.getDeltaMovement().add((this.random.nextFloat() - this.random.nextFloat()) * 0.1F,
					this.random.nextFloat() * 0.05F, (this.random.nextFloat() - this.random.nextFloat()) * 0.1F));
			}
		}
		this.fleeceIn = 24000 * 2;
	}

	@Override
	public boolean readyForShearing() {
		return this.isAlive() && !this.isBaby() && this.fleeceIn <= 0;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.WHEAT) || stack.is(Items.SWEET_BERRIES);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AwEntities.RIMEHORN.create(level, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("FleeceIn", this.fleeceIn);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.fleeceIn = input.getIntOr("FleeceIn", 0);
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_RIMEHORN.value();
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
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.8F;
	}
}
