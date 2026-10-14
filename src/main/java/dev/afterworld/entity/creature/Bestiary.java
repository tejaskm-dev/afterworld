package dev.afterworld.entity.creature;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Progress;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Fills the Journal's creature pages as the traveller meets things: a creature gets its entry the
 * first time it is seen clearly, close at hand. Some creatures have their own, harder ways in
 * (feeding a crow, being robbed by a wraith) and are left to those. A Warden opens its own page.
 */
public final class Bestiary {
	private static final Set<String> SPECIAL = Set.of("crow", "rule_wraith", "wisp",
		"duskstrider", "training_dummy", "pilgrim", "bell_ringer", "king_of_seconds");

	private Bestiary() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 11) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.isSpectator()) {
					continue;
				}
				for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.isAlive() && !m.isInvisible())) {
					var key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
					if (!key.getNamespace().equals(Afterworld.MOD_ID) || SPECIAL.contains(key.getPath())) {
						continue;
					}
					String entry = (mob instanceof dev.afterworld.entity.boss.WardenBoss ? "boss." : "creature.") + key.getPath();
					if (!Progress.hasCodex(p, entry) && p.hasLineOfSight(mob)) {
						Progress.codex(p, entry);
					}
				}
			}
		});
	}
}
