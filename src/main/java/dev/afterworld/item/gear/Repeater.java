package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.item.WeaponGrowth;
import dev.afterworld.util.Fx;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * The Brassmere Repeater: three shots, then a reload that eats a pinch of gunpowder. Loud,
 * heavy and very persuasive.
 */
public class Repeater extends Item implements WeaponGrowth.Growing, Tooltipped {
	public static final int ROUNDS = 3;

	public Repeater(Properties properties) {
		super(properties);
	}

	@Override
	public int maxTier() {
		return 2;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return WeaponGrowth.ready(stack);
	}

	public static int rounds(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("rounds", ROUNDS);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		int r = rounds(stack);
		if (r <= 0) {
			// Reload: one gunpowder, a second and a half.
			if (!player.isCreative() && dev.afterworld.util.Inv.take(sp, Items.GUNPOWDER, 1) == 0) {
				sp.sendOverlayMessage(Component.translatable("message.afterworld.repeater_powder").withColor(0xC86B5A));
				return InteractionResult.FAIL;
			}
			CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt("rounds", ROUNDS));
			player.getCooldowns().addCooldown(stack, 30);
			Fx.sound(sl, player.position(), SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 0.8F);
			return InteractionResult.SUCCESS;
		}
		dev.afterworld.combat.Projectiles.shot(sl, sp, WeaponGrowth.tier(stack));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt("rounds", r - 1));
		player.getCooldowns().addCooldown(stack, 8);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		WeaponGrowth.lore(stack, 2, out);
		out.accept(Component.translatable("item.afterworld.brassmere_repeater.desc", rounds(stack), ROUNDS).withStyle(ChatFormatting.DARK_AQUA));
	}
}
