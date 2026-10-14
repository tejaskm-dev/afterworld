package dev.afterworld.entity;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwParticles;
import dev.afterworld.util.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

/** A shard of living crystal flung by the Shardsong Glaive. It flies straight and shatters on what it hits. */
public class CrystalShard extends ThrowableItemProjectile {
	private float damage = 6.0F;

	public CrystalShard(EntityType<? extends CrystalShard> type, Level level) {
		super(type, level);
	}

	public CrystalShard(Level level, LivingEntity owner, float damage) {
		super(AwEntities.CRYSTAL_SHARD, owner, level, new ItemStack(Items.AMETHYST_SHARD));
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.AMETHYST_SHARD;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.005;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 2 == 0) {
			level.sendParticles(AwParticles.of(AwParticles.SHARD, 0xC8A8FF), this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
		}
		if (this.tickCount > 60) {
			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && target != this.getOwner()) {
			target.hurtServer(level, level.damageSources().mobProjectile(this, this.getOwner() instanceof LivingEntity l ? l : null), this.damage);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			Fx.burst(level, AwParticles.of(AwParticles.SHARD, 0xE0C8FF), this.position(), 10, 0.2, 0.15);
			Fx.sound(level, this.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.3F);
			this.discard();
		}
	}
}
