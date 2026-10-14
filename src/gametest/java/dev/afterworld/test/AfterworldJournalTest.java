package dev.afterworld.test;

import dev.afterworld.client.screen.JournalScreen;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The journal as a book: every section, plus a page turn caught mid-sweep. */
public class AfterworldJournalTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.COURT_MOURNER, Discipline.KEEPER);
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("execute as @p run afterworld rumour");
			server.runCommand("execute as @p run afterworld rumour");
			sp.getConnection().waitForChunksRender();
			for (int tab = 0; tab < 5; tab++) {
				int t = tab;
				context.runOnClient(mc -> JournalScreen.open(t));
				context.waitTicks(20);
				context.takeScreenshot("journal-" + tab);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			server.runCommand("afterworld learn all");
			context.waitTicks(10);
			context.runOnClient(mc -> mc.gui.setScreen(new dev.afterworld.client.screen.EdictWheelScreen()));
			context.waitTicks(4);
			context.getInput().setCursorPos(context.computeOnClient(mc -> mc.getWindow().getScreenWidth() / 2.0), context.computeOnClient(mc -> mc.getWindow().getScreenHeight() * 0.2));
			context.waitTicks(10);
			context.takeScreenshot("wheel");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}
}
