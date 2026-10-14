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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/** A creature of the air: owls and gulls that perch, eagles and vultures that circle, wyrmlings that hunt. */
public class WildFlyer extends PathfinderMob implements WildCreature {
	public WildFlyer(EntityType<? extends WildFlyer> type, Level level) {
		super(type, level);
		Species s = species();
		this.moveControl = new FlyingMoveControl<>(this, 20, s != Species.MOSSMERE_OWL && s != Species.BONE_GULL);
	}

	public static AttributeSupplier.Builder attributes(Species s) {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, s.health).add(Attributes.MOVEMENT_SPEED, s.speed)
			.add(Attributes.FLYING_SPEED, s.speed * 1.8).add(Attributes.ATTACK_DAMAGE, Math.max(1.0, s.damage)).add(Attributes.ARMOR, s.armor)
			.add(Attributes.FOLLOW_RANGE, 28.0);
	}

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(WildFlyer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION_AT = SynchedEntityData.defineId(WildFlyer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(WildFlyer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(WildFlyer.class, EntityDataSerializers.BOOLEAN);
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
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
		Species s = species();
		if (s.temper == Species.Temper.PASSIVE) {
			this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
		} else {
			this.goalSelector.addGoal(1, new Wild.AbilityGoal(this));
			this.goalSelector.addGoal(2, new StrikeGoal());
		}
		if (s == Species.CANYON_VULTURE || s == Species.WINDRIDER_EAGLE || s == Species.ASH_WYRMLING) {
			this.goalSelector.addGoal(4, new CircleGoal());
		}
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		Wild.standardTargets(this, this.targetSelector);
		if (s == Species.CANYON_VULTURE) {
			// Vultures go for whoever is already hurt.
			this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(p, l) -> p.getHealth() < p.getMaxHealth() * 0.4F));
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
	}

	/** Close in and strike, then pull up. */
	private final class StrikeGoal extends Goal {
		private int cooldown;

		StrikeGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = WildFlyer.this.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public void tick() {
			LivingEntity t = WildFlyer.this.getTarget();
			if (t == null) {
				return;
			}
			WildFlyer.this.getLookControl().setLookAt(t, 30.0F, 30.0F);
			double d = WildFlyer.this.distanceToSqr(t);
			if (this.cooldown > 0) {
				this.cooldown--;
			}
			Vec3 hover = t.position().add(0, t.getBbHeight() + (this.cooldown > 0 ? 3.5 : 0.6), 0);
			WildFlyer.this.getMoveControl().setWantedPosition(hover.x, hover.y, hover.z, 1.2);
			if (d < 3.2 && this.cooldown == 0 && WildFlyer.this.level() instanceof ServerLevel level) {
				WildFlyer.this.doHurtTarget(level, t);
				this.cooldown = 30;
			}
		}
	}

	/** Wheeling high over a spot when there is nothing to hunt. */
	private final class CircleGoal extends Goal {
		private Vec3 center;
		private float angle;

		CircleGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return WildFlyer.this.getTarget() == null && WildFlyer.this.getRandom().nextInt(40) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return WildFlyer.this.getTarget() == null && WildFlyer.this.getRandom().nextInt(600) != 0;
		}

		@Override
		public void start() {
			this.center = WildFlyer.this.position().add(0, 8 + WildFlyer.this.getRandom().nextInt(10), 0);
		}

		@Override
		public void tick() {
			this.angle += 0.035F;
			double r = 10;
			WildFlyer.this.getMoveControl().setWantedPosition(this.center.x + Math.cos(this.angle) * r, this.center.y, this.center.z + Math.sin(this.angle) * r, 0.9);
		}
	}
}
