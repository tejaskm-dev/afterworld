package dev.afterworld.client.render;

import dev.afterworld.Afterworld;
import dev.afterworld.client.model.GenericCreatureModel;
import dev.afterworld.client.model.gen.WildLayers;
import dev.afterworld.entity.wild.Ability;
import dev.afterworld.entity.wild.Species;
import dev.afterworld.entity.wild.WildCreature;
import dev.afterworld.entity.wild.WildEntities;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Renderers for the second bestiary: one shared model, each species' own geometry and skin. */
public final class WildRenderers {
	private WildRenderers() {
	}

	/** Marta's training dummy, drawn with the same model rig. */
	static final class DummyRenderer extends MobRenderer<dev.afterworld.entity.TrainingDummy, GenericCreatureModel.State, GenericCreatureModel> {
		private static final Identifier TEXTURE = Afterworld.id("textures/entity/training_dummy.png");

		DummyRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation location) {
			super(ctx, new GenericCreatureModel(ctx.bakeLayer(location), WildLayers.parts("training_dummy")), 0.4F);
		}

		@Override
		public GenericCreatureModel.State createRenderState() {
			return new GenericCreatureModel.State();
		}

		@Override
		public void extractRenderState(dev.afterworld.entity.TrainingDummy entity, GenericCreatureModel.State state, float partial) {
			super.extractRenderState(entity, state, partial);
			state.action = entity.action();
			state.actionTime = entity.actionTime(partial);
			state.attack = entity.getSwingAnimation(partial);
			state.walkAnimationSpeed = 0;
		}

		@Override
		public Identifier getTextureLocation(GenericCreatureModel.State state) {
			return TEXTURE;
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		ModelLayerLocation dummy = new ModelLayerLocation(Afterworld.id("training_dummy"), "main");
		ModelLayerRegistry.registerModelLayer(dummy, () -> WildLayers.create("training_dummy"));
		EntityRenderers.register(dev.afterworld.registry.AwEntities.TRAINING_DUMMY, ctx -> new DummyRenderer(ctx, dummy));
		for (Species s : Species.VALUES) {
			ModelLayerLocation location = new ModelLayerLocation(Afterworld.id(s.id), "main");
			ModelLayerRegistry.registerModelLayer(location, () -> WildLayers.create(s.id));
			EntityType<Mob> type = (EntityType<Mob>) (EntityType) WildEntities.type(s);
			EntityRenderers.register(type, ctx -> new Renderer(ctx, s, location));
		}
	}

	static final class Renderer extends MobRenderer<Mob, GenericCreatureModel.State, GenericCreatureModel> {
		private final Species species;
		private final Identifier texture;
		private final boolean translucent;

		Renderer(EntityRendererProvider.Context ctx, Species s, ModelLayerLocation location) {
			super(ctx, new GenericCreatureModel(ctx.bakeLayer(location), WildLayers.parts(s.id)), Math.max(0.2F, s.width * 0.5F));
			this.species = s;
			this.texture = Afterworld.id("textures/entity/" + s.id + ".png");
			this.translucent = s == Species.MOURNING_SHADE || s == Species.TIDECALLER_JELLY || s == Species.RIME_MOTH;
			Identifier glow = Afterworld.id("textures/entity/" + s.id + "_glow.png");
			if (ctx.getResourceManager().getResource(glow).isPresent()) {
				this.addLayer(new Glow(this, glow));
			}
		}

		@Override
		public GenericCreatureModel.State createRenderState() {
			return new GenericCreatureModel.State();
		}

		@Override
		public void extractRenderState(Mob entity, GenericCreatureModel.State state, float partial) {
			super.extractRenderState(entity, state, partial);
			state.attack = entity.getSwingAnimation(partial);
			if (entity instanceof WildCreature w) {
				state.action = w.action();
				state.actionTime = w.actionTime(partial);
				state.burrowed = w.burrowed();
			}
			state.swimmer = this.species.kind == Species.Kind.SWIMMER;
			state.flying = this.species.kind == Species.Kind.FLYER && !entity.onGround();
			state.inWaterNow = entity.isInWater();
		}

		@Override
		public Identifier getTextureLocation(GenericCreatureModel.State state) {
			return this.texture;
		}

		@Override
		protected RenderType getRenderType(GenericCreatureModel.State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
			if (this.translucent && isBodyVisible) {
				return RenderTypes.entityTranslucent(this.texture);
			}
			return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
		}

		@Override
		protected int getBlockLightLevel(Mob entity, BlockPos pos) {
			int base = super.getBlockLightLevel(entity, pos);
			return this.species.has(Ability.GLOW) ? Math.max(base, 9) : base;
		}
	}

	/** The glow texture, drawn full-bright over the model. */
	static final class Glow extends RenderLayer<GenericCreatureModel.State, GenericCreatureModel> {
		private final RenderType type;

		Glow(RenderLayerParent<GenericCreatureModel.State, GenericCreatureModel> parent, Identifier texture) {
			super(parent);
			this.type = RenderTypes.eyes(texture);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GenericCreatureModel.State state, float yRot, float xRot) {
			if (!state.isInvisible && !state.burrowed) {
				collector.submitModel(this.getParentModel(), state, poseStack, this.type, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
			}
		}
	}
}
