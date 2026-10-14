package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.afterworld.client.ClientMoves;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.util.Acts;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The body acting out Afterworld's moves: climbing hand over hand up a wall, hauling up over a
 * ledge, a feet-first slide, the lean of a dodge, arms spread in a glide. The local player's
 * moves come straight from {@link ClientMoves}; other players' from the server ({@link AwPayloads.PoseSync}).
 * Limbs are set after the game's own animation; the whole body is tipped in the renderer.
 */
public final class PlayerPoses {
	public static final RenderStateDataKey<ClientMoves.Pose> POSE = RenderStateDataKey.create(() -> "afterworld:pose");
	/** The power a player is acting out this frame (see {@link Acts}). */
	public static final RenderStateDataKey<Acting> ACT = RenderStateDataKey.create(() -> "afterworld:act");

	/** An act under way: which, how far through it (0..1), and how long it lasts. */
	public record Acting(int act, float u, float ticks) {
	}

	private record Started(int act, float start, int ticks) {
	}

	private static final Map<Integer, Started> ACTS = new HashMap<>();
	/** Ticks a move lasts, for moves the server only reports the start of. */
	private static final int[] LENGTH = {0, -1, 10, 14, 3, -1};

	private record Remote(int kind, long until, int length, Direction wall) {
	}

	private static final Map<Integer, Remote> REMOTE = new HashMap<>();

	private PlayerPoses() {
	}

