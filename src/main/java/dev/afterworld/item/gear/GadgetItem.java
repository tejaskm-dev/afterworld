package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.movement.Gadgets;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A tool used again and again: the hook, the skyward bell, the whistle, the sextant, the practice wing. */
public class GadgetItem extends Item implements Tooltipped {
	private final Gadgets.Gadget gadget;

	public GadgetItem(Properties properties, Gadgets.Gadget gadget) {
		super(properties);
		this.gadget = gadget;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			int cd = Gadgets.use(sl, sp, this.gadget, stack);
			if (cd < 0) {
				return InteractionResult.FAIL;
			}
			if (cd > 0) {
				player.getCooldowns().addCooldown(stack, cd);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		out.accept(Component.translatable("gadget.afterworld." + this.gadget.id + ".desc").withStyle(ChatFormatting.DARK_AQUA));
	}
}
