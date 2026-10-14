package dev.afterworld.economy;

import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.item.CoinItem;
import dev.afterworld.item.LostPurseItem;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwGoods;
import dev.afterworld.registry.MobMark;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where Marks come from and where they go when you fall. Hostile creatures drop coins in
 * proportion to how much fight they had in them (elites and the great powers much more); a
 * traveller who dies drops a tenth of what they carry as a purse they can walk back for.
 */
public final class Coins {
	private Coins() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (entity instanceof ServerPlayer p) {
				dropPurse(level, p);
				return;
			}
			if (!(entity instanceof Enemy) || !(source.getEntity() instanceof Player) || MobMark.has(entity, MobMark.ECHO) || MobMark.has(entity, MobMark.GHOST)) {
				return;
			}
			RandomSource r = level.getRandom();
			double worth = entity.getMaxHealth() / 3.0 * (0.6 + r.nextDouble() * 0.8);
			if (entity instanceof BossMob) {
				worth = 250 + r.nextInt(250);
			} else if (MobMark.has(entity, MobMark.MARKED_BEAST) || MobMark.has(entity, MobMark.WARDEN)) {
				worth *= 4;
			}
			if (source.getEntity() instanceof ServerPlayer killer) {
				worth *= dev.afterworld.player.Relics.coinBonus(killer);
				// Scavenger: a quarter more from everything the traveller slays.
				if (dev.afterworld.player.PlayerState.of(killer).skills.contains("wayfarer.scavenger")) {
					worth *= 1.25;
				}
			}
			scatter(level, entity, (int) Math.round(worth));
		});
	}

	/** Drops {@code marks} as a spray of the fewest coins. */
	public static void scatter(ServerLevel level, LivingEntity at, int marks) {
		for (net.minecraft.world.item.Item coin : AwGoods.COINS) {
			int v = ((CoinItem) coin).value;
			int n = marks / v;
			marks -= n * v;
			while (n > 0) {
				int k = Math.min(n, 16);
				n -= k;
				ItemEntity e = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), new ItemStack(coin, k));
				e.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.25, 0.25, (level.getRandom().nextDouble() - 0.5) * 0.25);
				level.addFreshEntity(e);
			}
		}
	}

	private static void dropPurse(ServerLevel level, ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		long lost = s.marks / 10;
		if (lost <= 0) {
			return;
		}
		s.marks -= lost;
		ItemEntity e = new ItemEntity(level, p.getX(), p.getY() + 0.5, p.getZ(), LostPurseItem.of(lost, p.getName().getString()));
		e.setUnlimitedLifetime();
		e.setGlowingTag(true);
		level.addFreshEntity(e);
		p.sendSystemMessage(Component.translatable("message.afterworld.purse_dropped", lost).withColor(0xC86B5A));
		Wallet.sync(p, s, (int) -lost);
	}

	/** Coins and purses go straight into the wallet. Returns true if the stack was taken. */
	public static boolean pickUp(ServerPlayer p, ItemEntity entity) {
		ItemStack stack = entity.getItem();
		long marks;
		if (stack.getItem() instanceof CoinItem coin) {
			marks = (long) coin.value * stack.getCount();
		} else if (stack.getItem() instanceof LostPurseItem) {
			marks = LostPurseItem.marks(stack);
		} else {
			return false;
		}
		p.take(entity, stack.getCount());
		entity.discard();
		Wallet.add(p, marks);
		Wallet.chime(p, marks);
		return true;
	}
}
