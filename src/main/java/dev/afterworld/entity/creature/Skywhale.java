package dev.afterworld.entity.creature;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Whales that swim the sky. They drift high over the land in slow arcs, glowing faintly along the
 * belly, and sing to each other across the valleys. Nothing can hurt them, and they hurt nothing.
 */
public class Skywhale extends Mob {
	/** How often each player's sky is checked for whales, in ticks. */
	private static final int CHECK = 400;
	private float heading;
	private float turn;
	private double cruise;
	private int songIn = 200;

	public Skywhale(EntityType<? extends Skywhale> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.heading = level.getRandom().nextFloat() * Mth.TWO_PI;
		this.cruise = 190 + level.getRandom().nextInt(40);
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 300.0).add(Attributes.MOVEMENT_SPEED, 0.1);
	}

	/** Brings whales into the sky above players who are outdoors, a pair at most within sight. */
	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK != 0) {
				return;
			}
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator() || p.getY() < 50 || level.getRandom().nextFloat() > 0.35F) {
					continue;
				}
				int near = level.getEntities(AwEntities.SKYWHALE, p.getBoundingBox().inflate(220, 400, 220), e -> true).size();
				if (near >= 2) {
					continue;
				}
				double ang = level.getRandom().nextDouble() * Math.PI * 2;
				double dist = 110 + level.getRandom().nextDouble() * 50;
				int x = (int) (p.getX() + Math.cos(ang) * dist);
				int z = (int) (p.getZ() + Math.sin(ang) * dist);
				if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, 64, z))) {
					continue;
				}
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				Skywhale whale = AwEntities.SKYWHALE.create(level, EntitySpawnReason.NATURAL);
				if (whale != null) {
					whale.cruise = Math.max(ground + 70, 185 + level.getRandom().nextInt(45));
					whale.snapTo(x, whale.cruise, z, 0, 0);
					// Head roughly across the player's sky rather than straight away.
					whale.heading = (float) (ang + Math.PI * 0.5 + (level.getRandom().nextDouble() - 0.5));
					level.addFreshEntity(whale);
				}
			}
		});
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount % 200 == 0) {
			this.turn = (this.random.nextFloat() - 0.5F) * 0.006F;
		}
		this.heading += this.turn;
		double speed = 0.16;
		Vec3 ahead = this.position().add(Math.cos(this.heading) * 24, 0, Math.sin(this.heading) * 24);
		if (level.hasChunkAt(net.minecraft.core.BlockPos.containing(ahead))) {
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) ahead.x, (int) ahead.z);
			if (ground + 40 > this.cruise) {
				this.cruise = ground + 50;
			}
		}
		double bob = Math.sin(this.tickCount * 0.02) * 0.02;
		double dy = Mth.clamp((this.cruise - this.getY()) * 0.01, -0.05, 0.05) + bob;
		Vec3 v = new Vec3(Math.cos(this.heading) * speed, dy, Math.sin(this.heading) * speed);
		this.setDeltaMovement(v);
		this.move(MoverType.SELF, v);
		this.setYRot((float) (Math.toDegrees(this.heading)) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		if (--this.songIn <= 0) {
			this.songIn = 500 + this.random.nextInt(700);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), AwSounds.MOB_WHALE.value(), SoundSource.AMBIENT, 9.0F, 0.85F + this.random.nextFloat() * 0.3F);
		}
		// Gone once nobody is near enough to watch.
		if (this.tickCount % 100 == 0 && level.getNearestPlayer(this, 300) == null) {
			this.discard();
		}
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distSqr) {
		return distSqr < 400 * 400;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return AwSounds.voice("whale").hurt();
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return AwSounds.voice("whale").death();
	}

	/** Something this size is heard a long way off. */
	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}
}
