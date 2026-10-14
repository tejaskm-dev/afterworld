package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.combat.Arts;
import dev.afterworld.economy.Values;
import dev.afterworld.item.LoreItem;
import dev.afterworld.item.gear.AspectHeartItem;
import dev.afterworld.item.gear.ConsumableItem;
import dev.afterworld.item.gear.GadgetItem;
import dev.afterworld.item.gear.GiftItem;
import dev.afterworld.item.gear.GrowingArt;
import dev.afterworld.item.gear.GrowingBow;
import dev.afterworld.item.gear.GrowingCrossbow;
import dev.afterworld.item.gear.RelicItem;
import dev.afterworld.item.gear.Repeater;
import dev.afterworld.item.gear.StarWand;
import dev.afterworld.movement.Gadgets;
import dev.afterworld.movement.Gifts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Afterworld 2.0's gear: weapons that grow, six armour sets, relics, gifts and gadgets,
 * consumables and meals, materials, quest things, and the hearts of the old powers. Every item
 * registers its value here too, so merchants know what it's worth.
 */
public final class AwGear {
	private AwGear() {
	}

	private static Item reg(AwTabs.Section section, String name, Function<Item.Properties, Item> factory, Item.Properties props, int value) {
		Item item = AwItems.register(section, name, factory, props);
		Values.set(item, value);
		return item;
	}

