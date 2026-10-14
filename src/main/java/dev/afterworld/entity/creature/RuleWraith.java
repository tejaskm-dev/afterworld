package dev.afterworld.entity.creature;

import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hooded thing that feeds on rules. Its touch steals one of your equipped Edicts for a while.
 * It drifts above the ground at night and burns away at dawn.
 */
public class RuleWraith extends Monster {
	public RuleWraith(EntityType<? extends RuleWraith> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22.0).add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.FLYING_SPEED, 0.45).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 24.0);
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
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof ServerPlayer p) {
			PlayerState s = PlayerState.of(p);
			long now = level.getGameTime();
			int slot = -1;
			int count = 0;
			for (int i = 0; i < s.slotCount(); i++) {
				boolean free = (s.suppressedMask & (1 << i)) == 0 || s.suppressedUntil <= now;
				if (s.slots[i] != null && free && this.random.nextInt(++count) == 0) {
					slot = i;
				}
			}
			if (slot >= 0) {
				if (s.suppressedUntil <= now) {
					s.suppressedMask = 0;
				}
				s.suppressedMask |= 1 << slot;
				s.suppressedUntil = Math.max(s.suppressedUntil, now + 160);
				s.hudDirty = true;
				Edict e = s.slots[slot];
				p.sendOverlayMessage(Component.translatable("message.afterworld.wraith_steals", e.displayName()).withColor(0xB79CE8));
				level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
				level.sendParticles(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.INK, 0x2A2440), p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.5, 0.4, 0.01);
				level.sendParticles(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.SHARD, e.color), p.getX(), p.getY() + 1.4, p.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
				this.playSound(AwSounds.EDICT_FORGOTTEN.value(), 0.8F, 0.7F);
				Progress.codex(p, "creature.rule_wraith");
			}
		}
		return hit;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			if (this.random.nextInt(4) == 0) {
				this.level().addParticle(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.INK, 0x2A2440),
					this.getRandomX(0.5), this.getY() + 0.2 + this.random.nextDouble() * 0.5, this.getRandomZ(0.5), 0, -0.01, 0);
			}
			if (this.random.nextInt(7) == 0) {
				this.level().addParticle(dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.RUNE, 0xB79CE8),
					this.getRandomX(0.8), this.getY() + 1.6 + this.random.nextDouble() * 0.4, this.getRandomZ(0.8), 0, 0.02, 0);
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AwSounds.MOB_WRAITH.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VEX_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}
}
