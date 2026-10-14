package dev.afterworld.movement;

import dev.afterworld.combat.Arts;
import dev.afterworld.entity.mount.Mounts;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Relics;
import dev.afterworld.quest.Quests;
import dev.afterworld.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gadgets: tools used again and again for getting around. The hook pulls you to any wall; the
 * skyward bell throws you into the sky; the whistle calls your Duskstrider; the sextant finds
 * the nearest landmark; the prototype wing lets anyone glide, badly.
 */
public final class Gadgets {
	public enum Gadget {
		GRAPPLE("grapple"),
		SKYWARD("skyward"),
		WHISTLE("whistle"),
		SEXTANT("sextant"),
		WING("wing");

		public final String id;

		Gadget(String id) {
			this.id = id;
		}
	}

	private Gadgets() {
	}

	/** Uses a gadget. Returns its cooldown in ticks, or -1 if it did nothing (and costs nothing). */
	public static int use(ServerLevel level, ServerPlayer p, Gadget g, ItemStack stack) {
		return switch (g) {
			case GRAPPLE -> grapple(level, p);
			case SKYWARD -> skyward(level, p);
			case WHISTLE -> Mounts.whistle(level, p) ? 60 : -1;
			case SEXTANT -> sextant(level, p);
			case WING -> wing(level, p);
		};
	}

	/** The Wayfarer's Hook: fire it at any surface within 32 blocks and be hauled there. */
	private static int grapple(ServerLevel level, ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(32));
		var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.MISS) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.hook_miss").withColor(0x9A9488));
			return -1;
		}
		Vec3 to = hit.getLocation();
		Fx.sound(level, p.position(), SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.4F);
		Fx.sound(level, to, SoundEvents.CHAIN_PLACE, 1.0F, 1.0F);
		Fx.burst(level, ParticleTypes.CRIT, to, 8, 0.1, 0.1);
		Arts.yank(level, p, to, 24);
		return Relics.has(p, "gearsprung_anklet") ? 20 : 30;
	}

	/** The Skyward Bell: one ring and the air throws you up; you drift down slowly afterwards. */
	private static int skyward(ServerLevel level, ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		s.safeFallUntil = level.getGameTime() + 200;
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, 1.6, 0));
		p.syncVelocity = true;
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
		Fx.sound(level, p.position(), SoundEvents.BELL_BLOCK, 1.5F, 1.4F);
		Fx.sound(level, p.position(), SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.8F);
		Fx.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 1.5, 20);
		Fx.ring(level, Fx.dust(0xE8DCC0, 1.2F), p.position().add(0, 0.4, 0), 2.5, 28);
		return 400;
	}

	/** The Survey Sextant: sight the nearest landmark and mark it on your map. */
	private static int sextant(ServerLevel level, ServerPlayer p) {
		Fx.sound(level, p.position(), SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
		// Beside a waystone, the sextant takes a sighting (Wren's survey) instead of pointing the way.
		BlockPos here = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(here.offset(-8, -4, -8), here.offset(8, 4, 8))) {
			if (level.getBlockState(q).is(dev.afterworld.registry.AwBlocks.WAYSTONE)) {
				if (PlayerState.of(p).flags.contains("sighting:" + q.getX() + "," + q.getZ())) {
					p.sendOverlayMessage(Component.translatable("trial.afterworld.sighting.done").withColor(0x9A9488));
					return 20;
				}
				dev.afterworld.minigame.Trials.sighting(p, q.immutable());
				return 40;
			}
		}
		Quests.mark(p, "landmark", null);
		return Relics.has(p, "lucky_sextant") ? 600 : 1200;
	}

	/** The Prototype Wing: a few seconds of clumsy flight. */
	private static int wing(ServerLevel level, ServerPlayer p) {
		if (p.onGround()) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.wing_ground").withColor(0x9A9488));
			return -1;
		}
		PlayerState.of(p).safeFallUntil = level.getGameTime() + 160;
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		p.setDeltaMovement(look.x * 1.1, 0.35, look.z * 1.1);
		p.syncVelocity = true;
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
		Fx.sound(level, p.position(), SoundEvents.PHANTOM_FLAP, 1.0F, 0.8F);
		Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 1, 0), 10, 0.5, 0.05);
		return 600;
	}
}
