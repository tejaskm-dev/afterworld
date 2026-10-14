package dev.afterworld.economy;

import dev.afterworld.economy.Merchant.Category;
import dev.afterworld.economy.Merchant.Temperament;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.AwGear;
import dev.afterworld.registry.AwGoods;
import dev.afterworld.registry.AwItems;
import dev.afterworld.world.Faction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Every merchant in the Afterworld. The camp's keepers stock what a traveller needs; the landmark
 * keepers each make one trade good (cheap there) and pay well for the goods of far places, which
 * is what makes a trade route worth walking. Items added later (meals, scrolls, gear) register
 * themselves into these catalogs from {@link #extend}.
 */
public final class Merchants {
	public static final Map<String, Merchant> ALL = new LinkedHashMap<>();

	private Merchants() {
	}

	public static Merchant get(String id) {
		return ALL.get(id);
	}

	private static Merchant add(Merchant m) {
		ALL.put(m.id, m);
		return m;
	}

	private static Supplier<ItemStack> st(Item item, int count) {
		return () -> new ItemStack(item, count);
	}

	private static Supplier<ItemStack> st(Item item) {
		return st(item, 1);
	}

	private static Supplier<ItemStack> potion(Holder<Potion> p) {
		return () -> {
			ItemStack s = new ItemStack(Items.POTION);
			s.set(DataComponents.POTION_CONTENTS, new PotionContents(p));
			return s;
		};
	}

	public static void init() {
		add(new Merchant("bram", null, 1.2, 0.5, Temperament.SHREWD))
			.sell(st(Items.BREAD, 6), -1, 0, Category.SUPPLIES)
			.sell(st(Items.COOKED_BEEF, 4), -1, 0, Category.SUPPLIES)
			.sell(st(Items.BAKED_POTATO, 8), -1, 0, Category.SUPPLIES)
			.sell(st(Items.TORCH, 16), -1, 0, Category.SUPPLIES)
			.sell(st(Items.LANTERN, 2), 12, 6, Category.SUPPLIES)
			.sell(st(Items.ARROW, 16), -1, 0, Category.SUPPLIES)
			.sell(st(Items.BED.pick(DyeColor.WHITE)), 6, 2, Category.SUPPLIES)
			.sell(st(Items.CAMPFIRE), 8, 4, Category.SUPPLIES)
			.sell(st(Items.SHIELD), 6, 3, Category.GEAR)
			.sell(st(Items.IRON_PICKAXE), 4, 2, Category.GEAR)
			.sell(st(Items.IRON_SHOVEL), 4, 2, Category.GEAR)
			.sell(st(Items.IRON_AXE), 4, 2, Category.GEAR)
			.sell(st(Items.SADDLE), 2, 1, Category.GEAR)
			.sell(st(Items.SPYGLASS), 2, 1, Category.GEAR);
		add(new Merchant("marta", null, 1.25, 0.5, Temperament.SHREWD))
			.sell(st(Items.IRON_SWORD), 4, 2, Category.ARMS)
			.sell(st(Items.IRON_HELMET), 3, 1, Category.GEAR)
			.sell(st(Items.IRON_CHESTPLATE), 3, 1, Category.GEAR)
			.sell(st(Items.IRON_LEGGINGS), 3, 1, Category.GEAR)
			.sell(st(Items.IRON_BOOTS), 3, 1, Category.GEAR)
			.sell(st(Items.CROSSBOW), 2, 1, Category.ARMS)
			.sell(st(Items.BOW), 3, 1, Category.ARMS)
			.sell(st(AwItems.EMBERBRAND), 1, 0, Category.ARMS, 4)
			.sell(st(AwItems.SHARDSONG_GLAIVE), 1, 0, Category.ARMS, 4)
			.sell(st(AwItems.LURKERS_HOOK), 1, 0, Category.ARMS, 3)
			.sell(st(AwItems.GEARSPRING_MAUL), 1, 0, Category.ARMS, 5)
			.sell(st(AwItems.RIMEFANG_SPEAR), 1, 0, Category.ARMS, 5)
			.sell(st(AwItems.ECHO_EDGE), 1, 0, Category.ARMS, 6)
			.produce(AwGoods.EMBER_GLASS)
			.sell(st(AwGoods.EMBER_GLASS, 2), 8, 4, Category.GOODS)
			.want(AwGoods.CLOCKWORK_CURIO, 1.7)
			.want(AwGoods.BOG_AMBER, 1.4)
			.want(Items.IRON_INGOT, 1.2)
			.want(AwItems.EMBER_CORE, 1.3);
		add(new Merchant("wren", Faction.GUILD, 1.15, 0.55, Temperament.VAIN))
			.sell(st(Items.MAP), -1, 0, Category.SUPPLIES)
			.sell(st(Items.COMPASS), 4, 2, Category.GEAR)
			.sell(st(Items.SPYGLASS), 3, 1, Category.GEAR)
			.sell(st(AwBlocks.WAYSTONE.asItem()), 2, 1, Category.CURIOS, 2)
			.sell(st(Items.ENDER_PEARL, 2), 6, 3, Category.CURIOS)
			.produce(AwGoods.CLOCKWORK_CURIO)
			.sell(st(AwGoods.CLOCKWORK_CURIO), 6, 3, Category.GOODS)
			.want(AwGoods.MIRROR_SALT, 1.6)
			.want(AwGoods.FROST_SILK, 1.5)
			.want(Items.PAPER, 1.5)
			.want(AwItems.TORN_MAP, 2.0);
		add(new Merchant("selene", Faction.BUREAU, 1.2, 0.55, Temperament.VAIN))
			.sell(st(Items.GLOWSTONE_DUST, 8), -1, 0, Category.MATERIALS)
			.sell(st(Items.AMETHYST_SHARD, 6), 12, 6, Category.MATERIALS)
			.sell(potion(Potions.LONG_NIGHT_VISION), 6, 3, Category.POTIONS)
			.sell(potion(Potions.LONG_SLOW_FALLING), 6, 3, Category.POTIONS)
			.sell(st(AwItems.LIVING_CRYSTAL, 2), 4, 2, Category.MATERIALS)
			.produce(AwGoods.STARLIGHT_INK)
			.sell(st(AwGoods.STARLIGHT_INK), 6, 3, Category.GOODS)
			.want(AwGoods.EMBER_GLASS, 1.6)
			.want(AwGoods.SKY_AMBERGRIS, 1.7)
			.want(AwItems.ANOMALY_RESIDUE, 1.6)
			.want(AwItems.MOTH_DUST, 1.4);
		add(new Merchant("odo", Faction.GUILD, 1.2, 0.5, Temperament.VAIN))
			.sell(st(Items.FIREWORK_ROCKET, 8), -1, 0, Category.SUPPLIES)
			.sell(potion(Potions.LONG_SLOW_FALLING), 6, 3, Category.POTIONS)
			.sell(st(Items.FEATHER, 12), -1, 0, Category.MATERIALS)
			.sell(st(Items.PHANTOM_MEMBRANE, 2), 4, 2, Category.MATERIALS)
			.sell(st(Items.CLOCK), 3, 1, Category.GEAR)
			.produce(AwGoods.SKY_AMBERGRIS)
			.sell(st(AwGoods.SKY_AMBERGRIS), 5, 3, Category.GOODS)
			.want(AwGoods.SPICE_CRATE, 1.6)
			.want(AwGoods.BOG_AMBER, 1.5)
			.want(AwItems.GEAR_SPRING, 1.5)
			.want(AwItems.CLOCKWORK_FRAGMENT, 1.5);
		// Rook, on every landing of the Warden's Ladder: what a climber runs short of, at a climber's prices.
		add(new Merchant("rook", null, 1.35, 0.4, Temperament.SHREWD))
			.sell(potion(Potions.HEALING), -1, 0, Category.POTIONS)
			.sell(potion(Potions.STRONG_HEALING), 8, 4, Category.POTIONS)
			.sell(potion(Potions.REGENERATION), 6, 3, Category.POTIONS)
			.sell(potion(Potions.STRENGTH), 6, 3, Category.POTIONS)
			.sell(potion(Potions.SWIFTNESS), 6, 3, Category.POTIONS)
			.sell(potion(Potions.LONG_FIRE_RESISTANCE), 4, 2, Category.POTIONS)
			.sell(st(AwGear.ELIXIR_OF_STAMINA, 2), 8, 4, Category.SUPPLIES)
			.sell(st(AwGear.PHIAL_OF_SECOND_WIND), 6, 3, Category.SUPPLIES)
			.sell(st(AwGear.SMOKE_BOMB, 3), 8, 4, Category.SUPPLIES)
			.sell(st(AwGear.FROST_BOMB, 2), 6, 3, Category.SUPPLIES)
			.sell(st(AwGear.THUNDER_BOMB, 2), 6, 3, Category.SUPPLIES)
			.sell(st(Items.GOLDEN_APPLE), 4, 2, Category.SUPPLIES)
			.sell(st(Items.COOKED_BEEF, 6), -1, 0, Category.SUPPLIES)
			.sell(st(Items.ARROW, 24), -1, 0, Category.SUPPLIES)
			.sell(st(Items.SHIELD), 3, 1, Category.GEAR);
		add(new Merchant("mab", null, 1.3, 0.45, Temperament.JEALOUS))
			.sell(potion(Potions.HEALING), 8, 4, Category.POTIONS)
			.sell(potion(Potions.REGENERATION), 6, 3, Category.POTIONS)
			.sell(potion(Potions.LONG_WATER_BREATHING), 6, 3, Category.POTIONS)
			.sell(potion(Potions.LONG_FIRE_RESISTANCE), 6, 3, Category.POTIONS)
			.sell(st(Items.SLIME_BALL, 4), 10, 5, Category.MATERIALS)
			.sell(st(Items.LILY_PAD, 6), -1, 0, Category.SUPPLIES)
			.produce(AwGoods.BOG_AMBER)
			.sell(st(AwGoods.BOG_AMBER), 8, 4, Category.GOODS)
			.want(AwGoods.MIRROR_SALT, 1.8)
			.want(AwGoods.STARLIGHT_INK, 1.5)
			.want(AwItems.LURKER_FANG, 1.4)
			.want(AwItems.GRAVE_WAX, 1.4);
		add(new Merchant("torvald", Faction.CROWN, 1.15, 0.5, Temperament.SHREWD))
			.sell(st(Items.COOKED_MUTTON, 5), -1, 0, Category.SUPPLIES)
			.sell(st(Items.ARROW, 24), -1, 0, Category.SUPPLIES)
			.sell(st(Items.BOW), 3, 1, Category.ARMS)
			.sell(st(Items.LEATHER_CHESTPLATE), 3, 1, Category.GEAR)
			.sell(st(Items.LEATHER_BOOTS), 3, 1, Category.GEAR)
			.sell(st(Items.CAMPFIRE), 6, 3, Category.SUPPLIES)
			.produce(AwGoods.FROST_SILK)
			.sell(st(AwGoods.FROST_SILK), 6, 3, Category.GOODS)
			.want(AwGoods.SPICE_CRATE, 1.9)
			.want(AwGoods.EMBER_GLASS, 1.8)
			.want(AwItems.RIME_CORE, 1.4)
			.want(Items.LEATHER, 1.3);
		add(new Merchant("ines", Faction.COURT, 1.1, 0.5, Temperament.VAIN))
			.sell(st(Items.GOLDEN_APPLE), 4, 1, Category.SUPPLIES)
			.sell(st(Items.CANDLE, 4), -1, 0, Category.SUPPLIES)
			.sell(potion(Potions.STRONG_HEALING), 6, 3, Category.POTIONS)
			.sell(st(Items.TOTEM_OF_UNDYING), 1, 0, Category.CURIOS, 8)
			.produce(AwGoods.MIRROR_SALT)
			.sell(st(AwGoods.MIRROR_SALT, 2), 8, 4, Category.GOODS)
			.want(AwGoods.SKY_AMBERGRIS, 1.8)
			.want(AwGoods.CLOCKWORK_CURIO, 1.4)
			.want(AwItems.ECHO_ESSENCE, 1.5)
			.want(AwItems.GRAVE_WAX, 1.5);
		add(new Merchant("rashid", Faction.GUILD, 1.2, 0.55, Temperament.JEALOUS))
			.sell(st(Items.SANDSTONE, 16), -1, 0, Category.MATERIALS)
			.sell(st(Items.CACTUS, 4), -1, 0, Category.MATERIALS)
			.sell(st(Items.DYE.pick(DyeColor.ORANGE), 4), -1, 0, Category.MATERIALS)
			.sell(st(Items.CARPET.pick(DyeColor.RED), 6), -1, 0, Category.SUPPLIES)
			.sell(st(Items.DECORATED_POT), 6, 3, Category.CURIOS)
			.sell(st(Items.CAMEL_SPAWN_EGG), 1, 0, Category.CURIOS, 4)
			.produce(AwGoods.SPICE_CRATE)
			.sell(st(AwGoods.SPICE_CRATE), 6, 3, Category.GOODS)
			.want(AwGoods.FROST_SILK, 1.9)
			.want(AwGoods.STARLIGHT_INK, 1.6)
			.want(AwItems.EMBER_CORE, 1.4);
		// Afterworld 2.0's supplies and gear.
		get("bram").sell(st(AwGear.HEARTY_STEW, 2), 8, 4, Category.SUPPLIES).sell(st(AwGear.WAYSTONE_SHARD), 6, 3, Category.SUPPLIES)
			.sell(st(AwGear.SMOKE_BOMB, 2), 8, 4, Category.SUPPLIES).sell(st(AwGear.LOCKPICK, 4), 10, 5, Category.SUPPLIES)
			.sell(st(AwGear.SURVEY_SCROLL), 4, 2, Category.SUPPLIES, 2);
		get("marta").sell(st(AwGear.MOONSILVER_RAPIER), 1, 0, Category.ARMS, 3).sell(st(AwGear.WAYFARERS_FANG), 1, 0, Category.ARMS, 2)
			.sell(st(AwGear.MIRRORGLASS_CHAKRAM), 1, 0, Category.ARMS, 4).sell(st(AwGear.ASHEN_WARBANNER), 1, 0, Category.ARMS, 6)
			.sell(st(AwGear.THUNDERDRUM_GAUNTLETS), 1, 0, Category.ARMS, 7).sell(st(AwGear.EMBER_OIL, 2), 6, 3, Category.SUPPLIES)
			.sell(st(AwGear.FROSTBITE_OIL, 2), 6, 3, Category.SUPPLIES)
			.sell(st(AwGear.MOONSILVER_INGOT), 4, 2, Category.MATERIALS, 3).sell(st(AwGear.SANDSTEEL_INGOT), 4, 2, Category.MATERIALS, 4);
		for (Item piece : AwGear.ARMOR.get(AwGear.Set.EMBERFORGED)) {
			get("marta").sell(st(piece), 1, 0, Category.GEAR, 8);
		}
		get("wren").sell(st(AwGear.SURVEY_SCROLL), 6, 3, Category.SUPPLIES).sell(st(AwGear.PROTOTYPE_WING), 1, 0, Category.GEAR, 3);
		get("selene").sell(st(AwGear.STARLIGHT_TEA, 2), 6, 3, Category.SUPPLIES).sell(st(AwGear.DRAUGHT_OF_RESONANCE), 3, 2, Category.POTIONS, 5)
			.sell(st(AwGear.STARWEAVERS_WAND), 1, 0, Category.ARMS, 6).sell(st(AwGear.STAR_FRAGMENT), 3, 2, Category.MATERIALS, 4);
		for (Item piece : AwGear.ARMOR.get(AwGear.Set.STARWEAVE)) {
			get("selene").sell(st(piece), 1, 0, Category.GEAR, 9);
		}
		get("odo").sell(st(AwGear.THUNDER_BOMB, 2), 6, 3, Category.SUPPLIES, 3).sell(st(AwGear.BRASSMERE_REPEATER), 1, 0, Category.ARMS, 6)
			.sell(st(AwGear.SKYHOOK_HARPOON), 1, 0, Category.ARMS, 5).sell(st(Items.GUNPOWDER, 8), -1, 0, Category.SUPPLIES);
		get("mab").sell(st(AwGear.VENOM_OIL, 2), 6, 3, Category.SUPPLIES).sell(st(AwGear.ELIXIR_OF_STAMINA), 6, 3, Category.POTIONS)
			.sell(st(AwGear.PHIAL_OF_SECOND_WIND), 6, 3, Category.POTIONS).sell(st(AwGear.SPORELASH_WHIP), 1, 0, Category.ARMS, 4)
			.sell(st(AwGear.BOGIRON_CROSSBOW), 1, 0, Category.ARMS, 4);
		get("torvald").sell(st(AwGear.FROST_BOMB, 2), 6, 3, Category.SUPPLIES, 2).sell(st(AwGear.DUSKPINE_LONGBOW), 1, 0, Category.ARMS, 3)
			.sell(st(AwGear.MOONBERRY_TART, 2), 6, 3, Category.SUPPLIES);
		for (Item piece : AwGear.ARMOR.get(AwGear.Set.DUSKPINE_RANGER)) {
			get("torvald").sell(st(piece), 1, 0, Category.GEAR, 4);
		}
		for (Item piece : AwGear.ARMOR.get(AwGear.Set.RIMEGUARD)) {
			get("torvald").sell(st(piece), 1, 0, Category.GEAR, 9);
		}
		get("ines").sell(st(AwGear.PHIAL_OF_SECOND_WIND, 2), 6, 3, Category.POTIONS).sell(st(AwGear.GLIMMERFIN_SUSHI, 2), 6, 3, Category.SUPPLIES);
		get("rashid").sell(st(AwGear.SPICED_KEBAB, 2), 8, 4, Category.SUPPLIES).sell(st(AwGear.EMBER_PEPPER, 3), 8, 4, Category.SUPPLIES)
			.sell(st(AwGear.SANDSTEEL_INGOT), 4, 2, Category.MATERIALS, 3).sell(st(AwGear.GLYPH_GOLD), 2, 1, Category.MATERIALS, 6);
		// Who wants what the wilds give up.
		get("marta").want(AwGear.BOAR_TUSK, 1.4).want(AwGear.RAM_HORN, 1.4).want(AwGear.WYRM_SCALE, 1.5).want(AwGear.NARWHAL_TUSK, 1.5)
			.want(AwGear.EMBER_CHITIN, 1.3);
		get("mab").want(AwGear.TOAD_OIL, 1.5).want(AwGear.PALE_ECTOPLASM, 1.7).want(AwGear.KITSUNE_TAIL, 1.4);
		get("selene").want(AwGear.ANGLER_LURE, 1.6).want(AwGear.KITSUNE_TAIL, 1.6).want(AwGear.PALE_ECTOPLASM, 1.3);
		get("torvald").want(AwGear.LYNX_PELT, 1.6).want(AwGear.DUSK_FANG, 1.5).want(AwGear.WINDRIDER_FEATHER, 1.4);
		get("rashid").want(AwGear.SIFTER_MANDIBLE, 1.6).want(AwGear.EMBER_CHITIN, 1.4);
		get("wren").want(AwGear.CRAB_SHELL, 1.5).want(AwGear.WINDRIDER_FEATHER, 1.3);
		get("odo").want(AwGear.WINDRIDER_FEATHER, 1.6).want(AwGear.NARWHAL_TUSK, 1.3);
		get("ines").want(AwGear.ROOT_HEARTWOOD, 1.5).want(AwGear.PALE_ECTOPLASM, 1.4);
		// The camp's quartermaster buys every good at a fair, unexciting price.
		Merchant bram = get("bram");
		for (Item good : AwGoods.TRADE_GOODS) {
			bram.want(good, 0.9);
		}
	}

	/** Lets later systems (meals, scrolls, oils) add their wares to a merchant. */
	public static void extend(String merchant, Supplier<ItemStack> stack, int stock, int restock, Category category, int minRenown) {
		Merchant m = get(merchant);
		if (m != null) {
			m.sell(stack, stock, restock, category, minRenown);
		}
	}
}
