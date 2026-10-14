package dev.afterworld.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.afterworld.AwConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Camera shake. Each kick adds "trauma" that drains away; the view wobbles by trauma squared, so
 * small kicks stay subtle and big ones really move. Applied where the game tilts the view on a hurt.
 */
public final class Shake {
	private static float trauma;
	private static float peak;
	private static long last = Util.getMillis();

	private Shake() {
	}

	public static void kick(float degrees, int ticks) {
		float scale = (float) AwConfig.get().screenShake;
		if (scale <= 0) {
			return;
		}
		peak = Math.max(peak * trauma, degrees * scale);
		trauma = Math.min(1.0F, trauma + 0.6F + ticks / 60.0F);
	}

	public static void apply(PoseStack pose) {
		long now = Util.getMillis();
		float dt = Math.min(0.1F, (now - last) / 1000.0F);
		last = now;
		if (trauma <= 0) {
			return;
		}
		trauma = Math.max(0, trauma - dt * 1.4F);
		float k = trauma * trauma * peak;
		float t = now / 1000.0F;
		float yaw = k * (Mth.sin(t * 37.1F) * 0.6F + Mth.sin(t * 61.7F) * 0.4F);
		float pitch = k * (Mth.sin(t * 43.3F + 1.3F) * 0.6F + Mth.sin(t * 71.9F) * 0.4F);
		float roll = k * 0.5F * Mth.sin(t * 29.5F + 2.1F);
		pose.rotateDegrees(Axis.YP, yaw);
		pose.rotateDegrees(Axis.XP, pitch);
		pose.rotateDegrees(Axis.ZP, roll);
	}
}
