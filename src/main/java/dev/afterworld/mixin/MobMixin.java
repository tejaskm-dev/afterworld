package dev.afterworld.mixin;

import dev.afterworld.edict.AwTargeting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes every "is this a valid target" decision through Afterworld's targeting rules. */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "asValidTarget", at = @At("RETURN"), cancellable = true)
	private void afterworld$filterTarget(LivingEntity target, CallbackInfoReturnable<LivingEntity> cir) {
		LivingEntity result = cir.getReturnValue();
		if (result != null) {
			LivingEntity filtered = AwTargeting.filter((Mob) (Object) this, result);
			if (filtered != result) {
				cir.setReturnValue(filtered);
			}
		}
	}
}
