package dev.afterworld.client.render;

import dev.afterworld.Afterworld;
import dev.afterworld.client.model.gen.GarbMesh;
import dev.afterworld.player.Garb;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Shared bits of garb rendering: the render-state key, model layers and textures. */
public final class GarbRender {
	public static final RenderStateDataKey<Garb> GARB = RenderStateDataKey.create(() -> "afterworld:garb");
	public static final ModelLayerLocation WIDE = new ModelLayerLocation(Afterworld.id("garb"), "wide");
	public static final ModelLayerLocation SLIM = new ModelLayerLocation(Afterworld.id("garb"), "slim");
	public static final Identifier CLOTH = Afterworld.id("textures/entity/garb/cloth.png");
	public static final Identifier TRIM = Afterworld.id("textures/entity/garb/trim.png");
	public static final Identifier FIXED = Afterworld.id("textures/entity/garb/fixed.png");
	public static final Identifier GLOW = Afterworld.id("textures/entity/garb/glow.png");
	public static final Identifier HEART = Afterworld.id("textures/entity/garb/heart.png");

	private GarbRender() {
	}

	@SuppressWarnings("unchecked")
	public static void register() {
		ModelLayerRegistry.registerModelLayer(WIDE, () -> GarbMesh.create(false));
		ModelLayerRegistry.registerModelLayer(SLIM, () -> GarbMesh.create(true));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new GarbLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar, context.getModelSet()));
			}
		});
	}
}
