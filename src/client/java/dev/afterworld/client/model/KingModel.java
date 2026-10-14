package dev.afterworld.client.model;

import dev.afterworld.entity.boss.KingEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The King of Seconds: brass and iron, a clock for a heart, a gear for a spine. */
public class KingModel extends EntityModel<BossRenderStates.Boss> {
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart scepter;
	private final ModelPart hourHand;
	private final ModelPart minuteHand;
	private final ModelPart gear;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public KingModel(ModelPart root) {
		super(root);
		ModelPart r = root.getChild("root");
		this.torso = r.getChild("torso");
		this.head = torso.getChild("head");
		this.rightArm = torso.getChild("right_arm");
		this.leftArm = torso.getChild("left_arm");
		this.scepter = rightArm.getChild("scepter");
		this.hourHand = torso.getChild("hour_hand");
		this.minuteHand = torso.getChild("minute_hand");
		this.gear = torso.getChild("gear");
		this.rightLeg = r.getChild("right_leg");
		this.leftLeg = r.getChild("left_leg");
	}

	@Override
	public void setupAnim(BossRenderStates.Boss s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float walk = Math.min(1.0F, s.walkAnimationSpeed * 1.8F);
		float pos = s.walkAnimationPos * 0.6F;
		rightLeg.xRot = Mth.cos(pos) * 0.6F * walk;
		leftLeg.xRot = -Mth.cos(pos) * 0.6F * walk;
		rightArm.xRot = -Mth.cos(pos) * 0.35F * walk;
		leftArm.xRot = Mth.cos(pos) * 0.35F * walk;
		head.yRot = Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD;
		head.xRot = Mth.clamp(s.xRot, -25, 25) * Mth.DEG_TO_RAD;
		boolean looping = s.anim == KingEntity.ANIM_LOOP;
		minuteHand.zRot = t * (looping ? -0.6F : 0.08F);
		hourHand.zRot = t * (looping ? -0.15F : 0.007F);
		gear.zRot = t * 0.04F;
		float a = s.animTime;
		switch (s.anim) {
			case KingEntity.ANIM_SWEEP_WINDUP -> {
				float p = Math.min(1.0F, a / 14.0F);
				rightArm.xRot = -2.2F * p;
				rightArm.yRot = 0.8F * p;
			}
			case KingEntity.ANIM_SWEEP -> {
				float p = Math.min(1.0F, a / 6.0F);
				rightArm.xRot = Mth.lerp(p, -2.2F, -1.0F);
				rightArm.yRot = Mth.lerp(p, 0.8F, -1.2F);
			}
			case KingEntity.ANIM_LOOP -> {
				leftArm.xRot = -1.6F;
				leftArm.zRot = -0.3F;
			}
			case KingEntity.ANIM_STAGGER -> {
				torso.xRot = 0.35F;
				torso.zRot = Mth.sin(t * 0.6F) * 0.15F;
				head.xRot = 0.4F;
			}
			case KingEntity.ANIM_KNEEL -> {
				float p = Math.min(1.0F, a / 30.0F);
				torso.y += 6.0F * p;
				rightLeg.xRot = -1.4F * p;
				leftLeg.xRot = 0.3F * p;
				head.xRot = 0.5F * p;
				rightArm.xRot = -0.4F * p;
			}
			default -> {
			}
		}
		scepter.xRot = 0.2F + Mth.sin(t * 0.1F) * 0.05F;
	}
}
