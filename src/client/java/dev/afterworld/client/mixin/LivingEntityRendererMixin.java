package dev.afterworld.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.afterworld.client.render.EchoLook;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.registry.MobMark;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hunting echoes and ghosts are drawn as translucent spectres instead of vanishing; creatures move with more life. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void afterworld$spectral(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		MobMark mark = entity.getAttached(AwAttachments.MOB);
		if (mark != null && (mark.has(MobMark.ECHO) || mark.has(MobMark.GHOST))) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;
			state.setData(EchoLook.TINT, mark.has(MobMark.GHOST) ? EchoLook.GHOST : mark.has(MobMark.BETRAYER) ? EchoLook.BETRAYER : EchoLook.ECHO);
		} else {
			state.setData(EchoLook.TINT, null);
		}
		dev.afterworld.client.render.Liveliness.extract(entity, state, partialTicks);
	}

	/** Afterworld's creatures bob, lean, flinch and fidget (see Liveliness). */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void afterworld$life(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack stack, float bodyRot, float scale, CallbackInfo ci) {
		dev.afterworld.client.render.Liveliness.apply(state, stack);
	}

	@ModifyExpressionValue(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "CONSTANT", args = "intValue=654311423")
	)
	private int afterworld$spectralColor(int original, @Local(argsOnly = true) LivingEntityRenderState state) {
		Integer tint = state.getData(EchoLook.TINT);
		return tint != null ? tint : original;
	}
}
