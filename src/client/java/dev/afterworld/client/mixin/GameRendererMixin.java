package dev.afterworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.client.fx.Shake;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Afterworld's camera shake on top of the vanilla hurt tilt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void afterworld$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		Shake.apply(poseStack);
	}
}
