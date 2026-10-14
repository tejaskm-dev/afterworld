package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The body in motion, played with the keys: up a sheer wall hand over hand and over the top, a
 * narrow stairwell climbed with the jump key held and mashed (nothing may throw the player off),
 * and a sprinting slide. Photographs each from behind and in front.
 */
public class AfterworldMovesTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			var input = context.getInput();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			// A test yard high above the ground: a 12-block wall with a ledge on top, and a stairwell.
			BlockPos base = server.computeOnServer(s -> s.overworld().getRespawnData().pos().offset(40, 40, 40));
			int x = base.getX();
			int y = base.getY();
			int z = base.getZ();
			server.runCommand(String.format("tp @p %d %d %d", x, y + 30, z));
			server.runCommand("effect give @p slow_falling 20 0 true");
			context.waitTicks(40);
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 6, y - 1, z - 6, x + 6, y - 1, z + 12));
			server.runCommand(String.format("fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 6, y + 16, z + 12));
			// The wall (north side, z - 3), with a terrace on top.
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 4, y, z - 6, x + 4, y + 11, z - 3));
			// The stairwell: a two-wide corridor of one-block steps, walled on both sides.
			for (int i = 0; i < 6; i++) {
				server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x + 3, y, z + 2 + i, x + 4, y + i, z + 2 + i));
			}
			server.runCommand(String.format("fill %d %d %d %d %d %d deepslate_bricks", x + 2, y, z + 1, x + 2, y + 10, z + 9));
			server.runCommand(String.format("fill %d %d %d %d %d %d deepslate_bricks", x + 5, y, z + 1, x + 5, y + 10, z + 9));
			context.waitTicks(10);

			// ---- the climb
			server.runCommand("effect clear @p slow_falling");
			server.runCommand(String.format("tp @p %d.5 %d %d.68 facing %d.5 %d %d.5", x, y, z - 2, x, y + 4, z - 6));
			context.waitTicks(20);
			input.lookAt(180.0F, 8.0F);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			double y0 = context.computeOnClient(mc -> mc.player.getY());
			input.holdKey(o -> o.keyUp);
			context.waitTicks(5);
			input.holdKeyFor(o -> o.keyJump, 2);
			context.waitTicks(28);
			String mid = context.computeOnClient(AfterworldMovesTest::diag);
			Afterworld.LOG.info("MOVESTEST climbing: {}", mid);
			context.takeScreenshot("moves-climb-back");
			// On up and over the top; let go once standing on it.
			for (int i = 0; i < 100; i++) {
				context.waitTicks(2);
				String d = context.computeOnClient(AfterworldMovesTest::diag);
				if (i % 3 == 0 || d.contains("y=12")) {
					Afterworld.LOG.info("MOVESTEST trace {}: {}", i, d);
				}
				boolean on = context.computeOnClient(mc -> mc.player.onGround() && mc.player.getY() >= y + 11.9);
				if (on) {
					break;
				}
			}
			input.releaseKey(o -> o.keyUp);
			context.waitTicks(10);
			Vec3 top = context.computeOnClient(mc -> mc.player.position());
			Afterworld.LOG.info("MOVESTEST climb: started at y {}, ended at {} (wall top y {})", y0, top, y + 12);
			context.takeScreenshot("moves-on-top");
			check(top.y >= y + 11.9 && top.z < z - 2.0, "climbed the 12-block wall and pulled up onto it");

			// ---- the mantle: a two-block ledge, jump, then jump again in the air to haul up
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 6, y, z + 6, x - 3, y + 1, z + 12));
			server.runCommand(String.format("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5", x - 5, y, z + 4, x - 5, y + 1, z + 7));
			context.waitTicks(15);
			input.lookAt(0.0F, 10.0F);
			input.holdKey(o -> o.keyUp);
			context.waitTicks(4);
			input.holdKeyFor(o -> o.keyJump, 2);
			context.waitTicks(4);
			input.holdKeyFor(o -> o.keyJump, 2);
			context.waitTicks(16);
			input.releaseKey(o -> o.keyUp);
			context.waitTicks(6);
			Vec3 ledge = context.computeOnClient(mc -> mc.player.position());
			Afterworld.LOG.info("MOVESTEST mantle: ended at {} (ledge top y {})", ledge, y + 2);
			check(ledge.y >= y + 1.9, "hauled up onto the two-block ledge with a second press of jump");

			// ---- the stairwell: hold forward, mash jump; nothing may throw the player sideways
			server.runCommand(String.format("tp @p %d.0 %d %d.5 facing %d.0 %d %d.5", x + 4, y, z + 1, x + 4, y + 3, z + 9));
			context.waitTicks(20);
			double minX = 99999;
			double maxX = -99999;
			input.holdKey(o -> o.keyUp);
			for (int i = 0; i < 24; i++) {
				input.holdKeyFor(o -> o.keyJump, 2);
				context.waitTicks(1);
				double px = context.computeOnClient(mc -> mc.player.getX());
				minX = Math.min(minX, px);
				maxX = Math.max(maxX, px);
			}
			input.releaseKey(o -> o.keyUp);
			context.waitTicks(10);
			Vec3 up = context.computeOnClient(mc -> mc.player.position());
			Afterworld.LOG.info("MOVESTEST stairwell: x stayed within {}..{} (walls at {} and {}), ended at {}", minX, maxX, x + 2, x + 5, up);
			check(minX > x + 3.2 && maxX < x + 4.8 && up.y >= y + 4, "the stairwell kept the player in, and up the steps");

			// ---- the slide: sprint, then crouch
			server.runCommand(String.format("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5", x - 5, y, z - 1, x + 5, y, z - 1));
			context.waitTicks(20);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			input.holdKey(o -> o.keyUp);
			input.holdKey(o -> o.keySprint);
			context.waitTicks(12);
			input.holdKey(o -> o.keyShift);
			context.waitTicks(3);
			context.takeScreenshot("moves-slide");
			context.waitTicks(10);
			input.releaseKey(o -> o.keyShift);
			input.releaseKey(o -> o.keySprint);
			input.releaseKey(o -> o.keyUp);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(10);
			Afterworld.LOG.info("MOVESTEST done");
		}
	}

	private static String diag(net.minecraft.client.Minecraft mc) {
		var p = mc.player;
		return String.format("y=%.2f z=%.2f ground=%s vy=%.3f stamina=%.1f screen=%s pose=%s fwd=%s", p.getY(), p.getZ(), p.onGround(), p.getDeltaMovement().y,
			dev.afterworld.client.ClientState.stamina, mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName(),
			dev.afterworld.client.ClientMoves.pose().kind(), p.input.keyPresses.forward());
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("MOVESTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
