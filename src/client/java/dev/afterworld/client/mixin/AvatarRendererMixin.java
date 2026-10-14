package dev.afterworld.client.mixin;

import dev.afterworld.client.render.GarbRender;
import dev.afterworld.player.Garb;
import dev.afterworld.registry.AwAttachments;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies a player's garb into their render state (a garb cloak replaces the vanilla cape) and
 * the move they are making (PlayerPoses tips the body for it), and hides keepers' names up close.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void afterworld$garb(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		Garb garb = entity.getAttached(AwAttachments.GARB);
		state.setData(GarbRender.GARB, garb);
		state.setData(dev.afterworld.client.render.PlayerPoses.POSE, dev.afterworld.client.render.PlayerPoses.of(entity));
		state.setData(dev.afterworld.client.render.PlayerPoses.ACT, dev.afterworld.client.render.PlayerPoses.acting(entity));
		if (garb != null && garb.visible() && garb.cloak() != 0 && garb.cloak() != 4) {
			state.showCape = false;
		}
		// A keeper's name would fill the screen from point-blank range, and sit behind any open
		// screen: hide it when the camera is that close or a screen is up.
		if (entity.getAttached(AwAttachments.NPC) != null) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.gui.screen() != null || entity.position().add(0, 1.6, 0).distanceToSqr(mc.gameRenderer.mainCamera().position()) < 2.6 * 2.6) {
				state.nameTag = null;
				state.scoreText = null;
			}
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void afterworld$pose(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack stack, float bodyRot, float scale, CallbackInfo ci) {
		dev.afterworld.client.render.PlayerPoses.rotate(state, stack, bodyRot);
	}
}
