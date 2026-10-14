package dev.afterworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.client.render.FirstPersonActs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The weapon in your own hand acts out your powers (see FirstPersonActs). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void afterworld$actBegin(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partial, float pitch, InteractionHand hand, float swing,
		ItemStack item, float equip, PoseStack ps, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ps.pushPose();
		if (hand == InteractionHand.MAIN_HAND) {
			var mc = Minecraft.getInstance();
			float side = mc.player != null && mc.player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
			FirstPersonActs.apply(ps, side);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void afterworld$actEnd(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partial, float pitch, InteractionHand hand, float swing,
		ItemStack item, float equip, PoseStack ps, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ps.popPose();
	}
}
