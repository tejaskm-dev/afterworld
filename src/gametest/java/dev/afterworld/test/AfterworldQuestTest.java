package dev.afterworld.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.hud.QuestHud;
import dev.afterworld.client.screen.DialogueScreen;
import dev.afterworld.client.screen.JournalScreen;
import dev.afterworld.client.screen.WaystoneScreen;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.world.AfterworldData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Walks the Prologue as a traveller would: wake at the camp gate, talk to Isolde, touch the
 * waystone, cast, open the Journal, visit Bram, and come back to Isolde for Chapter I. Checks the
 * tracker and its marker at every step, and photographs the camp, a conversation and the road.
 */
public class AfterworldQuestTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(false).create()) {
			var server = sp.getServer();
			Arrive.quickly(context, Origin.WANDERER, Discipline.ARCANIST);
			context.waitTicks(100);
			context.waitTicks(60);
			boolean camp = server.computeOnServer(s -> AfterworldData.get(s).campBuilt && AfterworldData.get(s).campCenter != Long.MIN_VALUE);
			check(camp, "the Waystone Camp was built");
			String homes = server.computeOnServer(s -> AfterworldData.get(s).npcHomes.keySet().toString());
			check(homes.contains("isolde") && homes.contains("bram") && homes.contains("wren") && homes.contains("marta"), "the camp's keepers are home: " + homes);
			context.takeScreenshot("quest-arrival");

			// The tracker shows the first objective and points at Isolde.
			context.waitFor(mc -> !QuestHud.ENTRIES.isEmpty(), 200);
			String first = context.computeOnClient(mc -> QuestHud.ENTRIES.get(0).text().getString());
			check(first.contains("Isolde"), "first objective names Isolde: " + first);
			boolean points = context.computeOnClient(mc -> QuestHud.focused() != null);
			check(points, "the marker has somewhere to point");

