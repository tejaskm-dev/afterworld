package dev.afterworld.entity.boss;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.AwConfig;
import dev.afterworld.registry.AwTags;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Pilgrim Without Feet. Glides a hand's breadth above the ground between its four shrines,
 * wearing a road into the grass. It only fights those who strike it first.
 */
public class PilgrimEntity extends BossMob {
	@Override
	public dev.afterworld.aspect.Aspect aspect() {
		return dev.afterworld.aspect.Aspect.FOOTSTEPS_OF_STONE;
	}

	public static final int ANIM_WALK = 0;
	public static final int ANIM_STOMP_WINDUP = 1;
	public static final int ANIM_STOMP = 2;
	public static final int ANIM_THROW = 3;
	public static final int ANIM_KNEEL = 4;

	private Vec3 waypoint;
	private int restTicks;
	private int calmTicks = 1000;
	private int cooldown = 40;
	private int windup;
	private LivingEntity foe;
	private boolean kneeling;
	private int specialCooldown = 120;
	/** Ticks left aloft in the last act's ascension, and how high it has risen. */
	private int aloft;
	private double lift;

	public PilgrimEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.GREEN);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 800.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.ARMOR_TOUGHNESS, 6.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ATTACK_DAMAGE, 12.0);
	}

	public void setWaypoint(Vec3 waypoint) {
		this.waypoint = waypoint;
	}

	public boolean engaged() {
		return foe != null && calmTicks < 600;
	}

	public void kneel() {
		kneeling = true;
		setAnim(ANIM_KNEEL);
	}

	@Override
	public void travel(Vec3 input) {
		// The Pilgrim does not walk; it is moved.
	}

	@Override
	public void tick() {
		this.noPhysics = true;
		this.setNoGravity(true);
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		calmTicks++;
		if (cooldown > 0) {
			cooldown--;
		}
		Vec3 pos = position();
		double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
		if (aloft > 0) {
			aloft--;
			lift = Math.min(7.0, lift + 0.25);
			if (aloft == 0) {
				land(level);
			}
		} else {
			lift = Math.max(0, lift - 0.6);
		}
		double hover = ground + 0.35 + lift + Math.sin(tickCount * 0.05) * 0.15;
		double dy = (hover - pos.y) * 0.15;
		Vec3 move = Vec3.ZERO;

		if (kneeling) {
			setDeltaMovement(Vec3.ZERO);
			setPos(pos.x, pos.y + dy, pos.z);
			return;
		}
		if (engaged() && foe.isAlive() && foe.level() == level && foe.distanceToSqr(this) < 48 * 48) {
			fight(level, pos);
			double dist = Math.sqrt(foe.distanceToSqr(this));
			if (dist > 9 && windup == 0) {
				Vec3 dir = new Vec3(foe.getX() - pos.x, 0, foe.getZ() - pos.z).normalize();
				move = dir.scale(0.1);
			}
			face(foe.position());
		} else {
			foe = null;
			if (anim() != ANIM_WALK && windup == 0) {
				setAnim(ANIM_WALK);
			}
			if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
				heal(1.0F);
			}
			if (restTicks > 0) {
				restTicks--;
			} else if (waypoint != null) {
				Vec3 to = new Vec3(waypoint.x - pos.x, 0, waypoint.z - pos.z);
				double d = to.length();
				if (d < 3.0) {
					restTicks = 200;
					PilgrimSystem.arrived(this);
				} else {
					move = to.scale(0.12 / d);
					face(waypoint);
					if (tickCount % 20 == 0) {
						footsteps(level);
					}
				}
			}
		}
		setDeltaMovement(move.x, dy, move.z);
		setPos(pos.x + move.x, pos.y + dy, pos.z + move.z);
	}

	private void face(Vec3 target) {
		double dx = target.x - getX();
		double dz = target.z - getZ();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private void fight(ServerLevel level, Vec3 pos) {
		if (inTransition()) {
			if (transition % 10 == 0) {
				Fx.column(level, ParticleTypes.END_ROD, pos, 20, 1.2, getBbHeight() + 3);
			}
			return;
		}
		if (aloft > 0) {
			// Light rains down on the challengers while it hangs in the air.
			if (aloft % 12 == 0 && foe != null) {
				for (ServerPlayer p : playersNear(30)) {
					Vec3 lead = p.position().add(p.getDeltaMovement().multiply(12, 0, 12));
					Hazards.strike(level, this, lead, 2.2, 28, 10.0F, 0xFFF0B0, ParticleTypes.END_ROD);
				}
			}
			return;
		}
		if (specialCooldown > 0) {
			specialCooldown--;
		} else if (phase >= 2 && windup == 0 && foe != null) {
			specialCooldown = phase >= 3 ? 220 : 260;
			if (phase >= 3 && random.nextBoolean()) {
				ascend(level);
			} else {
				road(level);
			}
			return;
		}
		if (windup > 0) {
			windup--;
			if (anim() == ANIM_STOMP_WINDUP && windup % 5 == 0) {
				Fx.ring(level, Fx.dust(0xC4553B, 1.4F), new Vec3(pos.x, pos.y + 0.2, pos.z), 6.0, 28);
				Fx.hostileSound(level, pos, SoundEvents.STONE_HIT, 1.5F, 0.4F + (25 - windup) * 0.02F);
			}
			if (windup == 0) {
				if (anim() == ANIM_STOMP_WINDUP) {
					stomp(level, pos);
				} else if (anim() == ANIM_THROW) {
					throwBoulder(level);
				}
			}
			return;
		}
		if (cooldown > 0) {
			return;
		}
		double dist = Math.sqrt(foe.distanceToSqr(this));
		if (dist < 7.5) {
			setAnim(ANIM_STOMP_WINDUP);
			windup = 25;
			cooldown = 70;
		} else if (dist < 36) {
			setAnim(ANIM_THROW);
			windup = 14;
			cooldown = 90;
		}
	}

	/** A road of light is drawn across the ground; when the light runs out, everything off it burns. */
	private void road(ServerLevel level) {
		Vec3 center = foe.position();
		java.util.List<Vec3> path = new java.util.ArrayList<>();
		Vec3 p = position();
		path.add(new Vec3(p.x, center.y, p.z));
		double heading = Math.atan2(center.z - p.z, center.x - p.x);
		Vec3 at = new Vec3(p.x, center.y, p.z);
		for (int i = 0; i < 6; i++) {
			heading += (random.nextDouble() - 0.5) * 1.6;
			at = at.add(Math.cos(heading) * 6, 0, Math.sin(heading) * 6);
			// Keep the road within reach of the challenger.
			if (at.distanceTo(center) > 12) {
				heading = Math.atan2(center.z - at.z, center.x - at.x);
			}
			path.add(at);
		}
		setAnim(ANIM_STOMP_WINDUP);
		windup = 30;
		Hazards.road(level, this, center, 18, path, 1.9, 80, 15.0F, 0xFFF0B0, 0xC4553B);
		Fx.hostileSound(level, position(), AwSounds.BOSS_ROAR, 2.5F, 0.6F);
		for (ServerPlayer pl : playersNear(30)) {
			pl.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.afterworld.pilgrim_road").withColor(0xFFF0B0));
		}
	}

	/** It rises into the air and lets the light fall, then comes down like a hammer. */
	private void ascend(ServerLevel level) {
		aloft = 110;
		Fx.hostileSound(level, position(), AwSounds.BOSS_ROAR, 3.0F, 1.2F);
		Fx.column(level, ParticleTypes.END_ROD, position(), 60, 1.5, 8);
	}

	private void land(ServerLevel level) {
		Vec3 pos = position().subtract(0, lift, 0);
		Fx.hostileSound(level, pos, AwSounds.BOSS_STOMP, 3.0F, 0.6F);
		Fx.shake(level, pos, 40, 5.0F, 20);
		Hazards.wave(level, this, pos, 18, 0.6, 12.0F, 0xFFF0B0);
		Hazards.wave(level, this, pos, 18, 0.4, 10.0F, 0xC4553B);
	}

	private void stomp(ServerLevel level, Vec3 pos) {
		setAnim(ANIM_STOMP);
		if (phase >= 2) {
			Hazards.wave(level, this, pos, 14, 0.6, 9.0F, 0xC4553B);
			Fx.shake(level, pos, 32, 3.0F, 12);
		}
		BlockState under = level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.6, pos.z));
		if (under.isAir()) {
			under = Blocks.DIRT.defaultBlockState();
		}
		Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, under), pos, 60, 3.0, 0.3);
		Fx.burst(level, ParticleTypes.EXPLOSION, pos, 3, 2.0, 0);
		Fx.hostileSound(level, pos, AwSounds.BOSS_STOMP, 2.0F, 0.8F);
		Fx.hostileSound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.6F);
		DamageSource src = level.damageSources().mobAttack(this);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.5, 2, 6.5), e -> e != this && e.isAlive())) {
			if (e instanceof Player p && !isTarget(p)) {
				continue;
			}
			if (e.hurtServer(level, src, 11.0F)) {
				Vec3 d = e.position().subtract(pos).normalize();
				e.knockback(1.6, -d.x, -d.z, src, 11.0F);
				e.push(0, 0.5, 0);
			}
		}
	}

	private void throwBoulder(ServerLevel level) {
		if (foe == null) {
			return;
		}
		HeavyProjectile rock = new HeavyProjectile(level, this, new ItemStack(Items.MOSSY_COBBLESTONE), 9.0F);
		rock.setPos(getX(), getY() + 4.2, getZ());
		Vec3 to = foe.getEyePosition().subtract(rock.position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		rock.shoot(to.x, to.y + horizontal * 0.22, to.z, 1.35F, 2.0F);
		level.addFreshEntity(rock);
		Fx.hostileSound(level, position(), SoundEvents.WITHER_SHOOT, 0.6F, 0.4F);
	}

	private void footsteps(ServerLevel level) {
		if (!AwConfig.get().pilgrimTerrainTrail) {
			return;
		}
		BlockPos c = BlockPos.containing(getX(), getY() - 0.8, getZ());
		if (level.getChunk(c).getInhabitedTime() > AwConfig.get().protectedChunkInhabitedTicks) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz)).below();
				BlockState s = level.getBlockState(p);
				if (s.is(AwTags.PILGRIM_TRAMPLES) && level.getBlockState(p.above()).isAir()) {
					level.setBlock(p, (dx == 0 && dz == 0) || random.nextBoolean() ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 3);
				}
			}
		}
		if (tickCount % 60 == 0) {
			Fx.hostileSound(level, position(), AwSounds.BOSS_STOMP, 1.6F, 1.0F);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (PilgrimSystem.atPeace(level) || kneeling) {
			if (source.getEntity() instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.translatable("message.afterworld.pilgrim_peace").withColor(0xB5AE98));
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
			if (foe == null) {
				Fx.hostileSound(level, position(), AwSounds.BOSS_ROAR, 1.6F, 0.7F);
				if (attacker instanceof ServerPlayer p) {
					p.sendSystemMessage(Component.translatable("message.afterworld.pilgrim_wakes").withColor(0xB5AE98));
				}
			}
			foe = attacker;
			calmTicks = 0;
			PilgrimSystem.provoked(this, attacker);
		}
		return hurt;
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		specialCooldown = 50;
	}

	@Override
	protected void onReset(ServerLevel level) {
		specialCooldown = 120;
		aloft = 0;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			PilgrimSystem.died(level, this, source);
			if (source.getEntity() instanceof net.minecraft.world.entity.player.Player) {
				this.spawnAtLocation(level, new ItemStack(dev.afterworld.registry.AwItems.PILGRIMS_STAFF), 1.0F);
			}
		}
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}
}
