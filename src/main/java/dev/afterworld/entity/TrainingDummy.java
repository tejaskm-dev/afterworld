package dev.afterworld.entity;

import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Marta's training dummy: straw and an old club on a post. It swings slow and honest at whoever
 * stands in front of it, so a traveller can learn to roll out of a blow and to turn one aside.
 * Strike it and it takes the blow (numbers and all) and is whole again a moment later.
 */
public class TrainingDummy extends PathfinderMob {
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION_AT = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
	private int windup = -1;
	private int rest = 40;
	private ServerPlayer mark;

	public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, 0);
		builder.define(ACTION_AT, 0);
	}

	public int action() {
		return this.entityData.get(ACTION);
	}

	public float actionTime(float partial) {
		return this.tickCount + partial - this.entityData.get(ACTION_AT);
	}

	private void setAction(int a) {
		this.entityData.set(ACTION, a);
		this.entityData.set(ACTION_AT, this.tickCount);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.windup >= 0) {
			if (this.mark != null) {
				this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, this.mark.getEyePosition());
			}
			if (this.windup % 4 == 0) {
				level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.6, this.getZ(), 3, 0.3, 0.2, 0.3, 0.05);
			}
			if (--this.windup < 0) {
				swing(level);
			}
			return;
		}
		if (--this.rest > 0) {
			return;
		}
		ServerPlayer p = (ServerPlayer) level.getNearestPlayer(this, 3.5);
		if (p != null && !p.isCreative() && !p.isSpectator()) {
			this.mark = p;
			this.windup = 16;
			this.setAction(2);
			this.playSound(SoundEvents.ARMOR_STAND_HIT, 1.0F, 0.6F);
			this.playSound(SoundEvents.WOODEN_DOOR_CLOSE, 0.5F, 0.6F);
		} else {
			this.rest = 20;
		}
	}

	private void swing(ServerLevel level) {
		this.setAction(3);
		this.rest = 34;
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
		ServerPlayer p = this.mark;
		this.mark = null;
		if (p == null || !p.isAlive() || p.distanceToSqr(this) > 3.9 * 3.9) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		long now = level.getGameTime();
		boolean guarded = s.dodgeUntil > now || s.parryUntil > now;
		p.hurtServer(level, level.damageSources().mobAttack(this), 1.0F);
		if (!guarded) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.dummy_hit").withColor(0xE8A33D));
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hit = super.hurtServer(level, source, amount);
		if (hit) {
			this.playSound(SoundEvents.ARMOR_STAND_HIT, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.2, this.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
			this.setHealth(this.getMaxHealth());
		}
		return hit;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.getEntity() == null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
	}

	// ---------------------------------------------------------------- keeping one in Marta's yard

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TrainingDummy::tickYard);
	}

	private static void tickYard(MinecraftServer server) {
		if (server.getTickCount() % 200 != 37) {
			return;
		}
		for (var marta : Npcs.loaded(Npc.MARTA)) {
			if (!(marta.level() instanceof ServerLevel level)) {
				continue;
			}
			if (!level.getEntitiesOfClass(TrainingDummy.class, marta.getBoundingBox().inflate(14)).isEmpty()) {
				continue;
			}
			BlockPos spot = findSpot(level, marta.blockPosition());
			if (spot == null) {
				continue;
			}
			TrainingDummy d = AwEntities.TRAINING_DUMMY.create(level, EntitySpawnReason.STRUCTURE);
			if (d != null) {
				Vec3 face = marta.position().subtract(Vec3.atBottomCenterOf(spot));
				float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
				d.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0);
				level.addFreshEntity(d);
				Fx.burst(level, ParticleTypes.POOF, d.position().add(0, 1, 0), 8, 0.3, 0.02);
			}
		}
	}

	/** Open, flat ground four to six blocks from Marta. */
	private static BlockPos findSpot(ServerLevel level, BlockPos near) {
		for (int r = 4; r <= 7; r++) {
			for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos p = near.relative(d, r).above(dy);
					if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).isAir()
						&& level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) {
						return p;
					}
				}
			}
		}
		return null;
	}

	/** For the client model. */
	public static boolean is(Mob m) {
		return m instanceof TrainingDummy;
	}
}
