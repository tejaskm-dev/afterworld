package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.movement.Gifts;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A Gift: use it once and its ability is yours for good (gliding, a double jump, climbing...). */
public class GiftItem extends Item implements Tooltipped {
	private final Gifts.Gift gift;

	public GiftItem(Properties properties, Gifts.Gift gift) {
		super(properties);
		this.gift = gift;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && Gifts.learn(sp, this.gift)) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		out.accept(Component.translatable("gift.afterworld.label").withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("gift.afterworld." + this.gift.id + ".desc").withStyle(ChatFormatting.DARK_AQUA));
	}
}
