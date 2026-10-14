package dev.afterworld.economy;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * What things are worth, in Marks, before any merchant has an opinion. Afterworld's own items set
 * their value when registered; vanilla items come from the table below, and anything unlisted is
 * judged by its rarity and whether it is gear. A value of 0 means nobody will buy it.
 */
public final class Values {
	private static final Object2IntOpenHashMap<Item> VALUE = new Object2IntOpenHashMap<>();

	private Values() {
	}

	public static void set(Item item, int marks) {
		VALUE.put(item, marks);
	}

	public static int of(ItemStack stack) {
		return stack.isEmpty() ? 0 : of(stack.getItem());
	}

	public static int of(Item item) {
		if (VALUE.containsKey(item)) {
			return VALUE.getInt(item);
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		int guess = guess(item, id.getPath());
		VALUE.put(item, guess);
		return guess;
	}

	private static int guess(Item item, String path) {
		ItemStack s = new ItemStack(item);
		Rarity r = s.getRarity();
		boolean gear = s.has(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS)
			&& !s.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
		int base = switch (r) {
			case COMMON -> 0;
			case UNCOMMON -> 8;
			case RARE -> 24;
			case EPIC -> 60;
		};
		if (gear) {
			base += path.startsWith("netherite") ? 120 : path.startsWith("diamond") ? 45 : path.startsWith("iron") ? 9 : path.startsWith("golden") ? 7
				: path.startsWith("chainmail") ? 8 : path.startsWith("stone") ? 1 : path.startsWith("leather") ? 3 : 4;
		}
		return base;
	}

	public static void init() {
		// Ores, ingots and gems.
		v(Items.COAL, 1);
		v(Items.CHARCOAL, 1);
		v(Items.RAW_COPPER, 1);
		v(Items.COPPER_INGOT, 1);
		v(Items.RAW_IRON, 2);
		v(Items.IRON_INGOT, 3);
		v(Items.IRON_NUGGET, 0);
		v(Items.RAW_GOLD, 4);
		v(Items.GOLD_INGOT, 5);
		v(Items.GOLD_NUGGET, 1);
		v(Items.LAPIS_LAZULI, 2);
		v(Items.REDSTONE, 1);
		v(Items.QUARTZ, 2);
		v(Items.AMETHYST_SHARD, 3);
		v(Items.EMERALD, 12);
		v(Items.DIAMOND, 40);
		v(Items.NETHERITE_SCRAP, 90);
		v(Items.NETHERITE_INGOT, 380);
		v(Items.ECHO_SHARD, 30);
		// Creature drops.
		v(Items.ROTTEN_FLESH, 1);
		v(Items.BONE, 1);
		v(Items.STRING, 1);
		v(Items.SPIDER_EYE, 1);
		v(Items.GUNPOWDER, 2);
		v(Items.ENDER_PEARL, 8);
		v(Items.BLAZE_ROD, 10);
		v(Items.SLIME_BALL, 2);
		v(Items.LEATHER, 2);
		v(Items.FEATHER, 1);
		v(Items.RABBIT_HIDE, 1);
		v(Items.PHANTOM_MEMBRANE, 6);
		v(Items.GHAST_TEAR, 18);
		v(Items.PRISMARINE_SHARD, 2);
		v(Items.PRISMARINE_CRYSTALS, 3);
		v(Items.NAUTILUS_SHELL, 10);
		v(Items.HEART_OF_THE_SEA, 120);
		v(Items.SHULKER_SHELL, 40);
		v(Items.TOTEM_OF_UNDYING, 220);
		v(Items.INK_SAC, 1);
		v(Items.GLOW_INK_SAC, 3);
		v(Items.HONEYCOMB, 2);
		// Food.
		v(Items.WHEAT, 0);
		v(Items.BREAD, 1);
		v(Items.APPLE, 1);
		v(Items.GOLDEN_APPLE, 45);
		v(Items.ENCHANTED_GOLDEN_APPLE, 400);
		v(Items.CARROT, 0);
		v(Items.POTATO, 0);
		v(Items.BAKED_POTATO, 1);
		v(Items.BEEF, 1);
		v(Items.COOKED_BEEF, 2);
		v(Items.PORKCHOP, 1);
		v(Items.COOKED_PORKCHOP, 2);
		v(Items.MUTTON, 1);
		v(Items.COOKED_MUTTON, 2);
		v(Items.CHICKEN, 1);
		v(Items.COOKED_CHICKEN, 2);
		v(Items.COD, 1);
		v(Items.COOKED_COD, 2);
		v(Items.SALMON, 1);
		v(Items.COOKED_SALMON, 2);
		v(Items.PUMPKIN_PIE, 3);
		v(Items.CAKE, 6);
		v(Items.SWEET_BERRIES, 0);
		v(Items.GLOW_BERRIES, 1);
		v(Items.HONEY_BOTTLE, 3);
		v(Items.MUSHROOM_STEW, 2);
		v(Items.RABBIT_STEW, 4);
		// Useful things.
		v(Items.TORCH, 0);
		v(Items.LANTERN, 3);
		v(Items.ARROW, 0);
		v(Items.SPECTRAL_ARROW, 1);
		v(Items.BOW, 4);
		v(Items.CROSSBOW, 8);
		v(Items.SHIELD, 5);
		v(Items.SADDLE, 10);
		v(Items.NAME_TAG, 10);
		v(Items.SPYGLASS, 8);
		v(Items.COMPASS, 6);
		v(Items.CLOCK, 8);
		v(Items.MAP, 3);
		v(Items.PAPER, 0);
		v(Items.BOOK, 2);
		v(Items.WRITABLE_BOOK, 3);
		v(Items.ENCHANTED_BOOK, 30);
		v(Items.EXPERIENCE_BOTTLE, 6);
		v(Items.FIREWORK_ROCKET, 1);
		v(Items.CANDLE, 1);
		v(Items.POTION, 3);
		v(Items.SPLASH_POTION, 5);
		v(Items.GLOWSTONE_DUST, 1);
		v(Items.BLAZE_POWDER, 5);
		v(Items.MAGMA_CREAM, 4);
		v(Items.TURTLE_SCUTE, 12);
		v(Items.ARMADILLO_SCUTE, 4);
		v(Items.TRIDENT, 110);
		v(Items.ELYTRA, 400);
		v(Items.BREEZE_ROD, 8);
		v(Items.HEAVY_CORE, 160);
		v(Items.MACE, 260);
	}

	private static void v(Item item, int marks) {
		VALUE.put(item, marks);
	}
}
