package dev.afterworld.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * One model for the whole second bestiary. Parts are found by name and animated by what they are:
 * legs walk (four in diagonal pairs, two like a person, many in a ripple), wings flap in flight,
 * tails and tentacles sway, jaws open to bite, heads follow their gaze, and everything breathes.
 */
public final class GenericCreatureModel extends EntityModel<GenericCreatureModel.State> {
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final float PI = Mth.PI;

	/** What the model needs from its creature, beyond what every living thing has. */
	public static class State extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState {
		public int action;
		public float actionTime;
		public boolean burrowed;
		public boolean flying;
		public boolean swimmer;
		public boolean inWaterNow;
		public float attack;
	}

	private final Map<String, ModelPart> parts = new HashMap<>();
	private final ModelPart rootPart;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart jaw;
	private final ModelPart[] quad = new ModelPart[4];
	private final ModelPart legL;
	private final ModelPart legR;
	private final ModelPart armL;
	private final ModelPart armR;
	private final ModelPart wingL;
	private final ModelPart wingR;
	private final List<ModelPart> bugL = new ArrayList<>();
	private final List<ModelPart> bugR = new ArrayList<>();
	private final List<ModelPart> tails = new ArrayList<>();
	private final List<ModelPart> tents = new ArrayList<>();

	public GenericCreatureModel(ModelPart root, String[] paths) {
		super(root);
		for (String path : paths) {
			ModelPart p = root;
			for (String seg : path.split("/")) {
				p = p.getChild(seg);
			}
			this.parts.put(path.substring(path.lastIndexOf('/') + 1), p);
		}
		this.rootPart = this.parts.get("root");
		this.head = this.parts.get("head");
		this.body = this.parts.get("body");
		this.jaw = this.parts.get("jaw");
		String[] q = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
		for (int i = 0; i < 4; i++) {
			this.quad[i] = this.parts.get(q[i]);
		}
		this.legL = this.parts.get("leg_l");
		this.legR = this.parts.get("leg_r");
		this.armL = this.parts.get("arm_l");
		this.armR = this.parts.get("arm_r");
		this.wingL = this.parts.get("wing_l");
		this.wingR = this.parts.get("wing_r");
		for (int i = 1; i <= 4; i++) {
			if (this.parts.containsKey("leg_l" + i)) {
				this.bugL.add(this.parts.get("leg_l" + i));
				this.bugR.add(this.parts.get("leg_r" + i));
			}
		}
		for (String t : new String[] {"tail", "tail2", "tail3", "tail_l", "tail_r"}) {
			if (this.parts.containsKey(t)) {
				this.tails.add(this.parts.get(t));
			}
		}
		for (int i = 1; i <= 8; i++) {
			if (this.parts.containsKey("tent" + i)) {
				this.tents.add(this.parts.get("tent" + i));
			}
		}
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float pos = s.walkAnimationPos;
		float spd = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
		boolean winding = s.action == 2;
		boolean striking = s.action == 3 || s.action == 1;
		float strike = striking ? Math.max(0, 1 - s.actionTime / 8.0F) : 0;
		float wind = winding ? Math.min(1, s.actionTime / 6.0F) : 0;
		float bite = Math.max(s.attack, Math.max(strike, wind * 0.6F));

		if (this.head != null) {
			this.head.yRot += Mth.clamp(s.yRot, -50, 50) * DEG;
			this.head.xRot += s.xRot * DEG * 0.7F;
		}
		if (this.body != null) {
			this.body.y += Mth.sin(t * 0.08F) * 0.25F;
			this.body.xRot += wind * -0.12F;
			if (s.swimmer) {
				this.body.yRot += Mth.sin(t * 0.25F) * 0.06F + Mth.sin(pos * 0.6F) * 0.15F * spd;
			}
		}
		if (this.jaw != null) {
			this.jaw.xRot += bite * 0.75F + Mth.sin(t * 0.05F) * 0.03F;
		}
		// Four legs: diagonal pairs.
		if (this.quad[0] != null) {
			float a = Mth.cos(pos * 0.6662F) * 1.1F * spd;
			this.quad[0].xRot += a;
			this.quad[3].xRot += a;
			this.quad[1].xRot -= a;
			this.quad[2].xRot -= a;
		}
		// Two legs and arms: a walk, and a heavy two-handed blow.
		if (this.legL != null) {
			float a = Mth.cos(pos * 0.6662F) * 1.2F * spd;
			this.legL.xRot += a;
			this.legR.xRot -= a;
		}
		if (this.armL != null) {
			float a = Mth.cos(pos * 0.6662F) * 0.9F * spd;
			this.armL.xRot -= a;
			this.armR.xRot += a;
			float raise = wind * 2.2F + bite * 1.4F;
			this.armL.xRot -= raise;
			this.armR.xRot -= raise;
			this.armL.zRot -= Mth.sin(t * 0.07F) * 0.04F;
			this.armR.zRot += Mth.sin(t * 0.07F) * 0.04F;
		}
		// Many legs: a ripple down each side, the sides out of step.
		for (int i = 0; i < this.bugL.size(); i++) {
			float ph = pos * 0.9F + i * PI * 0.5F;
			float sweep = Mth.sin(ph) * 0.45F * spd;
			float lift = Math.abs(Mth.cos(ph)) * 0.35F * spd;
			this.bugL.get(i).yRot += sweep;
			this.bugL.get(i).zRot += lift;
			this.bugR.get(i).yRot -= sweep;
			this.bugR.get(i).zRot -= Math.abs(Mth.cos(ph + PI)) * 0.35F * spd;
		}
		// Wings: flapping in the air, folded at rest.
		if (this.wingL != null) {
			if (s.flying || s.swimmer) {
				float f = Mth.sin(t * 0.9F) * 0.85F;
				this.wingL.zRot += f;
				this.wingR.zRot -= f;
			} else {
				this.wingL.zRot += 0.08F + Mth.sin(t * 0.05F) * 0.03F;
				this.wingR.zRot -= 0.08F + Mth.sin(t * 0.05F) * 0.03F;
			}
		}
		// Tails sway, more when running.
		for (int i = 0; i < this.tails.size(); i++) {
			ModelPart p = this.tails.get(i);
			p.yRot += Mth.sin(t * 0.12F - i * 0.7F) * 0.22F + Mth.sin(pos * 0.6662F) * 0.25F * spd;
		}
		// Tentacles, vines and tatters drift.
		for (int i = 0; i < this.tents.size(); i++) {
			ModelPart p = this.tents.get(i);
			p.xRot += Mth.sin(t * 0.1F + i * 1.1F) * 0.22F;
			p.zRot += Mth.cos(t * 0.08F + i * 1.7F) * 0.16F;
		}
		ModelPart claw = this.parts.get("claw_l");
		if (claw != null) {
			claw.yRot += Mth.sin(t * 0.3F) * 0.08F + bite * 0.5F;
			this.parts.get("claw_r").yRot -= Mth.sin(t * 0.3F) * 0.08F + bite * 0.5F;
		}
		for (String ear : new String[] {"ear_l", "ear_r"}) {
			ModelPart p = this.parts.get(ear);
			if (p != null && (int) (t / 40) % 7 == 0) {
				p.zRot += Mth.sin(t * 1.3F) * 0.15F;
			}
		}
		ModelPart finL = this.parts.get("fin_l");
		if (finL != null) {
			float f = Mth.sin(t * 0.3F) * 0.3F;
			finL.zRot += f;
			this.parts.get("fin_r").zRot -= f;
		}
		ModelPart lure = this.parts.get("lure");
		if (lure != null) {
			lure.zRot += Mth.sin(t * 0.08F) * 0.3F;
		}
		ModelPart cap = this.parts.get("cap");
		if (cap != null) {
			cap.zRot += Mth.sin(t * 0.07F) * 0.05F;
			cap.y += Mth.sin(t * 0.2F) * 0.3F * spd;
		}
		ModelPart neck = this.parts.get("neck");
		if (neck != null) {
			neck.xRot += Mth.sin(t * 0.06F) * 0.04F + Mth.sin(pos * 0.6662F * 2) * 0.05F * spd;
		}
		// A burrower lies sunk in the ground with only its back showing.
		if (s.burrowed && this.rootPart != null) {
			this.rootPart.y += 14.0F;
		}
	}
}
