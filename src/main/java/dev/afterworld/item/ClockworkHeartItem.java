package dev.afterworld.item;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.edict.Zones;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwComponents;
import dev.afterworld.util.Fx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Winds itself on every blow you take. Release a charge to slow everything thrown at you. */
public class ClockworkHeartItem extends LoreItem {
	public static final int MAX = 3;

	public ClockworkHeartItem(Properties properties) {
		super(properties, 2, false);
	}

	public static int charge(ItemStack stack) {
		Integer c = stack.get(AwComponents.CHARGE);
		return c == null ? 0 : c;
	}

	/** Called when the holder is hurt. */
	public static void wind(ServerPlayer p) {
		for (ItemStack stack : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
			if (stack.getItem() instanceof ClockworkHeartItem && charge(stack) < MAX) {
				stack.set(AwComponents.CHARGE, charge(stack) + 1);
				Fx.sound(p.level(), p.position(), AwSounds.BOSS_TICK, 0.5F, 1.5F);
				return;
			}
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}
		if (charge(stack) <= 0) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.heart_empty").withColor(0xA9D8E8));
			return InteractionResult.FAIL;
		}
		long now = level.getGameTime();
		var ps = dev.afterworld.player.PlayerState.of(p);
		long last = ps.heartUsedAt;
		stack.set(AwComponents.CHARGE, charge(stack) - 1);
		Zones.open(Zones.Kind.STILL, p.level(), p.position().add(0, 1, 0), 8, 100, p.getUUID(), 1.0F);
		Fx.sound(p.level(), p.position(), SoundEvents.BELL_RESONATE, 0.7F, 2.0F);
		Progress.codex(p, "relic.clockwork_heart");
		if (now - last < 400) {
			p.igniteForSeconds(3);
			p.getCooldowns().addCooldown(stack, 1200);
			p.sendOverlayMessage(Component.translatable("message.afterworld.heart_overheat").withColor(0xC4553B));
			Progress.codex(p, "relic.clockwork_heart.overheat");
		}
		ps.heartUsedAt = now;
		return InteractionResult.SUCCESS_SERVER;
	}
}
