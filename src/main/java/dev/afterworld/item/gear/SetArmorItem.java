package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.registry.AwGear;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A piece of one of the six armour sets. Its tooltip names the set and what the whole set does. */
public class SetArmorItem extends Item implements Tooltipped {
	public SetArmorItem(Properties properties) {
		super(properties);
	}

	private AwGear.Set set() {
		for (AwGear.Set set : AwGear.Set.values()) {
			for (Item piece : set.pieces) {
				if (piece == this) {
					return set;
				}
			}
		}
		return null;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		AwGear.Set set = this.set();
		if (set == null) {
			return;
		}
		int worn = 0;
		var player = VIEWER.get();
		if (player != null) {
			worn = dev.afterworld.combat.SetBonuses.count(player, set);
		}
		out.accept(Component.translatable("set.afterworld.label", Component.translatable("set.afterworld." + set.id), worn).withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("set.afterworld." + set.id + ".bonus").withStyle(worn == 4 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
	}

	/** Who is reading the tooltip: the client sets this to its own player (servers have none). */
	public static java.util.function.Supplier<net.minecraft.world.entity.player.Player> VIEWER = () -> null;
}
