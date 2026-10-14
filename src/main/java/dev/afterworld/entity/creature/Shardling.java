package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A crawling cluster of the Shardfields' crystal. It leaps, and when it breaks it breaks into two
 * smaller ones that keep coming.
 */
public class Shardling extends Monster {
	private boolean shard;

	public Shardling(EntityType<? extends Shardling> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** Makes this one of the small pieces a broken shardling leaves behind. */
	public void makeShard() {
		this.shard = true;
		this.getAttribute(Attributes.SCALE).setBaseValue(0.55);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6.0);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
		this.setHealth(6.0F);
		this.xpReward = 2;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			level.sendParticles(AwParticles.of(AwParticles.SHARD, 0xC8A8FF), this.getX(), this.getY() + 0.4, this.getZ(), 6, 0.3, 0.2, 0.3, 0.15);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(AwParticles.of(AwParticles.SHARD, 0xE0C8FF), this.getX(), this.getY() + 0.4, this.getZ(), 24, 0.4, 0.3, 0.4, 0.25);
		if (this.shard) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			Shardling piece = (Shardling) this.getType().create(level, EntitySpawnReason.REINFORCEMENT);
			if (piece != null) {
				piece.makeShard();
				piece.snapTo(this.getX() + (i - 0.5) * 0.6, this.getY() + 0.2, this.getZ(), this.random.nextFloat() * 360, 0);
				piece.setDeltaMovement((i - 0.5) * 0.4, 0.35, (this.random.nextFloat() - 0.5) * 0.4);
				piece.setTarget(this.getTarget());
				level.addFreshEntity(piece);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Shard", this.shard);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		if (input.getBooleanOr("Shard", false)) {
			this.shard = true;
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.3F, 1.6F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}
}
