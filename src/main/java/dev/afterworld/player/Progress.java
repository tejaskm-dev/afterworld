package dev.afterworld.player;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.world.Faction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Player-facing progression helpers: codex unlocks, legend lines, reputation, learning Edicts. */
public final class Progress {
	private Progress() {
	}

	public static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / 24000L;
	}

	public static long day(ServerPlayer player) {
		return day(player.level().getServer());
	}

	/** While set, unlocks and choices happen without notices or chat lines (the arrival already said it all). */
	private static boolean quiet;

	public static void quietly(Runnable r) {
		boolean was = quiet;
		quiet = true;
		try {
			r.run();
		} finally {
			quiet = was;
		}
	}

	public static boolean isQuiet() {
		return quiet;
	}

	public static void notice(ServerPlayer player, int kind, Component text) {
		if (!quiet && ServerPlayNetworking.canSend(player, AwPayloads.Notice.TYPE)) {
			ServerPlayNetworking.send(player, new AwPayloads.Notice(kind, text));
		}
	}

	/** Unlocks a Field Codex entry. Returns true if it was new. */
	public static boolean codex(ServerPlayer player, String entry) {
		PlayerState s = PlayerState.of(player);
		if (s.codex.add(entry)) {
			notice(player, AwPayloads.Notice.CODEX, Component.translatable("notice.afterworld.codex", Component.translatable("codex.afterworld." + entry)));
			return true;
		}
		return false;
	}

	public static boolean hasCodex(ServerPlayer player, String entry) {
		return PlayerState.of(player).codex.contains(entry);
	}

	public static void legend(ServerPlayer player, String kind, String text) {
		PlayerState.of(player).addLegend(day(player), kind, text);
	}

	public static void rep(ServerPlayer player, Faction faction, int amount) {
		if (amount == 0) {
			return;
		}
		PlayerState s = PlayerState.of(player);
		int oldTier = s.addRep(faction, amount);
		if (oldTier != Integer.MIN_VALUE) {
			int rep = s.rep(faction);
			notice(player, AwPayloads.Notice.WORLD, Component.translatable("notice.afterworld.rep_tier", faction.displayName(), Faction.tierName(rep)));
			legend(player, "reputation", "Became " + Faction.tierName(rep).getString() + " with " + faction.displayName().getString() + ".");
		}
	}

	/** Learns an Edict permanently and equips it if a slot is free. Returns false if already known. */
	public static boolean learn(ServerPlayer player, Edict edict) {
		PlayerState s = PlayerState.of(player);
		if (!s.learned.add(edict)) {
			return false;
		}
		boolean equipped = s.autoEquip(edict);
		s.hudDirty = true;
		codex(player, "edict." + edict.id);
		if (quiet) {
			legend(player, "discovery", "Bound the Edict of " + edict.displayName().getString() + ".");
			return true;
		}
		player.level().playSound(null, player.blockPosition(), AwSounds.UI_DISCOVERY.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.afterworld.learned", edict.displayName().copy().withColor(edict.color))
			.withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("  ").append(edict.rule().copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)));
		if (!equipped) {
			player.sendSystemMessage(Component.translatable("message.afterworld.slots_full").withStyle(ChatFormatting.DARK_GRAY));
		}
		legend(player, "discovery", "Bound the Edict of " + edict.displayName().getString() + ".");
		return true;
	}
}
