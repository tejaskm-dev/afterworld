package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.Edict;
import dev.afterworld.combat.Arts;
import dev.afterworld.item.ApologeticBladeItem;
import dev.afterworld.item.ArtWeapon;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import dev.afterworld.item.EdictFragmentItem;
import dev.afterworld.item.JournalItem;
import dev.afterworld.item.LoreItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public final class AwItems {
	public static final List<Item> ORDERED = new ArrayList<>();
	/** Which creative tab each item lives in; anything unlisted is a curio. */
	public static final Map<Item, AwTabs.Section> SECTIONS = new java.util.LinkedHashMap<>();
	public static final Map<Edict, Item> FRAGMENTS = new EnumMap<>(Edict.class);

	public static final Item JOURNAL = register(AwTabs.Section.RELICS, "expedition_journal", JournalItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	static {
		for (Edict e : Edict.VALUES) {
			FRAGMENTS.put(e, register(AwTabs.Section.RELICS, "edict_fragment_" + e.id, p -> new EdictFragmentItem(p, e), new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		}
	}

	// Relics
	public static final Item APOLOGETIC_BLADE = register(AwTabs.Section.ARMS, "apologetic_blade", ApologeticBladeItem::new,
		new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F).rarity(Rarity.RARE));

	// Arms with Arts: six forged from what the new monsters drop, three won from the great actors.
	public static final Item EMBERBRAND = register(AwTabs.Section.ARMS, "emberbrand", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.CINDER_CLEAVE, 3),
		arm(ToolMaterial.DIAMOND, 6.0F, -3.0F, 0).fireResistant().rarity(Rarity.RARE));
	public static final Item SHARDSONG_GLAIVE = register(AwTabs.Section.ARMS, "shardsong_glaive", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.CRYSTAL_FAN, 3),
		arm(ToolMaterial.IRON, 4.0F, -2.8F, 1.0F).rarity(Rarity.RARE));
	public static final Item LURKERS_HOOK = register(AwTabs.Section.ARMS, "lurkers_hook", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.REEL, 3),
		arm(ToolMaterial.IRON, 3.0F, -2.2F, 0.5F).rarity(Rarity.RARE));
	public static final Item ECHO_EDGE = register(AwTabs.Section.ARMS, "echo_edge", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.STEP_BEHIND, 3),
		arm(ToolMaterial.DIAMOND, 1.0F, -1.6F, 0).rarity(Rarity.RARE));
	public static final Item GEARSPRING_MAUL = register(AwTabs.Section.ARMS, "gearspring_maul", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.COIL_SLAM, 3),
		arm(ToolMaterial.IRON, 7.0F, -3.3F, 0).rarity(Rarity.RARE));
	public static final Item RIMEFANG_SPEAR = register(AwTabs.Section.ARMS, "rimefang_spear", p -> new dev.afterworld.item.gear.GrowingArt(p, Arts.Art.FROST_LANCE, 3),
		arm(ToolMaterial.DIAMOND, 3.0F, -2.6F, 1.5F).rarity(Rarity.RARE));
	public static final Item BELLHAMMER = register(AwTabs.Section.ARMS, "bellhammer", p -> new ArtWeapon(p, Arts.Art.TOLL),
		arm(ToolMaterial.NETHERITE, 7.0F, -3.2F, 0).fireResistant().rarity(Rarity.EPIC));
	public static final Item SECOND_HAND = register(AwTabs.Section.ARMS, "second_hand", p -> new ArtWeapon(p, Arts.Art.STOPPED_MOMENT),
		arm(ToolMaterial.NETHERITE, 3.0F, -2.0F, 0.5F).fireResistant().rarity(Rarity.EPIC));
	public static final Item PILGRIMS_STAFF = register(AwTabs.Section.ARMS, "pilgrims_staff", p -> new ArtWeapon(p, Arts.Art.WAY_OF_LIGHT),
		arm(ToolMaterial.NETHERITE, 2.0F, -2.4F, 1.0F).fireResistant().rarity(Rarity.EPIC));

	public static final Item UNFINISHED_COMPASS = register(AwTabs.Section.RELICS, "unfinished_compass", dev.afterworld.item.UnfinishedCompassItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item HUNGRY_LANTERN = register(AwTabs.Section.RELICS, "hungry_lantern", dev.afterworld.item.HungryLanternItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item CLOCKWORK_HEART = register(AwTabs.Section.RELICS, "clockwork_heart", dev.afterworld.item.ClockworkHeartItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item THIMBLE_OF_BORROWED_RAIN = register(AwTabs.Section.RELICS, "thimble_of_borrowed_rain", dev.afterworld.item.ThimbleItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	// Materials
	public static final Item ANOMALY_RESIDUE = register("anomaly_residue", LoreItem::new, new Item.Properties());
	public static final Item CLOCKWORK_FRAGMENT = register("clockwork_fragment", LoreItem::new, new Item.Properties());
	public static final Item MOTH_DUST = register("moth_dust", LoreItem::new, new Item.Properties());
	public static final Item GRAVE_WAX = register("grave_wax", LoreItem::new, new Item.Properties());
	public static final Item BELL_FRAGMENT = register("bell_fragment", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	// Forging materials, one from each of the second wave's monsters.
	public static final Item EMBER_CORE = register("ember_core", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item LIVING_CRYSTAL = register("living_crystal", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item LURKER_FANG = register("lurker_fang", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item ECHO_ESSENCE = register("echo_essence", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item GEAR_SPRING = register("gear_spring", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item RIME_CORE = register("rime_core", LoreItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));

	// Lore and quest items
	public static final Item CHRONICLE_PAGE = register("chronicle_page", LoreItem::new, new Item.Properties().stacksTo(16));
	public static final Item PILGRIM_OFFERING = register("pilgrim_offering", LoreItem::new, new Item.Properties().stacksTo(16));
	public static final Item TORN_MAP = register("torn_map", dev.afterworld.item.TornMapItem::new, new Item.Properties().stacksTo(16));
	public static final Item TORN_CENSUS = register("torn_census", LoreItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item RINGERS_LEDGER = register("ringers_ledger", LoreItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// Creatures in a bucket.
	public static final Item GLIMMERFIN_BUCKET = register(AwTabs.Section.BESTIARY, "glimmerfin_bucket",
		p -> new net.minecraft.world.item.MobBucketItem(AwEntities.GLIMMERFIN, net.minecraft.world.level.material.Fluids.WATER,
			net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH, p),
		new Item.Properties().stacksTo(1).component(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY));

	private AwItems() {
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		return register(AwTabs.Section.CURIOS, name, factory, props);
	}

	public static Item register(AwTabs.Section section, String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Afterworld.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ORDERED.add(item);
		SECTIONS.put(item, section);
		return item;
	}

	/** Sword-like stats, with optional extra reach for the long arms. */
	public static Item.Properties arm(ToolMaterial material, float damage, float speed, float reach) {
		Item.Properties p = new Item.Properties().sword(material, damage, speed);
		if (reach != 0) {
			p.attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(Afterworld.id("reach"), reach, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.build());
		}
		return p;
	}

	public static Item fragment(Edict e) {
		return FRAGMENTS.get(e);
	}

	public static void init() {
	}
}
