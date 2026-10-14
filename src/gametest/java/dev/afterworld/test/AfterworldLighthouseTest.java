package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Dark Lighthouse, built high in the air: walked from the door to the lamp room on the stair
 * alone (forward only, no jumping, auto-jump off), then the lamp's four braziers lit, which must
 * shatter the soot glass and open the keeper's strongbox. Photographs the tower, the stair and
 * the burning lamp.
 */
public class AfterworldLighthouseTest implements FabricClientGameTest {
	// The builder's numbers (tools/wonders/sites2.py, lighthouse()): the tower's centre in the grid,
	// the entry hall's floor level, the watch room and the lamp room.
	private static final int CX = 19;
	private static final int CZ = 24;
	private static final int TOP = 4;
	private static final int WATCH = TOP + 27;
	private static final int LAMP = TOP + 36;
	private static final int SINK = 3;

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
			server.runCommand("time set 5000");
			server.runCommand("weather clear");
			context.runOnClient(mc -> {
				mc.options.autoJump().set(false);
				mc.gui.toastManager().clear();
			});
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());
			int ax = spawn.getX() + 160;
			int az = spawn.getZ() + 160;
			int ay = 170;
			int ox = ((ax >> 4) << 4) - 16;
			int oy = ay - SINK;
			int oz = ((az >> 4) << 4) - 16;
			int cx = ox + CX;
			int cz = oz + CZ;
			server.runCommand("gamemode spectator @p");
			server.runCommand(String.format("tp @p %d %d %d", ax + 30, ay + 22, az + 34));
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender(false, 2400);
			server.runCommand(String.format("execute positioned %d %d %d run afterworld build lighthouse %d", ax, ay, az, SINK));
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender(false, 2400);
			// The tower from the sea side.
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", cx + 34, oy + 26, cz + 30, cx, oy + 26, cz));
			context.waitTicks(30);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.takeScreenshot("lighthouse-outside");
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", cx - 20, oy + 8, cz + 26, cx, oy + 22, cz));
			context.waitTicks(20);
			context.takeScreenshot("lighthouse-from-the-rocks");
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});

			// ---- up the stair, on foot
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 600 4 true");
			server.runCommand("effect give @p saturation 600 0 true");
			server.runCommand(String.format("tp @p %.1f %d %.1f facing %.1f %d %.1f", cx + 0.5, oy + TOP, cz + 3.5, cx + 0.5, oy + TOP, cz - 2.5));
			context.waitTicks(20);
			context.runOnClient(mc -> mc.player.setXRot(-55.0F));
			context.waitTicks(5);
			context.takeScreenshot("lighthouse-stairwell-up");
			context.runOnClient(mc -> mc.player.setXRot(10.0F));
			input.holdKey(o -> o.keyUp);
			int arrived = -1;
			double bestY = -1;
			int stuck = 0;
			boolean midShot = false;
			for (int i = 0; i < 1200; i++) {
				Vec3 p = context.computeOnClient(mc -> mc.player.position());
				double ang = Math.atan2(p.z - (cz + 0.5), p.x - (cx + 0.5));
				double target = ang + Math.toRadians(30);
				double tx = cx + 0.5 + Math.cos(target) * 4.6;
				double tz = cz + 0.5 + Math.sin(target) * 4.6;
				float yaw = (float) Math.toDegrees(Math.atan2(-(tx - p.x), tz - p.z));
				context.runOnClient(mc -> {
					mc.player.setYRot(yaw);
					mc.player.setYHeadRot(yaw);
				});
				context.waitTick();
				if (p.y > bestY + 0.01) {
					bestY = p.y;
					stuck = 0;
				} else if (++stuck > 100) {
					Afterworld.LOG.info("LIGHTHOUSETEST stuck at {} (angle {}), best y {}", p, Math.toDegrees(ang), bestY);
					break;
				}
				if (i % 40 == 0 || i < 80 && i % 4 == 0) {
					String under = context.computeOnClient(mc -> {
						BlockPos b = mc.player.blockPosition();
						return mc.level.getBlockState(b).toString() + " / " + mc.level.getBlockState(b.below()).toString();
					});
					Afterworld.LOG.info("LIGHTHOUSETEST climb {}: {} angle {} r {} on {}", i, p, (int) Math.toDegrees(ang),
						String.format("%.2f", Math.hypot(p.x - (cx + 0.5), p.z - (cz + 0.5))), under);
				}
				if (!midShot && p.y >= oy + TOP + 15) {
					midShot = true;
					context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
					context.waitTicks(2);
					context.takeScreenshot("lighthouse-stair-climbing");
					context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				}
				if (p.y >= oy + LAMP + 0.9) {
					arrived = i;
					break;
				}
			}
			input.releaseKey(o -> o.keyUp);
			context.waitTicks(10);
			Vec3 end = context.computeOnClient(mc -> mc.player.position());
			Afterworld.LOG.info("LIGHTHOUSETEST climb: arrived after {} ticks at {} (lamp room floor y {})", arrived, end, oy + LAMP + 1);
			check(arrived >= 0, "walked up the stair from the door to the lamp room without a jump");

			// ---- the lamp: dark until all four braziers burn
			server.runCommand(String.format("tp @p %.1f %d %.1f facing %.1f %d %.1f", cx + 4.5, oy + LAMP + 1, cz + 0.5, cx + 0.5, oy + LAMP + 2, cz + 0.5));
			context.waitTicks(20);
			context.takeScreenshot("lighthouse-lamp-dark");
			boolean dark = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(cx + 2, oy + LAMP + 2, cz)).is(Blocks.COAL_BLOCK));
			check(dark, "the great lamp starts sooted dark");
			server.runCommand("time set 18000");
			int[][] fires = {{2, 2}, {-2, 2}, {-2, -2}, {2, -2}};
			for (int i = 0; i < fires.length; i++) {
				server.runCommand(String.format("setblock %d %d %d campfire[lit=true]", cx + fires[i][0], oy + LAMP + 1, cz + fires[i][1]));
				context.waitTicks(8);
				if (i < 3) {
					boolean still = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(cx + 2, oy + LAMP + 2, cz)).is(Blocks.COAL_BLOCK));
					check(still, "the lamp stays dark with only some braziers lit");
				}
			}
			context.waitTicks(20);
			boolean lit = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(cx + 2, oy + LAMP + 2, cz)).isAir()
				&& s.overworld().getBlockState(new BlockPos(cx, oy + LAMP + 4, cz)).isAir());
			boolean open = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(cx - 4, oy + WATCH + 1, cz)).isAir()
				&& s.overworld().getBlockState(new BlockPos(cx - 5, oy + WATCH + 1, cz)).is(Blocks.CHEST));
			context.takeScreenshot("lighthouse-lamp-lit");
			check(lit, "lighting all four braziers knocked the soot off the lamp");
			check(open, "and opened the keeper's strongbox");
			// The lamp burning in the night, from out on the water.
			server.runCommand("gamemode spectator @p");
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", cx + 46, oy + 30, cz + 40, cx, oy + LAMP, cz));
			context.waitTicks(40);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.takeScreenshot("lighthouse-night");
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			server.runCommand("gamemode survival @p");
			Afterworld.LOG.info("LIGHTHOUSETEST done");
		}
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("LIGHTHOUSETEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
