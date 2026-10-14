package dev.afterworld.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Animated models for Afterworld's creatures. Geometry comes from tools/gen_mobs.py. */
public final class CreatureModels {
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final float PI = Mth.PI;

	private CreatureModels() {
	}

	/** One render state shared by every creature; each model reads the fields it needs. */
	public static class State extends LivingEntityRenderState {
		public boolean flying;
		public boolean mossy = true;
		public float graze;
		public int windup;
		public float attack;
	}

	public static final class Moth extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart wingL;
		private final ModelPart wingR;
		private final ModelPart abdomen;
		private final ModelPart antennaL;
		private final ModelPart antennaR;

		public Moth(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.wingL = this.body.getChild("wing_l");
			this.wingR = this.body.getChild("wing_r");
			this.abdomen = this.body.getChild("abdomen");
			ModelPart head = this.body.getChild("head");
			this.antennaL = head.getChild("antenna_l");
			this.antennaR = head.getChild("antenna_r");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float flap = Mth.sin(t * 1.9F);
			this.wingL.zRot = -0.25F - flap * 0.85F;
			this.wingR.zRot = -this.wingL.zRot;
			this.body.y += Mth.sin(t * 0.25F) * 0.8F;
			this.body.xRot = 0.1F;
			this.abdomen.xRot += Mth.sin(t * 0.3F) * 0.08F;
			this.antennaL.xRot += Mth.sin(t * 0.2F) * 0.1F;
			this.antennaR.xRot += Mth.sin(t * 0.2F + 1) * 0.1F;
		}
	}

	public static final class Crow extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart wingL;
		private final ModelPart wingR;
		private final ModelPart tail;
		private final ModelPart legL;
		private final ModelPart legR;

		public Crow(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.wingL = this.body.getChild("wing_l");
			this.wingR = this.body.getChild("wing_r");
			this.tail = this.body.getChild("tail");
			this.legL = r.getChild("leg_l");
			this.legR = r.getChild("leg_r");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			if (s.flying) {
				float flap = Mth.sin(t * 1.4F);
				this.wingL.zRot = -1.25F - flap * 0.7F;
				this.wingR.zRot = -this.wingL.zRot;
				this.legL.xRot = 1.1F;
				this.legR.xRot = 1.1F;
				this.body.xRot = 0.15F;
				this.tail.xRot = -0.15F;
				this.body.y += Mth.sin(t * 1.4F + 0.5F) * 0.6F;
			} else {
				float walk = Math.min(1.0F, s.walkAnimationSpeed * 2);
				this.legL.xRot = Mth.cos(s.walkAnimationPos * 1.3F) * 1.2F * walk;
				this.legR.xRot = Mth.cos(s.walkAnimationPos * 1.3F + PI) * 1.2F * walk;
				this.head.yRot = Mth.clamp(s.yRot, -50, 50) * DEG;
				this.head.xRot += s.xRot * DEG * 0.5F;
				float cycle = t % 110.0F;
				if (cycle < 9) {
					this.head.xRot += Mth.sin(cycle / 9.0F * PI) * 0.9F;
				}
				this.tail.xRot += Mth.sin(t * 0.1F) * 0.06F;
			}
		}
	}

	public static final class Mossback extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart moss;
		private final ModelPart head;
		private final ModelPart tail;
		private final ModelPart legFL;
		private final ModelPart legFR;
		private final ModelPart legBL;
		private final ModelPart legBR;

		public Mossback(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.moss = this.body.getChild("moss");
			this.head = this.body.getChild("head");
			this.tail = this.body.getChild("tail");
			this.legFL = r.getChild("leg_fl");
			this.legFR = r.getChild("leg_fr");
			this.legBL = r.getChild("leg_bl");
			this.legBR = r.getChild("leg_br");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 0.5F;
			float sp = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
			this.moss.visible = s.mossy;
			this.legFL.xRot = Mth.cos(w) * 0.6F * sp;
			this.legBR.xRot = this.legFL.xRot;
			this.legFR.xRot = Mth.cos(w + PI) * 0.6F * sp;
			this.legBL.xRot = this.legFR.xRot;
			this.head.yRot = Mth.clamp(s.yRot, -35, 35) * DEG;
			this.head.xRot = s.xRot * DEG * 0.6F + Mth.sin(t * 0.05F) * 0.05F;
			this.body.y += Mth.sin(t * 0.08F) * 0.3F;
			this.body.zRot = Mth.sin(w) * 0.03F * sp;
			this.tail.yRot = Mth.sin(t * 0.1F) * 0.15F;
		}
	}

	public static final class Stag extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart neck;
		private final ModelPart head;
		private final ModelPart earL;
		private final ModelPart earR;
		private final ModelPart tail;
		private final ModelPart legFL;
		private final ModelPart legFR;
		private final ModelPart legBL;
		private final ModelPart legBR;

		public Stag(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.neck = this.body.getChild("neck");
			this.head = this.neck.getChild("head");
			this.earL = this.head.getChild("ear_l");
			this.earR = this.head.getChild("ear_r");
			this.tail = this.body.getChild("tail");
			this.legFL = r.getChild("leg_fl");
			this.legFR = r.getChild("leg_fr");
			this.legBL = r.getChild("leg_bl");
			this.legBR = r.getChild("leg_br");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 0.9F;
			float sp = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);
			this.legFL.xRot = Mth.cos(w) * 1.0F * sp;
			this.legBR.xRot = this.legFL.xRot;
			this.legFR.xRot = Mth.cos(w + PI) * 1.0F * sp;
			this.legBL.xRot = this.legFR.xRot;
			float g = s.graze;
			this.neck.xRot += g * 1.25F;
			this.neck.yRot = (1 - g) * Mth.clamp(s.yRot, -30, 30) * DEG * 0.5F;
			this.head.xRot += g * 0.25F + (1 - g) * s.xRot * DEG * 0.4F;
			this.head.yRot = (1 - g) * Mth.clamp(s.yRot, -30, 30) * DEG * 0.5F;
			float twitch = ((int) (t / 40.0F)) % 3 == 0 && t % 40.0F < 4 ? 0.45F : 0.0F;
			this.earL.zRot -= twitch;
			this.earR.zRot += twitch * 0.5F;
			this.tail.xRot += Mth.sin(t * 0.6F) * 0.1F * (1 + sp);
			this.body.xRot = Mth.sin(w * 2) * 0.04F * sp;
		}
	}

	public static final class Sentry extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart gear;
		private final ModelPart[] legs = new ModelPart[4];

		public Sentry(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.gear = this.body.getChild("gear");
			String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
			for (int i = 0; i < 4; i++) {
				this.legs[i] = r.getChild(names[i]);
			}
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 1.2F;
			float sp = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
			float a = Mth.sin(w) * 0.45F * sp;
			float b = Mth.sin(w + PI) * 0.45F * sp;
			// Diagonal pairs step together; the stepping legs lift.
			this.legs[0].yRot += a;
			this.legs[3].yRot += a;
			this.legs[1].yRot += b;
			this.legs[2].yRot += b;
			float lift = Math.max(0, Mth.cos(w)) * 0.35F * sp;
			float lift2 = Math.max(0, Mth.cos(w + PI)) * 0.35F * sp;
			this.legs[0].zRot += lift;
			this.legs[3].zRot -= lift;
			this.legs[1].zRot -= lift2;
			this.legs[2].zRot += lift2;
			this.gear.zRot = t * 0.12F;
			this.head.yRot = Mth.clamp(s.yRot, -60, 60) * DEG;
			this.head.xRot = s.xRot * DEG * 0.5F;
			this.body.y += Mth.sin(t * 0.3F) * 0.2F;
			if (s.windup > 0) {
				float k = s.windup / 12.0F;
				this.head.xRot -= 0.35F * k;
				this.body.y += 1.0F * k;
			}
		}
	}

	public static final class Wraith extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart hood;
		private final ModelPart armL;
		private final ModelPart armR;
		private final ModelPart tatterF;
		private final ModelPart tatterB;
		private final ModelPart tatterL;
		private final ModelPart tatterR;
		private final ModelPart halo;

		public Wraith(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.hood = this.body.getChild("hood");
			this.armL = this.body.getChild("arm_l");
			this.armR = this.body.getChild("arm_r");
			this.tatterF = this.body.getChild("tatter_f");
			this.tatterB = this.body.getChild("tatter_b");
			this.tatterL = this.body.getChild("tatter_l");
			this.tatterR = this.body.getChild("tatter_r");
			this.halo = this.body.getChild("halo");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			this.body.y += Mth.sin(t * 0.08F) * 1.6F;
			this.body.xRot = 0.08F + Math.min(1.0F, s.walkAnimationSpeed * 2) * 0.25F;
			float atk = s.attack;
			this.armL.xRot = -0.3F + Mth.sin(t * 0.07F) * 0.1F - atk * 1.4F;
			this.armR.xRot = -0.3F + Mth.sin(t * 0.07F + 1) * 0.1F - atk * 1.4F;
			this.armL.zRot = -0.2F;
			this.armR.zRot = 0.2F;
			this.hood.yRot = Mth.clamp(s.yRot, -50, 50) * DEG;
			this.hood.xRot = s.xRot * DEG * 0.5F;
			this.tatterF.xRot = -0.2F + Mth.sin(t * 0.15F) * 0.15F;
			this.tatterB.xRot = 0.2F + Mth.sin(t * 0.15F + 1.5F) * 0.15F;
			this.tatterL.xRot = Mth.sin(t * 0.15F + 3.0F) * 0.15F;
			this.tatterR.xRot = Mth.sin(t * 0.15F + 4.5F) * 0.15F;
			this.halo.yRot = t * 0.04F;
			this.halo.y += Mth.sin(t * 0.1F) * 0.4F;
		}
	}

	public static final class Wisp extends EntityModel<State> {
		private final ModelPart core;
		private final ModelPart orbitA;
		private final ModelPart orbitB;
		private final ModelPart orbitC;

		public Wisp(ModelPart root) {
			super(root);
			this.core = root.getChild("root").getChild("core");
			this.orbitA = this.core.getChild("orbit_a");
			this.orbitB = this.core.getChild("orbit_b");
			this.orbitC = this.core.getChild("orbit_c");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			this.core.y += Mth.sin(t * 0.15F) * 1.2F;
			this.core.yRot = t * 0.05F;
			this.orbitA.yRot = t * 0.25F;
			this.orbitB.yRot = -t * 0.19F;
			this.orbitC.xRot = t * 0.22F;
		}
	}
}
