package dev.afterworld.test;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.world.wonder.WonderData;
import dev.afterworld.world.wonder.WonderPiece;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The underground as the world makes it. Each delve is found where world generation put it,
 * checked block for block against its file, its seal found at its place far below the levelled
 * ground of its way in; then photographed from the air, at its way in, at dusk and inside. And the
 * caves round spawn hold the camps of those who came before (cold fires on cave floors).
 */
public class AfterworldUndergroundTest implements FabricClientGameTest {
	static final String[] DELVES = {"sunken_forge", "glowcap_hollow", "geode_vault", "drowned_catacombs"};
	/** The grids' size across and their ground line (tools/wonders/delves.py: 48 square, G = 38). */
	static final int SIZE = 48;
	static final int G = 38;
	/** Where each seal stands, in the build's own cells. */
	static final Map<String, int[]> SEAL = Map.of(
		"sunken_forge", new int[] {36, 9, 27},
		"glowcap_hollow", new int[] {34, 9, 37},
		"geode_vault", new int[] {27, 14, 26},
		"drowned_catacombs", new int[] {24, 10, 31});
	/** A view of the way in, as {eye x, y, z, look-at x, y, z} in the build's cells. */
	static final Map<String, int[]> WAY = Map.of(
		"sunken_forge", new int[] {24, G + 4, 34, 24, G + 1, 12},
		"glowcap_hollow", new int[] {24, G + 3, 25, 24, G + 2, 9},
		"geode_vault", new int[] {37, G + 7, 37, 24, G, 24},
		"drowned_catacombs", new int[] {24, G + 3, 36, 24, G + 4, 16});
	/** And a view inside. */
	static final Map<String, int[]> INSIDE = Map.of(
		"sunken_forge", new int[] {9, 13, 27, 38, 10, 27},
		"glowcap_hollow", new int[] {24, 14, 15, 30, 11, 38},
		"geode_vault", new int[] {17, 18, 17, 24, 14, 24},
		"drowned_catacombs", new int[] {24, 13, 29, 24, 12, 19});

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(8);
				mc.gui.toastManager().clear();
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			BlockPos spawn = server.computeOnServer(s -> s.overworld().getRespawnData().pos());

