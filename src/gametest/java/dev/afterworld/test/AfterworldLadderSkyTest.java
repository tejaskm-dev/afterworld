package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.dungeon.Ladder;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The Ladder's sky, photographed on a few floors: looking out over the edge, and up. */
public class AfterworldLadderSkyTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("effect give @p resistance 999999 4 true");
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			for (int floor : new int[] {5, 12, 25}) {
				server.runOnServer(s -> Ladder.jump(s.getPlayerList().getPlayers().get(0), floor));
				context.waitFor(mc -> mc.level != null && mc.level.dimension() == Ladder.DIMENSION, 200);
				context.waitTicks(30);
				sp.getConnection().waitForChunksRender(false, 600);
				context.runOnClient(mc -> mc.player.setXRot(-8.0F));
				context.waitTicks(10);
				context.takeScreenshot("ladder-sky-" + floor + "-out");
				context.runOnClient(mc -> mc.player.setXRot(-60.0F));
				context.waitTicks(5);
				context.takeScreenshot("ladder-sky-" + floor + "-up");
			}
			Afterworld.LOG.info("LADDERSKYTEST done");
		}
	}
}
