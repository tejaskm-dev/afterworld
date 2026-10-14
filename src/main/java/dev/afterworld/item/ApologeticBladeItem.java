package dev.afterworld.item;

import dev.afterworld.player.Progress;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Scheduler;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A sword whose spirit counterattacks after a perfect parry, and who is deeply sorry about the
 * whole business whenever you swing it at something that meant no harm.
 */
public class ApologeticBladeItem extends Item implements Tooltipped {
	private static final String[] APOLOGIES = {
		"Oh no. Oh, I am so sorry.", "That one had a family, you know.", "I did not consent to this.", "Please tell it I'm sorry.",
		"We are going to talk about this later.", "I was forged for better than this.", "Sorry! Sorry. Sorry.", "It was looking at us kindly."
	};

	public ApologeticBladeItem(Properties properties) {
		super(properties);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer p && (target instanceof Animal || target instanceof AbstractVillager)) {
			String line = APOLOGIES[p.getRandom().nextInt(APOLOGIES.length)];
			p.sendOverlayMessage(Component.literal("“" + line + "”").withStyle(ChatFormatting.ITALIC).withColor(0xD8C9A3));
			Progress.codex(p, "relic.apologetic_blade.conscience");
		}
	}

	/** Called by the parry hook: the blade answers a perfect parry with a strike of its own. */
	public static void counter(ServerPlayer p, LivingEntity enemy) {
		ServerLevel level = p.level();
		Scheduler.after(3, () -> {
			if (!enemy.isAlive() || !p.isAlive()) {
				return;
			}
			float damage = 4.0F + (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.75F;
			enemy.hurtServer(level, level.damageSources().playerAttack(p), damage);
			Fx.burst(level, ParticleTypes.SWEEP_ATTACK, enemy.position().add(0, enemy.getBbHeight() * 0.5, 0), 1, 0, 0);
			Fx.sound(level, enemy.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.1F);
			Progress.codex(p, "relic.apologetic_blade");
		});
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("item.afterworld.apologetic_blade.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		builder.accept(Component.translatable("item.afterworld.apologetic_blade.effect").withStyle(ChatFormatting.DARK_AQUA));
	}
}
