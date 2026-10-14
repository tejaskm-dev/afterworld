package dev.afterworld.entity.mount;

import dev.afterworld.player.PlayerState;
import dev.afterworld.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Duskstrider: a long-legged stag of the dusk roads that answers the Wayfarer's Whistle.
 * Faster than any horse, sure-footed on hillsides, and it fades back into the dusk when you
 * leave it alone for a minute. Only the one who whistled may ride it.
 */
public class Duskstrider extends AbstractHorse {
	private int idle;

	public Duskstrider(EntityType<? extends Duskstrider> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return AbstractHorse.createBaseHorseAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.MOVEMENT_SPEED, 0.34)
			.add(Attributes.JUMP_STRENGTH, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.5)
			.add(Attributes.SAFE_FALL_DISTANCE, 12.0);
	}

	@Override
	public boolean isSaddled() {
		return true;
	}

	@Override
	protected boolean canPerformRearing() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isVehicle()) {
			return InteractionResult.PASS;
		}
		LivingEntity owner = this.getOwner();
		if (owner != null && owner != player) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide()) {
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void openCustomInventoryScreen(Player player) {
		// A Duskstrider carries nothing; the inventory key shows your own pack instead (nothing to open here).
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.isVehicle()) {
			this.idle = 0;
			if (this.getDeltaMovement().horizontalDistanceSqr() > 0.04 && this.tickCount % 2 == 0) {
				Fx.burst(level, Fx.dust(0x6A5A9A, 1.0F), this.position().add(0, 0.3, 0), 2, 0.4, 0.0);
			}
			if (this.getFirstPassenger() instanceof ServerPlayer rider) {
				rider.fallDistance = 0;
			}
			return;
		}
		LivingEntity owner = this.getOwner();
		if (++this.idle > 1200 || owner == null || owner.distanceToSqr(this) > 96 * 96) {
			fade(level);
		}
	}

	/** Back into the dusk it came from. */
	public void fade(ServerLevel level) {
		Fx.burst(level, Fx.dust(0x6A5A9A, 1.5F), this.position().add(0, 1, 0), 40, 0.6, 0.05);
		Fx.burst(level, ParticleTypes.SMOKE, this.position().add(0, 1, 0), 10, 0.4, 0.02);
		Fx.sound(level, this.position(), SoundEvents.HORSE_BREATHE, 1.0F, 0.6F);
		if (this.getOwner() instanceof ServerPlayer p && this.getUUID().equals(PlayerState.of(p).mount)) {
			PlayerState.of(p).mount = null;
		}
		this.discard();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HORSE_BREATHE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HORSE_DEATH;
	}
}
