package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.player.Relics;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A relic: use it to wear it in the first free relic slot (the Character screen shows and swaps
 * them). Its effect is looked up wherever it matters by its id.
 */
public class RelicItem extends Item implements Tooltipped {
	public RelicItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer sp) {
			List<ItemStack> worn = Relics.of(sp);
			int free = -1;
			for (int i = 0; i < Relics.SLOTS; i++) {
				if (i >= worn.size() || worn.get(i).isEmpty()) {
					free = i;
					break;
				}
			}
			if (free < 0) {
				sp.sendOverlayMessage(Component.translatable("message.afterworld.relics_full").withColor(0xC86B5A));
				return InteractionResult.FAIL;
			}
			Relics.equip(sp, free, stack);
			stack.shrink(1);
			sp.level().playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.3F);
			sp.sendOverlayMessage(Component.translatable("message.afterworld.relic_worn", stack.getHoverName()).withColor(0xE8C873));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		out.accept(Component.translatable("relic.afterworld.label").withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("item.afterworld." + id + ".desc").withStyle(ChatFormatting.DARK_AQUA));
	}
}
