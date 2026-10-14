package dev.afterworld.mixin;

import dev.afterworld.edict.EdictSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** One hook for every rule that scales incoming damage (suspension, ambush, machinery...). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float afterworld$scaleDamage(float damage, ServerLevel level, DamageSource source) {
		return EdictSystem.scaleDamage((LivingEntity) (Object) this, source, damage);
	}

	/** The Mirewalk Gift: sprinting travellers run across water. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void afterworld$waterWalk(net.minecraft.world.level.material.FluidState fluid,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof net.minecraft.world.entity.player.Player p && fluid.is(net.minecraft.tags.FluidTags.WATER)
			&& dev.afterworld.movement.Moves.canWaterWalk(p)) {
			cir.setReturnValue(true);
		}
	}
}
