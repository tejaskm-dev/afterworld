package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.block.LadderStoneBlock;
import dev.afterworld.client.screen.LadderScreen;
import dev.afterworld.dungeon.Ladder;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The Warden's Ladder, climbed: the gate's window opens and lets the traveller in, floor one's
 * waves come and go and leave a chest and a lit rung, the rung climbs on, a landing has Rook and
 * its rungs lit, an echo of a Warden falls without counting as the Warden, and a mortal blow throws
 * the climber out at the gate, alive, able to take the climb up again from floor eleven.
 */
public class AfterworldLadderTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("effect give @p resistance 999999 3 true");
			context.runOnClient(mc -> mc.gui.toastManager().clear());

			// ---- the gate
			BlockPos here = server.computeOnServer(s -> player(s).blockPosition());
			server.runCommand(String.format("execute positioned %d %d %d run afterworld build ladder_gate 3", here.getX() + 6, here.getY(), here.getZ() + 6));
			context.waitTicks(10);
			BlockPos gate = server.computeOnServer(s -> {
				for (BlockPos q : BlockPos.betweenClosed(here.offset(-30, -6, -30), here.offset(40, 12, 40))) {
					if (s.overworld().getBlockState(q).is(AwBlocks.LADDER_STONE)) {
						return q.immutable();
					}
				}
				return null;
			});
			check(gate != null, "the gate stands, with its stone");
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", gate.getX(), gate.getY(), gate.getZ() + 3, gate.getX(), gate.getY(), gate.getZ()));
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Ladder.use(p.level(), p, gate, p.level().getBlockState(gate));
			});
			context.waitFor(mc -> mc.gui.screen() instanceof LadderScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot("ladder-gate-window");
			context.runOnClient(mc -> ((LadderScreen) mc.gui.screen()).chooseForTest(0));

			// ---- floor one
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Ladder.DIMENSION, 200);
			sp.getConnection().waitForChunksRender(false, 600);
			context.waitTicks(20);
			context.takeScreenshot("ladder-floor1-arrive");
			waitPhase(context, server, "FIGHTING", 200);
			context.waitTicks(30);
			int foes = server.computeOnServer(s -> s.getLevel(Ladder.DIMENSION).getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				player(s).getBoundingBox().inflate(48), m -> m.entityTags().contains("afterworld:ladder")).size());
			Afterworld.LOG.info("LADDERTEST floor 1 wave 1: {} creatures", foes);
			check(foes >= 3, "floor one's first wave came");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.runOnClient(mc -> mc.player.setXRot(18.0F));
			context.waitTicks(4);
			context.takeScreenshot("ladder-floor1-wave");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (int i = 0; i < 12 && !phase(server).equals("CLEARED"); i++) {
				server.runOnServer(s -> Ladder.clearWave(player(s)));
				context.waitTicks(70);
			}
			check(phase(server).equals("CLEARED"), "every wave of floor one was beaten");
			BlockPos chest = server.computeOnServer(s -> Ladder.chest(player(s)));
			BlockPos up = server.computeOnServer(s -> Ladder.upRung(player(s)));
			boolean chestThere = server.computeOnServer(s -> chest != null && s.getLevel(Ladder.DIMENSION).getBlockState(chest).is(Blocks.CHEST));
			boolean lit = server.computeOnServer(s -> up != null && s.getLevel(Ladder.DIMENSION).getBlockState(up).getValue(LadderStoneBlock.ACTIVE));
			check(chestThere, "a chest stands on the dais");
			check(lit, "the rung up is lit");
			server.runCommand(String.format("execute in afterworld:ladder run tp @p %d %d %d facing %d %d %d", chest.getX(), chest.getY(), chest.getZ() + 4, up.getX(), up.getY() + 1, up.getZ()));
			context.waitTicks(15);
			context.takeScreenshot("ladder-floor1-won");

			// ---- up the rung
			server.runCommand(String.format("execute in afterworld:ladder run tp @p %d.5 %d %d.5", up.getX(), up.getY() + 1, up.getZ()));
			context.waitTicks(10);
			String diag = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return p.position() + " on " + p.getOnPos() + " = " + p.level().getBlockState(p.getOnPos()) + " ground " + p.onGround() + " dim "
					+ p.level().dimension().identifier() + " up " + up + " floor " + Ladder.floorOf(p) + " phase " + Ladder.phaseOf(p);
			});
			Afterworld.LOG.info("LADDERTEST on the rung: {}", diag);
			context.waitTicks(20);
			int floor = server.computeOnServer(s -> Ladder.floorOf(player(s)));
			check(floor == 2, "stepping on the lit rung climbed to floor two");

			// ---- a landing
			server.runOnServer(s -> Ladder.jump(player(s), 5));
			context.waitTicks(30);
			sp.getConnection().waitForChunksRender(false, 600);
			check(phase(server).equals("RESTING"), "floor five is a landing");
			boolean rook = server.computeOnServer(s -> !dev.afterworld.npc.Npcs.loaded(dev.afterworld.npc.Npc.ROOK).isEmpty());
			check(rook, "Rook keeps his stall on the landing");
			server.runCommand("execute as @p at @p run tp @p ~ ~ ~ facing ~10 ~1 ~-6");
			context.waitTicks(20);
			context.takeScreenshot("ladder-landing");

			// ---- an echo
			int before = server.computeOnServer(s -> dev.afterworld.world.AfterworldData.get(s).wardensDefeated);
			server.runOnServer(s -> Ladder.jump(player(s), 10));
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender(false, 600);
			waitPhase(context, server, "FIGHTING", 200);
			context.waitTicks(40);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(4);
			context.takeScreenshot("ladder-echo");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (int i = 0; i < 6 && !phase(server).equals("CLEARED"); i++) {
				server.runOnServer(s -> Ladder.clearWave(player(s)));
				context.waitTicks(40);
			}
			check(phase(server).equals("CLEARED"), "the echo fell");
			int after = server.computeOnServer(s -> dev.afterworld.world.AfterworldData.get(s).wardensDefeated);
			check(after == before, "an echo's fall is not the Warden's");
			int resume = server.computeOnServer(s -> PlayerState.of(player(s)).ladderResume);
			check(resume == 11, "the climb may be taken up again from floor eleven");

			// ---- thrown out
			server.runCommand("effect clear @p resistance");
			server.runCommand("kill @p");
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.OVERWORLD, 200);
			context.waitTicks(20);
			float health = context.computeOnClient(mc -> mc.player.getHealth());
			boolean near = server.computeOnServer(s -> player(s).blockPosition().distSqr(gate) < 12 * 12);
			Afterworld.LOG.info("LADDERTEST thrown out with {} health, near the gate: {}", health, near);
			check(health > 0 && near, "a mortal blow threw the climber out at the gate, alive");
			context.takeScreenshot("ladder-thrown");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Ladder.use(p.level(), p, gate, p.level().getBlockState(gate));
			});
			context.waitFor(mc -> mc.gui.screen() instanceof LadderScreen, 100);
			context.waitTicks(10);
			context.takeScreenshot("ladder-gate-resume");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			Afterworld.LOG.info("LADDERTEST done");
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String phase(TestServerContext server) {
		return server.computeOnServer(s -> Ladder.phaseOf(player(s)));
	}

	private static void waitPhase(ClientGameTestContext context, TestServerContext server, String want, int ticks) {
		for (int i = 0; i < ticks / 5 && !phase(server).equals(want); i++) {
			context.waitTicks(5);
		}
		Afterworld.LOG.info("LADDERTEST phase {} (wanted {})", phase(server), want);
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("LADDERTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
