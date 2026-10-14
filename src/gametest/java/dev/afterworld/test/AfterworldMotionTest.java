package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Creatures in motion (see client.render.Liveliness): a pack running at the traveller (the lean
 * into the run, the bob of each stride), and a pair standing, one of them just struck (the flinch).
 * Photographs a few frames of each.
 */
public class AfterworldMotionTest implements FabricClientGameTest {
	private static final String[] PACK = {"dusk_hound", "mire_shambler", "rootling_sentinel", "ashen_husk", "gearhound", "ice_crawler"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			// A flat stage in the sky.
			server.runCommand("gamemode creative @p");
			server.runCommand("tp @p 0 205 0");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender(false, 1200);
			server.runCommand("fill -20 199 -20 20 199 30 grass_block");
			server.runCommand("fill -20 200 -20 20 210 30 air");
			context.waitTicks(10);
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 999999 4 true");
			for (int i = 0; i < PACK.length; i++) {
				server.runCommand(String.format("summon afterworld:%s %d 200 %d", PACK[i], -10 + i * 4, 24));
			}
			server.runCommand("tp @p 0 200 -6 facing 0 200.5 24");
			context.waitTicks(30);
			for (int f = 0; f < 4; f++) {
				context.waitTicks(3);
				context.takeScreenshot("motion-pack-" + f);
			}
			server.runCommand("kill @e[type=!player]");
			// Two standing still; strike one.
			server.runCommand("summon afterworld:mire_shambler -1.5 200 3 {NoAI:1b,Rotation:[180f,0f]}");
			server.runCommand("summon afterworld:mire_shambler 1.5 200 3 {NoAI:1b,Rotation:[180f,0f]}");
			server.runCommand("tp @p 0 200.5 -3 facing 0 201 3");
			context.waitTicks(20);
			context.takeScreenshot("motion-pair-still");
			server.runCommand("damage @e[type=afterworld:mire_shambler,x=1.5,y=200,z=3,distance=..0.6,limit=1] 1");
			context.waitTicks(2);
			context.takeScreenshot("motion-pair-flinch");
			Afterworld.LOG.info("MOTIONTEST done");
		}
	}
}
