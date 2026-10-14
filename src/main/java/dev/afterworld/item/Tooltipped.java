package dev.afterworld.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Items that add lore lines under their name. Rendered by the client tooltip hook. */
public interface Tooltipped {
	void lore(ItemStack stack, Consumer<Component> out);
}
