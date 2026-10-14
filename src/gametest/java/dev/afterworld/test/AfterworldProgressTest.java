package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.client.screen.DialogueScreen;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.npc.Dialogue;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.PlayerState;
import dev.afterworld.quest.Chapter;
import dev.afterworld.quest.Quests;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Waystones;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;

/**
 * The prologue done out of order: the waystone touched, the Edict cast, the roll, the parry, the
 * journal and two trades all before Isolde has asked for any of it, then Marta and Bram met ahead
 * of their turn. None of it may be lost: each step counts the moment the story reaches it, and
 * the keepers speak to where the traveller actually is.
 */
public class AfterworldProgressTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 6000");
			context.waitTicks(40);
			String before = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				AfterworldData data = AfterworldData.get(s);
				for (AfterworldData.Waystone w : data.waystones.values()) {
					if (w.camp) {
						Waystones.use(p, w.blockPos());
					}
				}
				Quests.onCast(p);
				Quests.onDodge(p);
				Quests.onParry(p);
				Quests.onJournal(p);
				Quests.progress(p, Chapter.Goal.TRADE, 2);
				return describe(PlayerState.of(p));
			});
			Afterworld.LOG.info("PROGRESSTEST before Isolde: {}", before);
			// Isolde, at last. Then the steps already taken should fall into place, one a second.
			server.runOnServer(s -> Quests.complete(player(s), "meet_isolde"));
			context.waitTicks(20 * 5);
			String afterIsolde = server.computeOnServer(s -> describe(PlayerState.of(player(s))));
			Afterworld.LOG.info("PROGRESSTEST after Isolde: {}", afterIsolde);
			// Marta, met before the story turns to her: she should train, not greet a stranger.
			int marta = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Mannequin m = Npcs.spawn(s.overworld(), Npc.MARTA, p.blockPosition().offset(2, 0, 1), 0);
				Dialogue.start(p, m, Npc.MARTA);
				return m.getId();
			});
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			String martaSays = context.computeOnClient(mc -> firstLine((DialogueScreen) mc.gui.screen()));
			Afterworld.LOG.info("PROGRESSTEST marta opens with: {}", martaSays);
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).closeQuietly());
			// Bram, also ahead of his turn.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Mannequin m = Npcs.spawn(s.overworld(), Npc.BRAM, p.blockPosition().offset(-2, 0, 1), 0);
				Dialogue.start(p, m, Npc.BRAM);
			});
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			String bramSays = context.computeOnClient(mc -> firstLine((DialogueScreen) mc.gui.screen()));
			Afterworld.LOG.info("PROGRESSTEST bram opens with: {}", bramSays);
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).closeQuietly());
			// What their conversations do (the dialogue's "complete:" actions).
			server.runOnServer(s -> {
				Quests.complete(player(s), "meet_marta");
				Quests.complete(player(s), "meet_bram");
			});
			context.waitTicks(20 * 8);
			String end = server.computeOnServer(s -> describe(PlayerState.of(player(s))));
			boolean reportOpen = server.computeOnServer(s -> Quests.isActive(PlayerState.of(player(s)), "report_prologue"));
			Afterworld.LOG.info("PROGRESSTEST end: {} report_prologue open={}", end, reportOpen);
			context.takeScreenshot("progress-tracker");
			if (!reportOpen) {
				throw new AssertionError("The prologue stalled: " + end);
			}
			if (!martaSays.contains("Isolde sent me")) {
				throw new AssertionError("Marta greeted a sent traveller as a stranger: " + martaSays);
			}
			Afterworld.LOG.info("PROGRESSTEST done (marta entity {})", marta);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String describe(PlayerState s) {
		Chapter c = Quests.chapter(s);
		StringBuilder b = new StringBuilder("chapter " + s.chapter + ":");
		if (c != null) {
			for (int i = 0; i < c.objectives.size(); i++) {
				b.append(' ').append(c.objectives.get(i).id()).append(Quests.done(s, i) ? "=done" : Quests.open(s, i) ? "=OPEN" : "=later");
			}
		}
		return b.append(" deeds=").append(s.deeds).toString();
	}

	private static String firstLine(DialogueScreen d) {
		try {
			var f = DialogueScreen.class.getDeclaredField("page");
			f.setAccessible(true);
			var page = (AwPayloads.DialogueShow) f.get(d);
			return page.lines().isEmpty() ? "" : page.lines().get(0).text();
		} catch (ReflectiveOperationException e) {
			return "?";
		}
	}
}