			for (String name : DELVES) {
				BlockPos found = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(structure(level, name)), spawn, 100, false);
					return hit == null ? null : hit.getFirst();
				});
				check(found != null, name + " stands somewhere within reach of spawn");
				Afterworld.LOG.info("UNDERTEST {} near {} ({} blocks from spawn)", name, found, (int) Math.sqrt(found.distSqr(spawn)));
				server.runCommand("time set 6000");
				server.runCommand(String.format("tp @p %d 220 %d", found.getX(), found.getZ()));
				context.waitTicks(40);
				sp.getConnection().waitForChunksRender(false, 2400);
				// Where it was put and which way round: then every cell can be found in the world.
				int[] at = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					StructureStart start = level.getChunk(found.getX() >> 4, found.getZ() >> 4).getStartForStructure(structure(level, name).value());
					if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
						return null;
					}
					WonderPiece piece = (WonderPiece) start.getPieces().get(0);
					for (int x = (piece.origin().getX() >> 4) - 1; x <= (piece.origin().getX() + SIZE) >> 4; x++) {
						for (int z = (piece.origin().getZ() >> 4) - 1; z <= (piece.origin().getZ() + SIZE) >> 4; z++) {
							level.getChunk(x, z);
						}
					}
					int[] v = WonderData.get(name).verify(level, piece.origin(), piece.turn());
					return new int[] {piece.origin().getX(), piece.origin().getY(), piece.origin().getZ(), piece.turn().ordinal(), v[0], v[1]};
				});
				check(at != null, name + " has its piece where it was found");
				Rotation turn = Rotation.values()[at[3]];
				Afterworld.LOG.info("UNDERTEST {} origin {} {} {} turned {}: {} blocks placed, {} differ", name, at[0], at[1], at[2], turn, at[4], at[5]);
				// Ores, caves and lakes may change a few blocks after; much more means it never went down whole.
				check(at[4] > 0 && at[5] * 20 < at[4], name + " is placed whole");
				int[] sc = SEAL.get(name);
				BlockPos seal = cell(at, turn, sc[0], sc[1], sc[2]);
				boolean sealed = server.computeOnServer(s -> s.overworld().getBlockState(seal).is(AwBlocks.SEAL));
				check(sealed, name + "'s seal stands at " + seal);
				int ground = at[1] + G;
				Afterworld.LOG.info("UNDERTEST {} seal at {}, {} below the ground line", name, seal, ground - seal.getY());
				check(ground - seal.getY() > 15, name + "'s seal is deep underground");

				BlockPos mid = cell(at, turn, SIZE / 2, G, SIZE / 2);
				shot(context, sp, "under-" + name + "-air", mid.getX() + 30, ground + 26, mid.getZ() + 30, mid.getX(), ground, mid.getZ());
				int[] w = WAY.get(name);
				BlockPos eye = cell(at, turn, w[0], w[1], w[2]);
				BlockPos look = cell(at, turn, w[3], w[4], w[5]);
				shot(context, sp, "under-" + name + "-way", eye.getX(), eye.getY(), eye.getZ(), look.getX(), look.getY(), look.getZ());
				server.runCommand("time set 13200");
				shot(context, sp, "under-" + name + "-dusk", mid.getX() - 26, ground + 14, mid.getZ() + 22, mid.getX(), ground + 2, mid.getZ());
				int[] in = INSIDE.get(name);
				BlockPos ie = cell(at, turn, in[0], in[1], in[2]);
				BlockPos il = cell(at, turn, in[3], in[4], in[5]);
				server.runCommand("effect give @p night_vision 60 0 true");
				shot(context, sp, "under-" + name + "-inside", ie.getX(), ie.getY(), ie.getZ(), il.getX(), il.getY(), il.getZ());
				server.runCommand("effect clear @p night_vision");
			}

			// Camps in the caves round spawn: cold fires on cave floors, well under the ground.
			server.runCommand("time set 6000");
			int[] camp = server.computeOnServer(s -> camps(s.overworld(), spawn, 7));
			Afterworld.LOG.info("UNDERTEST in the caves of 225 chunks round spawn: {} cold fires (camps and mushroom rings), {} shrines' pots, {} explorers' skulls; "
				+ "first fire at {} {} {}", camp[0], camp[4], camp[5], camp[1], camp[2], camp[3]);
			check(camp[0] > 0, "the caves hold camps");
			server.runCommand("effect give @p night_vision 60 0 true");
			BlockPos fire = new BlockPos(camp[1], camp[2], camp[3]);
			BlockPos eye = server.computeOnServer(s -> vantage(s.overworld(), fire));
			shot(context, sp, "under-cave-camp", eye.getX(), eye.getY(), eye.getZ(), fire.getX(), fire.getY(), fire.getZ());
			Afterworld.LOG.info("UNDERTEST done");
		}
	}

	private static Holder.Reference<Structure> structure(ServerLevel level, String name) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE, Afterworld.id(name)));
	}

	/** A build cell in the world, given the piece's {origin x, y, z, turn}: as WonderData turns it. */
	private static BlockPos cell(int[] at, Rotation turn, int ux, int uy, int uz) {
		int n = SIZE - 1;
		int x = switch (turn) {
			case NONE -> ux;
			case CLOCKWISE_90 -> n - uz;
			case CLOCKWISE_180 -> n - ux;
			case COUNTERCLOCKWISE_90 -> uz;
		};
		int z = switch (turn) {
			case NONE -> uz;
			case CLOCKWISE_90 -> ux;
			case CLOCKWISE_180 -> n - uz;
			case COUNTERCLOCKWISE_90 -> n - ux;
		};
		return new BlockPos(at[0] + x, at[1] + uy, at[2] + z);
	}

	/** Below y 40 in the chunks round {@code at}: {unlit campfires, x, y, z of the first, decorated pots, skeleton skulls}. */
	private static int[] camps(ServerLevel level, BlockPos at, int chunks) {
		int[] out = {0, at.getX(), 40, at.getZ(), 0, 0};
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -chunks; dx <= chunks; dx++) {
			for (int dz = -chunks; dz <= chunks; dz++) {
				var chunk = level.getChunk(cx + dx, cz + dz);
				int x0 = (cx + dx) << 4;
				int z0 = (cz + dz) << 4;
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = -48; y < 40; y++) {
							p.set(x0 + x, y, z0 + z);
							var st = chunk.getBlockState(p);
							if (st.is(Blocks.CAMPFIRE) && !st.getValue(CampfireBlock.LIT)) {
								if (out[0]++ == 0) {
									out[1] = p.getX();
									out[2] = p.getY();
									out[3] = p.getZ();
								}
							} else if (st.is(Blocks.DECORATED_POT)) {
								out[4]++;
							} else if (st.is(Blocks.SKELETON_SKULL)) {
								out[5]++;
							}
						}
					}
				}
			}
		}
		return out;
	}

	/** Somewhere open in the cave to look at {@code at} from: two clear blocks to stand in, a clear line to it. */
	private static BlockPos vantage(ServerLevel level, BlockPos at) {
		for (int r = 5; r >= 2; r--) {
			for (int a = 0; a < 360; a += 30) {
				for (int dy = 2; dy >= 0; dy--) {
					BlockPos p = at.offset((int) Math.round(Math.cos(Math.toRadians(a)) * r), dy, (int) Math.round(Math.sin(Math.toRadians(a)) * r));
					if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) {
						continue;
					}
					boolean clear = true;
					for (int i = 1; i < 10 && clear; i++) {
						double f = i / 10.0;
						BlockPos q = BlockPos.containing(p.getX() + 0.5 + (at.getX() - p.getX()) * f, p.getY() + 1.6 + (at.getY() + 0.5 - p.getY() - 1.6) * f,
							p.getZ() + 0.5 + (at.getZ() - p.getZ()) * f);
						clear = q.equals(at) || !level.getBlockState(q).isSolidRender();
					}
					if (clear) {
						return p;
					}
				}
			}
		}
		return at.offset(2, 1, 2);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		sp.getServer().runCommand(String.format("tp @p %d %d %d facing %d %d %d", x, y, z, lx, ly, lz));
		context.waitTicks(30);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.runOnClient(mc -> mc.levelExtractor.allChanged());
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(20);
		context.takeScreenshot(name);
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("UNDERTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
