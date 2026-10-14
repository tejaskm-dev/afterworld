package dev.afterworld.economy;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Guild Ledger: Marks kept here are safe from death and earn one percent a day (at most 500
 * a day). Wren keeps the ledger; any of her counters will do.
 */
public final class Bank {
	public static final int DEPOSIT = 0;
	public static final int WITHDRAW = 1;

	private Bank() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.BankAction.TYPE, (payload, ctx) -> act(ctx.player(), payload.action(), payload.amount()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 200 != 0) {
				return;
			}
			long today = Progress.day(server);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				interest(p, PlayerState.of(p), today);
			}
		});
	}

	private static void interest(ServerPlayer p, PlayerState s, long today) {
		if (s.interestDay < 0) {
			s.interestDay = today;
			return;
		}
		if (today <= s.interestDay) {
			return;
		}
		long days = Math.min(30, today - s.interestDay);
		s.interestDay = today;
		long paid = 0;
		for (int d = 0; d < days; d++) {
			long i = Math.min(500, s.banked / 100);
			s.banked += i;
			paid += i;
		}
		if (paid > 0) {
			Progress.notice(p, AwPayloads.Notice.WORLD, Component.translatable("notice.afterworld.interest", paid));
			Wallet.sync(p, s, 0);
		}
	}

	public static void open(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		ServerPlayNetworking.send(p, new AwPayloads.BankView(s.marks, s.banked, Math.min(500, s.banked / 100)));
		Fx.privateSound(p, AwSounds.UI_OPEN, 0.6F, 1.2F);
	}

	private static void act(ServerPlayer p, int action, long amount) {
		PlayerState s = PlayerState.of(p);
		if (action == DEPOSIT) {
			long n = amount < 0 ? s.marks : Math.min(amount, s.marks);
			if (n > 0) {
				s.marks -= n;
				s.banked += n;
			}
		} else if (action == WITHDRAW) {
			long n = amount < 0 ? s.banked : Math.min(amount, s.banked);
			if (n > 0) {
				s.banked -= n;
				s.marks += n;
			}
		}
		Fx.privateSound(p, AwSounds.COIN, 0.6F, 1.0F);
		Wallet.sync(p, s, 0);
		ServerPlayNetworking.send(p, new AwPayloads.BankView(s.marks, s.banked, Math.min(500, s.banked / 100)));
	}
}
