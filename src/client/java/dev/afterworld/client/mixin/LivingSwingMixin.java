package dev.afterworld.client.mixin;

import dev.afterworld.client.fx.VfxRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each swing of a blade leaves an arc of light (on the client, for every player in view). */
@Mixin(LivingEntity.class)
abstract class LivingSwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void afterworld$arc(InteractionHand hand, SwingAnimation animation, boolean updateSelf, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() && self.level().isClientSide()) {
			VfxRenderer.onSwing(self, hand);
		}
	}
}
