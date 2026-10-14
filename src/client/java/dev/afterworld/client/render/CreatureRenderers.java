package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.Afterworld;
import dev.afterworld.client.model.CreatureModels;
import dev.afterworld.client.model.gen.ClockworkSentryLayer;
import dev.afterworld.client.model.gen.CrowLayer;
import dev.afterworld.client.model.gen.HollowStagLayer;
import dev.afterworld.client.model.gen.LanternMothLayer;
import dev.afterworld.client.model.gen.MossbackLayer;
import dev.afterworld.client.model.gen.RuleWraithLayer;
import dev.afterworld.client.model.gen.WispLayer;
import dev.afterworld.client.model.WildModels;
import dev.afterworld.client.model.gen.AshenHuskLayer;
import dev.afterworld.client.model.gen.BogLurkerLayer;
import dev.afterworld.client.model.gen.EchoStalkerLayer;
import dev.afterworld.client.model.gen.EmberfoxLayer;
import dev.afterworld.client.model.gen.GearhoundLayer;
import dev.afterworld.client.model.gen.GlimmerfinLayer;
import dev.afterworld.client.model.gen.MirrorRayLayer;
import dev.afterworld.client.model.gen.PetalSpriteLayer;
import dev.afterworld.client.model.gen.RimeWightLayer;
import dev.afterworld.client.model.gen.RimehornLayer;
import dev.afterworld.client.model.gen.ShardlingLayer;
import dev.afterworld.client.model.gen.SkywhaleLayer;
import dev.afterworld.entity.creature.BogLurker;
import dev.afterworld.entity.creature.RimeWight;
import dev.afterworld.entity.creature.ClockworkSentry;
import dev.afterworld.entity.creature.Crow;
import dev.afterworld.entity.creature.HollowStag;
import dev.afterworld.entity.creature.Mossback;
import dev.afterworld.entity.creature.RuleWraith;
import dev.afterworld.registry.AwEntities;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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

/** Renderers for the creatures: textured models, an emissive glow pass, and translucency where needed. */
public final class CreatureRenderers {
	private CreatureRenderers() {
	}

	public static void register() {
		creature(AwEntities.LANTERN_MOTH, "lantern_moth", LanternMothLayer::create, CreatureModels.Moth::new, 0.2F, true, false, 12, (e, s) -> {
		});
		creature(AwEntities.CROW, "crow", CrowLayer::create, CreatureModels.Crow::new, 0.25F, false, false, -1, (e, s) -> s.flying = ((Crow) e).isFlying());
		creature(AwEntities.MOSSBACK, "mossback", MossbackLayer::create, CreatureModels.Mossback::new, 1.1F, false, false, -1, (e, s) -> s.mossy = ((Mossback) e).isMossy());
		creature(AwEntities.HOLLOW_STAG, "hollow_stag", HollowStagLayer::create, CreatureModels.Stag::new, 0.6F, true, false, -1, (e, s) -> {
		});
		creature(AwEntities.CLOCKWORK_SENTRY, "clockwork_sentry", ClockworkSentryLayer::create, CreatureModels.Sentry::new, 0.7F, true, false, -1,
			(e, s) -> s.windup = ((ClockworkSentry) e).windup());
		creature(AwEntities.RULE_WRAITH, "rule_wraith", RuleWraithLayer::create, CreatureModels.Wraith::new, 0.0F, true, true, -1, (e, s) -> {
		});
		creature(AwEntities.WISP, "wisp", WispLayer::create, CreatureModels.Wisp::new, 0.0F, true, true, 15, (e, s) -> {
		});

		// The second wave.
		creature(AwEntities.ASHEN_HUSK, "ashen_husk", AshenHuskLayer::create, WildModels.Husk::new, 0.5F, true, false, -1, NONE);
		creature(AwEntities.SHARDLING, "shardling", ShardlingLayer::create, WildModels.Shardling::new, 0.5F, true, false, -1, NONE);
		creature(AwEntities.BOG_LURKER, "bog_lurker", BogLurkerLayer::create, WildModels.Lurker::new, 0.8F, true, false, -1,
			(e, s) -> s.windup = ((BogLurker) e).lunge());
		creature(AwEntities.ECHO_STALKER, "echo_stalker", EchoStalkerLayer::create, WildModels.Stalker::new, 0.4F, true, false, -1, NONE);
		creature(AwEntities.GEARHOUND, "gearhound", GearhoundLayer::create, WildModels.Hound::new, 0.45F, true, false, -1, NONE);
		creature(AwEntities.RIME_WIGHT, "rime_wight", RimeWightLayer::create, WildModels.Wight::new, 0.0F, true, true, 10,
			(e, s) -> s.windup = ((RimeWight) e).charge());
		creature(AwEntities.SKYWHALE, "skywhale", SkywhaleLayer::create, WildModels.Whale::new, 0.0F, true, false, 6, NONE, 3.0F);
		creature(AwEntities.GLIMMERFIN, "glimmerfin", GlimmerfinLayer::create, WildModels.Fin::new, 0.0F, true, true, -1, NONE);
		creature(AwEntities.EMBERFOX, "emberfox", EmberfoxLayer::create, WildModels.Fox::new, 0.4F, true, false, -1, NONE);
		creature(AwEntities.RIMEHORN, "rimehorn", RimehornLayer::create, WildModels.Yak::new, 0.9F, true, false, -1, NONE);
		creature(AwEntities.PETAL_SPRITE, "petal_sprite", PetalSpriteLayer::create, WildModels.Sprite::new, 0.0F, true, true, 12, NONE);
		creature(AwEntities.MIRROR_RAY, "mirror_ray", MirrorRayLayer::create, WildModels.Ray::new, 0.0F, true, false, -1, NONE, 1.6F);
		// The Duskstrider: the stag's build, longer in the leg, in dusk colours.
		creature(AwEntities.DUSKSTRIDER, "duskstrider", dev.afterworld.client.model.gen.DuskstriderLayer::create, CreatureModels.Stag::new, 0.8F, true, false, -1, NONE, 1.25F);
	}

