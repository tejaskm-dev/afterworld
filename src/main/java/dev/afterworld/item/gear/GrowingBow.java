package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.item.WeaponGrowth;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The Duskpine Longbow. A fully drawn arrow becomes a Moonshot: it glows, curves toward the
 * nearest foe in its path, and hits harder with each tier.
 */
public class GrowingBow extends BowItem implements WeaponGrowth.Growing, Tooltipped {
	public GrowingBow(Properties properties) {
		super(properties);
	}

	@Override
	public int maxTier() {
		return 3;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return WeaponGrowth.ready(stack) || super.isFoil(stack);
	}

	@Override
	protected net.minecraft.world.entity.projectile.Projectile createProjectile(net.minecraft.world.level.Level level, LivingEntity shooter, ItemStack weapon,
			ItemStack projectile, boolean isCrit) {
		var shot = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		if (shot instanceof AbstractArrow arrow) {
			int tier = WeaponGrowth.tier(weapon);
			arrow.setBaseDamage(3.0 + tier);
			if (isCrit && !level.isClientSide()) {
				dev.afterworld.combat.Projectiles.moonshot(arrow, tier);
			}
		}
		return shot;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		WeaponGrowth.lore(stack, 3, out);
		out.accept(Component.translatable("item.afterworld.duskpine_longbow.desc").withStyle(ChatFormatting.DARK_AQUA));
	}
}
