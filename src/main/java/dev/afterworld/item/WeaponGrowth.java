package dev.afterworld.item;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Progress;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Weapons that grow. A growing weapon keeps its tier (1-3) and its renown (earned by fighting)
 * in its custom data; when the renown reaches the next threshold it hums (glints) and is ready to
 * be woken at Marta's anvil, where the smithing trial raises its tier: a new look (custom model
 * data), more damage and a stronger Art.
 */
public final class WeaponGrowth {
	public static final int[] THRESHOLD = {0, 150, 600};
	/** Extra damage per tier above the first. */
	public static final double DAMAGE_PER_TIER = 1.5;

	private WeaponGrowth() {
	}

	public static boolean grows(ItemStack stack) {
		return stack.getItem() instanceof Growing;
	}

	public static int tier(ItemStack stack) {
		return Math.max(1, Math.min(3, data(stack).getIntOr("tier", 1)));
	}

	public static int renown(ItemStack stack) {
		return data(stack).getIntOr("renown", 0);
	}

	public static boolean ready(ItemStack stack) {
		if (!grows(stack)) {
			return false;
		}
		int t = tier(stack);
		return t < ((Growing) stack.getItem()).maxTier() && renown(stack) >= THRESHOLD[t];
	}

	private static CompoundTag data(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	/** Called when the weapon's wielder kills something: big things teach a weapon more. */
	public static void earn(ServerPlayer p, ItemStack stack, int amount) {
		if (!grows(stack) || amount <= 0) {
			return;
		}
		boolean before = ready(stack);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt("renown", t.getIntOr("renown", 0) + amount));
		if (!before && ready(stack)) {
			Progress.notice(p, AwPayloads.Notice.EDICT, Component.translatable("notice.afterworld.weapon_hums", stack.getHoverName()));
		}
	}

	/** Raises the weapon one tier: look, damage and Art. Returns false if it wasn't ready. */
	public static boolean evolve(ItemStack stack) {
		if (!ready(stack)) {
			return false;
		}
		int next = tier(stack) + 1;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt("tier", next));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(String.valueOf(next)), List.of()));
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(Attributes.ATTACK_DAMAGE,
			new AttributeModifier(dev.afterworld.Afterworld.id("growth"), DAMAGE_PER_TIER * (next - 1), AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND));
		return true;
	}

	public static void lore(ItemStack stack, int maxTier, Consumer<Component> out) {
		int t = tier(stack);
		Component name = dev.afterworld.item.gear.GrowingArt.tierName(t);
		if (t < maxTier) {
			int need = THRESHOLD[t];
			out.accept(Component.translatable("growth.afterworld.line", name, Math.min(renown(stack), need), need).withStyle(ChatFormatting.GRAY));
			if (ready(stack)) {
				out.accept(Component.translatable("growth.afterworld.ready").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		} else {
			out.accept(Component.translatable("growth.afterworld.max", name).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Implemented by weapons that can grow. */
	public interface Growing {
		int maxTier();
	}
}
