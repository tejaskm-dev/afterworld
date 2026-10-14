package dev.afterworld.economy;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * A traveller's Marks. Carried Marks live in the wallet, not the inventory; banked Marks are in the
 * Guild Ledger and survive death. Every change is sent to the client, which shows it on the HUD.
 */
public final class Wallet {
	private Wallet() {
	}

	public static long carried(ServerPlayer p) {
		return PlayerState.of(p).marks;
	}

	public static void add(ServerPlayer p, long amount) {
		if (amount <= 0) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		s.marks = Math.min(999_999_999L, s.marks + amount);
		s.lifetimeEarned += amount;
		sync(p, s, (int) Math.min(Integer.MAX_VALUE, amount));
	}

	/** Takes {@code amount} if the traveller has it. Returns whether they did. */
	public static boolean spend(ServerPlayer p, long amount) {
		PlayerState s = PlayerState.of(p);
		if (amount < 0 || s.marks < amount) {
			return false;
		}
		s.marks -= amount;
		sync(p, s, (int) -Math.min(Integer.MAX_VALUE, amount));
		return true;
	}

	public static void sync(ServerPlayer p, PlayerState s, int delta) {
		if (ServerPlayNetworking.canSend(p, AwPayloads.WalletSync.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.WalletSync(s.marks, s.banked, delta));
		}
	}

	public static void chime(ServerPlayer p, long amount) {
		Fx.privateSound(p, AwSounds.COIN, 0.6F, amount >= 100 ? 0.8F : amount >= 10 ? 1.0F : 1.3F);
	}
}
