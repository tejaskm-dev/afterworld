package dev.afterworld.item;

import dev.afterworld.combat.Arts;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A weapon with an Art: strike with it as normal, and press use (right-click) to perform its
 * signature move, which then has to recover. Each also carries a quiet passive on every hit.
 */
public class ArtWeapon extends Item implements Tooltipped {
	private final Arts.Art art;

	public ArtWeapon(Properties properties, Arts.Art art) {
		super(properties);
		this.art = art;
	}

	public Arts.Art art() {
		return this.art;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (!Arts.perform(this.art, server, sp, stack)) {
				return InteractionResult.FAIL;
			}
			float cd = this.art.cooldown * (dev.afterworld.player.Relics.has(sp, "echo_shell") ? 0.8F : 1.0F)
				* (dev.afterworld.player.Skills.has(sp, "arcanum.recall") ? 0.85F : 1.0F)
				* (1.0F - (float) dev.afterworld.player.Skills.bonus(dev.afterworld.player.PlayerState.of(sp), dev.afterworld.player.Skills.Stat.ART_RECOVERY));
			player.getCooldowns().addCooldown(stack, Math.round(cd));
			stack.hurtAndBreak(2, player, hand.asEquipmentSlot());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer p) {
			Arts.passive(this.art, p.level(), p, target);
		}
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		String base = "art.afterworld." + this.art.id;
		out.accept(Component.translatable(base + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		out.accept(Component.translatable("art.afterworld.label", Component.translatable(base), this.art.cooldown / 20)
			.withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable(base + ".desc").withStyle(ChatFormatting.DARK_AQUA));
		out.accept(Component.translatable(base + ".passive").withStyle(ChatFormatting.DARK_GREEN));
	}
}
