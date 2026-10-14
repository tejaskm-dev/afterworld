package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.Garb;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;

/** Draws a player's garb in three passes (dyed cloth, dyed trim, fixed metal) plus lantern glow and the heart shard. */
public final class GarbLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final GarbModel wide;
	private final GarbModel slim;

	public GarbLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.wide = new GarbModel(models.bakeLayer(GarbRender.WIDE), false);
		this.slim = new GarbModel(models.bakeLayer(GarbRender.SLIM), true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Garb g = state.getData(GarbRender.GARB);
		if (g == null || !g.visible() || state.isInvisible) {
			return;
		}
		GarbModel model = state.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(GarbRender.CLOTH), light, overlay, ARGB.opaque(g.primaryColor()), null, state.outlineColor);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(GarbRender.TRIM), light, overlay, ARGB.opaque(g.secondaryColor()), null, state.outlineColor);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(GarbRender.FIXED), light, overlay, -1, null, state.outlineColor);
		if (g.accent() == 4 || g.head() == 4) {
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(GarbRender.GLOW), light, OverlayTexture.NO_OVERLAY, -1, null, 0);
		}
		Edict heart = g.heartEdict();
		if (heart != null) {
			// A slow pulse, so the shard reads as alive rather than painted on.
			float pulse = 0.82F + 0.18F * Mth.sin(state.ageInTicks * 0.12F);
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(GarbRender.HEART), light, OverlayTexture.NO_OVERLAY,
				ARGB.color(255, ARGB.scaleRGB(heart.color, pulse)), null, 0);
		}
	}
}
