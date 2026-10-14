package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.Afterworld;
import dev.afterworld.client.model.WardenModel;
import dev.afterworld.client.model.gen.BossLayers;
import dev.afterworld.entity.boss.WardenBoss;
import dev.afterworld.registry.AwEntities;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** The five Wardens: one rig, each its own body, skin and glow. Sleeping ones are drawn as stone. */
public final class WardenRenderers {
	private WardenRenderers() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		reg((EntityType) AwEntities.ASHEN_SHOGUN, "ashen_shogun", 0.8F, false);
		reg((EntityType) AwEntities.CRIMSON_LICH, "crimson_lich", 0.7F, true);
		reg((EntityType) AwEntities.RIMEBACK_COLOSSUS, "rimeback_colossus", 1.6F, false);
		reg((EntityType) AwEntities.DUNE_SENTINEL, "dune_sentinel", 1.8F, false);
		reg((EntityType) AwEntities.GILDED_WARDEN, "gilded_warden", 2.2F, false);
	}

	private static void reg(EntityType<WardenBoss> type, String id, float shadow, boolean floats) {
		ModelLayerLocation location = new ModelLayerLocation(Afterworld.id(id), "main");
		ModelLayerRegistry.registerModelLayer(location, () -> BossLayers.create(id));
		EntityRenderers.register(type, ctx -> new Renderer(ctx, id, location, shadow, floats));
	}

	static final class Renderer extends MobRenderer<WardenBoss, WardenModel.State, WardenModel> {
		private final Identifier texture;
		private final boolean floats;

		Renderer(EntityRendererProvider.Context ctx, String id, ModelLayerLocation location, float shadow, boolean floats) {
			super(ctx, new WardenModel(ctx.bakeLayer(location), BossLayers.parts(id)), shadow);
			this.texture = Afterworld.id("textures/entity/" + id + ".png");
			this.floats = floats;
			Identifier glow = Afterworld.id("textures/entity/" + id + "_glow.png");
			if (ctx.getResourceManager().getResource(glow).isPresent()) {
				this.addLayer(new Glow(this, glow));
			}
		}

		@Override
		public WardenModel.State createRenderState() {
			return new WardenModel.State();
		}

		@Override
		public void extractRenderState(WardenBoss entity, WardenModel.State state, float partial) {
			super.extractRenderState(entity, state, partial);
			state.anim = entity.anim();
			state.animTime = entity.animTime(partial);
			state.dormant = entity.dormant();
			state.floats = this.floats && !entity.dormant();
			state.phase = entity.phase();
		}

		@Override
		public Identifier getTextureLocation(WardenModel.State state) {
			return this.texture;
		}
	}

	/** Eyes, lanterns, gems and glyphs, drawn full-bright; dark while the Warden sleeps. */
	static final class Glow extends RenderLayer<WardenModel.State, WardenModel> {
		private final RenderType type;

		Glow(RenderLayerParent<WardenModel.State, WardenModel> parent, Identifier texture) {
			super(parent);
			this.type = RenderTypes.eyes(texture);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WardenModel.State state, float yRot, float xRot) {
			if (!state.isInvisible && !state.dormant) {
				collector.submitModel(this.getParentModel(), state, poseStack, this.type, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
			}
		}
	}
}
