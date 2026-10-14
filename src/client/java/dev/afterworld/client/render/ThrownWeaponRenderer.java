package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.afterworld.entity.ThrownWeapon;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws a Returned weapon tumbling end over end along its flight. */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon, ThrownWeaponRenderer.State> {
	private final ItemModelResolver items;

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float spin;
		public float yaw;
		public boolean spectral;
	}

	public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownWeapon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		items.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.FIXED, entity);
		state.spin = (entity.tickCount + partialTicks) * 42.0F;
		var v = entity.getDeltaMovement();
		state.yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
		state.spectral = entity.isSpectral();
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.0F, 0.25F, 0.0F);
		pose.rotateDegrees(Axis.YP, state.yaw - 90.0F);
		pose.rotateDegrees(Axis.ZP, -state.spin);
		pose.scale(1.1F, 1.1F, 1.1F);
		state.item.submit(pose, collector, state.spectral ? 0xF000F0 : state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
