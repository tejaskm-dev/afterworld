package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.afterworld.util.Acts;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The weapon in your own hand acting out a power, seen from your own eyes: raised overhead and
 * brought down for a slam, drawn across for a sweep, driven forward for a lunge, wound back for
 * a throw. (Others see the whole body do it; see {@link PlayerPoses}.)
 */
public final class FirstPersonActs {
	private FirstPersonActs() {
	}

	/** Moves the main hand for the local player's act, if any. {@code side} is 1 for the right hand, -1 for the left. */
	public static void apply(PoseStack ps, float side) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		PlayerPoses.Acting a = PlayerPoses.acting(mc.player);
		if (a == null) {
			return;
		}
		float u = a.u();
		float k = PlayerPoses.envelope(u);
		float dx = 0;
		float dy = 0;
		float dz = 0;
		float rx = 0;
		float ry = 0;
		float rz = 0;
		float wind;
		float strike;
		switch (a.act()) {
			case Acts.CAST, Acts.POINT -> {
				float push = PlayerPoses.bell(PlayerPoses.in(u, 0.05F, 0.65F));
				dz = -0.28F * push;
				dy = 0.1F * push;
				rx = -14 * push;
				dx = -0.08F * push;
			}
			case Acts.INVOKE -> {
				float up = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.3F)) * (1 - PlayerPoses.ease(PlayerPoses.in(u, 0.6F, 0.85F)));
				dy = 0.5F * up;
				dx = -0.12F * up;
				rx = 35 * up;
			}
			case Acts.SLAM, Acts.PLANT -> {
				wind = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.35F));
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.35F, 0.52F));
				dy = Mth.lerp(strike, 0.38F * wind, -0.32F);
				dz = Mth.lerp(strike, 0.12F * wind, -0.25F);
				rx = Mth.lerp(strike, 50 * wind, -65);
			}
			case Acts.SWEEP, Acts.SPIN -> {
				wind = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.3F));
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.3F, a.act() == Acts.SPIN ? 0.7F : 0.5F));
				dx = Mth.lerp(strike, 0.25F * wind, -0.62F);
				dz = -0.1F * strike;
				ry = Mth.lerp(strike, -45 * wind, 70);
				rz = 18 * strike;
			}
			case Acts.THRUST -> {
				wind = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.25F));
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.25F, 0.4F));
				dz = Mth.lerp(strike, 0.16F * wind, -0.5F);
				dx = -0.12F * strike;
				rx = -10 * strike;
			}
			case Acts.THROW, Acts.LASH -> {
				wind = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.3F));
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.3F, 0.45F));
				dy = Mth.lerp(strike, 0.3F * wind, a.act() == Acts.THROW ? -0.45F : -0.1F);
				dz = Mth.lerp(strike, 0.25F * wind, -0.4F);
				rx = Mth.lerp(strike, 60 * wind, -50);
			}
			case Acts.CLAP -> {
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.25F, 0.42F));
				dx = -0.34F * strike;
				dz = -0.18F * strike;
				ry = 25 * strike;
				if (u > 0.42F && u < 0.55F) {
					dy += Mth.sin(u * 140) * 0.02F;
				}
			}
			case Acts.BRACE -> {
				dx = -0.22F;
				dy = 0.06F;
				dz = -0.06F;
				ry = 32;
				rz = -10;
			}
			case Acts.LEAP -> {
				float air = PlayerPoses.bell(PlayerPoses.in(u, 0.05F, 0.95F));
				dy = 0.28F * air;
				rx = 32 * air;
			}
			case Acts.FLURRY -> {
				float w = Mth.sin(u * Mth.PI * 6);
				dx = 0.3F * w;
				dz = -0.16F;
				ry = 30 * w;
				rz = 22 * w;
			}
			case Acts.RISE -> {
				wind = PlayerPoses.ease(PlayerPoses.in(u, 0, 0.28F));
				strike = PlayerPoses.ease(PlayerPoses.in(u, 0.28F, 0.45F));
				dy = Mth.lerp(strike, -0.25F * wind, 0.36F);
				rx = Mth.lerp(strike, -30 * wind, 50);
			}
			default -> {
				return;
			}
		}
		ps.translate(dx * side * k, dy * k, dz * k);
		if (rx != 0) {
			ps.rotateDegrees(Axis.XP, rx * k);
		}
		if (ry != 0) {
			ps.rotateDegrees(Axis.YP, ry * side * k);
		}
		if (rz != 0) {
			ps.rotateDegrees(Axis.ZP, rz * side * k);
		}
	}
}
