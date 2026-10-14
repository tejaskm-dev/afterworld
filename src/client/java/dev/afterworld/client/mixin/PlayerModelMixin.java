package dev.afterworld.client.mixin;

import dev.afterworld.client.render.PlayerPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The limbs act out climbing, mantling, slides, dodges and glides (see PlayerPoses). */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void afterworld$pose(AvatarRenderState state, CallbackInfo ci) {
		PlayerPoses.animate((PlayerModel) (Object) this, state);
	}
}
