package dev.afterworld.movement;

import dev.afterworld.aspect.Aspect;
import dev.afterworld.aspect.Aspects;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.net.AwPayloads.MoveAction;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Relics;
import dev.afterworld.quest.Quests;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The traveller's body: dodge rolls, parries, slides, mantling, wall-jumps and the Gifts
 * (double jump, glide, climb, water-walking). The client moves itself the moment a key is
 * pressed so it feels instant; the server pays the stamina and grants what the move protects.
 */
public final class Moves {
	public static final float DODGE_COST = 22;
	public static final float PARRY_COST = 8;
	public static final float SLIDE_COST = 14;
	public static final float MANTLE_COST = 10;
	public static final float WALL_JUMP_COST = 14;
	public static final float DOUBLE_JUMP_COST = 16;
	public static final float GLIDE_DRAIN = 0.12F;
	public static final float CLIMB_DRAIN = 0.45F;
	/** Climbing with the Climbing Claws drains this much less. */
	public static final float CLAWS_DRAIN = 0.5F;
	public static final float CLIMB_LEAP_COST = 12;
	public static final float WATER_WALK_DRAIN = 0.16F;

	private Moves() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(MoveAction.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Moves::tick);
	}

	/** Frames of a dodge in which nothing can touch you. */
	public static int dodgeFrames(ServerPlayer p) {
		return Relics.has(p, "sentinel_sandglass") ? 14 : 8;
	}

	/** How long the parry window stays open. */
	public static int parryFrames(ServerPlayer p, PlayerState s) {
		int f = 5;
		if (s.discipline == Discipline.VANGUARD) {
			f += 2;
		}
		if (Relics.has(p, "shogun_tsuba")) {
			f += 3;
		}
		if (s.skills.contains("blade.riposte")) {
			f += 3;
		}
		return f;
	}

	private static void handle(ServerPlayer p, MoveAction a) {
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		ServerLevel level = p.level();
		long now = level.getGameTime();
		switch (a.action()) {
			case MoveAction.DODGE -> {
				pose(p, 4, -1);
				float cost = DODGE_COST * (s.skills.contains("wayfarer.ghoststep") ? 0.7F : 1.0F) * (s.discipline == Discipline.WAYFARER ? 0.75F : 1.0F);
				if (now < s.dodgeUntil + 4 || !Stamina.spend(p, cost)) {
					return;
				}
				if (s.skills.contains("wayfarer.ghoststep")) {
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 20, 1, false, false, true));
				}
				s.dodgeUntil = now + dodgeFrames(p);
				s.dodgePerfect = false;
				Fx.burst(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 6, 0.3, 0.02);
				dev.afterworld.util.Vfx.trail(level, p, 0.6F, 0xB8E8FF, 9);
				Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.6F);
				Quests.onDodge(p);
			}
			case MoveAction.PARRY -> {
				if (now < s.parryReadyAt || !Stamina.spend(p, PARRY_COST)) {
					return;
				}
				s.parryUntil = now + parryFrames(p, s);
				s.parryReadyAt = now + 14;
				dev.afterworld.util.Acts.play(p, dev.afterworld.util.Acts.BRACE, parryFrames(p, s) + 4);
				Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 1.8F);
			}
			case MoveAction.SLIDE -> {
				pose(p, 3, -1);
				if (now < s.slideUntil || !Stamina.spend(p, SLIDE_COST)) {
					return;
				}
				s.slideUntil = now + 14;
				Fx.burst(level, ParticleTypes.POOF, p.position(), 6, 0.3, 0.02);
				dev.afterworld.util.Vfx.trail(level, p, 0.4F, 0xE8E0D0, 14);
				Fx.sound(level, p.position(), SoundEvents.GRAVEL_STEP, 1.0F, 0.6F);
			}
			case MoveAction.MANTLE -> {
				pose(p, 2, -1);
				// x = 1: pulling over the top at the end of a climb, which costs nothing more.
				if (a.x() < 0.5F && !Stamina.spend(p, MANTLE_COST)) {
					return;
				}
				s.safeFallUntil = now + 20;
				Fx.sound(level, p.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.3F);
			}
			case MoveAction.WALL_JUMP -> {
				if (!Stamina.spend(p, WALL_JUMP_COST)) {
					return;
				}
				p.fallDistance = 0;
				s.safeFallUntil = now + 10;
				Fx.burst(level, ParticleTypes.POOF, p.position().add(0, 1, 0), 5, 0.2, 0.02);
				Fx.sound(level, p.position(), SoundEvents.STONE_STEP, 1.0F, 1.2F);
			}
			case MoveAction.DOUBLE_JUMP -> {
				if (!Gifts.has(s, Gifts.Gift.DOUBLE_JUMP) || !Stamina.spend(p, DOUBLE_JUMP_COST)) {
					return;
				}
				p.fallDistance = 0;
				Fx.ring(level, ParticleTypes.CLOUD, p.position(), 0.8, 8);
				dev.afterworld.util.Vfx.ring(level, p.position(), 1.6F, 0.35F, 0, 0xE8F4FF, 8);
				Fx.sound(level, p.position(), SoundEvents.BREEZE_JUMP, 0.8F, 1.3F);
			}
			case MoveAction.GLIDE -> {
				pose(p, a.x() > 0.5F ? 5 : 0, -1);
				boolean on = a.x() > 0.5F && Gifts.has(s, Gifts.Gift.GLIDE) && !p.onGround();
				if (on && !s.gliding) {
					Fx.sound(level, p.position(), SoundEvents.PHANTOM_FLAP, 0.6F, 1.5F);
				}
				s.gliding = on;
			}
			// Anyone can climb now; the Climbing Claws only make it easier.
			case MoveAction.CLIMB -> {
				s.climbing = a.x() > 0.5F;
				pose(p, s.climbing ? 1 : 0, (int) a.z());
			}
			case MoveAction.CLIMB_LEAP -> Stamina.spend(p, CLIMB_LEAP_COST);
			case MoveAction.WATER_WALK -> s.waterWalking = a.x() > 0.5F && Gifts.has(s, Gifts.Gift.WATER_WALK);
			case MoveAction.ULTIMATE -> Aspects.cast(p);
			case MoveAction.NEXT_ASPECT -> nextAspect(p, s);
			default -> {
			}
		}
		Stamina.sync(p, s);
	}

	private static void nextAspect(ServerPlayer p, PlayerState s) {
		if (s.aspects.isEmpty()) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.no_aspect").withColor(0x9A9488));
			return;
		}
		Aspect cur = Aspects.equipped(s);
		int start = cur == null ? -1 : cur.ordinal();
		for (int i = 1; i <= Aspect.VALUES.length; i++) {
			Aspect a = Aspect.VALUES[(start + i) % Aspect.VALUES.length];
			if (s.aspects.contains(a.id)) {
				if (a != cur) {
					// Changing Aspects scatters half the Resonance you'd built.
					s.resonance *= 0.5F;
				}
				Aspects.equip(p, a);
				p.sendOverlayMessage(Component.translatable("message.afterworld.aspect_equipped", Component.translatable("aspect.afterworld." + a.id)).withColor(a.color));
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			boolean changed = false;
			if (s.gliding) {
				if (p.onGround() || p.isInWater() || !Stamina.drain(p, GLIDE_DRAIN)) {
					s.gliding = false;
					changed = true;
				} else if (p.tickCount % 3 == 0) {
					Vec3 back = p.position().add(p.getLookAngle().scale(-0.6)).add(0, 0.8, 0);
					p.level().sendParticles(ParticleTypes.END_ROD, back.x, back.y, back.z, 1, 0.1, 0.05, 0.1, 0.0);
				}
			}
			if (s.climbing && !Stamina.drain(p, CLIMB_DRAIN * (Gifts.has(s, Gifts.Gift.CLIMB) ? CLAWS_DRAIN : 1.0F))) {
				s.climbing = false;
				changed = true;
			}
			if (s.waterWalking && !Stamina.drain(p, WATER_WALK_DRAIN)) {
				s.waterWalking = false;
				changed = true;
			}
			if (changed) {
				Stamina.sync(p, s);
			}
			// The Amulet of Quiet Steps: crouched, you slip out of the notice of anything not already close.
			if (p.tickCount % 10 == 0 && p.isShiftKeyDown() && Relics.has(p, "amulet_of_quiet_steps")) {
				for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p && m.distanceToSqr(p) > 64)) {
					m.setTarget(null);
				}
			}
		}
	}

	/** Whether this player may stand on water right now (the Mirewalk Gift, sprinting). Called on both sides. */
	public static boolean canWaterWalk(net.minecraft.world.entity.player.Player p) {
		if (p.isShiftKeyDown() || !p.isSprinting()) {
			return false;
		}
		if (p instanceof ServerPlayer sp) {
			PlayerState s = PlayerState.of(sp);
			return Gifts.has(s, Gifts.Gift.WATER_WALK) && s.stamina > 0;
		}
		return CLIENT_WATER_WALK.test(p);
	}

	/** The client's view of the same question (set by the client; servers never see a client player). */
	public static java.util.function.Predicate<net.minecraft.world.entity.player.Player> CLIENT_WATER_WALK = p -> false;

	/** Sends a fresh copy of every movement and power sync, e.g. on join. */
	public static void syncAll(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		Stamina.sync(p, s);
		Aspects.sync(p, s);
	}

	/** Shows a player's move to everyone else who can see them (kinds as in the client's ClientMoves.Pose). */
	private static void pose(ServerPlayer p, int kind, int wall) {
		AwPayloads.PoseSync sync = new AwPayloads.PoseSync(p.getId(), kind, wall);
		for (ServerPlayer other : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(p)) {
			if (other != p && ServerPlayNetworking.canSend(other, AwPayloads.PoseSync.TYPE)) {
				ServerPlayNetworking.send(other, sync);
			}
		}
	}
}
