package dev.afterworld.test;

import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Lines up every creature for a portrait, by day and by night, plus a crow in flight. */
public class AfterworldCreatureTest implements FabricClientGameTest {
	private static final String[] CREATURES = {"lantern_moth", "crow", "mossback", "hollow_stag", "clockwork_sentry", "rule_wraith", "wisp"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @p");
			for (int i = 0; i < CREATURES.length; i++) {
				String name = CREATURES[i];
				boolean flier = name.equals("lantern_moth") || name.equals("rule_wraith") || name.equals("wisp");
				int x = -12 + i * 4;
				String y = flier ? "-59.2" : "-60";
				server.runCommand("summon afterworld:" + name + " " + x + " " + y + " 0 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]" + (flier ? ",NoGravity:1b" : "") + "}");
			}
			server.runCommand("summon afterworld:crow -2 -55 4 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Rotation:[30f,0f]}");
			server.runCommand("tp @p 0 -56.5 13 facing 0 -59 0");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("creatures-day");
			server.runCommand("tp @p -10 -58.6 5 facing -10 -59.4 0");
			context.waitTicks(20);
			context.takeScreenshot("creatures-small");
			server.runCommand("tp @p 10 -58 6 facing 9 -59 0");
			context.waitTicks(20);
			context.takeScreenshot("creatures-spectral");
			server.runCommand("time set 18000");
			server.runCommand("tp @p 0 -56.5 13 facing 0 -59 0");
			context.waitTicks(30);
			context.takeScreenshot("creatures-night");
		}
	}
}
