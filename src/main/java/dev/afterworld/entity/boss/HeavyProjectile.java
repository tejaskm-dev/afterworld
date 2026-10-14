package dev.afterworld.entity.boss;

import dev.afterworld.registry.AwEntities;
import dev.afterworld.util.Fx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A thrown boulder (the Pilgrim) or a thrown bell (the Bell-Ringer). Heavy, slow, and it hurts. */
public class HeavyProjectile extends ThrowableItemProjectile {
	private float damage = 9.0F;
	/** Only for show (a bell falling in a bell rain): hurts nothing and leaves nothing behind. */
	private boolean prop;

	public HeavyProjectile(EntityType<? extends HeavyProjectile> type, Level level) {
		super(type, level);
	}

	public HeavyProjectile(Level level, LivingEntity owner, ItemStack look, float damage) {
		super(AwEntities.HEAVY_PROJECTILE, owner, level, look);
		this.damage = damage;
	}

	public HeavyProjectile asProp() {
		this.prop = true;
		this.damage = 0;
		return this;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.MOSSY_COBBLESTONE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && target != getOwner() && !this.prop) {
			target.hurtServer(level, level.damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), damage);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()), position(), 24, 0.4, 0.15);
			Fx.hostileSound(level, position(), SoundEvents.STONE_BREAK, 1.2F, 0.6F);
			if (getItem().is(Items.BELL) && !this.prop) {
				Fx.hostileSound(level, position(), SoundEvents.BELL_BLOCK, 1.5F, 0.5F);
				BellRingerSystem.bellLanded(level, this);
			}
			discard();
		}
	}
}
