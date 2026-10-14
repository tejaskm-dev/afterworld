package dev.afterworld.client.model;

import dev.afterworld.client.model.CreatureModels.State;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Animated models for the second wave of creatures. Geometry comes from tools/mobs_more.py. */
public final class WildModels {
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final float PI = Mth.PI;

	private WildModels() {
	}

	private static float walk(State s, float speed) {
		return Math.min(1.0F, s.walkAnimationSpeed * speed);
	}

	/** Four legs on the root, trotting in diagonal pairs. */
	private static void trot(ModelPart[] legs, State s, float rate, float swing) {
		float w = s.walkAnimationPos * rate;
		float sp = walk(s, 1.6F);
		legs[0].xRot = Mth.cos(w) * swing * sp;
		legs[3].xRot = legs[0].xRot;
		legs[1].xRot = Mth.cos(w + PI) * swing * sp;
		legs[2].xRot = legs[1].xRot;
	}

	private static ModelPart[] legs(ModelPart r) {
		return new ModelPart[] {r.getChild("leg_fl"), r.getChild("leg_fr"), r.getChild("leg_bl"), r.getChild("leg_br")};
	}

	public static final class Husk extends EntityModel<State> {
		private final ModelPart head;
		private final ModelPart body;
		private final ModelPart armL;
		private final ModelPart armR;
		private final ModelPart legL;
		private final ModelPart legR;

		public Husk(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.head = r.getChild("head");
			this.body = r.getChild("body");
			this.armL = r.getChild("arm_l");
			this.armR = r.getChild("arm_r");
			this.legL = r.getChild("leg_l");
			this.legR = r.getChild("leg_r");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 0.6662F;
			float sp = walk(s, 1.0F);
			this.head.yRot = s.yRot * DEG;
			this.head.xRot = s.xRot * DEG;
			this.legL.xRot = Mth.cos(w) * 1.2F * sp;
			this.legR.xRot = Mth.cos(w + PI) * 1.2F * sp;
			// Arms reach for prey, swaying; a swing slams them down.
			float reach = -PI / 2.2F + Mth.sin(t * 0.09F) * 0.06F;
			this.armL.xRot = reach + Mth.cos(w) * 0.2F * sp - s.attack * 0.9F;
			this.armR.xRot = reach + Mth.cos(w + PI) * 0.2F * sp - s.attack * 0.9F;
			this.armL.zRot = -0.08F;
			this.armR.zRot = 0.08F;
			this.body.xRot = 0.08F;
			this.head.z -= 0.6F;
		}
	}

	public static final class Shardling extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart[] legs = new ModelPart[6];
		private final ModelPart[] crystals = new ModelPart[4];

