package dev.afterworld.test;

import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.site.Builds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Builds each grand structure on a flat world and photographs it outside and in. */
public class AfterworldGrandTest implements FabricClientGameTest {
	/** kind, x, z, then an interior camera: dx, dy, dz, look dx, dy, dz (relative to the site, ground = -60). */
	private static final Object[][] GRAND = {
		{"bastion", 0, 0, 0, 3, -1, 0, 2, 9},
		{"observatory", 160, 0, 6, 3, -6, 0, 0, 2},
		{"mausoleum", 320, 0, 0, 4, -4, 0, 4, 10},
		{"containment", 0, 160, 0, 3, -6, -6, 2, 0},
		{"archive", 160, 160, 9, 6, -9, 0, 18, 0},
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 5000");
			server.runCommand("gamemode spectator @p");
			for (Object[] g : GRAND) {
				String kind = (String) g[0];
				int x = (Integer) g[1];
				int z = (Integer) g[2];
				server.runCommand("tp @p " + (x + 40) + " -30 " + (z - 50) + " facing " + x + " -52 " + z);
				context.waitTicks(30);
				sp.getConnection().waitForChunksRender();
				server.runCommand("afterworld siteat " + kind + " " + x + " " + z);
				context.waitTicks(20);
				server.waitFor(s -> !Builds.busy(), 1200);
				context.waitTicks(40);
				sp.getConnection().waitForChunksRender();
				context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
				context.takeScreenshot("grand-" + kind);
				server.runCommand("tp @p " + (x - 42) + " -38 " + (z + 38) + " facing " + x + " -50 " + z);
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("grand-" + kind + "-back");
				int ix = x + (Integer) g[3];
				int iy = -60 + (Integer) g[4];
				int iz = z + (Integer) g[5];
				server.runCommand("tp @p " + ix + " " + iy + " " + iz + " facing " + (x + (Integer) g[6]) + " " + (-60 + (Integer) g[7]) + " " + (z + (Integer) g[8]));
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("grand-" + kind + "-inside");
			}
		}
	}
}
