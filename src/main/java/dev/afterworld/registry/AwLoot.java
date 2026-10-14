package dev.afterworld.registry;

import dev.afterworld.edict.Edict;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Seeds Afterworld's finds into vanilla loot: Edict fragments in old places, torn maps where
 * maps are kept, residue where strange things happened. Added at drop time, so it costs nothing
 * until a chest is opened.
 */
public final class AwLoot {
	private record Roll(Item item, float chance, int min, int max) {
	}

	private static final Map<String, List<Roll>> RULES = new HashMap<>();
	/** Marker item meaning "a random Edict fragment". */
	private static final Item FRAGMENT = null;

	private AwLoot() {
	}

	private static void rule(String table, Roll... rolls) {
		RULES.put("minecraft:" + table, List.of(rolls));
	}

	private static Roll fragment(float chance) {
		return new Roll(FRAGMENT, chance, 1, 1);
	}

	public static void init() {
		rule("chests/simple_dungeon", fragment(0.18F), new Roll(AwItems.TORN_MAP, 0.3F, 1, 1), new Roll(AwItems.ANOMALY_RESIDUE, 0.25F, 1, 3));
		rule("chests/abandoned_mineshaft", fragment(0.1F), new Roll(AwItems.TORN_MAP, 0.25F, 1, 1), new Roll(AwItems.CLOCKWORK_FRAGMENT, 0.2F, 1, 2));
		rule("chests/stronghold_library", fragment(0.3F), new Roll(AwItems.CHRONICLE_PAGE, 0.5F, 2, 4), new Roll(AwItems.TORN_MAP, 0.3F, 1, 2));
		rule("chests/stronghold_corridor", fragment(0.12F), new Roll(AwItems.ANOMALY_RESIDUE, 0.2F, 1, 3));
		rule("chests/desert_pyramid", fragment(0.15F), new Roll(AwItems.TORN_MAP, 0.25F, 1, 1));
		rule("chests/jungle_temple", fragment(0.18F), new Roll(AwItems.TORN_MAP, 0.2F, 1, 1));
		rule("chests/woodland_mansion", fragment(0.25F), new Roll(AwItems.GRAVE_WAX, 0.3F, 1, 3));
		rule("chests/ancient_city", fragment(0.3F), new Roll(AwItems.CLOCKWORK_FRAGMENT, 0.3F, 1, 3), new Roll(AwItems.ANOMALY_RESIDUE, 0.4F, 2, 4));
		rule("chests/pillager_outpost", new Roll(AwItems.TORN_MAP, 0.3F, 1, 1));
		rule("chests/shipwreck_map", new Roll(AwItems.TORN_MAP, 0.5F, 1, 1));
		rule("chests/buried_treasure", fragment(0.25F));
		rule("chests/ruined_portal", new Roll(AwItems.ANOMALY_RESIDUE, 0.3F, 1, 3));
		rule("chests/trial_chambers/reward", fragment(0.15F), new Roll(AwItems.CLOCKWORK_FRAGMENT, 0.3F, 1, 2));
		rule("chests/igloo_chest", fragment(0.2F));
		rule("chests/village/village_cartographer", new Roll(AwItems.TORN_MAP, 0.4F, 1, 1));
		rule("chests/end_city_treasure", fragment(0.25F));
		rule("chests/bastion_treasure", fragment(0.2F));
		rule("entities/bat", new Roll(AwItems.MOTH_DUST, 0.6F, 1, 2));
		rule("entities/phantom", new Roll(AwItems.MOTH_DUST, 0.3F, 1, 2));
		rule("entities/witch", new Roll(AwItems.ANOMALY_RESIDUE, 0.15F, 1, 1));
		rule("entities/zombie_villager", new Roll(AwItems.GRAVE_WAX, 0.1F, 1, 1));

		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
			var key = holder.unwrapKey();
			if (key.isEmpty()) {
				return;
			}
			List<Roll> rolls = RULES.get(key.get().identifier().toString());
			if (rolls == null) {
				return;
			}
			RandomSource r = context.getRandom();
			for (Roll roll : rolls) {
				if (r.nextFloat() >= roll.chance) {
					continue;
				}
				int count = roll.min + (roll.max > roll.min ? r.nextInt(roll.max - roll.min + 1) : 0);
				Item item = roll.item;
				if (item == null) {
					Edict e;
					do {
						e = Edict.VALUES[r.nextInt(Edict.COUNT)];
					} while (e == Edict.BORROWED_TIME || e == Edict.TRESPASS);
					item = AwItems.fragment(e);
				}
				drops.add(new ItemStack(item, count));
			}
		});
	}
}
