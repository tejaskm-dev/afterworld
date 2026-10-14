package dev.afterworld.player;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Settlement;
import dev.afterworld.world.wonder.Wonders;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Title cards: arriving at a named place or a wonder, entering a settlement, meeting a boss. Each card shows
 * at most once per cooldown for each player, so passing back and forth stays quiet.
 */
public final class Titles {
	private static final Map<UUID, Map<String, Long>> SHOWN = new HashMap<>();
	private static final long PLACE_COOLDOWN = 20L * 60 * 10;

	private Titles() {
	}

	public static void init() {
		Sites.onPresenceAll(Titles::site);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 23) {
				return;
			}
			AfterworldData data = AfterworldData.get(server);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.isSpectator()) {
					continue;
				}
				String wonder = Wonders.at(p.level(), p.blockPosition());
				if (wonder != null && wonder.endsWith("_snow")) {
					// The snowed-in builds share their summer twin's name.
					wonder = wonder.substring(0, wonder.length() - 5);
				}
				if (wonder != null) {
					show(p, AwPayloads.Title.PLACE, Component.translatable("wonder.afterworld." + wonder),
						Component.translatable("wonder.afterworld." + wonder + ".sub"), Wonders.color(wonder), "wonder:" + wonder, PLACE_COOLDOWN);
					Progress.codex(p, "wonder." + wonder);
				}
				String dim = p.level().dimension().identifier().toString();
				for (Settlement s : data.settlements.values()) {
					if (s.dim.equals(dim) && s.state != Settlement.ABANDONED && sq(s.x - p.getX()) + sq(s.z - p.getZ()) < 44 * 44) {
						Faction f = Faction.byOrdinal(s.patron);
						Component sub = Component.translatable("title.afterworld.settlement", f == null ? Component.literal("?") : f.displayName());
						show(p, AwPayloads.Title.PLACE, Component.literal(s.name), sub, f == null ? 0xE8DCC0 : f.color, "settlement:" + s.id, PLACE_COOLDOWN);
						break;
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.getPlayer().getUUID()));
	}

	private static double sq(double v) {
		return v * v;
	}

	private static void site(ServerPlayer p, PlayerState s, Site site, double distSq) {
		int r = site.kind.radius + 4;
		if (distSq > (double) r * r || !titled(site.kind)) {
			return;
		}
		Faction f = Faction.byOrdinal(site.faction);
		show(p, AwPayloads.Title.PLACE, Component.literal(site.name), Component.translatable("title.afterworld.site." + site.kind.id),
			f == null ? 0xE8DCC0 : f.color, "site:" + site.id, PLACE_COOLDOWN);
	}

	private static boolean titled(SiteKind kind) {
		return kind.grand() || kind == SiteKind.CATHEDRAL || kind == SiteKind.VAULT || kind == SiteKind.UNREMEMBERED
			|| kind == SiteKind.ANOMALY || kind == SiteKind.LAIR || kind == SiteKind.SHRINE;
	}

	/** Sends a title card unless this player saw the same one within {@code cooldown} ticks. */
	public static void show(ServerPlayer p, int style, Component title, Component subtitle, int color, String key, long cooldown) {
		long now = p.level().getGameTime();
		Map<String, Long> seen = SHOWN.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Long last = seen.get(key);
		if (last != null && now - last < cooldown && now >= last) {
			return;
		}
		seen.put(key, now);
		ServerPlayNetworking.send(p, new AwPayloads.Title(style, title, subtitle, color));
	}
}
