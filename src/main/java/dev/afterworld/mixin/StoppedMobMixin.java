package dev.afterworld.mixin;

import dev.afterworld.registry.AwEffects;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob caught in a stopped moment does not think at all until the moment passes. */
@Mixin(Mob.class)
public abstract class StoppedMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void afterworld$stopped(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (self.hasEffect(AwEffects.STOPPED)) {
			self.setDeltaMovement(0, Math.min(0, self.getDeltaMovement().y), 0);
			self.setXxa(0);
			self.setZza(0);
			self.setJumping(false);
			ci.cancel();
		}
	}
}