	// ------------------------------------------------------------------ weapons
	public static final Item MOONSILVER_RAPIER = reg(AwTabs.Section.ARMS, "moonsilver_rapier", p -> new GrowingArt(p, Arts.Art.RIPOSTE, 3),
		AwItems.arm(ToolMaterial.IRON, 3.0F, -1.8F, 0.5F).rarity(Rarity.RARE), 260);
	public static final Item DUSKPINE_LONGBOW = reg(AwTabs.Section.ARMS, "duskpine_longbow", GrowingBow::new,
		new Item.Properties().durability(520).enchantable(1).rarity(Rarity.RARE), 240);
	public static final Item BOGIRON_CROSSBOW = reg(AwTabs.Section.ARMS, "bogiron_crossbow", GrowingCrossbow::new,
		new Item.Properties().stacksTo(1).durability(480).component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1).rarity(Rarity.RARE), 220);
	public static final Item WAYFARERS_FANG = reg(AwTabs.Section.ARMS, "wayfarers_fang", p -> new GrowingArt(p, Arts.Art.FANG_FLURRY, 2),
		AwItems.arm(ToolMaterial.IRON, 2.0F, -1.3F, -0.5F).rarity(Rarity.RARE), 180);
	public static final Item MIRRORGLASS_CHAKRAM = reg(AwTabs.Section.ARMS, "mirrorglass_chakram", p -> new GrowingArt(p, Arts.Art.CHAKRAM, 2),
		AwItems.arm(ToolMaterial.IRON, 3.5F, -2.2F, 0).rarity(Rarity.RARE), 220);
	public static final Item STARWEAVERS_WAND = reg(AwTabs.Section.ARMS, "starweavers_wand", StarWand::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 300);
	public static final Item ASHEN_WARBANNER = reg(AwTabs.Section.ARMS, "ashen_warbanner", p -> new GrowingArt(p, Arts.Art.RALLY, 2),
		AwItems.arm(ToolMaterial.IRON, 4.0F, -2.8F, 1.0F).rarity(Rarity.RARE), 240);
	public static final Item SPORELASH_WHIP = reg(AwTabs.Section.ARMS, "sporelash_whip", p -> new GrowingArt(p, Arts.Art.SPORE_LASH, 2),
		AwItems.arm(ToolMaterial.IRON, 2.0F, -2.0F, 2.5F).rarity(Rarity.RARE), 200);
	public static final Item BRASSMERE_REPEATER = reg(AwTabs.Section.ARMS, "brassmere_repeater", Repeater::new,
		new Item.Properties().stacksTo(1).durability(400).rarity(Rarity.RARE), 340);
	public static final Item SKYHOOK_HARPOON = reg(AwTabs.Section.ARMS, "skyhook_harpoon", p -> new GrowingArt(p, Arts.Art.HARPOON, 2),
		AwItems.arm(ToolMaterial.IRON, 4.0F, -2.6F, 1.0F).rarity(Rarity.RARE), 220);
	public static final Item THUNDERDRUM_GAUNTLETS = reg(AwTabs.Section.ARMS, "thunderdrum_gauntlets", p -> new GrowingArt(p, Arts.Art.THUNDERCLAP, 2),
		AwItems.arm(ToolMaterial.IRON, 3.0F, -1.2F, -0.5F).rarity(Rarity.RARE), 240);
	public static final Item HITODAMA = reg(AwTabs.Section.ARMS, "hitodama", p -> new dev.afterworld.item.ArtWeapon(p, Arts.Art.LANTERN_CUTS),
		AwItems.arm(ToolMaterial.NETHERITE, 5.0F, -2.2F, 0.5F).fireResistant().rarity(Rarity.EPIC), 900);
	public static final Item CRIMSON_REQUIEM = reg(AwTabs.Section.ARMS, "crimson_requiem", p -> new dev.afterworld.item.ArtWeapon(p, Arts.Art.CRIMSON_REAP),
		AwItems.arm(ToolMaterial.NETHERITE, 7.0F, -3.0F, 1.0F).fireResistant().rarity(Rarity.EPIC), 900);
	public static final Item RIMEBACK_CLEAVER = reg(AwTabs.Section.ARMS, "rimeback_cleaver", p -> new dev.afterworld.item.ArtWeapon(p, Arts.Art.AVALANCHE),
		AwItems.arm(ToolMaterial.NETHERITE, 8.0F, -3.3F, 0).fireResistant().rarity(Rarity.EPIC), 900);
	public static final Item DUNEWALKERS_CHAIN = reg(AwTabs.Section.ARMS, "dunewalkers_chain", p -> new dev.afterworld.item.ArtWeapon(p, Arts.Art.CHAIN_PULL),
		AwItems.arm(ToolMaterial.NETHERITE, 4.0F, -2.2F, 2.0F).fireResistant().rarity(Rarity.EPIC), 900);
	public static final Item GLYPHWARDEN_AEGIS = reg(AwTabs.Section.ARMS, "glyphwarden_aegis", p -> new dev.afterworld.item.ArtWeapon(p, Arts.Art.GLYPH_BEAM),
		AwItems.arm(ToolMaterial.NETHERITE, 5.0F, -2.6F, 0).fireResistant().rarity(Rarity.EPIC)
			.component(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.15F, 0.8F, List.of(new BlocksAttacks.DamageReduction(90.0F, java.util.Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F), java.util.Optional.empty(), java.util.Optional.of(SoundEvents.SHIELD_BLOCK), java.util.Optional.of(SoundEvents.SHIELD_BREAK))), 900);

	// ------------------------------------------------------------------ armour
	public enum Set {
		WARDENS_REGALIA("wardens_regalia", 28, 3, 6, 7, 3, 12, 1.0F, 0.0F, 420),
		DUSKPINE_RANGER("duskpine_ranger", 22, 2, 4, 5, 2, 15, 0.0F, 0.0F, 220),
		EMBERFORGED("emberforged", 33, 3, 6, 8, 3, 10, 2.0F, 0.05F, 380),
		RIMEGUARD("rimeguard", 30, 3, 5, 7, 3, 10, 1.5F, 0.05F, 340),
		STARWEAVE("starweave", 20, 2, 4, 5, 2, 20, 0.0F, 0.0F, 360),
		GILDED_GLYPHMAIL("gilded_glyphmail", 35, 3, 6, 8, 3, 18, 2.5F, 0.1F, 520);

		public final String id;
		public final ArmorMaterial material;
		public final int value;
		public final Item[] pieces = new Item[4];

		Set(String id, int durability, int boots, int legs, int chest, int helm, int enchant, float toughness, float kb, int value) {
			this.id = id;
			ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Afterworld.id(id));
			TagKey<Item> repair = TagKey.create(Registries.ITEM, Afterworld.id("repairs_" + id));
			this.material = new ArmorMaterial(durability, java.util.Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest,
				ArmorType.HELMET, helm, ArmorType.BODY, chest), enchant, SoundEvents.ARMOR_EQUIP_IRON, toughness, kb, repair, asset);
			this.value = value;
		}
	}

	public static final Map<Set, List<Item>> ARMOR = new EnumMap<>(Set.class);

	static {
		ArmorType[] types = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
		String[] names = {"helmet", "chestplate", "leggings", "boots"};
		for (Set set : Set.values()) {
			List<Item> pieces = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Item item = reg(AwTabs.Section.ARMOR, set.id + "_" + names[i], dev.afterworld.item.gear.SetArmorItem::new,
					new Item.Properties().humanoidArmor(set.material, types[i]).rarity(Rarity.RARE), set.value * (i == 1 ? 2 : 1) / 2);
				pieces.add(item);
				set.pieces[i] = item;
			}
			ARMOR.put(set, pieces);
		}
	}

	// ------------------------------------------------------------------ relics
	private static Item relic(String name, Rarity rarity, int value) {
		return reg(AwTabs.Section.RELICS, name, RelicItem::new, new Item.Properties().stacksTo(1).rarity(rarity), value);
	}

	public static final Item RING_OF_SECOND_BREATH = relic("ring_of_second_breath", Rarity.EPIC, 600);
	public static final Item AMULET_OF_QUIET_STEPS = relic("amulet_of_quiet_steps", Rarity.RARE, 260);
	public static final Item EMBERHEART_PENDANT = relic("emberheart_pendant", Rarity.RARE, 280);
	public static final Item RIMECORE_CHARM = relic("rimecore_charm", Rarity.RARE, 280);
	public static final Item GLIMMERFIN_SCALE = relic("glimmerfin_scale", Rarity.RARE, 220);
	public static final Item MOTHLANTERN_LOCKET = relic("mothlantern_locket", Rarity.RARE, 260);
	public static final Item PILGRIMS_BEAD = relic("pilgrims_bead", Rarity.RARE, 320);
	public static final Item LUCKY_SEXTANT = relic("lucky_sextant", Rarity.RARE, 300);
	public static final Item BRAM_PURSE_KNOT = relic("bram_purse_knot", Rarity.RARE, 200);
	public static final Item GEARSPRUNG_ANKLET = relic("gearsprung_anklet", Rarity.RARE, 240);
	public static final Item ECHO_SHELL = relic("echo_shell", Rarity.RARE, 260);
	public static final Item LICH_FINGER_BONE = relic("lich_finger_bone", Rarity.EPIC, 700);
	public static final Item SHOGUN_TSUBA = relic("shogun_tsuba", Rarity.EPIC, 700);
	public static final Item SENTINEL_SANDGLASS = relic("sentinel_sandglass", Rarity.EPIC, 700);
	public static final Item WARDEN_GLYPH_RING = relic("warden_glyph_ring", Rarity.EPIC, 700);
	public static final Item STARSEED = relic("starseed", Rarity.EPIC, 650);
	public static final Item CARAVAN_SEAL = relic("caravan_seal", Rarity.RARE, 0);

	// ------------------------------------------------------------------ gifts and gadgets
	private static Item gift(String name, Gifts.Gift g) {
		return reg(AwTabs.Section.RELICS, name, p -> new GiftItem(p, g), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 0);
	}

	private static Item gadget(String name, Gadgets.Gadget g, Rarity rarity) {
		return reg(AwTabs.Section.RELICS, name, p -> new GadgetItem(p, g), new Item.Properties().stacksTo(1).rarity(rarity), 0);
	}

	public static final Item MOTH_SILK_GLIDER = gift("moth_silk_glider", Gifts.Gift.GLIDE);
	public static final Item FEATHERSTEP_BOOTS = gift("featherstep_boots", Gifts.Gift.DOUBLE_JUMP);
	public static final Item CLIMBING_CLAWS = gift("climbing_claws", Gifts.Gift.CLIMB);
	public static final Item MIREWALK_CHARM = gift("mirewalk_charm", Gifts.Gift.WATER_WALK);
	public static final Item WAYFARERS_HOOK = gadget("wayfarers_hook", Gadgets.Gadget.GRAPPLE, Rarity.EPIC);
	public static final Item SKYWARD_BELL = gadget("skyward_bell", Gadgets.Gadget.SKYWARD, Rarity.EPIC);
	public static final Item WAYFARERS_WHISTLE = gadget("wayfarers_whistle", Gadgets.Gadget.WHISTLE, Rarity.EPIC);
	public static final Item SURVEY_SEXTANT = gadget("survey_sextant", Gadgets.Gadget.SEXTANT, Rarity.UNCOMMON);
	public static final Item PROTOTYPE_WING = gadget("prototype_wing", Gadgets.Gadget.WING, Rarity.UNCOMMON);

	// ------------------------------------------------------------------ consumables and meals
	private static Item consumable(String name, ConsumableItem.Kind kind, int stack, int value) {
		return reg(AwTabs.Section.SUPPLIES, name, p -> new ConsumableItem(p, kind), new Item.Properties().stacksTo(stack).rarity(Rarity.UNCOMMON), value);
	}

	public static final Item ELIXIR_OF_STAMINA = consumable("elixir_of_stamina", ConsumableItem.Kind.STAMINA_ELIXIR, 16, 14);
	public static final Item DRAUGHT_OF_RESONANCE = consumable("draught_of_resonance", ConsumableItem.Kind.RESONANCE_DRAUGHT, 16, 30);
	public static final Item PHIAL_OF_SECOND_WIND = consumable("phial_of_second_wind", ConsumableItem.Kind.SECOND_WIND, 16, 16);
	public static final Item EMBER_OIL = consumable("ember_oil", ConsumableItem.Kind.EMBER_OIL, 16, 12);
	public static final Item FROSTBITE_OIL = consumable("frostbite_oil", ConsumableItem.Kind.FROST_OIL, 16, 12);
	public static final Item VENOM_OIL = consumable("venom_oil", ConsumableItem.Kind.VENOM_OIL, 16, 12);
	public static final Item SMOKE_BOMB = consumable("smoke_bomb", ConsumableItem.Kind.SMOKE_BOMB, 16, 8);
	public static final Item FROST_BOMB = consumable("frost_bomb", ConsumableItem.Kind.FROST_BOMB, 16, 10);
	public static final Item THUNDER_BOMB = consumable("thunder_bomb", ConsumableItem.Kind.THUNDER_BOMB, 16, 12);
	public static final Item WAYSTONE_SHARD = consumable("waystone_shard", ConsumableItem.Kind.WAYSTONE_SHARD, 16, 20);
	public static final Item SURVEY_SCROLL = consumable("survey_scroll", ConsumableItem.Kind.SURVEY_SCROLL, 16, 16);
	public static final Item LOCKPICK = consumable("lockpick", ConsumableItem.Kind.LOCKPICK, 32, 3);

	private static Item meal(String name, int nutrition, float saturation, int value, MobEffectInstance... effects) {
		var consumable = Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects))).build();
		return reg(AwTabs.Section.SUPPLIES, name, LoreItem::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().build(), consumable)
				.rarity(Rarity.UNCOMMON), value);
	}

	public static final Item HEARTY_STEW = meal("hearty_stew", 10, 1.0F, 12, new MobEffectInstance(MobEffects.HEALTH_BOOST, 6000, 0),
		new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
	public static final Item MOONBERRY_TART = meal("moonberry_tart", 6, 0.6F, 10, new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0),
		new MobEffectInstance(MobEffects.SPEED, 1200, 0));
	public static final Item SPICED_KEBAB = meal("spiced_kebab", 8, 0.8F, 10, new MobEffectInstance(MobEffects.HASTE, 3600, 0),
		new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
	public static final Item GLIMMERFIN_SUSHI = meal("glimmerfin_sushi", 6, 0.6F, 10, new MobEffectInstance(MobEffects.WATER_BREATHING, 3600, 0),
		new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 1200, 0));
	public static final Item EMBER_PEPPER = meal("ember_pepper", 3, 0.3F, 6, new MobEffectInstance(MobEffects.STRENGTH, 2400, 0),
		new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
	public static final Item STARLIGHT_TEA = meal("starlight_tea", 4, 0.4F, 14, new MobEffectInstance(AwEffects.STARLIT, 6000, 0));

	// ------------------------------------------------------------------ materials
	private static Item material(String name, Rarity rarity, int value) {
		return reg(AwTabs.Section.CURIOS, name, LoreItem::new, new Item.Properties().rarity(rarity), value);
	}

	public static final Item RAW_MOONSILVER = material("raw_moonsilver", Rarity.UNCOMMON, 14);
	public static final Item MOONSILVER_INGOT = material("moonsilver_ingot", Rarity.UNCOMMON, 30);
	public static final Item GLIMMER_SILK = material("glimmer_silk", Rarity.UNCOMMON, 12);
	public static final Item GLYPH_GOLD = material("glyph_gold", Rarity.RARE, 40);
	public static final Item RIME_PEARL = material("rime_pearl", Rarity.RARE, 36);
	public static final Item SANDSTEEL_INGOT = material("sandsteel_ingot", Rarity.UNCOMMON, 26);
	public static final Item LICH_BONE = material("lich_bone", Rarity.EPIC, 120);
	public static final Item SHOGUN_STEEL = material("shogun_steel", Rarity.EPIC, 120);
	public static final Item EDICT_SHARD = material("edict_shard", Rarity.RARE, 45);
	public static final Item STAR_FRAGMENT = material("star_fragment", Rarity.RARE, 38);
	// What the second bestiary leaves behind.
	public static final Item BOAR_TUSK = material("boar_tusk", Rarity.COMMON, 9);
	public static final Item RAM_HORN = material("ram_horn", Rarity.COMMON, 10);
	public static final Item LYNX_PELT = material("lynx_pelt", Rarity.UNCOMMON, 14);
	public static final Item TOAD_OIL = material("toad_oil", Rarity.COMMON, 8);
	public static final Item CRAB_SHELL = material("crab_shell", Rarity.UNCOMMON, 12);
	public static final Item KITSUNE_TAIL = material("kitsune_tail", Rarity.RARE, 40);
	public static final Item EMBER_CHITIN = material("ember_chitin", Rarity.UNCOMMON, 13);
	public static final Item SIFTER_MANDIBLE = material("sifter_mandible", Rarity.UNCOMMON, 15);
	public static final Item ROOT_HEARTWOOD = material("root_heartwood", Rarity.UNCOMMON, 14);
	public static final Item DUSK_FANG = material("dusk_fang", Rarity.UNCOMMON, 12);
	public static final Item WYRM_SCALE = material("wyrm_scale", Rarity.RARE, 22);
	public static final Item WINDRIDER_FEATHER = material("windrider_feather", Rarity.RARE, 20);
	public static final Item PALE_ECTOPLASM = material("pale_ectoplasm", Rarity.RARE, 24);
	public static final Item ANGLER_LURE = material("angler_lure", Rarity.RARE, 26);
	public static final Item NARWHAL_TUSK = material("narwhal_tusk", Rarity.RARE, 30);

	// ------------------------------------------------------------------ quest things
	private static Item quest(String name, int stack) {
		return reg(AwTabs.Section.CURIOS, name, LoreItem::new, new Item.Properties().stacksTo(stack).rarity(Rarity.UNCOMMON), 0);
	}

	public static final Item STAR_CHART_FRAGMENT = quest("star_chart_fragment", 3);
	public static final Item GLYPH_TABLET = quest("glyph_tablet", 5);
	public static final Item PHYLACTERY_SHARD = quest("phylactery_shard", 3);
	public static final Item CHALLENGE_LANTERN = quest("challenge_lantern", 1);
	public static final Item SUN_DIAL_KEY = quest("sun_dial_key", 1);
	public static final Item CRYPT_KEY = quest("crypt_key", 8);
	public static final Item BOUNTY_WRIT = quest("bounty_writ", 1);
	public static final Item BELL_ROPE = quest("bell_rope", 1);

	// ------------------------------------------------------------------ the old powers' hearts
	public static final Map<Aspect, Item> HEARTS = new EnumMap<>(Aspect.class);

	static {
		for (Aspect a : Aspect.values()) {
			HEARTS.put(a, reg(AwTabs.Section.RELICS, a.heart, p -> new AspectHeartItem(p, a), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), 0));
		}
	}

	public static void init() {
	}
}
