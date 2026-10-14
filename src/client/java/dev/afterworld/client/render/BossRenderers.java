package dev.afterworld.client.render;

import dev.afterworld.Afterworld;
import dev.afterworld.client.model.BellRingerModel;
import dev.afterworld.client.model.BossRenderStates;
import dev.afterworld.client.model.KingModel;
import dev.afterworld.client.model.PilgrimModel;
import dev.afterworld.client.model.gen.BellRingerLayer;
import dev.afterworld.client.model.gen.KingLayer;
import dev.afterworld.client.model.gen.PilgrimLayer;
import dev.afterworld.entity.boss.BellRingerEntity;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.entity.boss.KingEntity;
import dev.afterworld.entity.boss.PilgrimEntity;
import dev.afterworld.registry.AwEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Renderers for the bosses and their thrown things. */
public final class BossRenderers {
	public static final ModelLayerLocation PILGRIM = new ModelLayerLocation(Afterworld.id("pilgrim"), "main");
	public static final ModelLayerLocation BELL_RINGER = new ModelLayerLocation(Afterworld.id("bell_ringer"), "main");
	public static final ModelLayerLocation KING = new ModelLayerLocation(Afterworld.id("king_of_seconds"), "main");

	private BossRenderers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(PILGRIM, PilgrimLayer::create);
		ModelLayerRegistry.registerModelLayer(BELL_RINGER, BellRingerLayer::create);
		ModelLayerRegistry.registerModelLayer(KING, KingLayer::create);
		EntityRenderers.register(AwEntities.PILGRIM, Pilgrim::new);
		EntityRenderers.register(AwEntities.BELL_RINGER, Ringer::new);
		EntityRenderers.register(AwEntities.KING_OF_SECONDS, King::new);
		EntityRenderers.register(AwEntities.HEAVY_PROJECTILE, ctx -> new ThrownItemRenderer<>(ctx, 2.6F, false));
	}

	static void fill(BossMob e, BossRenderStates.Boss s, float partial) {
		s.anim = e.anim();
		s.animTime = e.animTime(partial);
	}

	static final class Pilgrim extends MobRenderer<PilgrimEntity, BossRenderStates.Boss, PilgrimModel> {
		private static final Identifier TEXTURE = Afterworld.id("textures/entity/pilgrim.png");

		Pilgrim(EntityRendererProvider.Context ctx) {
			super(ctx, new PilgrimModel(ctx.bakeLayer(PILGRIM)), 1.6F);
		}

		@Override
		public Identifier getTextureLocation(BossRenderStates.Boss state) {
			return TEXTURE;
		}

		@Override
		public BossRenderStates.Boss createRenderState() {
			return new BossRenderStates.Boss();
		}

		@Override
		public void extractRenderState(PilgrimEntity e, BossRenderStates.Boss s, float partial) {
			super.extractRenderState(e, s, partial);
			fill(e, s, partial);
		}
	}

	static final class Ringer extends MobRenderer<BellRingerEntity, BossRenderStates.Ringer, BellRingerModel> {
		private static final Identifier TEXTURE = Afterworld.id("textures/entity/bell_ringer.png");

		Ringer(EntityRendererProvider.Context ctx) {
			super(ctx, new BellRingerModel(ctx.bakeLayer(BELL_RINGER)), 1.2F);
		}

		@Override
		public Identifier getTextureLocation(BossRenderStates.Ringer state) {
			return TEXTURE;
		}

		@Override
		public BossRenderStates.Ringer createRenderState() {
			return new BossRenderStates.Ringer();
		}

		@Override
		public void extractRenderState(BellRingerEntity e, BossRenderStates.Ringer s, float partial) {
			super.extractRenderState(e, s, partial);
			fill(e, s, partial);
			s.bellHeld = e.bellHeld();
		}
	}

	static final class King extends MobRenderer<KingEntity, BossRenderStates.Boss, KingModel> {
		private static final Identifier TEXTURE = Afterworld.id("textures/entity/king_of_seconds.png");

		King(EntityRendererProvider.Context ctx) {
			super(ctx, new KingModel(ctx.bakeLayer(KING)), 0.9F);
		}

		@Override
		public Identifier getTextureLocation(BossRenderStates.Boss state) {
			return TEXTURE;
		}

		@Override
		public BossRenderStates.Boss createRenderState() {
			return new BossRenderStates.Boss();
		}

		@Override
		public void extractRenderState(KingEntity e, BossRenderStates.Boss s, float partial) {
			super.extractRenderState(e, s, partial);
			fill(e, s, partial);
		}

		@Override
		protected int getBlockLightLevel(KingEntity entity, BlockPos pos) {
			return Math.max(6, super.getBlockLightLevel(entity, pos));
		}
	}
}
