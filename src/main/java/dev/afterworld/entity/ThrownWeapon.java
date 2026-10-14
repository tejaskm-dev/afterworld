package dev.afterworld.entity;

import dev.afterworld.edict.Edict;
import dev.afterworld.edict.Zones;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Edict of Return made physical: a hurled weapon that flies out, pierces, and comes home to
 * the slot it left. Never persisted; every exit path hands the item back so nothing is lost.
 */
public class ThrownWeapon extends Entity {
	private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> ITEM =
		SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> SPECTRAL = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BOOLEAN);
	private static final List<ThrownWeapon> IN_FLIGHT = new ArrayList<>();

	private UUID ownerId;
	private int slot = -1;
	private float damage = 4;
	private Vec3 direction = Vec3.ZERO;
	private int outTicks;
	private int maxOut = 11;
	private boolean returning;
	private boolean delivered;
	private int pierce = 4;
	private final IntOpenHashSet hit = new IntOpenHashSet();
	private Vec3 orbitCenter;
	private long orbitUntil;
	private double orbitRadius;
	private int orbitTick;
	/** Return III: foes it may still leap to, and whether it lands home with a shockwave. */
	private int ricochet;
	private boolean shockwave;

	public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static ThrownWeapon launch(ServerPlayer owner, ItemStack stack, int slot, float damage, float power, boolean spectral) {
		ServerLevel level = owner.level();
		ThrownWeapon w = new ThrownWeapon(AwEntities.THROWN_WEAPON, level);
		w.ownerId = owner.getUUID();
		w.slot = slot;
		w.damage = damage;
		w.direction = owner.getLookAngle().normalize();
		w.maxOut = Math.round(11 * Math.max(0.6F, power));
		w.setItem(stack);
		w.entityData.set(SPECTRAL, spectral);
		Vec3 eye = owner.getEyePosition();
		w.setPos(eye.x + w.direction.x * 0.6, eye.y - 0.25 + w.direction.y * 0.6, eye.z + w.direction.z * 0.6);
		w.setDeltaMovement(w.direction.scale(1.5));
		level.addFreshEntity(w);
		if (!spectral) {
			IN_FLIGHT.add(w);
		}
		Fx.sound(level, owner.position(), SoundEvents.TRIDENT_THROW.value(), 0.9F, spectral ? 1.5F : 1.1F);
		dev.afterworld.util.Vfx.trail(level, w, 0.45F, spectral ? Edict.ECHOES.color : Edict.RETURN.color, 60);
		return w;
	}

	/** Called when a player leaves or the server stops: every weapon in the air goes home now. */
	public static void deliverAll(UUID owner) {
		for (ThrownWeapon w : new ArrayList<>(IN_FLIGHT)) {
			if (owner == null || owner.equals(w.ownerId)) {
				ServerPlayer p = w.owner();
				if (p != null) {
					w.deliver(p);
				} else {
					w.dropAsItem();
				}
				w.discard();
			}
		}
	}

	/** Return III: the weapon leaps from foe to foe up to {@code times} times, then comes home with a shockwave. */
	public void ricochet(int times) {
		this.ricochet = times;
		this.shockwave = true;
		this.pierce = Math.max(this.pierce, times + 1);
	}

	public ItemStack getItem() {
		return this.entityData.get(ITEM);
	}

	private void setItem(ItemStack stack) {
		this.entityData.set(ITEM, stack.copy());
	}

	public boolean isSpectral() {
		return this.entityData.get(SPECTRAL);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ITEM, ItemStack.EMPTY);
		builder.define(SPECTRAL, false);
	}

	private ServerPlayer owner() {
		if (ownerId == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		Player p = sl.getServer().getPlayerList().getPlayer(ownerId);
		return p instanceof ServerPlayer sp ? sp : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			setPos(position().add(getDeltaMovement()));
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || !owner.isAlive() || owner.level() != level || tickCount > 240) {
			if (owner != null && owner.isAlive()) {
				deliver(owner);
			} else {
				dropAsItem();
			}
			discard();
			return;
		}

		Vec3 velocity;
		long now = level.getGameTime();
		if (orbitUntil > now && orbitCenter != null) {
			orbitTick++;
			double angle = orbitTick * 0.45;
			Vec3 next = orbitCenter.add(Math.cos(angle) * orbitRadius, 0.4 + Math.sin(orbitTick * 0.2) * 0.6, Math.sin(angle) * orbitRadius);
			velocity = next.subtract(position());
			if (orbitTick % 8 == 0) {
				hit.clear();
			}
		} else if (!returning) {
			outTicks++;
			velocity = direction.scale(1.5);
			HitResult blockHit = level.clip(new ClipContext(position(), position().add(velocity), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (blockHit.getType() != HitResult.Type.MISS || outTicks >= maxOut) {
				startReturn();
				if (blockHit.getType() != HitResult.Type.MISS) {
					Fx.burst(level, ParticleTypes.CRIT, blockHit.getLocation(), 6, 0.1, 0.2);
					Fx.sound(level, blockHit.getLocation(), SoundEvents.TRIDENT_HIT_GROUND, 0.7F, 1.2F);
					velocity = Vec3.ZERO;
				}
			}
			Zones.Zone rift = Zones.riftAt(level, position());
			if (rift != null && orbitCenter == null && !isSpectral()) {
				orbitCenter = rift.center;
				orbitUntil = rift.end;
				orbitRadius = Math.max(1.5, rift.radius * 0.6);
				Progress.codex(owner, "combo.gravity_orbit");
			}
		} else {
			Vec3 target = owner.getEyePosition().subtract(0, 0.5, 0);
			Vec3 to = target.subtract(position());
			double dist = to.length();
			if (dist < 1.3) {
				deliver(owner);
				discard();
				return;
			}
			double speed = Math.min(2.2, 0.9 + tickCount * 0.04);
			velocity = to.scale(Math.min(speed, dist) / dist);
		}

		strike(level, owner, velocity);
		setDeltaMovement(velocity);
		setPos(position().add(velocity));
		if (tickCount % 2 == 0) {
			Fx.burst(level, isSpectral() ? Fx.dust(Edict.ECHOES.color, 1.0F) : Fx.dust(Edict.RETURN.color, 0.8F), position(), 1, 0.05, 0);
		}
	}

	private void startReturn() {
		returning = true;
		hit.clear();
	}

	private void strike(ServerLevel level, ServerPlayer owner, Vec3 velocity) {
		if (pierce <= 0 && !returning && orbitUntil <= level.getGameTime()) {
			startReturn();
		}
		AABB box = getBoundingBox().expandTowards(velocity).inflate(0.45);
		LivingEntity struck = null;
		for (Entity e : level.getEntities(this, box, e -> e instanceof LivingEntity && e.isAlive() && e != owner)) {
			if (hit.contains(e.getId()) || MobMark.has(e, MobMark.ECHO) && MobMark.of(e).ownedBy(owner.getUUID())) {
				continue;
			}
			if (e instanceof Player p && (!owner.canHarmPlayer(p))) {
				continue;
			}
			hit.add(e.getId());
			LivingEntity living = (LivingEntity) e;
			DamageSource source = level.damageSources().thrown(this, owner);
			if (living.hurtServer(level, source, damage)) {
				Vec3 push = velocity.lengthSqr() > 1e-4 ? velocity.normalize() : direction;
				living.knockback(0.35, -push.x, -push.z, source, damage);
				Fx.sound(level, living.position(), SoundEvents.TRIDENT_HIT, 0.8F, 1.3F);
				dev.afterworld.util.Vfx.flare(level, living.position().add(0, living.getBbHeight() * 0.6, 0), 0.9F, true, Edict.RETURN.color, 8);
				dev.afterworld.edict.EdictSystem.onEdictHit(owner, living, Edict.RETURN);
				struck = living;
			}
			if (!returning) {
				pierce--;
			}
		}
		if (struck != null && ricochet > 0 && !returning) {
			LivingEntity next = nextFoe(level, owner, struck);
			if (next != null) {
				ricochet--;
				direction = next.getBoundingBox().getCenter().subtract(position()).normalize();
				outTicks = 0;
				maxOut = 16;
				dev.afterworld.util.Vfx.beam(level, position(), next.getBoundingBox().getCenter(), 0.14F, 0, Edict.RETURN.color, 6);
				dev.afterworld.util.Vfx.flare(level, position(), 0.8F, true, Edict.RETURN.color, 7);
				Fx.sound(level, position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6F, 1.6F);
			} else {
				startReturn();
			}
		}
	}

	/** The nearest foe in reach of {@code from} that this throw has not struck yet. */
	private LivingEntity nextFoe(ServerLevel level, ServerPlayer owner, LivingEntity from) {
		LivingEntity best = null;
		double bestDist = 10 * 10;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(10), e -> e.isAlive() && e != owner && e != from
			&& e instanceof net.minecraft.world.entity.monster.Enemy && !hit.contains(e.getId()))) {
			double d = e.distanceToSqr(from);
			if (d < bestDist && from.hasLineOfSight(e)) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private void deliver(ServerPlayer owner) {
		if (delivered) {
			return;
		}
		delivered = true;
		IN_FLIGHT.remove(this);
		if (isSpectral()) {
			return;
		}
		ItemStack stack = getItem().copy();
		if (stack.isEmpty()) {
			return;
		}
		if (stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage() - 1) {
			stack.setDamageValue(stack.getDamageValue() + 1);
		}
		var inv = owner.getInventory();
		if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
			inv.setItem(slot, stack);
		} else {
			dev.afterworld.util.Inv.give(owner, stack);
		}
		Fx.sound(owner.level(), owner.position(), SoundEvents.TRIDENT_RETURN, 0.9F, 1.2F);
		if (shockwave && owner.isAlive() && owner.level() == level()) {
			ServerLevel level = owner.level();
			Vec3 at = owner.position();
			for (LivingEntity e : dev.afterworld.combat.Combat.foes(level, owner, at, 4.5)) {
				e.hurtServer(level, level.damageSources().thrown(this, owner), damage * 0.8F);
				Vec3 push = e.position().subtract(at);
				if (push.lengthSqr() > 1.0E-4) {
					e.knockback(0.8, -push.x, -push.z, level.damageSources().thrown(this, owner), damage);
				}
			}
			Fx.ring(level, Fx.dust(Edict.RETURN.color, 1.6F), at.add(0, 0.2, 0), 4.5, 36);
			Fx.burst(level, ParticleTypes.SWEEP_ATTACK, at.add(0, 1, 0), 6, 1.6, 0);
			Fx.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.5F, 1.6F);
		}
	}

	private void dropAsItem() {
		if (delivered) {
			return;
		}
		delivered = true;
		IN_FLIGHT.remove(this);
		if (isSpectral() || getItem().isEmpty() || !(level() instanceof ServerLevel)) {
			return;
		}
		ItemEntity item = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
		item.setDefaultPickUpDelay();
		level().addFreshEntity(item);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && !delivered) {
			ServerPlayer owner = owner();
			if (owner != null && owner.isAlive()) {
				deliver(owner);
			} else {
				dropAsItem();
			}
		}
		IN_FLIGHT.remove(this);
		super.remove(reason);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64 * 64;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