	/** What a player's body is doing this frame. */
	public static ClientMoves.Pose of(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			return ClientMoves.pose();
		}
		Remote r = REMOTE.get(entity.getId());
		if (r == null || mc.level == null) {
			return ClientMoves.Pose.NONE;
		}
		long now = mc.level.getGameTime();
		if (r.until >= 0 && now > r.until) {
			REMOTE.remove(entity.getId());
			return ClientMoves.Pose.NONE;
		}
		float progress = r.length > 0 ? Mth.clamp((r.until - now) / (float) r.length, 0, 1) : 0;
		if (r.kind == ClientMoves.Pose.MANTLE) {
			progress = 1 - progress;
		}
		return new ClientMoves.Pose(r.kind, progress, r.wall);
	}

	/** Another player's move, as the server reports it. */
	public static void remote(AwPayloads.PoseSync sync) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (sync.kind() == ClientMoves.Pose.IDLE) {
			REMOTE.remove(sync.entity());
			return;
		}
		int length = sync.kind() < LENGTH.length ? LENGTH[sync.kind()] : -1;
		Direction wall = sync.wall() >= 0 && sync.wall() < 6 ? Direction.from3DDataValue(sync.wall()) : null;
		REMOTE.put(sync.entity(), new Remote(sync.kind(), length > 0 ? mc.level.getGameTime() + length : -1, length, wall));
	}

	public static void clear() {
		REMOTE.clear();
		ACTS.clear();
	}

	/** A player begins acting out a power. */
	public static void act(AwPayloads.Act a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (a.act() == Acts.NONE) {
			ACTS.remove(a.entity());
			return;
		}
		ACTS.put(a.entity(), new Started(a.act(), mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), Math.max(2, a.ticks())));
		if (ACTS.size() > 256) {
			ACTS.clear();
		}
	}

	/** What this entity is acting out now, or null. */
	public static Acting acting(Entity e) {
		Started s = ACTS.get(e.getId());
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.level == null) {
			return null;
		}
		float now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float u = (now - s.start) / s.ticks;
		if (u >= 1 || u < -0.5F) {
			ACTS.remove(e.getId());
			return null;
		}
		return new Acting(s.act, Mth.clamp(u, 0, 1), s.ticks);
	}

	static float in(float u, float a, float b) {
		return Mth.clamp((u - a) / (b - a), 0, 1);
	}

	static float ease(float t) {
		return 1 - (1 - t) * (1 - t);
	}

	static float bell(float t) {
		return Mth.sin(Mth.clamp(t, 0, 1) * Mth.PI);
	}

	/** In quickly, held, out at the end: how much an act owns the body. */
	static float envelope(float u) {
		return Math.min(1, Math.min(u / 0.1F, (1 - u) / 0.2F));
	}

	/** Sets the limbs for the move (after the game's own walk, crouch and swing animation). */
	public static void animate(PlayerModel model, AvatarRenderState state) {
		Acting acting = state.getData(ACT);
		if (acting != null) {
			animateAct(model, state, acting);
			return;
		}
		ClientMoves.Pose pose = state.getData(POSE);
		if (pose == null || pose.kind() == ClientMoves.Pose.IDLE) {
			return;
		}
		float t = state.ageInTicks;
		switch (pose.kind()) {
			case ClientMoves.Pose.CLIMB -> {
				// Hand over hand: the arms reach up in turn, the legs push in turn.
				float amp = Mth.clamp(Math.abs(pose.progress()) * 9.0F + 0.15F, 0.15F, 1.0F);
				float c = Mth.sin(t * 0.55F) * amp;
				model.rightArm.xRot = -2.75F + c * 0.5F;
				model.leftArm.xRot = -2.75F - c * 0.5F;
				model.rightArm.zRot = 0.18F;
				model.leftArm.zRot = -0.18F;
				model.rightArm.yRot = 0;
				model.leftArm.yRot = 0;
				model.rightLeg.xRot = -0.45F - c * 0.55F;
				model.leftLeg.xRot = -0.45F + c * 0.55F;
				model.rightLeg.zRot = 0.08F;
				model.leftLeg.zRot = -0.08F;
				model.body.xRot = 0.12F;
				model.head.xRot = Math.min(model.head.xRot, -0.25F);
			}
			case ClientMoves.Pose.MANTLE -> {
				// Hands on the edge, then pushing down on it as the body rises over.
				float p = pose.progress();
				float lift = Mth.sin(p * Mth.PI);
				model.rightArm.xRot = Mth.lerp(p, -2.9F, -0.7F);
				model.leftArm.xRot = Mth.lerp(p, -2.9F, -0.7F);
				model.rightArm.zRot = 0.12F;
				model.leftArm.zRot = -0.12F;
				model.rightLeg.xRot = -1.3F * lift;
				model.leftLeg.xRot = -0.6F * lift;
				model.body.xRot = 0.45F * lift;
			}
			case ClientMoves.Pose.SLIDE -> {
				// Feet first, one leg tucked, hands trailing behind for balance.
				model.rightLeg.xRot = -1.45F;
				model.leftLeg.xRot = -0.95F;
				model.rightLeg.zRot = 0.05F;
				model.leftLeg.zRot = -0.12F;
				model.rightArm.xRot = 0.75F;
				model.leftArm.xRot = 0.35F;
				model.rightArm.zRot = 0.55F;
				model.leftArm.zRot = -0.75F;
				model.body.xRot = 0;
				model.head.xRot = Math.max(model.head.xRot - 0.6F, -1.2F);
			}
			case ClientMoves.Pose.DODGE -> {
				// Low and quick: arms swept back, legs mid-stride.
				float k = pose.progress();
				model.rightArm.xRot = 0.9F * k + model.rightArm.xRot * (1 - k);
				model.leftArm.xRot = 0.9F * k + model.leftArm.xRot * (1 - k);
				model.rightArm.zRot = 0.35F * k;
				model.leftArm.zRot = -0.35F * k;
				model.rightLeg.xRot = -0.7F * k;
				model.leftLeg.xRot = 0.5F * k;
			}
			case ClientMoves.Pose.GLIDE -> {
				// Arms spread wide under the glider, legs together and trailing.
				float sway = Mth.sin(t * 0.18F) * 0.08F;
				model.rightArm.xRot = -0.25F;
				model.leftArm.xRot = -0.25F;
				model.rightArm.zRot = 1.35F + sway;
				model.leftArm.zRot = -1.35F + sway;
				model.rightLeg.xRot = 0.2F;
				model.leftLeg.xRot = 0.28F;
				model.rightLeg.zRot = 0.03F;
				model.leftLeg.zRot = -0.03F;
				model.body.xRot = 0;
			}
			default -> {
			}
		}
		// The sleeves, trousers and hat are children of their parts, so they follow.
	}

	/**
	 * The limbs acting out a power: wound up, struck through, held a moment and let go. The arm
	 * that holds the weapon is the main arm; the other helps (two hands on a hammer, a palm
	 * thrown out with a cast).
	 */
	private static void animateAct(PlayerModel model, AvatarRenderState state, Acting a) {
		float u = a.u();
		float k = envelope(u);
		boolean right = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
		float side = right ? 1 : -1;
		net.minecraft.client.model.geom.ModelPart main = right ? model.rightArm : model.leftArm;
		net.minecraft.client.model.geom.ModelPart off = right ? model.leftArm : model.rightArm;
		float wind;
		float strike;
		switch (a.act()) {
			case Acts.CAST -> {
				float push = bell(in(u, 0.05F, 0.6F));
				arm(main, k, -1.45F - 0.3F * push, -0.25F * side, 0.05F * side);
				arm(off, k * 0.8F, -1.2F - 0.2F * push, 0.3F * side, -0.05F * side);
			}
			case Acts.INVOKE -> {
				float up = ease(in(u, 0, 0.3F));
				float down = ease(in(u, 0.6F, 0.85F));
				float x = Mth.lerp(down, Mth.lerp(up, -0.4F, -2.95F), -1.35F);
				arm(main, k, x, 0, 0.35F * side * (1 - down));
				arm(off, k, x, 0, -0.35F * side * (1 - down));
				model.head.xRot = Mth.lerp(k * up * (1 - down), model.head.xRot, -0.55F);
			}
			case Acts.SLAM, Acts.PLANT -> {
				wind = ease(in(u, 0, 0.35F));
				strike = ease(in(u, 0.35F, 0.52F));
				float x = Mth.lerp(strike, Mth.lerp(wind, -0.6F, -3.0F), -0.55F);
				arm(main, k, x, -0.15F * side, 0.05F * side);
				arm(off, k, x + 0.1F, 0.35F * side, -0.05F * side);
				if (strike > 0) {
					legs(model, k * bell(in(u, 0.35F, 0.9F)), -0.6F, 0.35F);
				}
			}
			case Acts.SWEEP -> {
				wind = ease(in(u, 0, 0.3F));
				strike = ease(in(u, 0.3F, 0.5F));
				float y = Mth.lerp(strike, Mth.lerp(wind, 0, 0.95F), -1.0F) * side;
				arm(main, k, -1.45F, y, 0.1F * side);
				arm(off, k * 0.6F, -0.3F, 0, -0.5F * side);
			}
			case Acts.THRUST -> {
				wind = ease(in(u, 0, 0.25F));
				strike = ease(in(u, 0.25F, 0.4F));
				float x = Mth.lerp(strike, Mth.lerp(wind, -1.4F, -0.9F), -1.62F);
				arm(main, k, x, -0.08F * side, 0);
				arm(off, k, 0.55F * strike, 0, -0.35F * side);
				legs(model, k * strike, -0.7F, 0.5F);
			}
			case Acts.THROW, Acts.LASH -> {
				float end = a.act() == Acts.THROW ? 0.45F : 0.42F;
				wind = ease(in(u, 0, 0.3F));
				strike = ease(in(u, 0.3F, end));
				float x = Mth.lerp(strike, Mth.lerp(wind, -0.8F, -2.8F), a.act() == Acts.THROW ? -1.0F : -0.7F);
				arm(main, k, x, -0.1F * side, 0.25F * side * (1 - strike));
				arm(off, k, -1.25F, 0.2F * side, -0.1F * side);
			}
			case Acts.CLAP -> {
				wind = ease(in(u, 0, 0.3F));
				strike = ease(in(u, 0.3F, 0.42F));
				float y = Mth.lerp(strike, 0.7F * wind, -0.42F);
				arm(main, k, -1.45F, -y * side, 0);
				arm(off, k, -1.45F, y * side, 0);
			}
			case Acts.SPIN -> {
				arm(main, k, -1.5F, 0, 1.1F * side);
				arm(off, k, -0.3F, 0, -1.2F * side);
			}
			case Acts.BRACE -> {
				arm(main, k, -1.25F, -0.55F * side, 0);
				arm(off, k, -1.35F, 0.55F * side, 0);
				legs(model, k, -0.35F, 0.25F);
			}
			case Acts.LEAP -> {
				float air = bell(in(u, 0.05F, 0.95F));
				arm(main, k, -2.7F, 0, 0.3F * side);
				arm(off, k, -2.5F, 0, -0.4F * side);
				legs(model, k * air, -1.1F, -0.9F);
			}
			case Acts.FLURRY -> {
				float w = Mth.sin(u * Mth.PI * 6);
				arm(main, k, -1.4F + 0.35F * w, 0.8F * w * side, 0.1F * side);
				arm(off, k, -1.4F - 0.35F * w, 0.8F * w * side, -0.1F * side);
			}
			case Acts.POINT -> {
				arm(main, k, -1.6F, -0.12F * side, 0);
			}
			case Acts.RISE -> {
				wind = ease(in(u, 0, 0.28F));
				strike = ease(in(u, 0.28F, 0.45F));
				float x = Mth.lerp(strike, Mth.lerp(wind, -0.8F, 0.15F), -2.8F);
				arm(main, k, x, -0.1F * side, 0.1F * side);
				arm(off, k, x + 0.2F, 0.25F * side, -0.1F * side);
			}
			default -> {
			}
		}
	}

	/** Blends an arm toward a pose by {@code k}. */
	private static void arm(net.minecraft.client.model.geom.ModelPart arm, float k, float x, float y, float z) {
		arm.xRot = Mth.lerp(k, arm.xRot, x);
		arm.yRot = Mth.lerp(k, arm.yRot, y);
		arm.zRot = Mth.lerp(k, arm.zRot, z);
	}

	/** One leg forward, one back (a lunge, a landing), by {@code k}. */
	private static void legs(PlayerModel model, float k, float front, float back) {
		model.rightLeg.xRot = Mth.lerp(k, model.rightLeg.xRot, front);
		model.leftLeg.xRot = Mth.lerp(k, model.leftLeg.xRot, back);
	}

	/** Tips the whole body: facing the wall while climbing, reclined in a slide, level in a glide. */
	public static void rotate(AvatarRenderState state, PoseStack stack, float bodyRot) {
		Acting acting = state.getData(ACT);
		if (acting != null) {
			rotateAct(stack, acting);
			return;
		}
		ClientMoves.Pose pose = state.getData(POSE);
		if (pose == null || pose.kind() == ClientMoves.Pose.IDLE) {
			return;
		}
		switch (pose.kind()) {
			case ClientMoves.Pose.CLIMB -> {
				if (pose.wall() != null) {
					// Square to the wall, whichever way the head is turned.
					stack.rotateDegrees(Axis.YP, bodyRot - pose.wall().toYRot());
				}
			}
			case ClientMoves.Pose.SLIDE -> {
				// Leaning back about the feet, as in a feet-first slide.
				float k = Mth.clamp(pose.progress() * 3.0F, 0, 1);
				stack.rotateDegrees(Axis.XP, -55.0F * k);
			}
			case ClientMoves.Pose.DODGE -> stack.rotateDegrees(Axis.XP, 18.0F * pose.progress());
			case ClientMoves.Pose.GLIDE -> {
				// Tipped forward about the middle of the body.
				stack.translate(0.0F, 0.9F, 0.0F);
				stack.rotateDegrees(Axis.XP, 52.0F);
				stack.translate(0.0F, -0.9F, 0.0F);
			}
			default -> {
			}
		}
	}

	/** The whole body in an act: a lean into a lunge, a twist through a sweep, a full turn, a flip in the air. */
	private static void rotateAct(PoseStack stack, Acting a) {
		float u = a.u();
		float k = envelope(u);
		switch (a.act()) {
			case Acts.SLAM, Acts.PLANT -> {
				float hit = bell(in(u, 0.35F, 0.9F));
				stack.translate(0.0F, -0.18F * hit * k, 0.0F);
				stack.rotateDegrees(Axis.XP, 22.0F * hit * k);
			}
			case Acts.SWEEP -> stack.rotateDegrees(Axis.YP, Mth.lerp(ease(in(u, 0.3F, 0.5F)), 30.0F * ease(in(u, 0, 0.3F)), -40.0F) * k);
			case Acts.THRUST -> stack.rotateDegrees(Axis.XP, 16.0F * ease(in(u, 0.25F, 0.4F)) * k);
			case Acts.LASH, Acts.THROW -> stack.rotateDegrees(Axis.XP, 10.0F * bell(in(u, 0.3F, 0.7F)) * k);
			case Acts.SPIN -> stack.rotateDegrees(Axis.YP, -360.0F * ease(in(u, 0.05F, 0.8F)));
			case Acts.LEAP -> {
				// A front flip about the middle of the body.
				float flip = ease(in(u, 0.12F, 0.78F));
				stack.translate(0.0F, 0.9F, 0.0F);
				stack.rotateDegrees(Axis.XP, 360.0F * flip);
				stack.translate(0.0F, -0.9F, 0.0F);
			}
			case Acts.INVOKE -> {
				float up = bell(in(u, 0, 0.8F));
				stack.translate(0.0F, 0.12F * up, 0.0F);
				stack.rotateDegrees(Axis.XP, -10.0F * up);
			}
			case Acts.RISE -> stack.rotateDegrees(Axis.XP, -12.0F * bell(in(u, 0.28F, 0.8F)) * k);
			case Acts.BRACE -> stack.rotateDegrees(Axis.XP, 8.0F * k);
			default -> {
			}
		}
	}
}
