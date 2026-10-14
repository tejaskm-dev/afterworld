package dev.afterworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.entity.wild.WildCreature;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The life in Afterworld's creatures beyond their own limbs' animation, given to every one of them
 * the same way, by tipping, lifting and squashing the whole body about its feet: a bob at each
 * footfall and a lean into the run; a crouch to wind up a blow and a lunge that stretches into it;
 * a flinch away when hurt; a lean into turns; breathing; and now and then, standing still, a
 * fidget (a shake, a stretch, a little hop, a sniff at the ground).
 */
public final class Liveliness {
	public static final RenderStateDataKey<Life> LIFE = RenderStateDataKey.create(() -> "afterworld:life");

	/** What the body is doing this frame. */
	public record Life(float hurt, float strike, float windup, float roll, int seed, boolean flying, boolean ground) {
	}

	/** Each creature's last body turn, to lean into turns: id -> {bodyRot, ageInTicks, roll}. */
	private static final Map<Integer, float[]> TURNS = new HashMap<>();

	private Liveliness() {
	}

	/** Reads the creature (render-state extraction, on the render thread). */
	public static void extract(LivingEntity e, LivingEntityRenderState state, float partial) {
		if (!wanted(e)) {
			state.setData(LIFE, null);
			return;
		}
		float hurt = e.hurtTime > 0 && e.hurtDuration > 0 ? Mth.clamp((e.hurtTime - partial) / e.hurtDuration, 0, 1) : 0;
		float strike = e.getSwingAnimation(partial);
		float windup = 0;
		if (e instanceof WildCreature w) {
			int action = w.action();
			float at = w.actionTime(partial);
			if (action == 2) {
				windup = Math.min(1, at / 6.0F);
			} else if (action == 1 || action == 3) {
				strike = Math.max(strike, Math.max(0, 1 - at / 8.0F));
			}
		}
		if (TURNS.size() > 1024) {
			TURNS.clear();
		}
		float[] turn = TURNS.computeIfAbsent(e.getId(), k -> new float[] {state.bodyRot, state.ageInTicks, 0});
		float dt = state.ageInTicks - turn[1];
		if (dt > 0.001F && dt < 20) {
			float rate = Mth.wrapDegrees(state.bodyRot - turn[0]) / dt;
			turn[2] = Mth.lerp(Math.min(1, dt * 0.25F), turn[2], Mth.clamp(rate * 0.012F, -0.22F, 0.22F));
		}
		turn[0] = state.bodyRot;
		turn[1] = state.ageInTicks;
		state.setData(LIFE, new Life(hurt, strike, windup, turn[2], e.getId(), !e.onGround() && !e.isInWater(), e.onGround()));
	}

	private static boolean wanted(LivingEntity e) {
		if (e instanceof BossMob || e.deathTime > 0) {
			return false;
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("afterworld");
	}

	/** Tips, lifts and squashes the body (after the renderer has turned it to face its way). */
	public static void apply(LivingEntityRenderState state, PoseStack stack) {
		Life l = state.getData(LIFE);
		if (l == null || state.deathTime > 0) {
			return;
		}
		float t = state.ageInTicks;
		float spd = Math.min(1.0F, state.walkAnimationSpeed * 1.5F);
		float pos = state.walkAnimationPos;
		float size = Mth.clamp(state.boundingBoxHeight, 0.4F, 3.0F);
		float bob = 0;
		float lean = 0;
		float roll = l.roll();
		float sy = 1;
		float sxz = 1;
		// Steps: up at each stride, a little squash between, leaning into the run.
		if (l.ground()) {
			float step = Math.abs(Mth.sin(pos * 0.6662F));
			bob += step * 0.045F * spd * size;
			lean += 0.10F * spd;
			sy *= 1 - 0.035F * spd * (1 - step);
		} else if (l.flying()) {
			bob += Mth.sin(t * 0.18F + l.seed()) * 0.05F * size;
			lean += 0.08F * spd;
		}
		// Breathing.
		float breath = Mth.sin(t * 0.09F + l.seed() * 0.7F);
		sy *= 1 + 0.012F * breath;
		sxz *= 1 - 0.006F * breath;
		// A blow: crouch and lean back to wind up, then lunge and stretch into it.
		if (l.windup() > 0) {
			sy *= 1 - 0.09F * l.windup();
			sxz *= 1 + 0.05F * l.windup();
			lean -= 0.18F * l.windup();
		}
		if (l.strike() > 0) {
			float s = Mth.sin(l.strike() * Mth.PI);
			sy *= 1 + 0.07F * s;
			sxz *= 1 - 0.03F * s;
			lean += 0.30F * s;
		}
		// Hurt: a flinch, back and to one side, the body squeezed.
		if (l.hurt() > 0) {
			float k = Mth.sin(l.hurt() * Mth.PI);
			lean -= 0.22F * k;
			roll += ((l.seed() & 1) == 0 ? 1 : -1) * 0.15F * k;
			sy *= 1 - 0.08F * k;
			sxz *= 1 + 0.05F * k;
		}
		// Standing still, now and then, a fidget of its own.
		if (spd < 0.05F && l.ground() && l.hurt() == 0 && l.strike() == 0 && l.windup() == 0) {
			float period = 160 + Math.floorMod(l.seed(), 7) * 23;
			float ph = (t + Math.floorMod(l.seed(), 997) * 37) % period;
			if (ph < 24) {
				float env = Mth.sin(ph / 24.0F * Mth.PI);
				switch (Math.floorMod(l.seed(), 4)) {
					case 0 -> roll += Mth.sin(t * 2.4F) * 0.12F * env;
					case 1 -> {
						sy *= 1 + 0.07F * env;
						sxz *= 1 - 0.03F * env;
					}
					case 2 -> {
						bob += 0.16F * size * env * env;
						sy *= 1 - 0.05F * (1 - env);
					}
					default -> lean += 0.20F * env;
				}
			}
		}
		if (bob != 0) {
			stack.translate(0.0F, bob, 0.0F);
		}
		if (lean != 0) {
			stack.rotateDegrees(Axis.XP, -lean * Mth.RAD_TO_DEG);
		}
		if (roll != 0) {
			stack.rotateDegrees(Axis.ZP, roll * Mth.RAD_TO_DEG);
		}
		if (sy != 1 || sxz != 1) {
			stack.scale(sxz, sy, sxz);
		}
	}
}
