package dev.afterworld.registry;

import dev.afterworld.economy.Values;
import dev.afterworld.item.CoinItem;
import dev.afterworld.item.LoreItem;
import dev.afterworld.item.LostPurseItem;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The economy's things: the three coins, the purse a traveller drops when they fall, and the
 * eight trade goods that are cheap where they are made and dear far away.
 */
public final class AwGoods {
	public static final Item COPPER_MARK = coin("copper_mark", 1, Rarity.COMMON);
	public static final Item SILVER_MARK = coin("silver_mark", 10, Rarity.UNCOMMON);
	public static final Item GOLD_CROWN = coin("gold_crown", 100, Rarity.RARE);
	public static final Item LOST_PURSE = AwItems.register(AwTabs.Section.TRADE, "lost_purse", LostPurseItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());

	public static final Item SPICE_CRATE = good("spice_crate", 20);
	public static final Item FROST_SILK = good("frost_silk", 22);
	public static final Item EMBER_GLASS = good("ember_glass", 18);
	public static final Item BOG_AMBER = good("bog_amber", 16);
	public static final Item STARLIGHT_INK = good("starlight_ink", 24);
	public static final Item SKY_AMBERGRIS = good("sky_ambergris", 26);
	public static final Item CLOCKWORK_CURIO = good("clockwork_curio", 28);
	public static final Item MIRROR_SALT = good("mirror_salt", 14);

	public static final List<Item> COINS = List.of(GOLD_CROWN, SILVER_MARK, COPPER_MARK);
	public static final List<Item> TRADE_GOODS = List.of(SPICE_CRATE, FROST_SILK, EMBER_GLASS, BOG_AMBER, STARLIGHT_INK, SKY_AMBERGRIS, CLOCKWORK_CURIO, MIRROR_SALT);

	private AwGoods() {
	}

	private static Item coin(String name, int value, Rarity rarity) {
		Item item = AwItems.register(AwTabs.Section.TRADE, name, p -> new CoinItem(p, value), new Item.Properties().stacksTo(99).rarity(rarity));
		Values.set(item, value);
		return item;
	}

	private static Item good(String name, int value) {
		Item item = AwItems.register(AwTabs.Section.TRADE, name, LoreItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
		Values.set(item, value);
		return item;
	}

	public static void init() {
	}
}
