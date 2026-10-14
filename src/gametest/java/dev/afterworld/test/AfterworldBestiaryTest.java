package dev.afterworld.test;

import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Portraits of the second wave: the monsters in a row, the wildlife in another, and a skywhale overhead. */
public class AfterworldBestiaryTest implements FabricClientGameTest {
	private static final String[] MONSTERS = {"ashen_husk", "shardling", "bog_lurker", "echo_stalker", "gearhound", "rime_wight"};
	private static final String[] WILD = {"emberfox", "rimehorn", "petal_sprite", "glimmerfin", "mirror_ray"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @p");
			for (int i = 0; i < MONSTERS.length; i++) {
				String name = MONSTERS[i];
				boolean flier = name.equals("rime_wight");
				summon(server, name, -12.5 + i * 5, flier ? -59.0 : -60.0, 0, flier);
			}
			for (int i = 0; i < WILD.length; i++) {
				String name = WILD[i];
				boolean flier = !name.equals("emberfox") && !name.equals("rimehorn");
				summon(server, name, -10.5 + i * 5, flier ? -59.3 : -60.0, 12, flier);
			}
			server.runCommand("tp @p 0 -55.5 -14 facing 0 -59 0");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot("bestiary-monsters");
			server.runCommand("tp @p 0 -55.5 26 facing 0 -59 12");
			context.waitTicks(20);
			context.takeScreenshot("bestiary-wild");
			server.runCommand("tp @p -2 -59 4.5 facing -2.5 -59 0");
			context.waitTicks(20);
			context.takeScreenshot("bestiary-close");
			server.runCommand("kill @e[type=!player]");
			server.runCommand("summon afterworld:skywhale 0 -40 30 {NoAI:1b,Rotation:[90f,0f]}");
			server.runCommand("tp @p -24 -55 0 facing 0 -40 30");
			context.waitTicks(40);
			context.takeScreenshot("bestiary-whale");
			server.runCommand("time set 18000");
			context.waitTicks(30);
			context.takeScreenshot("bestiary-whale-night");
		}
	}

	private static void summon(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String name, double x, double y, double z, boolean flier) {
		server.runCommand("summon afterworld:" + name + " " + x + " " + y + " " + z + " {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]"
			+ (flier ? ",NoGravity:1b" : "") + "}");
	}
}
