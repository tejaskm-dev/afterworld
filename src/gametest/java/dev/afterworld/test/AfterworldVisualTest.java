package dev.afterworld.test;

import dev.afterworld.client.screen.JournalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Builds every site and summons every boss on a flat world, photographing each one. */
public class AfterworldVisualTest implements FabricClientGameTest {
	private static final String[][] SITES = {
		{"shrine", "0", "60"}, {"anomaly", "60", "60"}, {"lair", "120", "60"}, {"cache", "180", "60"}, {"caravan", "240", "60"},
		{"unremembered", "0", "180"}, {"cathedral", "120", "180"}
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, dev.afterworld.player.Origin.WANDERER, dev.afterworld.player.Discipline.VANGUARD);
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set 6000");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamemode spectator @p");
			for (String[] site : SITES) {
				int x = Integer.parseInt(site[1]);
				int z = Integer.parseInt(site[2]);
				int radius = site[0].equals("unremembered") || site[0].equals("cathedral") || site[0].equals("vault") ? 34 : 18;
				server.runCommand("tp @p " + (x + radius) + " " + (-60 + radius * 0.8) + " " + (z + radius) + " facing " + x + " -58 " + z);
				context.waitTicks(30);
				sp.getConnection().waitForChunksRender();
				server.runCommand("afterworld siteat " + site[0] + " " + x + " " + z);
				context.waitTicks(60);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("site-" + site[0]);
			}
			// Inside the cathedral nave.
			server.runCommand("tp @p 120 -59 168 facing 120 -55 190");
			context.waitTicks(30);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("site-cathedral-inside");

			server.runCommand("tp @p 400 -60 0 facing 400 -58 14");
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			server.runCommand("summon afterworld:pilgrim 392 -60 18 {Rotation:[180f,0f]}");
			server.runCommand("summon afterworld:bell_ringer 401 -60 12 {Rotation:[180f,0f]}");
			server.runCommand("summon afterworld:king_of_seconds 407 -60 10 {Rotation:[200f,0f]}");
			context.waitTicks(40);
			context.takeScreenshot("bosses");
			server.runCommand("tp @p 398 -57 8 facing 392 -57 18");
			context.waitTicks(10);
			context.takeScreenshot("boss-pilgrim");
			server.runCommand("tp @p 401 -58 7 facing 401 -58 12");
			context.waitTicks(10);
			context.takeScreenshot("boss-ringer");
			server.runCommand("tp @p 407 -58 6 facing 407 -58.3 10");
			context.waitTicks(10);
			context.takeScreenshot("boss-king");

			server.runCommand("tp @p 500 -60 0 facing 500 -59 6");
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			server.runCommand("execute as @p run afterworld learn hunger");
			server.runCommand("execute as @p run afterworld cast hunger");
			server.runCommand("summon zombie 500 -60 6 {Rotation:[180f,0f]}");
			server.runCommand("summon skeleton 502 -60 7 {Rotation:[180f,0f]}");
			context.waitTicks(5);
			server.runCommand("damage @e[type=zombie,limit=1,sort=nearest] 200 minecraft:player_attack by @p");
			server.runCommand("damage @e[type=skeleton,limit=1,sort=nearest] 200 minecraft:player_attack by @p");
			context.waitTicks(30);
			context.takeScreenshot("echoes");

			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run afterworld rumour");
			server.runCommand("execute as @p run afterworld rumour");
			context.waitTicks(10);
			context.runOnClient(mc -> JournalScreen.open(1));
			context.waitTicks(15);
			context.takeScreenshot("journal-rumours");
			context.runOnClient(mc -> JournalScreen.open(3));
			context.waitTicks(15);
			context.takeScreenshot("journal-world");
			context.setScreen(() -> null);
		}
	}
}
