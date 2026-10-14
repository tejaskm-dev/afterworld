package dev.afterworld.entity.creature;

import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.rumour.Rumours;
import dev.afterworld.world.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crows know things. They take off when you come near, unless you creep; feed one seeds and it
 * will tell you what it has seen, which is to say a rumour.
 */
public class Crow extends Animal {
	private long toldAt = -24000;

	public Crow(EntityType<? extends Crow> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 10, false);
	}

	public static AttributeSupplier.Builder attributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.45).add(Attributes.MOVEMENT_SPEED, 0.22);
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
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, p -> !p.isShiftKeyDown(), 6.0F, 1.1, 1.5,
			e -> e instanceof Player player && !player.isCreative() && !player.isSpectator()));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.WHEAT_SEEDS) && !stack.is(Items.BEETROOT_SEEDS) && !stack.is(Items.MELON_SEEDS) && !stack.is(Items.PUMPKIN_SEEDS)) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level && player instanceof ServerPlayer sp) {
			long now = level.getGameTime();
			stack.consume(1, player);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.4, getZ(), 4, 0.2, 0.2, 0.2, 0.0);
			this.playSound(AwSounds.MOB_CROW.value(), 1.0F, 1.1F);
			if (now - this.toldAt > 12000) {
				this.toldAt = now;
				Rumours.ask(sp, this.getUUID(), "a crow", Settlements.at(level, blockPosition()));
				Progress.codex(sp, "creature.crow");
			} else {
				sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.afterworld.crow_full").withColor(0x9A9384));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	public boolean isFlying() {
		return !this.onGround();
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
		return AwSounds.MOB_CROW.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PARROT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PARROT_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("ToldAt", this.toldAt);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.toldAt = input.getLongOr("ToldAt", -24000L);
	}

	public static boolean checkSpawnRules(EntityType<Crow> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(BlockTags.DIRT) || below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS) || below.is(BlockTags.BASE_STONE_OVERWORLD))
			&& level.getRawBrightness(pos, 0) > 8;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return false;
	}
}
