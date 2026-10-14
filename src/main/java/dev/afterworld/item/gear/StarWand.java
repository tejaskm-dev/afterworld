package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.item.WeaponGrowth;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * The Starweaver's Wand: six charges of star bolts that refill one every three seconds. At tier
 * III each bolt splits into three on impact.
 */
public class StarWand extends Item implements WeaponGrowth.Growing, Tooltipped {
	public static final int CHARGES = 6;
	private static final int REFILL = 60;

	public StarWand(Properties properties) {
		super(properties);
	}

	@Override
	public int maxTier() {
		return 3;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return WeaponGrowth.ready(stack);
	}

	public static int charges(ItemStack stack) {
		CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return t.getIntOr("charges", CHARGES);
	}

	private static void setCharges(ItemStack stack, int n, int timer) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
			t.putInt("charges", n);
			t.putInt("refill", timer);
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int c = charges(stack);
		if (c <= 0) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			dev.afterworld.combat.Projectiles.starBolt(sl, sp, WeaponGrowth.tier(stack));
			setCharges(stack, c - 1, REFILL);
			player.getCooldowns().addCooldown(stack, 6);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (level.getGameTime() % 5 != 0) {
			return;
		}
		CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		int c = t.getIntOr("charges", CHARGES);
		if (c >= CHARGES) {
			return;
		}
		int timer = t.getIntOr("refill", REFILL) - 5;
		if (timer <= 0) {
			setCharges(stack, c + 1, REFILL);
		} else {
			setCharges(stack, c, timer);
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return charges(stack) < CHARGES;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charges(stack) / CHARGES);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xC08AFF;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		WeaponGrowth.lore(stack, 3, out);
		out.accept(Component.translatable("item.afterworld.starweavers_wand.desc", charges(stack), CHARGES).withStyle(ChatFormatting.DARK_AQUA));
	}
}
