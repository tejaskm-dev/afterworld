package dev.afterworld.movement;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Relics;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stamina: the wheel by the crosshair. Dodging, sliding, mantling, wall-jumps, climbing,
 * gliding and walking on water all spend it; it comes back quickly once you stop. An Elixir of
 * Stamina raises the wheel by half for three minutes; the Pilgrim's Bead makes everything cheaper.
 */
public final class Stamina {
	public static final float BASE = 100.0F;
	private static final float REGEN = 1.4F;
	private static final int REGEN_DELAY = 16;

	private Stamina() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Stamina::tick);
	}

	public static float max(ServerPlayer p, PlayerState s) {
		float m = BASE;
		if (s.staminaBoostUntil > p.level().getGameTime()) {
			m *= 1.5F;
		}
		if (s.skills.contains("wayfarer.endurance")) {
			m += 25;
		}
		m += (float) dev.afterworld.player.Skills.bonus(s, dev.afterworld.player.Skills.Stat.STAMINA);
		if (dev.afterworld.combat.SetBonuses.has(p, dev.afterworld.registry.AwGear.Set.DUSKPINE_RANGER)) {
			m += 20;
		}
		return m;
	}

	/** Spends stamina if there's enough. Returns whether it was spent. */
	public static boolean spend(ServerPlayer p, float amount) {
		if (p.isCreative()) {
			return true;
		}
		PlayerState s = PlayerState.of(p);
		if (Relics.has(p, "pilgrims_bead")) {
			amount *= 0.8F;
		}
		if (s.stamina < amount && secondWind(p, s)) {
			s.stamina -= Math.min(s.stamina, amount);
			s.staminaSpentAt = p.level().getGameTime();
			sync(p, s);
			return true;
		}
		if (s.stamina < amount) {
			s.staminaEmptyAt = p.level().getGameTime();
			Fx.privateSound(p, AwSounds.EDICT_FAIL, 0.4F, 1.4F);
			sync(p, s);
			return false;
		}
		s.stamina -= amount;
		s.staminaSpentAt = p.level().getGameTime();
		sync(p, s);
		return true;
	}

	/** Drains continuously (climbing, gliding). Returns false (and stops the move) when empty. */
	public static boolean drain(ServerPlayer p, float perTick) {
		if (p.isCreative()) {
			return true;
		}
		PlayerState s = PlayerState.of(p);
		if (Relics.has(p, "pilgrims_bead")) {
			perTick *= 0.8F;
		}
		s.stamina = Math.max(0, s.stamina - perTick);
		s.staminaSpentAt = p.level().getGameTime();
		if (s.stamina <= 0 && secondWind(p, s)) {
			sync(p, s);
		}
		return s.stamina > 0;
	}

	/** Second Wind: running dry, half the traveller's stamina comes back at once (once in thirty seconds). */
	private static boolean secondWind(ServerPlayer p, PlayerState s) {
		long now = p.level().getGameTime();
		if (!s.skills.contains("wayfarer.secondwind") || now - s.secondWindAt < 600) {
			return false;
		}
		s.secondWindAt = now;
		s.stamina = max(p, s) * 0.5F;
		Fx.privateSound(p, AwSounds.UI_AWAKEN, 1.6F, 0.5F);
		return true;
	}

	/** Gives stamina back (perfect dodges and parries pay for themselves). */
	public static void refund(ServerPlayer p, float amount) {
		PlayerState s = PlayerState.of(p);
		if (s.stamina >= 0) {
			s.stamina = Math.min(max(p, s), s.stamina + amount);
		}
	}

	public static void boost(ServerPlayer p, int ticks) {
		PlayerState s = PlayerState.of(p);
		s.staminaBoostUntil = p.level().getGameTime() + ticks;
		s.stamina = max(p, s);
		sync(p, s);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			long now = p.level().getGameTime();
			float m = max(p, s);
			if (s.stamina < 0) {
				s.stamina = m;
			}
			boolean busy = s.gliding || s.climbing || s.waterWalking;
			if (!busy && now - s.staminaSpentAt > REGEN_DELAY && s.stamina < m) {
				float regen = REGEN * (p.hasEffect(dev.afterworld.registry.AwEffects.STARLIT) ? 1.25F : 1.0F);
				s.stamina = Math.min(m, s.stamina + regen);
			}
			if (s.stamina > m) {
				s.stamina = m;
			}
			if (now % 4 == 0 && (Math.abs(s.stamina - s.staminaSent) > 0.5F || m != s.staminaMaxSent)) {
				sync(p, s);
			}
		}
	}

	public static void sync(ServerPlayer p, PlayerState s) {
		s.staminaSent = s.stamina;
		s.staminaMaxSent = max(p, s);
		if (ServerPlayNetworking.canSend(p, AwPayloads.MoveSync.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.MoveSync(s.stamina, s.staminaMaxSent, s.gifts | s.trialGifts, s.slideUntil > p.level().getGameTime()));
		}
	}
}
