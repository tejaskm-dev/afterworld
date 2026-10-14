package dev.afterworld.test;

import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.screen.JournalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/** Walks through the mod's main surfaces in a real client and takes screenshots of each. */
public class AfterworldClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setSeed("afterworld");
				s.setAllowCommands(true);
			})
			.create()) {
			Arrive.quickly(context, dev.afterworld.player.Origin.WANDERER, dev.afterworld.player.Discipline.ARCANIST);
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set 1000");
			server.runCommand("weather clear 6000");
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute as @p run afterworld discipline arcanist");
			server.runCommand("execute as @p run afterworld learn all");
			context.waitTicks(40);
			context.takeScreenshot("01-hud");

			for (int tab = 0; tab < 5; tab++) {
				int t = tab;
				context.runOnClient(mc -> JournalScreen.open(t));
				context.waitTicks(15);
				context.takeScreenshot("02-journal-tab" + tab);
			}
			context.setScreen(() -> null);
			context.waitTicks(5);

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
			server.runCommand("execute as @p run afterworld site anomaly weightlessness");
			context.waitTicks(80);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("03-site-anomaly");

			context.getInput().pressKey(AfterworldClient.SLOT_KEYS[3]);
			context.waitTicks(10);
			context.takeScreenshot("04-cast");

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 10");
			server.runCommand("execute as @p run afterworld site shrine");
			context.waitTicks(60);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("05-site-shrine");

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 90 5");
			server.runCommand("execute as @p run afterworld site cathedral");
			context.waitTicks(120);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("06-site-cathedral");
		}
	}
}
