package dev.afterworld.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Wardens' bodies, animated by what they are doing: a one-armed wind-up and its strike, both
 * arms raised and brought down in a slam, arms held up to cast, a spin, a stagger, the long sleep
 * of a statue and the rising from it. Floating fists, capes, tatters and lanterns move on their own.
 */
public final class WardenModel extends EntityModel<WardenModel.State> {
	public static final class State extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState {
		public int anim;
		public float animTime;
		public boolean dormant;
		public boolean floats;
		public int phase = 1;
	}

	private final Map<String, ModelPart> parts = new HashMap<>();
	private final List<ModelPart> tatters = new ArrayList<>();

	public WardenModel(ModelPart root, String[] paths) {
		super(root);
		for (String path : paths) {
			ModelPart p = root;
			for (String seg : path.split("/")) {
				p = p.getChild(seg);
			}
			this.parts.put(path.substring(path.lastIndexOf('/') + 1), p);
		}
		for (int i = 1; i <= 8; i++) {
			ModelPart t = this.parts.get("tent" + i);
			if (t != null) {
				this.tatters.add(t);
			}
		}
	}

	private ModelPart part(String name) {
		return this.parts.get(name);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float at = s.animTime;
		float pos = s.walkAnimationPos;
		float spd = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
		ModelPart root = part("root");
		ModelPart body = part("body");
		ModelPart head = part("head");
		ModelPart armL = part("arm_l");
		ModelPart armR = part("arm_r");
		ModelPart legL = part("leg_l");
		ModelPart legR = part("leg_r");
		float sleep = s.dormant ? 1 : s.anim == 7 ? 1 : s.anim == 8 ? 1 - ease(at / 40F) : 0;

		if (head != null) {
			head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * (1 - sleep);
			head.xRot += s.xRot * Mth.DEG_TO_RAD * 0.6F * (1 - sleep) + sleep * 0.6F;
		}
		if (body != null) {
			body.y += Mth.sin(t * 0.07F) * 0.35F * (1 - sleep);
			body.xRot += sleep * 0.35F;
		}
		if (s.floats && root != null) {
			root.y -= 4 + Mth.sin(t * 0.08F) * 1.2F;
		}
		// Walking.
		if (legL != null && legR != null) {
			float a = Mth.cos(pos * 0.6662F) * 1.0F * spd;
			legL.xRot += a;
			legR.xRot -= a;
		}
		String[] quad = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
		if (part(quad[0]) != null) {
			float a = Mth.cos(pos * 0.6662F) * 0.8F * spd;
			part(quad[0]).xRot += a;
			part(quad[3]).xRot += a;
			part(quad[1]).xRot -= a;
			part(quad[2]).xRot -= a;
		}
		if (armL != null && armR != null) {
			float swing = Mth.cos(pos * 0.6662F) * 0.7F * spd;
			armL.xRot -= swing;
			armR.xRot += swing;
			armL.zRot -= Mth.sin(t * 0.06F) * 0.04F;
			armR.zRot += Mth.sin(t * 0.06F) * 0.04F;
			switch (s.anim) {
				case 1 -> { // wind-up: the weapon arm drawn back
					float k = ease(at / 8F);
					armR.xRot -= 2.3F * k;
					armL.xRot -= 0.5F * k;
					if (body != null) {
						body.yRot += 0.35F * k;
					}
				}
				case 2 -> { // the strike: down and across
					float k = ease(at / 5F);
					armR.xRot += -2.3F + 2.9F * k;
					armR.yRot -= 0.5F * k;
					if (body != null) {
						body.yRot += 0.35F - 0.7F * k;
					}
				}
				case 10 -> { // both arms raised overhead
					float k = ease(at / 10F);
					armL.xRot -= 2.8F * k;
					armR.xRot -= 2.8F * k;
					if (body != null) {
						body.xRot -= 0.15F * k;
					}
				}
				case 3 -> { // the slam: down hard
					float k = ease(at / 4F);
					armL.xRot += -2.8F + 2.4F * k;
					armR.xRot += -2.8F + 2.4F * k;
					if (body != null) {
						body.xRot += 0.25F * k;
					}
				}
				case 4 -> { // casting: arms up and forward, trembling
					float k = ease(at / 8F);
					armL.xRot -= 1.7F * k;
					armR.xRot -= 1.7F * k;
					armL.zRot -= 0.3F * k;
					armR.zRot += 0.3F * k;
					armL.yRot += Mth.sin(t * 0.9F) * 0.05F * k;
				}
				case 5 -> { // spinning
					if (body != null) {
						body.yRot += at * 0.9F;
					}
					armL.zRot -= 1.2F;
					armR.zRot += 1.2F;
				}
				case 9 -> { // an overhand throw
					float k = at < 6 ? ease(at / 6F) : 1 - ease((at - 6) / 6F);
					armR.xRot -= 2.6F * k;
				}
				case 6 -> { // staggered
					if (body != null) {
						body.xRot -= 0.3F;
					}
				}
				default -> {
				}
			}
			armL.xRot += sleep * 0.4F;
			armR.xRot += sleep * 0.4F;
		} else if (body != null) {
			// The turtle: no arms, so the whole shell rears and stamps.
			if (s.anim == 10) {
				body.xRot -= 0.3F * ease(at / 10F);
			} else if (s.anim == 3) {
				body.xRot += -0.3F + 0.45F * ease(at / 4F);
			} else if (s.anim == 5) {
				body.yRot += at * 0.8F;
			}
		}
		// Floating fists on their chains.
		for (String side : new String[] {"fist_l", "fist_r"}) {
			ModelPart f = part(side);
			if (f == null) {
				continue;
			}
			float ph = side.equals("fist_l") ? 0 : Mth.PI;
			f.y += Mth.sin(t * 0.09F + ph) * 1.5F * (1 - sleep) + sleep * 6;
			f.xRot += Mth.sin(t * 0.05F + ph) * 0.1F;
			if (s.anim == 10 || s.anim == 4) {
				f.y -= 6 * ease(at / 10F);
			} else if (s.anim == 3) {
				f.y += -6 + 12 * ease(at / 4F);
			}
		}
		for (String c : new String[] {"chain_l", "chain_r"}) {
			ModelPart p = part(c);
			if (p != null) {
				p.zRot += Mth.sin(t * 0.09F) * 0.08F;
			}
		}
		ModelPart cape = part("cape");
		if (cape != null) {
			cape.xRot += 0.1F + spd * 0.35F + Mth.sin(t * 0.09F) * 0.05F;
		}
		for (int i = 0; i < this.tatters.size(); i++) {
			ModelPart p = this.tatters.get(i);
			p.xRot += Mth.sin(t * 0.1F + i * 1.1F) * 0.2F;
			p.zRot += Mth.cos(t * 0.08F + i * 1.7F) * 0.12F;
		}
		ModelPart lantern = part("lantern");
		if (lantern != null) {
			lantern.zRot += Mth.sin(t * 0.12F) * 0.25F;
			lantern.xRot += Mth.cos(t * 0.1F) * 0.15F;
		}
		ModelPart jaw = part("jaw");
		if (jaw != null) {
			jaw.xRot += (s.anim == 4 || s.anim == 10 ? 0.6F * ease(at / 6F) : 0) + Mth.sin(t * 0.05F) * 0.03F;
		}
		ModelPart tail = part("tail");
		if (tail != null) {
			tail.yRot += Mth.sin(t * 0.08F) * 0.2F;
		}
	}
}
