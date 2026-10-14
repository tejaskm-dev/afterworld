package dev.afterworld.item;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.util.Fx;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A shard of one of the world's rules. Using it binds the rule to you. */
public class EdictFragmentItem extends Item implements Tooltipped {
	public final Edict edict;

	public EdictFragmentItem(Properties properties, Edict edict) {
		super(properties);
		this.edict = edict;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		PlayerState s = PlayerState.of(sp);
		if (s.learned.contains(edict)) {
			// A rule you already hold shatters into raw anomaly and insight.
			if (!sp.isCreative()) {
				stack.shrink(1);
			}
			sp.giveExperiencePoints(40);
			ItemStack residue = new ItemStack(AwItems.ANOMALY_RESIDUE, 3);
			dev.afterworld.util.Inv.give(sp, residue);
			Fx.burst(sp.level(), Fx.dust(edict.color, 1.2F), sp.position().add(0, 1.2, 0), 16, 0.4, 0.02);
			Fx.burst(sp.level(), dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.SHARD, edict.color), sp.position().add(0, 1.4, 0), 18, 0.3, 0.25);
			Fx.sound(sp.level(), sp.position(), AwSounds.WORLD_SHARD, 1.0F, 1.0F);
			sp.sendOverlayMessage(Component.translatable("message.afterworld.fragment_shatter", edict.displayName()).withColor(edict.color));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (Progress.learn(sp, edict)) {
			if (!sp.isCreative()) {
				stack.shrink(1);
			}
			Fx.burst(sp.level(), ParticleTypes.ENCHANT, sp.position().add(0, 1.4, 0), 50, 0.5, 0.6);
			Fx.burst(sp.level(), Fx.dust(edict.color, 1.4F), sp.position().add(0, 1.0, 0), 24, 0.5, 0.02);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> builder) {
		builder.accept(edict.rule().copy().withStyle(ChatFormatting.ITALIC).withColor(edict.color));
		builder.accept(Component.translatable("edict.afterworld.kind." + edict.kind.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable("item.afterworld.edict_fragment.hint").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
