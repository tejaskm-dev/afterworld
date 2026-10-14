package dev.afterworld.item;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The Marks a traveller dropped when they fell. Picking it up puts them back in the wallet. */
public class LostPurseItem extends Item implements Tooltipped {
	public LostPurseItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(long marks, String owner) {
		ItemStack s = new ItemStack(dev.afterworld.registry.AwGoods.LOST_PURSE);
		CompoundTag t = new CompoundTag();
		t.putLong("marks", marks);
		t.putString("owner", owner);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		return s;
	}

	public static long marks(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr("marks", 0);
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		out.accept(Component.translatable("tooltip.afterworld.lost_purse", marks(stack)).withColor(0xE8C873));
	}
}
