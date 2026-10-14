package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import java.lang.management.ManagementFactory;
import net.minecraft.client.InactivityFpsLimit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Performance probe: flies across fresh terrain for a while, then hovers, logging frames per
 * second, server tick time and process CPU load once a second. Settings come from environment
 * variables so one build can measure several setups: AW_PERF_W/AW_PERF_H (window size),
 * AW_PERF_RD (render distance, default 6), AW_PERF_FLY and AW_PERF_HOVER (seconds per phase).
 */
public class AfterworldPerfTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		int rd = env("AW_PERF_RD", 6);
		int fly = env("AW_PERF_FLY", 40);
		int hover = env("AW_PERF_HOVER", 15);
		if (env("AW_PERF_W", 0) > 0) {
			context.getInput().resizeWindow(env("AW_PERF_W", 0), env("AW_PERF_H", 0));
		}
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.WAYFARER);
			var server = sp.getServer();
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(rd);
				mc.options.framerateLimit().set(260);
				mc.options.enableVsync().set(false);
				// No input arrives during the test, so keep the AFK frame cap from kicking in.
				mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			});
			server.runOnServer(s -> s.getPlayerList().setViewDistance(rd));
			server.runCommand("time set 1000");
			String size = context.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
			Afterworld.LOG.info("PERFTEST framebuffer {} rd {}", size, rd);
			// The town first, where a traveller spends most of their time: standing in the plaza,
			// turning slowly round, its keepers about their business.
			int town = env("AW_PERF_TOWN", 20);
			var spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			context.waitTicks(100);
			for (int sec = 0; sec < town; sec++) {
				for (int t = 0; t < 20; t++) {
					float yaw = (sec * 20 + t) * 360.0F / Math.max(1, town * 20);
					server.runCommand(String.format("tp @p %d %d %d %.1f 5", spawn.getX(), spawn.getY() + 1, spawn.getZ(), yaw));
					context.waitTick();
				}
				report(context, server, "town", sec);
			}
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p 0 140 0 -90 20");
			context.waitTicks(200);
			// Fly east across new ground: 1.5 blocks a tick is a brisk horse.
			for (int sec = 0; sec < fly; sec++) {
				int x0 = sec * 30;
				for (int t = 0; t < 20; t++) {
					server.runCommand("tp @p " + (x0 + t * 1.5) + " 140 0 -90 20");
					context.waitTick();
				}
				report(context, server, "fly", sec);
			}
			for (int sec = 0; sec < hover; sec++) {
				context.waitTicks(20);
				report(context, server, "hover", sec);
			}
		}
	}

	private static int env(String key, int fallback) {
		String v = System.getenv(key);
		return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
	}

	private static void report(ClientGameTestContext context, TestServerContext server, String phase, int sec) {
		int fps = context.computeOnClient(mc -> mc.getFps());
		String biome = server.computeOnServer(s -> {
			var p = s.getPlayerList().getPlayers().get(0);
			return s.overworld().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		});
		double mspt = server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1_000_000.0);
		double cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuLoad() * 100 : -1;
		Afterworld.LOG.info("PERFTEST {} {} fps={} mspt={} cpu={}% biome={}", phase, sec, fps, String.format("%.1f", mspt), String.format("%.0f", cpu), biome);
	}
}
