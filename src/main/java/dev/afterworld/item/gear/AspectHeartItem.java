package dev.afterworld.item.gear;

import dev.afterworld.aspect.Aspect;
import dev.afterworld.aspect.Aspects;
import dev.afterworld.item.Tooltipped;
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

/** The heart of an old power. Use it to bind its Aspect; it is consumed. */
public class AspectHeartItem extends Item implements Tooltipped {
	private final Aspect aspect;

	public AspectHeartItem(Properties properties, Aspect aspect) {
		super(properties);
		this.aspect = aspect;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && Aspects.bind(sp, this.aspect)) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		out.accept(Component.translatable("aspect.afterworld.label", Component.translatable("aspect.afterworld." + this.aspect.id)).withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("aspect.afterworld." + this.aspect.id + ".desc").withStyle(ChatFormatting.DARK_AQUA));
		out.accept(Component.translatable("aspect.afterworld.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
