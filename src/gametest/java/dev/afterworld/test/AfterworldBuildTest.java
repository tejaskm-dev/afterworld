package dev.afterworld.test;

import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Photographs builds: each one in AW_BUILDS ("name:sink:size,...") is placed on the flat test
 * world and shot from three sides by day and once by night.
 */
public class AfterworldBuildTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode spectator @p");
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.gui.hud.toggle();
			});
			String[] builds = System.getenv().getOrDefault("AW_BUILDS", "moon_gazebo:3:32").split(",");
			int x = 0;
			for (String spec : builds) {
				String[] p = spec.split(":");
				String name = p[0];
				int sink = Integer.parseInt(p[1]);
				int size = Integer.parseInt(p[2]);
				int tall = p.length > 3 ? Integer.parseInt(p[3]) : size;
				server.runCommand("time set 6000");
				server.runCommand("tp @p " + x + " -60 0");
				context.waitTicks(10);
				server.runCommand("execute positioned " + x + " -60 0 run afterworld build " + name + " " + sink);
				double r = Math.max(size, tall * 1.05) + 6;
				int eye = -60 + tall / 3;
				shoot(context, server, name + "-front", x, eye, r, 0, tall);
				shoot(context, server, name + "-corner", x, eye + 4, r, 45, tall);
				shoot(context, server, name + "-side", x, eye - 4, Math.max(size, tall * 0.6) * 0.8 + 4, 100, tall);
				server.runCommand("time set 18000");
				shoot(context, server, name + "-night", x, eye, r, 20, tall);
				x += 400;
			}
		}
	}

	private static void shoot(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			String shot, int cx, int eye, double r, double angle, int size) {
		double a = Math.toRadians(angle);
		double px = cx + Math.sin(a) * r;
		double pz = Math.cos(a) * r;
		server.runCommand("tp @p " + px + " " + eye + " " + pz + " facing " + cx + " " + (-60 + size * 0.42) + " 0");
		context.waitTicks(40);
		context.takeScreenshot("build-" + shot);
	}
}
