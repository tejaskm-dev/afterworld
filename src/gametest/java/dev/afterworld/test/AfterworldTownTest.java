package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.world.AfterworldData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The town a new world wakes in: where it stands, where its keepers stand (and how far apart),
 * and how it looks from the gate, from the air, in the plaza and from the country outside.
 */
public class AfterworldTownTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> mc.options.renderDistance().set(12));
			var server = sp.getServer();
			server.runOnServer(s -> s.getPlayerList().setViewDistance(12));
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("time set 3000");
			BlockPos center = server.computeOnServer(s -> BlockPos.of(AfterworldData.get(s).campCenter));
			String keepers = server.computeOnServer(s -> {
				StringBuilder b = new StringBuilder();
				for (Npc npc : Npc.VALUES) {
					BlockPos home = Npcs.home(s, npc);
					if (home != null && home.distManhattan(center) < 200) {
						b.append(npc.id).append('@').append((int) Math.sqrt(home.distSqr(center))).append("m ");
					}
				}
				return b.toString();
			});
			Afterworld.LOG.info("TOWNTEST center {} keepers from the waystone: {}", center.toShortString(), keepers);
			context.waitTicks(100);
			sp.getConnection().waitForChunksRender(false, 2400);
			context.waitTicks(20);
			context.takeScreenshot("town-arrival");
			server.runCommand("gamemode spectator @p");
			// The town turns with the seed: aim from the gate and from where each keeper stands.
			BlockPos gate = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			double gx = gate.getX() - center.getX();
			double gz = gate.getZ() - center.getZ();
			double gl = Math.max(1, Math.hypot(gx, gz));
			int ux = (int) Math.round(gx / gl * 100);
			int uz = (int) Math.round(gz / gl * 100);
			int cx = center.getX();
			int cy = center.getY();
			int cz = center.getZ();
			shot(context, sp, "town-aerial", cx + ux * 130 / 100, cy + 80, cz + uz * 130 / 100, cx, cy, cz);
			shot(context, sp, "town-aerial-side", cx - uz * 120 / 100, cy + 60, cz + ux * 120 / 100, cx, cy, cz);
			shot(context, sp, "town-gate-outside", cx + ux * 100 / 100, cy + 14, cz + uz * 100 / 100, gate.getX(), cy + 4, gate.getZ());
			shot(context, sp, "town-street", cx + ux * 40 / 100, cy + 3, cz + uz * 40 / 100, cx, cy + 6, cz);
			shot(context, sp, "town-plaza", cx + ux * 16 / 100 - uz * 12 / 100, cy + 4, cz + uz * 16 / 100 + ux * 12 / 100, cx, cy + 4, cz);
			shot(context, sp, "town-ring", cx - uz * 112 / 100 + ux * 40 / 100, cy + 18, cz + ux * 112 / 100 + uz * 40 / 100, cx - uz * 70 / 100, cy, cz + ux * 70 / 100);
			for (Npc npc : new Npc[] {Npc.MARTA, Npc.BRAM, Npc.WREN}) {
				BlockPos home = server.computeOnServer(s -> Npcs.home(s, npc));
				if (home == null) {
					continue;
				}
				double hx = home.getX() - cx;
				double hz = home.getZ() - cz;
				double hl = Math.max(1, Math.hypot(hx, hz));
				int px = (int) Math.round(home.getX() - hx / hl * 9);
				int pz = (int) Math.round(home.getZ() - hz / hl * 9);
				shot(context, sp, "town-" + npc.id, px, home.getY() + 3, pz, home.getX(), home.getY() + 2, home.getZ());
			}
			Afterworld.LOG.info("TOWNTEST done");
		}
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		sp.getServer().runCommand("tp @p " + x + " " + y + " " + z + " facing " + lx + " " + ly + " " + lz);
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(10);
		context.takeScreenshot(name);
	}
}
