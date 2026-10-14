package dev.afterworld.movement;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Gifts: traversal abilities, each learned once from a keeper's questline and kept for good. */
public final class Gifts {
	public enum Gift {
		GLIDE("glide"),
		DOUBLE_JUMP("double_jump"),
		CLIMB("climb"),
		WATER_WALK("water_walk");

		public final String id;

		Gift(String id) {
			this.id = id;
		}

		public int bit() {
			return 1 << ordinal();
		}
	}

	private Gifts() {
	}

	public static boolean has(PlayerState s, Gift g) {
		return ((s.gifts | s.trialGifts) & g.bit()) != 0;
	}

	public static boolean learn(ServerPlayer p, Gift g) {
		PlayerState s = PlayerState.of(p);
		if (has(s, g)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.gift_known").withColor(0x9A9488));
			return false;
		}
		s.gifts |= g.bit();
		Fx.burst(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 50, 0.6, 0.15);
		Fx.privateSound(p, AwSounds.UI_DISCOVERY, 1.0F, 1.1F);
		if (ServerPlayNetworking.canSend(p, AwPayloads.Title.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.CHAPTER, Component.translatable("gift.afterworld.learned"),
				Component.translatable("gift.afterworld." + g.id), 0x7FE3D2));
		}
		p.sendSystemMessage(Component.translatable("gift.afterworld." + g.id + ".how").withColor(0xE8DCC0));
		Progress.legend(p, "milestone", "Learned " + Component.translatable("gift.afterworld." + g.id).getString() + ".");
		Stamina.sync(p, s);
		return true;
	}
}
