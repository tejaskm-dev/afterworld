package dev.afterworld.entity.wild;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.phys.Vec3;

/** A creature of the water. */
public class WildSwimmer extends WaterAnimal implements WildCreature {
	public WildSwimmer(EntityType<? extends WildSwimmer> type, Level level) {
		super(type, level);
		this.moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.02F, 0.1F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 10);
	}

	public static AttributeSupplier.Builder attributes(Species s) {
		return WaterAnimal.createMobAttributes().add(Attributes.MAX_HEALTH, s.health).add(Attributes.MOVEMENT_SPEED, s.speed)
			.add(Attributes.ATTACK_DAMAGE, Math.max(1.0, s.damage)).add(Attributes.ARMOR, s.armor).add(Attributes.FOLLOW_RANGE, 20.0);
	}

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(WildSwimmer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION_AT = SynchedEntityData.defineId(WildSwimmer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(WildSwimmer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(WildSwimmer.class, EntityDataSerializers.BOOLEAN);
	private Species species;
	private Wild.Brain brain;

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, 0);
		builder.define(ACTION_AT, 0);
		builder.define(BURROWED, false);
		builder.define(CLIMBING, false);
	}

	@Override
	public Species species() {
		if (this.species == null) {
			this.species = WildEntities.speciesOf(this.getType());
		}
		return this.species;
	}

	@Override
	public Mob self() {
		return this;
	}

	@Override
	public Wild.Brain brain() {
		if (this.brain == null) {
			this.brain = new Wild.Brain();
		}
		return this.brain;
	}

	@Override
	public int action() {
		return this.entityData.get(ACTION);
	}

	@Override
	public float actionTime(float partial) {
		return this.tickCount + partial - this.entityData.get(ACTION_AT);
	}

	@Override
	public void setAction(int action) {
		this.entityData.set(ACTION, action);
		this.entityData.set(ACTION_AT, this.tickCount);
	}

	@Override
	public boolean burrowed() {
		return this.entityData.get(BURROWED);
	}

	@Override
	public void setBurrowed(boolean burrowed) {
		this.entityData.set(BURROWED, burrowed);
	}

	@Override
	public boolean climbing() {
		return this.entityData.get(CLIMBING);
	}

	@Override
	public void setClimbing(boolean climbing) {
		this.entityData.set(CLIMBING, climbing);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			Wild.tick(this);
			if (species().has(Ability.STATIONARY)) {
				this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity l) {
			Wild.onBite(this, l);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!Wild.onHurt(level, this, source)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			Wild.onDeath(level, this);
		}
		super.die(source);
	}

	@Override
	public void setTarget(LivingEntity target) {
		boolean fresh = target != null && target != this.getTarget();
		super.setTarget(target);
		if (fresh) {
			Wild.onTarget(this, target);
		}
	}

	@Override
	public boolean onClimbable() {
		return species().has(Ability.CLIMB) ? this.climbing() : super.onClimbable();
	}

	@Override
	public boolean isPushable() {
		return !species().has(Ability.STATIONARY) && !this.burrowed() && super.isPushable();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.burrowed() ? null : species().sounds.ambient().get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return species().sounds.hurt().get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return species().sounds.death().get();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * species().sounds.pitch();
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	protected void registerGoals() {
		Species s = species();
		if (s.temper != Species.Temper.PASSIVE) {
			this.goalSelector.addGoal(1, new Wild.AbilityGoal(this));
			this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
		}
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, s == Species.TIDECALLER_JELLY ? 0.4 : 1.0, s == Species.TIDECALLER_JELLY ? 40 : 10));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		Wild.standardTargets(this, this.targetSelector);
	}

	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		this.moveRelative(this.getSpeed(), input);
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
		if (this.getTarget() == null) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
		}
	}
}
