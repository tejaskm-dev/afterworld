package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.block.SealBlock;
import dev.afterworld.block.SealBlockEntity;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The small places with a prize: a proving ring is struck, its waves come and are beaten, and its
 * cist opens; the Climber's Needle stands, its flag at the top. Photographs both.
 */
public class AfterworldRingTest implements FabricClientGameTest {
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
			server.runCommand("effect give @p resistance 999999 4 true");
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			BlockPos spawn = server.computeOnServer(s -> player(s).blockPosition());
			int ax = spawn.getX() + 140;
			int az = spawn.getZ() + 60;
			server.runCommand(String.format("tp @p %d 200 %d", ax, az));
			context.waitTicks(30);
			sp.getConnection().waitForChunksRender(false, 1200);
			int ay = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az));
			server.runCommand(String.format("execute positioned %d %d %d run afterworld build proving_ring 3", ax, ay, az));
			context.waitTicks(10);
			BlockPos seal = find(server, new BlockPos(ax, ay, az), AwBlocks.SEAL);
			check(seal != null, "the proving ring stands, with its horn-stone");
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", seal.getX() + 8, seal.getY() + 3, seal.getZ() + 8, seal.getX(), seal.getY(), seal.getZ()));
			context.waitTicks(20);
			context.takeScreenshot("ring-waiting");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				if (p.level().getBlockEntity(seal) instanceof SealBlockEntity be) {
					be.challenge(p.level(), seal, p);
				}
			});
			context.waitTicks(30);
			int foes = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				new net.minecraft.world.phys.AABB(seal).inflate(24), m -> m.entityTags().contains("afterworld:ring")).size());
			Afterworld.LOG.info("RINGTEST first wave: {} creatures", foes);
			check(foes >= 3, "striking the stone brought the first wave");
			context.takeScreenshot("ring-wave");
			boolean solved = false;
			for (int i = 0; i < 12 && !solved; i++) {
				server.runOnServer(s -> {
					for (var m : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(seal).inflate(40),
						m -> m.entityTags().contains("afterworld:ring"))) {
						m.kill(s.overworld());
					}
				});
				context.waitTicks(60);
				solved = server.computeOnServer(s -> s.overworld().getBlockState(seal).getValue(SealBlock.SOLVED));
				String dbg = server.computeOnServer(s -> s.overworld().getBlockEntity(seal) instanceof SealBlockEntity be ? be.ringDebug() : "no seal");
				int left = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
					new net.minecraft.world.phys.AABB(seal).inflate(40), m -> m.entityTags().contains("afterworld:ring") && m.isAlive()).size());
				Afterworld.LOG.info("RINGTEST round {}: {} ({} ring creatures alive)", i, dbg, left);
			}
			check(solved, "holding the ring through its waves turned the seal");
			BlockPos chest = find(server, seal, Blocks.CHEST);
			boolean open = server.computeOnServer(s -> {
				BlockPos front = chest.south();
				return s.overworld().getBlockState(front).isAir();
			});
			check(open, "the cist's bars fell");
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", chest.getX(), chest.getY() + 1, chest.getZ() + 6, chest.getX(), chest.getY(), chest.getZ()));
			context.waitTicks(15);
			context.takeScreenshot("ring-won");

			// ---- the Needle
			int nx = ax + 60;
			int nz = az + 20;
			server.runCommand(String.format("tp @p %d 200 %d", nx, nz));
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender(false, 1200);
			int ny = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz));
			server.runCommand(String.format("execute positioned %d %d %d run afterworld build climbers_needle 3", nx, ny, nz));
			context.waitTicks(10);
			server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", nx + 26, ny + 14, nz + 26, nx, ny + 24, nz));
			context.waitTicks(20);
			context.takeScreenshot("needle");
			BlockPos cache = find(server, new BlockPos(nx, ny + 44, nz), Blocks.CHEST);
			check(cache != null, "a cache waits at the top of the Needle");
			Afterworld.LOG.info("RINGTEST done");
		}
	}

	private static BlockPos find(TestServerContext server, BlockPos around, net.minecraft.world.level.block.Block block) {
		return server.computeOnServer(s -> {
			for (BlockPos q : BlockPos.betweenClosed(around.offset(-24, -8, -24), around.offset(24, 12, 24))) {
				if (s.overworld().getBlockState(q).is(block)) {
					return q.immutable();
				}
			}
			return null;
		});
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("RINGTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
