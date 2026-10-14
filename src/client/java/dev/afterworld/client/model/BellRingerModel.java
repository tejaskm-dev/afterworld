package dev.afterworld.client.model;

import dev.afterworld.entity.boss.BellRingerEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The Bell-Ringer: hunched, long-armed, dragging its bell from one hand. */
public class BellRingerModel extends EntityModel<BossRenderStates.Ringer> {
	private final ModelPart hips;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart bell;
	private final ModelPart bellHand;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public BellRingerModel(ModelPart root) {
		super(root);
		this.hips = root.getChild("root").getChild("hips");
		this.torso = hips.getChild("torso");
		this.head = torso.getChild("head");
		this.rightArm = torso.getChild("right_arm");
		this.leftArm = torso.getChild("left_arm");
		this.bell = hips.getChild("bell");
		this.bellHand = rightArm.getChild("bell_hand");
		this.rightLeg = hips.getChild("right_leg");
		this.leftLeg = hips.getChild("left_leg");
	}

	@Override
	public void setupAnim(BossRenderStates.Ringer s) {
		super.setupAnim(s);
		float walk = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);
		float pos = s.walkAnimationPos * 0.45F;
		rightLeg.xRot = Mth.cos(pos) * 0.7F * walk;
		leftLeg.xRot = -Mth.cos(pos) * 0.7F * walk;
		rightArm.xRot = -0.35F - Mth.cos(pos) * 0.4F * walk;
		leftArm.xRot = -0.35F + Mth.cos(pos) * 0.4F * walk;
		head.yRot = Mth.clamp(s.yRot, -50, 50) * Mth.DEG_TO_RAD;
		head.xRot = Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD - 0.4F;
		torso.zRot = Mth.sin(s.ageInTicks * 0.08F) * 0.04F;
		boolean lifted = s.anim == BellRingerEntity.ANIM_RING_WINDUP || s.anim == BellRingerEntity.ANIM_RING || s.anim == BellRingerEntity.ANIM_THROW;
		bell.visible = s.bellHeld && !lifted;
		bellHand.visible = s.bellHeld && lifted;
		bell.zRot = Mth.sin(s.walkAnimationPos * 0.45F) * 0.08F * walk;
		float a = s.animTime;
		switch (s.anim) {
			case BellRingerEntity.ANIM_RING_WINDUP -> {
				float p = Math.min(1.0F, a / 30.0F);
				rightArm.xRot = Mth.lerp(p, -0.35F, -3.0F);
				torso.xRot = Mth.lerp(p, 0.55F, 0.15F);
				bellHand.xRot = Mth.sin(a * 0.6F) * 0.3F;
			}
			case BellRingerEntity.ANIM_RING -> {
				float p = Math.min(1.0F, a / 6.0F);
				rightArm.xRot = Mth.lerp(p, -3.0F, -0.2F);
				torso.xRot = Mth.lerp(p, 0.15F, 0.75F);
			}
			case BellRingerEntity.ANIM_THROW -> {
				float p = Math.min(1.0F, a / 18.0F);
				rightArm.xRot = p < 0.7F ? -3.0F * (p / 0.7F) : -3.0F + (p - 0.7F) / 0.3F * 2.6F;
			}
			case BellRingerEntity.ANIM_SWING -> {
				float p = Math.min(1.0F, a / 8.0F);
				leftArm.xRot = Mth.lerp(p, -2.2F, 0.2F);
				leftArm.yRot = Mth.lerp(p, 0.6F, -0.4F);
			}
			case BellRingerEntity.ANIM_STUNNED -> {
				torso.xRot = 1.0F;
				head.xRot = 0.6F;
				rightArm.xRot = 0.2F;
				leftArm.xRot = 0.2F;
				torso.zRot = Mth.sin(s.ageInTicks * 0.5F) * 0.12F;
			}
			case BellRingerEntity.ANIM_REMEMBER -> {
				float p = Math.min(1.0F, a / 40.0F);
				hips.y += 6.0F * p;
				torso.xRot = Mth.lerp(p, 0.55F, 0.95F);
				head.xRot = 0.7F * p;
				rightArm.xRot = -0.1F;
				leftArm.xRot = -0.1F;
			}
			default -> {
			}
		}
	}
}