			// Walk up to Isolde and talk.
			Vec3 isolde = server.computeOnServer(s -> Vec3.atBottomCenterOf(BlockPos.of(AfterworldData.get(s).npcHomes.get("isolde"))));
			server.runCommand(String.format("tp @p %.1f %.1f %.1f", isolde.x, isolde.y, isolde.z + 3.0));
			context.waitTicks(30);
			server.runCommand(String.format("tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f", isolde.x, isolde.y, isolde.z + 3.0, isolde.x, isolde.y + 1.6, isolde.z));
			context.waitTicks(20);
			context.takeScreenshot("quest-isolde");
			talk(context, sp, Npc.ISOLDE);
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			context.waitTicks(30);
			context.takeScreenshot("quest-dialogue");
			// Read through the greeting and ask what to do first (the third answer).
			advanceToAnswers(context);
			String options = context.computeOnClient(mc -> ((DialogueScreen) mc.gui.screen()).optionsForTest().toString());
			check(options.contains("What should I do first?"), "Isolde offers the first questions: " + options);
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).chooseForTest(2));
			context.waitTicks(20);
			advanceToAnswers(context);
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).chooseForTest(0));
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			check(objectiveOpen(server, "attune_camp"), "talking to Isolde opened the waystone objective");

			// Touch the waystone.
			BlockPos stone = server.computeOnServer(s -> {
				for (var w : AfterworldData.get(s).waystones.values()) {
					if (w.camp) {
						return w.blockPos();
					}
				}
				return null;
			});
			check(stone != null, "the camp waystone is registered");
			server.runCommand(String.format("tp @p %d %d %d", stone.getX(), stone.getY(), stone.getZ() + 2));
			context.waitTicks(20);
			useBlock(server, stone);
			context.waitTicks(20);
			check(objectiveOpen(server, "cast"), "touching the waystone opened the cast objective");

			// Cast, with the key.
			context.getInput().pressKey(AfterworldClient.SLOT_KEYS[0]);
			context.waitTicks(30);
			check(objectiveOpen(server, "meet_marta"), "casting opened the Marta objective");

			// Marta trains you: her lesson completes the objective as she gives it.
			Vec3 marta = server.computeOnServer(s -> Vec3.atBottomCenterOf(BlockPos.of(AfterworldData.get(s).npcHomes.get("marta"))));
			server.runCommand(String.format("tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f", marta.x - 3, marta.y, marta.z, marta.x, marta.y + 1.6, marta.z));
			context.waitTicks(30);
			talk(context, sp, Npc.MARTA);
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			advanceToAnswers(context);
			context.takeScreenshot("quest-marta");
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).chooseForTest(0));
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			check(objectiveOpen(server, "dodge"), "Marta's lesson opened the dodge objective");
			// The roll and the parry, as the combat code reports them.
			server.runOnServer(s -> dev.afterworld.quest.Quests.onDodge(s.getPlayerList().getPlayers().get(0)));
			context.waitTicks(5);
			server.runOnServer(s -> dev.afterworld.quest.Quests.onParry(s.getPlayerList().getPlayers().get(0)));
			context.waitTicks(5);
			check(objectiveOpen(server, "journal"), "rolling and parrying opened the journal objective");

			// Open the Journal, with the key.
			context.getInput().pressKey(AfterworldClient.JOURNAL);
			context.waitFor(mc -> mc.gui.screen() instanceof JournalScreen, 100);
			context.waitTicks(30);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(20);
			check(objectiveOpen(server, "meet_bram"), "opening the journal opened the Bram objective");

			// Bram: his welcome and twenty-five Marks, then his stall.
			server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4)));
			long before = server.computeOnServer(s -> dev.afterworld.economy.Wallet.carried(s.getPlayerList().getPlayers().get(0)));
			Vec3 bram = server.computeOnServer(s -> Vec3.atBottomCenterOf(BlockPos.of(AfterworldData.get(s).npcHomes.get("bram"))));
			server.runCommand(String.format("tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f", bram.x - 3, bram.y, bram.z, bram.x, bram.y + 1.6, bram.z));
			context.waitTicks(30);
			talk(context, sp, Npc.BRAM);
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			advanceToAnswers(context);
			context.takeScreenshot("quest-bram");
			long after = server.computeOnServer(s -> dev.afterworld.economy.Wallet.carried(s.getPlayerList().getPlayers().get(0)));
			check(after - before >= 25, "Bram's welcome purse arrived: " + (after - before) + " Marks");
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).chooseForTest(0));
			context.waitFor(mc -> mc.gui.screen() instanceof dev.afterworld.client.screen.ShopScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot("quest-shop");
			// Sell him something, then buy something: the trade objective counts one of each.
			context.runOnClient(mc -> {
				var view = ((dev.afterworld.client.screen.ShopScreen) mc.gui.screen()).viewForTest();
				if (!view.sell().isEmpty()) {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new dev.afterworld.net.AwPayloads.ShopAction(dev.afterworld.economy.Shop.SELL, view.sell().get(0).slot(), 1, 0));
				}
			});
			context.waitTicks(10);
			context.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new dev.afterworld.net.AwPayloads.ShopAction(dev.afterworld.economy.Shop.BUY, 0, 1, 0)));
			context.waitTicks(10);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			check(objectiveOpen(server, "report_prologue"), "trading with Bram opened the report objective");

			// Back to Isolde: Chapter I.
			server.runCommand(String.format("tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f", isolde.x, isolde.y, isolde.z + 3.0, isolde.x, isolde.y + 1.6, isolde.z));
			context.waitTicks(20);
			talk(context, sp, Npc.ISOLDE);
			context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
			advanceToAnswers(context);
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).chooseForTest(1));
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			int chapter = server.computeOnServer(s -> PlayerState.of(s.getPlayerList().getPlayers().get(0)).chapter);
			check(chapter == 1, "the Prologue is done and Chapter I has begun: chapter " + chapter);
			context.waitTicks(30);
			context.takeScreenshot("quest-chapter-one");
			context.waitTicks(120);

			// The tracker now lists Chapter I, and the anomaly objective points somewhere.
			context.waitFor(mc -> QuestHud.ENTRIES.size() >= 4, 200);
			String objs = context.computeOnClient(mc -> QuestHud.ENTRIES.stream().map(e -> e.text().getString()).toList().toString());
			check(objs.contains("loose Edict") && objs.contains("shrine"), "Chapter I objectives listed: " + objs);
			context.waitFor(mc -> QuestHud.pointed().size() >= 2, 300);
			// Face the focused target and photograph the road with its pillar of light.
			Vec3 target = context.computeOnClient(mc -> QuestHud.target(QuestHud.focused()));
			server.runCommand(String.format("tp @p ~ ~ ~ facing %.1f %.1f %.1f", target.x, target.y + 30, target.z));
			context.waitTicks(40);
			context.takeScreenshot("quest-pillar");

			// The waystone road: touch the stone again and the travel list opens.
			server.runCommand(String.format("tp @p %d %d %d", stone.getX(), stone.getY(), stone.getZ() + 2));
			context.waitTicks(20);
			useBlock(server, stone);
			context.waitFor(mc -> mc.gui.screen() instanceof WaystoneScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot("quest-waystones");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			Afterworld.LOG.info("QUESTTEST done");
		}
	}

	/** Right-clicks the nearest NPC of this kind, server side (the click itself is vanilla's). */
	private static void talk(ClientGameTestContext context, TestSingleplayerContext sp, Npc npc) {
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Mannequin m = Npcs.loaded(npc).stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElseThrow();
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, m, null);
		});
	}

	private static void useBlock(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos pos) {
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var state = p.level().getBlockState(pos);
			check(state.is(AwBlocks.WAYSTONE), "there is a waystone at " + pos.toShortString());
			state.useWithoutItem(p.level(), p, new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
		});
	}

	private static void advanceToAnswers(ClientGameTestContext context) {
		for (int i = 0; i < 8; i++) {
			boolean more = context.computeOnClient(mc -> mc.gui.screen() instanceof DialogueScreen d
				&& d.lineForTest() < 7 && !d.optionsForTest().isEmpty() && d.lineForTest() < linesOf(d) - 1);
			if (!more) {
				break;
			}
			context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).advanceForTest());
			context.waitTicks(4);
		}
		context.waitTicks(4);
	}

	private static int linesOf(DialogueScreen d) {
		try {
			var f = DialogueScreen.class.getDeclaredField("page");
			f.setAccessible(true);
			return ((dev.afterworld.net.AwPayloads.DialogueShow) f.get(d)).lines().size();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static boolean objectiveOpen(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String id) {
		return server.computeOnServer(s -> dev.afterworld.quest.Quests.isActive(PlayerState.of(s.getPlayerList().getPlayers().get(0)), id));
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("QUESTTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
