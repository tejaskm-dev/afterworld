package dev.afterworld.client.model;

import dev.afterworld.entity.boss.PilgrimEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The Pilgrim Without Feet: robe, hood, staff and lantern, a pack of stone tablets. */
public class PilgrimModel extends EntityModel<BossRenderStates.Boss> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart staff;

	public PilgrimModel(ModelPart root) {
		super(root);
		ModelPart r = root.getChild("root");
		this.body = r.getChild("body");
		this.head = body.getChild("head");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.staff = rightArm.getChild("staff");
	}

	@Override
	public void setupAnim(BossRenderStates.Boss s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float walk = Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
		body.zRot = Mth.sin(t * 0.05F) * 0.03F;
		body.xRot = 0.05F + walk * 0.06F;
		head.yRot = Mth.clamp(s.yRot, -40, 40) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot = Mth.clamp(s.xRot, -20, 30) * Mth.DEG_TO_RAD * 0.5F;
		float sway = Mth.sin(s.walkAnimationPos * 0.25F) * 0.22F * walk;
		rightArm.xRot = -0.15F + sway;
		leftArm.xRot = -sway;
		leftArm.zRot = -0.06F;
		rightArm.zRot = 0.06F;
		float a = s.animTime;
		switch (s.anim) {
			case PilgrimEntity.ANIM_STOMP_WINDUP -> {
				float p = Math.min(1.0F, a / 25.0F);
				rightArm.xRot = -2.7F * p;
				leftArm.xRot = -2.7F * p;
				body.xRot = -0.2F * p;
			}
			case PilgrimEntity.ANIM_STOMP -> {
				float p = Math.min(1.0F, a / 5.0F);
				rightArm.xRot = Mth.lerp(p, -2.7F, 0.35F);
				leftArm.xRot = Mth.lerp(p, -2.7F, 0.35F);
				body.xRot = Mth.lerp(p, -0.2F, 0.3F);
			}
			case PilgrimEntity.ANIM_THROW -> {
				float p = Math.min(1.0F, a / 14.0F);
				leftArm.xRot = p < 0.6F ? -2.8F * (p / 0.6F) : Mth.lerp((p - 0.6F) / 0.4F, -2.8F, -0.6F);
			}
			case PilgrimEntity.ANIM_KNEEL -> {
				float p = Math.min(1.0F, a / 40.0F);
				body.y += 10.0F * p;
				body.xRot = 0.45F * p;
				head.xRot = 0.6F * p;
				rightArm.xRot = -0.5F * p;
				leftArm.xRot = -0.5F * p;
			}
			default -> {
			}
		}
		staff.xRot = -0.1F - rightArm.xRot * 0.2F;
	}
}
