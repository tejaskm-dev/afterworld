package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import net.minecraft.world.phys.Vec3;

/**
 * Something that walks a step behind the world. It is never quite where it was: it steps out of
 * its own echo at your back, and its touch leaves you a heartbeat slow. Daylight thins it.
 */
public class EchoStalker extends Monster {
	private int blinkCooldown = 60;

	public EchoStalker(EntityType<? extends EchoStalker> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 3 == 0) {
			level.sendParticles(AwParticles.of(AwParticles.INK, 0x2A2244), this.getX(), this.getY() + 1.6, this.getZ(), 1, 0.3, 0.8, 0.3, 0.0);
		}
		// Daylight thins it.
		if (level.isBrightOutside() && level.canSeeSky(this.blockPosition()) && this.tickCount % 20 == 0) {
			this.hurtServer(level, this.damageSources().magic(), 1.0F);
		}
		LivingEntity t = this.getTarget();
		if (t != null && --this.blinkCooldown <= 0 && this.distanceToSqr(t) < 24 * 24) {
			this.blinkCooldown = 90 + this.random.nextInt(60);
			blinkBehind(level, t);
		}
	}

	/** Steps out of its echo two blocks behind the target, if there is room. */
	private void blinkBehind(ServerLevel level, LivingEntity t) {
		Vec3 back = t.getLookAngle().multiply(1, 0, 1).normalize().scale(-2.2);
		Vec3 to = t.position().add(back);
		Vec3 from = this.position();
		if (!this.randomTeleport(to.x, t.getY(), to.z, false, state -> false)) {
			return;
		}
		for (int i = 0; i <= 12; i++) {
			Vec3 p = from.lerp(this.position(), i / 12.0);
			level.sendParticles(AwParticles.of(AwParticles.INK, 0x6A4AB0), p.x, p.y + 1.3, p.z, 3, 0.2, 0.6, 0.2, 0.0);
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.3, from.z, 30, 0.3, 0.9, 0.3, 0.05);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 0.8F, 0.55F);
		this.lookAt(t, 180, 180);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), this);
		}
		return hit;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dev.afterworld.registry.AwSounds.voice("stalker").ambient();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return dev.afterworld.registry.AwSounds.voice("stalker").hurt();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.voice("stalker").death();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	public int getAmbientSoundInterval() {
		return 220;
	}
}
