package dev.afterworld.player;

import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Rewards that reach players even if they were offline when they earned them. */
public final class Rewards {
	private Rewards() {
	}

	public static void init() {
		ServerPlayerEvents.JOIN.register(p -> {
			AfterworldData data = AfterworldData.get(p.level().getServer());
			List<String> owed = data.pending.remove(p.getUUID());
			if (owed != null) {
				data.setDirty();
				for (String r : owed) {
					apply(p, r);
				}
			}
		});
	}

	public static String item(Item item, int count) {
		return "item:" + BuiltInRegistries.ITEM.getKey(item) + "*" + count;
	}

	public static void give(MinecraftServer server, UUID player, String reward) {
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p != null) {
			apply(p, reward);
		} else {
			AfterworldData.get(server).owe(player, reward);
		}
	}

	private static void apply(ServerPlayer p, String reward) {
		if (reward.startsWith("item:")) {
			String body = reward.substring(5);
			int star = body.lastIndexOf('*');
			String id = star > 0 ? body.substring(0, star) : body;
			int count = star > 0 ? Integer.parseInt(body.substring(star + 1)) : 1;
			Identifier key = Identifier.tryParse(id);
			if (key != null) {
				BuiltInRegistries.ITEM.getOptional(key).ifPresent(item -> Inv.give(p, new ItemStack(item, count)));
			}
		} else if (reward.startsWith("bond:")) {
			BondType type = null;
			for (BondType t : BondType.VALUES) {
				if (t.id.equals(reward.substring(5))) {
					type = t;
				}
			}
			if (type != null) {
				Bonds.offer(p, PlayerState.of(p), type);
			}
		}
	}
}
