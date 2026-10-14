package dev.afterworld.item.gear;

import dev.afterworld.combat.Arts;
import dev.afterworld.item.ArtWeapon;
import dev.afterworld.item.WeaponGrowth;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A weapon with an Art that grows through tiers as it is used (see {@link WeaponGrowth}). */
public class GrowingArt extends ArtWeapon implements WeaponGrowth.Growing {
	private final int maxTier;

	public GrowingArt(Properties properties, Arts.Art art, int maxTier) {
		super(properties, art);
		this.maxTier = maxTier;
	}

	@Override
	public int maxTier() {
		return this.maxTier;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return WeaponGrowth.ready(stack) || super.isFoil(stack);
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		WeaponGrowth.lore(stack, this.maxTier, out);
		super.lore(stack, out);
	}

	public static Component tierName(int tier) {
		return Component.translatable("growth.afterworld.tier." + tier).withStyle(tier == 3 ? ChatFormatting.LIGHT_PURPLE : tier == 2 ? ChatFormatting.AQUA : ChatFormatting.GRAY);
	}
}
