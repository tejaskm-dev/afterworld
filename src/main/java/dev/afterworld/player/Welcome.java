package dev.afterworld.player;

import dev.afterworld.AwConfig;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.registry.AwItems;
import dev.afterworld.util.Inv;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** First steps: an Expedition Journal, a torn map, and a line or two about where you are. */
public final class Welcome {
	private Welcome() {
	}

	/** The first lines a traveller reads, once they have arrived. */
	static void intro(net.minecraft.server.level.ServerPlayer p, PlayerState s) {
		if (s.introShown) {
			return;
		}
		s.introShown = true;
		p.sendSystemMessage((s.travellerName.isEmpty()
			? Component.translatable("message.afterworld.intro.1")
			: Component.translatable("message.afterworld.intro.named", s.travellerName)).withColor(0xE8DCC0));
		p.sendSystemMessage(Component.translatable(s.discipline == Discipline.NONE ? "message.afterworld.intro.2" : "message.afterworld.intro.2b").withStyle(ChatFormatting.GRAY));
		Progress.legend(p, "milestone", "Arrived in a world that had not yet decided what to make of them.");
		dev.afterworld.quest.Quests.narrate(p, s, "prologue");
	}

	public static void init() {
		ServerPlayerEvents.JOIN.register(p -> {
			PlayerState s = PlayerState.of(p);
			if (!s.journalGiven && AwConfig.get().giveJournalOnFirstJoin) {
				s.journalGiven = true;
				Inv.give(p, new ItemStack(AwItems.JOURNAL));
				Inv.give(p, new ItemStack(AwItems.TORN_MAP));
			}
			if (!Arrival.onJoin(p, s)) {
				intro(p, s);
			}
			Disciplines.applyPassives(p, s);
			Bonds.applyPassives(p, s);
			s.hudDirty = true;
			s.questDirty = true;
			dev.afterworld.player.Renown.apply(p, s);
			dev.afterworld.player.Skills.apply(p, s);
			dev.afterworld.economy.Wallet.sync(p, s, 0);
			dev.afterworld.movement.Moves.syncAll(p);
			if (s.arrived) {
				dev.afterworld.util.Scheduler.after(60, () -> dev.afterworld.quest.Quests.announce(p));
			}
			EdictSystem.syncHud(p, s);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, p, alive) -> {
			PlayerState s = PlayerState.of(p);
			dev.afterworld.player.Renown.apply(p, s);
			dev.afterworld.player.Skills.apply(p, s);
		});
	}
}
