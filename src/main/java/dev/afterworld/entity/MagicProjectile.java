package dev.afterworld.entity;

import dev.afterworld.combat.Projectiles;
import dev.afterworld.registry.AwEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Star bolts and thrown bombs: one small entity whose flight and burst are decided by
 * {@link Projectiles}. It is drawn as the item it was made from.
 */
public class MagicProjectile extends ThrowableItemProjectile {
	public static final int STAR = 0;
	public static final int SPLINTER = 1;
	public static final int SMOKE_BOMB = 2;
	public static final int FROST_BOMB = 3;
	public static final int THUNDER_BOMB = 4;
	/** Creatures' shots. */
	public static final int FIRE_SPIT = 5;
	public static final int WEB = 6;
	public static final int CHILL = 7;
	public static final int ROCK = 8;
	public static final int HARPOON = 9;

	public boolean fromCreature() {
		return this.kind >= FIRE_SPIT;
	}

	public int kind;
	public int tier = 1;
	public float damage = 4.0F;

	public MagicProjectile(EntityType<? extends MagicProjectile> type, Level level) {
		super(type, level);
	}

	public MagicProjectile(Level level, LivingEntity owner, ItemStack look, int kind, int tier, float damage) {
		super(AwEntities.MAGIC_PROJECTILE, owner, level, look);
		this.kind = kind;
		this.tier = tier;
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.NETHER_STAR;
	}

	@Override
	protected double getDefaultGravity() {
		return switch (this.kind) {
			case STAR, SPLINTER, CHILL -> 0.0;
			case FIRE_SPIT -> 0.008;
			case WEB -> 0.02;
			default -> 0.05;
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			Projectiles.flight(level, this);
			if (this.tickCount > (this.kind <= SPLINTER ? 50 : fromCreature() ? 80 : 120)) {
				this.discard();
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && target != this.getOwner() && this.kind <= SPLINTER) {
			target.hurtServer(level, level.damageSources().indirectMagic(this, this.getOwner()), this.damage);
		}
		if (this.level() instanceof ServerLevel level && target != this.getOwner() && fromCreature() && target instanceof LivingEntity l) {
			LivingEntity owner = this.getOwner() instanceof LivingEntity o ? o : null;
			if (owner instanceof dev.afterworld.entity.wild.WildCreature w && l instanceof dev.afterworld.entity.wild.WildCreature lw && w.species() == lw.species()) {
				return;
			}
			l.hurtServer(level, level.damageSources().mobProjectile(this, owner), this.damage);
			switch (this.kind) {
				case FIRE_SPIT -> l.igniteForSeconds(3.0F);
				case WEB -> l.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 70, 2), owner);
				case CHILL -> {
					l.setTicksFrozen(Math.min(l.getTicksRequiredToFreeze() + 60, l.getTicksFrozen() + 80));
					l.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1), owner);
				}
				case ROCK -> l.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 0), owner);
				case HARPOON -> {
					if (owner != null) {
						Vec3 pull = owner.position().subtract(l.position()).normalize().scale(0.9);
						l.push(pull.x, 0.3, pull.z);
						l.syncVelocity = true;
					}
				}
				default -> {
				}
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			Vec3 at = hit.getLocation();
			Projectiles.burst(level, this, at, hit instanceof EntityHitResult e ? e.getEntity() : null);
			this.discard();
		}
	}
}