		public Shardling(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			for (int i = 0; i < 3; i++) {
				this.legs[i * 2] = r.getChild("leg_l" + i);
				this.legs[i * 2 + 1] = r.getChild("leg_r" + i);
			}
			String[] c = {"crystal_a", "crystal_b", "crystal_c", "crystal_d"};
			for (int i = 0; i < 4; i++) {
				this.crystals[i] = this.body.getChild(c[i]);
			}
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 1.6F;
			float sp = walk(s, 1.8F);
			for (int i = 0; i < 6; i++) {
				float phase = (i % 2 == 0 ? 0 : PI) + (i / 2) * PI;
				float side = i % 2 == 0 ? 1 : -1;
				this.legs[i].yRot += Mth.sin(w + phase) * 0.45F * sp * side;
				this.legs[i].zRot += Math.max(0, Mth.cos(w + phase)) * 0.4F * sp * side;
			}
			this.body.y += Mth.sin(t * 0.25F) * 0.25F + Math.abs(Mth.sin(w)) * -0.6F * sp;
			this.body.yRot = s.yRot * DEG * 0.3F;
			for (int i = 0; i < 4; i++) {
				this.crystals[i].zRot += Mth.sin(t * 0.07F + i) * 0.03F;
			}
		}
	}

	public static final class Lurker extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart jaw;
		private final ModelPart tail;
		private final ModelPart tailTip;
		private final ModelPart[] legs;

		public Lurker(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.jaw = this.body.getChild("jaw");
			this.tail = this.body.getChild("tail");
			this.tailTip = this.tail.getChild("tail_tip");
			this.legs = legs(r);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			trot(this.legs, s, 0.7F, 0.7F);
			float sp = walk(s, 1.6F);
			this.tail.yRot = Mth.sin(t * 0.12F + s.walkAnimationPos * 0.5F) * (0.15F + 0.25F * sp);
			this.tailTip.yRot = Mth.sin(t * 0.12F + 1.2F + s.walkAnimationPos * 0.5F) * (0.2F + 0.3F * sp);
			this.body.zRot = Mth.sin(s.walkAnimationPos * 0.7F) * 0.05F * sp;
			// Jaw: a lazy breath at rest, thrown wide open in a lunge or bite.
			float open = Math.max(s.windup / 14.0F, s.attack);
			this.jaw.xRot = 0.05F + Mth.sin(t * 0.05F) * 0.04F + open * 0.9F;
			this.head.xRot = -open * 0.35F;
			this.head.yRot = Mth.clamp(s.yRot, -25, 25) * DEG * 0.5F;
		}
	}

	public static final class Stalker extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart armL;
		private final ModelPart armR;
		private final ModelPart cloak;
		private final ModelPart legL;
		private final ModelPart legR;

		public Stalker(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.armL = this.body.getChild("arm_l");
			this.armR = this.body.getChild("arm_r");
			this.cloak = this.body.getChild("cloak");
			this.legL = r.getChild("leg_l");
			this.legR = r.getChild("leg_r");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float w = s.walkAnimationPos * 0.45F;
			float sp = walk(s, 1.4F);
			this.legL.xRot = Mth.cos(w) * 0.7F * sp;
			this.legR.xRot = Mth.cos(w + PI) * 0.7F * sp;
			this.armL.xRot = Mth.cos(w + PI) * 0.5F * sp - s.attack * 1.8F + Mth.sin(t * 0.05F) * 0.05F;
			this.armR.xRot = Mth.cos(w) * 0.5F * sp - s.attack * 1.8F + Mth.sin(t * 0.05F + 1) * 0.05F;
			this.armL.zRot = -0.05F;
			this.armR.zRot = 0.05F;
			// The head tilts, listening; a stutter every few seconds.
			this.head.yRot = s.yRot * DEG;
			this.head.xRot = s.xRot * DEG;
			this.head.zRot = Mth.sin(t * 0.03F) * 0.2F + (t % 70 < 3 ? 0.35F : 0);
			this.body.xRot = 0.12F;
			this.cloak.xRot = 0.15F + sp * 0.35F + Mth.sin(t * 0.1F) * 0.06F;
		}
	}

	public static final class Hound extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart tail;
		private final ModelPart gear;
		private final ModelPart[] legs;

		public Hound(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.tail = this.body.getChild("tail");
			this.gear = this.body.getChild("gear");
			this.legs = legs(r);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			trot(this.legs, s, 1.1F, 1.1F);
			float sp = walk(s, 1.6F);
			this.head.yRot = Mth.clamp(s.yRot, -45, 45) * DEG;
			this.head.xRot = s.xRot * DEG * 0.6F - s.attack * 0.5F;
			this.tail.yRot = Mth.sin(t * 0.5F) * 0.25F;
			this.tail.xRot += sp * 0.3F;
			this.gear.xRot = t * (0.15F + sp * 0.4F);
			this.body.y += Mth.sin(s.walkAnimationPos * 2.2F) * 0.5F * sp;
		}
	}

	public static final class Wight extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart armL;
		private final ModelPart armR;
		private final ModelPart mist;

		public Wight(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.head = this.body.getChild("head");
			this.armL = this.body.getChild("arm_l");
			this.armR = this.body.getChild("arm_r");
			this.mist = this.body.getChild("mist");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			this.body.y += Mth.sin(t * 0.07F) * 1.4F - 2.0F;
			this.head.yRot = Mth.clamp(s.yRot, -60, 60) * DEG;
			this.head.xRot = s.xRot * DEG * 0.5F;
			// Gathering cold: both arms rise and spread.
			float c = Math.min(1.0F, s.windup / 12.0F);
			this.armL.xRot = -0.2F + Mth.sin(t * 0.06F) * 0.08F - c * 1.9F;
			this.armR.xRot = -0.2F + Mth.sin(t * 0.06F + 1) * 0.08F - c * 1.9F;
			this.armL.zRot = -0.25F - c * 0.5F;
			this.armR.zRot = 0.25F + c * 0.5F;
			this.mist.xRot = Mth.sin(t * 0.11F) * 0.12F;
			this.mist.zRot = Mth.sin(t * 0.09F + 1) * 0.08F;
		}
	}

	public static final class Whale extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart tail;
		private final ModelPart tail2;
		private final ModelPart flukes;
		private final ModelPart finL;
		private final ModelPart finR;
		private final ModelPart jaw;

		public Whale(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.tail = this.body.getChild("tail");
			this.tail2 = this.tail.getChild("tail2");
			this.flukes = this.tail2.getChild("flukes");
			this.finL = this.body.getChild("fin_l");
			this.finR = this.body.getChild("fin_r");
			this.jaw = this.body.getChild("jaw");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks * 0.06F;
			// A slow wave runs from the body down to the flukes.
			this.body.xRot = Mth.sin(t) * 0.05F;
			this.tail.xRot = Mth.sin(t - 0.8F) * 0.18F;
			this.tail2.xRot = Mth.sin(t - 1.6F) * 0.25F;
			this.flukes.xRot = Mth.sin(t - 2.4F) * 0.3F;
			this.finL.zRot = 0.35F + Mth.sin(t * 0.8F) * 0.22F;
			this.finR.zRot = -this.finL.zRot;
			this.jaw.xRot = Math.max(0, Mth.sin(s.ageInTicks * 0.004F)) * 0.08F;
			this.body.y += Mth.sin(t * 0.5F) * 3.0F;
		}
	}

	public static final class Fin extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart tail;

		public Fin(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.tail = this.body.getChild("tail");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			this.tail.yRot = Mth.sin(t * 0.9F) * 0.45F;
			this.body.yRot = Mth.sin(t * 0.9F + 1) * 0.08F;
		}
	}

	public static final class Fox extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart tail;
		private final ModelPart[] legs;

		public Fox(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.tail = this.body.getChild("tail");
			this.legs = legs(r);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			trot(this.legs, s, 1.0F, 1.1F);
			float sp = walk(s, 1.6F);
			this.head.yRot = Mth.clamp(s.yRot, -40, 40) * DEG;
			this.head.xRot = s.xRot * DEG * 0.6F;
			this.tail.zRot = Mth.sin(t * 0.15F) * 0.2F;
			this.tail.xRot += -sp * 0.4F;
		}
	}

	public static final class Yak extends EntityModel<State> {
		private final ModelPart head;
		private final ModelPart body;
		private final ModelPart[] legs;

		public Yak(ModelPart root) {
			super(root);
			ModelPart r = root.getChild("root");
			this.body = r.getChild("body");
			this.head = this.body.getChild("head");
			this.legs = legs(r);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			trot(this.legs, s, 0.6F, 0.6F);
			this.head.yRot = Mth.clamp(s.yRot, -30, 30) * DEG;
			this.head.xRot = s.xRot * DEG * 0.5F + 0.15F + Mth.sin(t * 0.04F) * 0.05F;
			this.body.zRot = Mth.sin(s.walkAnimationPos * 0.6F) * 0.04F * walk(s, 1.6F);
		}
	}

	public static final class Sprite extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart wingL;
		private final ModelPart wingR;

		public Sprite(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.wingL = this.body.getChild("wing_l");
			this.wingR = this.body.getChild("wing_r");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			float flap = Mth.sin(t * 2.2F);
			this.wingL.yRot = -0.4F - flap * 0.7F;
			this.wingR.yRot = -this.wingL.yRot;
			this.wingL.zRot = -0.2F;
			this.wingR.zRot = 0.2F;
			this.body.y += Mth.sin(t * 0.2F) * 1.0F;
			this.body.yRot = Mth.sin(t * 0.05F) * 0.3F;
		}
	}

	public static final class Ray extends EntityModel<State> {
		private final ModelPart body;
		private final ModelPart wingL;
		private final ModelPart wingR;
		private final ModelPart tail;

		public Ray(ModelPart root) {
			super(root);
			this.body = root.getChild("root").getChild("body");
			this.wingL = this.body.getChild("wing_l");
			this.wingR = this.body.getChild("wing_r");
			this.tail = this.body.getChild("tail");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks * 0.12F;
			this.wingL.zRot = Mth.sin(t) * 0.5F;
			this.wingR.zRot = -this.wingL.zRot;
			this.tail.yRot = Mth.sin(t * 0.7F) * 0.2F;
			this.tail.xRot = Mth.sin(t * 0.5F) * 0.1F;
			this.body.xRot = s.xRot * DEG;
			this.body.y += Mth.cos(t) * 0.6F;
		}
	}
}
