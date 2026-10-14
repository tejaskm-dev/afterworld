package dev.afterworld.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** An item with a line or two of in-world lore under its name. */
public class LoreItem extends Item implements Tooltipped {
	private final int lines;
	private final boolean foil;

	public LoreItem(Properties properties, int lines, boolean foil) {
		super(properties);
		this.lines = lines;
		this.foil = foil;
	}

	public LoreItem(Properties properties) {
		this(properties, 1, false);
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> builder) {
		String base = getDescriptionId() + ".lore";
		for (int i = 0; i < lines; i++) {
			builder.accept(Component.translatable(i == 0 ? base : base + (i + 1)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return foil || super.isFoil(stack);
	}
}
