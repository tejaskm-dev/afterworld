package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.item.WeaponGrowth;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** The Bog-iron Crossbow: its bolts carry lurker venom, which spreads to whatever stands near the hit. */
public class GrowingCrossbow extends CrossbowItem implements WeaponGrowth.Growing, Tooltipped {
	public GrowingCrossbow(Properties properties) {
		super(properties);
	}

	@Override
	public int maxTier() {
		return 2;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return WeaponGrowth.ready(stack) || super.isFoil(stack);
	}

	@Override
	protected net.minecraft.world.entity.projectile.Projectile createProjectile(net.minecraft.world.level.Level level,
			net.minecraft.world.entity.LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
		var shot = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		if (shot instanceof AbstractArrow arrow && !level.isClientSide()) {
			arrow.setBaseDamage(4.0 + WeaponGrowth.tier(weapon));
			dev.afterworld.combat.Projectiles.venom(arrow, WeaponGrowth.tier(weapon));
		}
		return shot;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		WeaponGrowth.lore(stack, 2, out);
		out.accept(Component.translatable("item.afterworld.bogiron_crossbow.desc").withStyle(ChatFormatting.DARK_AQUA));
	}
}
