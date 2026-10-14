package dev.afterworld.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.afterworld.client.hud.QuestHud;
import dev.afterworld.net.AwPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A pillar of light on the focused objective. Within sight it stands on the place itself; beyond
 * the fog it stands on the horizon in the right direction, so it can always be walked towards.
 * Also captures the camera each frame so the HUD can project places onto the screen.
 */
public final class QuestBeam {
	private QuestBeam() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			CameraRenderState cam = ctx.levelState().cameraRenderState;
			QuestHud.VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
			QuestHud.cameraPos = cam.pos;
			QuestHud.cameraValid = true;
			AwPayloads.QuestSync.Entry e = QuestHud.focused();
			Minecraft mc = Minecraft.getInstance();
			if (e == null || mc.level == null || !QuestHud.sameDimension(e) || !dev.afterworld.AwConfig.get().questBeam) {
				return;
			}
			Vec3 at = QuestHud.target(e);
			double dx = at.x - cam.pos.x;
			double dz = at.z - cam.pos.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < 5) {
				return;
			}
			double reach = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 - 24);
			double bx = at.x;
			double by = at.y;
			double bz = at.z;
			if (dist > reach) {
				bx = cam.pos.x + dx / dist * reach;
				bz = cam.pos.z + dz / dist * reach;
				by = Math.min(cam.pos.y - 16, at.y);
			}
			float fade = (float) Mth.clamp((dist - 5) / 20.0, 0, 1);
			int color = ARGB.color((int) (255 * fade), QuestHud.color(e));
			float scale = (float) Math.max(1.0, Math.min(dist, reach) / 80.0);
			float time = (mc.level.getGameTime() % 40) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			PoseStack ps = ctx.poseStack();
			ps.pushPose();
			ps.translate(bx - 0.5 - cam.pos.x, by - cam.pos.y, bz - 0.5 - cam.pos.z);
			BeaconRenderer.submitBeaconBeam(ps, ctx.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, time, 0, 320, color,
				0.16F * scale, 0.24F * scale);
			ps.popPose();
		});
	}
}
