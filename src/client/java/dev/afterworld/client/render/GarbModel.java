package dev.afterworld.client.render;

import dev.afterworld.player.Garb;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

/**
 * The garb pieces, hung on a copy of the player skeleton. Visibility is decided here from the
 * render state, because models are drawn later and shared between every player on screen.
 */
public final class GarbModel extends PlayerModel {
	private static final float DEG = Mth.DEG_TO_RAD;
	private final ModelPart hood;
	private final ModelPart hoodTip;
	private final ModelPart mask;
	private final ModelPart hat;
	private final ModelPart circlet;
	private final ModelPart hoodCollar;
	private final ModelPart cloak;
	private final ModelPart cloakShort;
	private final ModelPart cloakLong;
	private final ModelPart cloakTattered;
	private final ModelPart mantle;
	private final ModelPart belt;
	private final ModelPart satchel;
	private final ModelPart sash;
	private final ModelPart bandolier;
	private final ModelPart lantern;
	private final ModelPart[] cloakEmblems = new ModelPart[Garb.EMBLEMS];
	private final ModelPart[] badges = new ModelPart[Garb.EMBLEMS];
	private final ModelPart heart;
	private final ModelPart bracerR;
	private final ModelPart bracerL;
	private final ModelPart wrapR;
	private final ModelPart wrapL;
	private final ModelPart bootR;
	private final ModelPart bootL;
	private final ModelPart legwrapR;
	private final ModelPart legwrapL;

	public GarbModel(ModelPart root, boolean slim) {
		super(root, slim);
		this.hood = this.head.getChild("garb_hood");
		this.hoodTip = this.head.getChild("garb_hood_tip");
		this.mask = this.head.getChild("garb_mask");
		this.hat = this.head.getChild("garb_hat");
		this.circlet = this.head.getChild("garb_circlet");
		this.hoodCollar = this.body.getChild("garb_hood_collar");
		this.cloak = this.body.getChild("garb_cloak");
		this.cloakShort = this.cloak.getChild("garb_cloak_short");
		this.cloakLong = this.cloak.getChild("garb_cloak_long");
		this.cloakTattered = this.cloak.getChild("garb_cloak_tattered");
		this.mantle = this.body.getChild("garb_mantle");
		this.belt = this.body.getChild("garb_belt");
		this.satchel = this.body.getChild("garb_satchel");
		this.sash = this.body.getChild("garb_sash");
		this.bandolier = this.body.getChild("garb_bandolier");
		this.lantern = this.body.getChild("garb_lantern");
		for (int i = 1; i < Garb.EMBLEMS; i++) {
			this.cloakEmblems[i] = this.cloak.getChild("garb_cloak_emblem_" + i);
			this.badges[i] = this.body.getChild("garb_badge_" + i);
		}
		this.heart = this.body.getChild("garb_heart");
		this.bracerR = this.rightArm.getChild("garb_bracer");
		this.bracerL = this.leftArm.getChild("garb_bracer");
		this.wrapR = this.rightArm.getChild("garb_wrap");
		this.wrapL = this.leftArm.getChild("garb_wrap");
		this.bootR = this.rightLeg.getChild("garb_boot");
		this.bootL = this.leftLeg.getChild("garb_boot");
		this.legwrapR = this.rightLeg.getChild("garb_legwrap");
		this.legwrapL = this.leftLeg.getChild("garb_legwrap");
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		Garb g = state.getData(GarbRender.GARB);
		boolean on = g != null && g.visible();
		boolean helmet = !state.headEquipment.isEmpty();
		boolean chest = !state.chestEquipment.isEmpty();
		boolean legs = !state.legsEquipment.isEmpty();
		boolean feet = !state.feetEquipment.isEmpty();

		int head = on && !helmet ? g.head() : 0;
		boolean hooded = head == 1 || head == 3;
		this.hood.visible = hooded;
		this.hoodTip.visible = hooded;
		this.mask.visible = head == 3;
		this.hat.visible = head == 2;
		this.circlet.visible = head == 4;

		int cloakKind = on && !state.chestEquipment.is(Items.ELYTRA) ? g.cloak() : 0;
		boolean mantled = on && !chest && (g.cloak() == 4 || g.cloak() == 5);
		this.mantle.visible = mantled;
		this.hoodCollar.visible = hooded && !chest && !mantled;
		this.cloak.visible = cloakKind == 1 || cloakKind == 2 || cloakKind == 3 || cloakKind == 5;
		this.cloakShort.visible = cloakKind == 1;
		this.cloakLong.visible = cloakKind == 2 || cloakKind == 5;
		this.cloakTattered.visible = cloakKind == 3;
		if (this.cloak.visible) {
			// The same swing as the vanilla cape, so cloaks billow when running and settle when still.
			this.cloak.rotateBy(new Quaternionf()
				.rotateY((float) -Math.PI)
				.rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * DEG)
				.rotateZ(state.capeLean2 / 2.0F * DEG)
				.rotateY((180.0F - state.capeLean2 / 2.0F) * DEG));
			if (chest) {
				this.cloak.z += 1.1F;
				this.cloak.y -= 0.85F;
			}
		}

		int accent = on ? g.accent() : 0;
		this.belt.visible = !chest && (accent == 1 || accent == 4);
		this.satchel.visible = !chest && accent == 1;
		this.sash.visible = !chest && accent == 2;
		this.bandolier.visible = !chest && accent == 3;
		this.lantern.visible = accent == 4;
		if (this.lantern.visible) {
			float sway = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.45F * state.walkAnimationSpeed;
			this.lantern.zRot = sway + Mth.sin(state.ageInTicks * 0.07F) * 0.05F;
			this.lantern.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.25F * state.walkAnimationSpeed;
			if (chest) {
				this.lantern.x -= 1.0F;
			}
		}
		int emblem = on ? g.emblem() : 0;
		for (int i = 1; i < Garb.EMBLEMS; i++) {
			this.cloakEmblems[i].visible = emblem == i;
			this.badges[i].visible = emblem == i && !chest;
		}

		// The heart floats a little in front of the chest, and clear of any chestplate.
		this.heart.visible = on && g.heart() >= 0;
		if (this.heart.visible) {
			this.heart.z -= (chest ? 1.3F : 0.0F) + 0.15F + 0.15F * Mth.sin(state.ageInTicks * 0.08F);
		}

		int arms = on && !chest ? g.arms() : 0;
		this.bracerR.visible = arms == 1;
		this.bracerL.visible = arms == 1;
		this.wrapR.visible = arms == 2;
		this.wrapL.visible = arms == 2;
		int feetKind = on && !feet ? g.feet() : 0;
		this.bootR.visible = feetKind == 1;
		this.bootL.visible = feetKind == 1;
		this.legwrapR.visible = feetKind == 2 && !legs;
		this.legwrapL.visible = feetKind == 2 && !legs;
	}
}
