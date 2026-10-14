package dev.afterworld.util;

import dev.afterworld.net.AwPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * The body's part in a power: what a player's arms, legs and whole body do while an Art, an
 * Edict, an Aspect or a technique plays out. Sent to the player (who sees it in the weapon in
 * their hand) and to everyone who can see them (who see the whole body act it). See
 * client.render.PlayerPoses and client.render.FirstPersonActs.
 */
public final class Acts {
	public static final int NONE = 0;
	/** An Edict: the hand thrown forward, palm out. */
	public static final int CAST = 1;
	/** An Aspect: both arms raised to the sky, then brought down. */
	public static final int INVOKE = 2;
	/** Raised overhead and brought down into the ground. */
	public static final int SLAM = 3;
	/** A wide cut across the body. */
	public static final int SWEEP = 4;
	/** A lunge, the weapon driven forward. */
	public static final int THRUST = 5;
	/** Wound back over the shoulder and let go. */
	public static final int THROW = 6;
	/** Both hands brought together in front. */
	public static final int CLAP = 7;
	/** A full turn with the weapon held out. */
	public static final int SPIN = 8;
	/** Lifted high and driven into the ground, like a banner. */
	public static final int PLANT = 9;
	/** Braced behind the weapon, held. */
	public static final int BRACE = 10;
	/** Wound back and snapped forward, like a whip. */
	public static final int LASH = 11;
	/** A leap: a forward flip in the air, the weapon raised. */
	public static final int LEAP = 12;
	/** Quick cuts, one way then the other. */
	public static final int FLURRY = 13;
	/** The arm held out, pointing. */
	public static final int POINT = 14;
	/** A rising cut, from low to high. */
	public static final int RISE = 15;

	private Acts() {
	}

	/** {@code p} acts out {@code act} over {@code ticks}, for themself and everyone watching. */
	public static void play(ServerPlayer p, int act, int ticks) {
		AwPayloads.Act a = new AwPayloads.Act(p.getId(), act, ticks);
		if (ServerPlayNetworking.canSend(p, AwPayloads.Act.TYPE)) {
			ServerPlayNetworking.send(p, a);
		}
		for (ServerPlayer o : PlayerLookup.tracking(p)) {
			if (o != p && ServerPlayNetworking.canSend(o, AwPayloads.Act.TYPE)) {
				ServerPlayNetworking.send(o, a);
			}
		}
	}
}