	private static final BiConsumer<Mob, CreatureModels.State> NONE = (e, s) -> {
	};

	private static <T extends Mob, M extends EntityModel<CreatureModels.State>> void creature(EntityType<T> type, String name, Supplier<LayerDefinition> layer,
			Function<ModelPart, M> model, float shadow, boolean glow, boolean translucent, int minLight, BiConsumer<Mob, CreatureModels.State> extra) {
		creature(type, name, layer, model, shadow, glow, translucent, minLight, extra, 1.0F);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Mob, M extends EntityModel<CreatureModels.State>> void creature(EntityType<T> type, String name, Supplier<LayerDefinition> layer,
			Function<ModelPart, M> model, float shadow, boolean glow, boolean translucent, int minLight, BiConsumer<Mob, CreatureModels.State> extra, float modelScale) {
		ModelLayerLocation location = new ModelLayerLocation(Afterworld.id(name), "main");
		ModelLayerRegistry.registerModelLayer(location, layer::get);
		EntityRenderers.register(type, ctx -> new Renderer<>(ctx, model.apply(ctx.bakeLayer(location)), shadow, name, glow, translucent, minLight, extra, modelScale));
	}

	static final class Renderer<T extends Mob, M extends EntityModel<CreatureModels.State>> extends MobRenderer<T, CreatureModels.State, M> {
		private final Identifier texture;
		private final boolean translucent;
		private final int minLight;
		private final BiConsumer<Mob, CreatureModels.State> extra;
		private final float modelScale;

		Renderer(EntityRendererProvider.Context ctx, M model, float shadow, String name, boolean glow, boolean translucent, int minLight,
				BiConsumer<Mob, CreatureModels.State> extra, float modelScale) {
			super(ctx, model, shadow);
			this.modelScale = modelScale;
			this.texture = Afterworld.id("textures/entity/" + name + ".png");
			this.translucent = translucent;
			this.minLight = minLight;
			this.extra = extra;
			if (glow) {
				this.addLayer(new Glow<>(this, Afterworld.id("textures/entity/" + name + "_glow.png")));
			}
		}

		@Override
		protected void scale(CreatureModels.State state, PoseStack poseStack) {
			if (this.modelScale != 1.0F) {
				poseStack.scale(this.modelScale, this.modelScale, this.modelScale);
			}
		}

		@Override
		public Identifier getTextureLocation(CreatureModels.State state) {
			return this.texture;
		}

		@Override
		public CreatureModels.State createRenderState() {
			return new CreatureModels.State();
		}

		@Override
		public void extractRenderState(T entity, CreatureModels.State state, float partial) {
			super.extractRenderState(entity, state, partial);
			state.attack = entity.getSwingAnimation(partial);
			if (entity instanceof HollowStag stag) {
				state.graze = net.minecraft.util.Mth.lerp(partial, stag.grazeAnimO, stag.grazeAnim);
			}
			this.extra.accept(entity, state);
		}

		@Override
		protected RenderType getRenderType(CreatureModels.State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
			if (this.translucent && isBodyVisible) {
				return RenderTypes.entityTranslucent(this.texture);
			}
			return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
		}

		@Override
		protected int getBlockLightLevel(T entity, BlockPos pos) {
			return Math.max(this.minLight, super.getBlockLightLevel(entity, pos));
		}
	}

	/** The glow texture, drawn full-bright over the model (lanterns, antlers, eyes, runes). */
	static final class Glow<S extends CreatureModels.State, M extends EntityModel<S>> extends RenderLayer<S, M> {
		private final RenderType type;

		Glow(RenderLayerParent<S, M> parent, Identifier texture) {
			super(parent);
			this.type = RenderTypes.eyes(texture);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
			if (!state.isInvisible) {
				collector.submitModel(this.getParentModel(), state, poseStack, this.type, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
			}
		}
	}
}
