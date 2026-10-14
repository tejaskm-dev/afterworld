package dev.afterworld.minigame;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Trials that aren't fights: lockpicking, brewing, bell melodies, races, duels and the rest. Each
 * minigame registers a starter under its id; conversations start them with {@code minigame:<id>}.
 */
public final class Minigames {
	@FunctionalInterface
	public interface Starter {
		void start(ServerPlayer player, Entity host);
	}

	private static final Map<String, Starter> STARTERS = new HashMap<>();

	private Minigames() {
	}

	public static void register(String id, Starter starter) {
		STARTERS.put(id, starter);
	}

	public static void start(ServerPlayer p, String id, Entity host) {
		Starter s = STARTERS.get(id);
		if (s == null) {
			p.sendSystemMessage(Component.translatable("message.afterworld.minigame_missing", id).withColor(0x9A9488));
			return;
		}
		s.start(p, host);
	}
}
