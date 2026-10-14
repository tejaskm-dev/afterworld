package dev.afterworld.test;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import dev.afterworld.block.SealBlockEntity;
import dev.afterworld.entity.boss.WardenBoss;
import dev.afterworld.minigame.Minigames;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.world.wonder.WonderData;
import dev.afterworld.world.wonder.WonderPiece;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Afterworld 2.0's lands: the biome sites are found in a normal world and checked against their
 * files, their keepers (a Warden in each lair, Leyla in the obelisk, configured seals) are where
 * they should be, the Wardens fight, the trials open, and everything is photographed.
 */
public class AfterworldLandsTest implements FabricClientGameTest {
	private static final String[] SITES = {"caravan_wreck", "buried_obelisk", "sunken_bog_temple", "frozen_mage_tower", "overgrown_ziggurat",
		"moonlit_clearing", "barrow_mound", "ossuary_gate", "ember_kilns", "crystal_observatory", "lantern_dojo", "peak_monastery", "druids_ring",
		"crimson_necropolis", "rimeback_den", "sun_temple", "gilded_vault", "drowned_shrine", "lighthouse", "tea_house", "nomad_yurts"};
	private static final List<EntityType<? extends WardenBoss>> BOSSES = List.of(AwEntities.ASHEN_SHOGUN, AwEntities.CRIMSON_LICH,
		AwEntities.RIMEBACK_COLOSSUS, AwEntities.DUNE_SENTINEL, AwEntities.GILDED_WARDEN);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.runOnClient(mc -> mc.options.renderDistance().set(10));
			var server = sp.getServer();
			server.runOnServer(s -> s.getPlayerList().setViewDistance(10));
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("time set 5000");
			server.runCommand("gamemode spectator @p");
			// -Plands=bosses,trials (see build.gradle) runs only those parts.
			String parts = System.getProperty("afterworld.lands", "sites,bosses,trials");
			if (parts.contains("sites")) {
				int found = 0;
				for (String name : SITES) {
					if (site(context, sp, name)) {
						found++;
					}
				}
				Afterworld.LOG.info("LANDSTEST found {} of {} sites", found, SITES.length);
				if (found < SITES.length / 2) {
					throw new AssertionError("Only " + found + " of " + SITES.length + " sites were found");
				}
			}
			if (parts.contains("bosses")) {
				bosses(context, sp);
			}
			if (parts.contains("trials")) {
				trials(context, sp);
			}
		}
	}

	private static Holder.Reference<Structure> structure(ServerLevel level, String name) {
		var key = ResourceKey.create(Registries.STRUCTURE, Afterworld.id(name));
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key).orElse(null);
	}

	/** Finds one site, checks it block by block and its keepers, and photographs it. */
	private static boolean site(ClientGameTestContext context, TestSingleplayerContext sp, String name) {
		var server = sp.getServer();
		BlockPos at = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			var holder = structure(level, name);
			if (holder == null) {
				return null;
			}
			Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 160, false);
			return hit == null ? null : hit.getFirst();
		});
		if (at == null) {
			Afterworld.LOG.warn("LANDSTEST could not find {}", name);
			return false;
		}
		server.runCommand("tp @p " + at.getX() + " 200 " + at.getZ());
		context.waitTicks(120);
		sp.getConnection().waitForChunksRender(false, 2400);
		BoundingBox box = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			StructureStart start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(structure(level, name).value());
			return start != null && start.isValid() && !start.getPieces().isEmpty() ? start.getPieces().get(0).getBoundingBox() : null;
		});
		if (box == null) {
			Afterworld.LOG.error("LANDSTEST {} has no start at {}", name, at);
			return false;
		}
		int[] check = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			StructureStart start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(structure(level, name).value());
			WonderPiece piece = (WonderPiece) start.getPieces().get(0);
			for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
				for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
					level.getChunk(x, z);
				}
			}
			return WonderData.get(name).verify(level, piece.origin(), piece.turn());
		});
		// What the site brought with it: Wardens, keepers, configured seals.
		String keepers = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			AABB area = new AABB(box.minX() - 2, box.minY() - 16, box.minZ() - 2, box.maxX() + 2, box.maxY() + 8, box.maxZ() + 2);
			int bosses = level.getEntitiesOfClass(WardenBoss.class, area).size();
			int people = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Mannequin.class, area).size();
			int mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area).size();
			int seals = 0;
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 14, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockEntity(p) instanceof SealBlockEntity) {
					seals++;
				}
			}
			return "bosses=" + bosses + " people=" + people + " mobs=" + mobs + " seals=" + seals;
		});
		Afterworld.LOG.info("LANDSTEST {} at {} placed {} blocks, {} differ; {}", name, at, check[0], check[1], keepers);
		if (check[0] == 0 || check[1] * 10 > check[0] * 4) {
			throw new AssertionError("Site " + name + " is incomplete: " + check[1] + " of " + check[0] + " blocks differ");
		}
		if (check[1] * 10 > check[0]) {
			Afterworld.LOG.warn("LANDSTEST {} differs by more than a tenth", name);
		}
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		int size = Math.max(box.getXSpan(), box.getZSpan());
		int ground = box.minY();
		// From above the site's own clearing, so the woods round it do not hide it.
		shot(context, sp, "site-" + name, cx + size / 4, ground + size * 2 / 3, cz + size / 3, cx, ground + 2, cz - size / 8);
		return true;
	}

	/** Each Warden on a stone arena in the sky: asleep, woken, then fighting someone who cannot be hurt. */
	private static void bosses(ClientGameTestContext context, TestSingleplayerContext sp) {
		var server = sp.getServer();
		BlockPos base = new BlockPos(64, 200, 64);
		server.runCommand("time set 6000");
		// Go there first: a command can only build where the chunks are loaded.
		server.runCommand("gamemode spectator @p");
		server.runCommand("tp @p 64 210 50");
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender(false, 2400);
		server.runCommand("fill 44 199 44 84 199 84 minecraft:smooth_stone");
		for (EntityType<? extends WardenBoss> type : BOSSES) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			java.util.UUID uuid = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				WardenBoss b = type.create(level, EntitySpawnReason.COMMAND);
				b.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180, 0);
				b.setHome(Vec3.atBottomCenterOf(base));
				b.setDormant(true);
				level.addFreshEntity(b);
				return b.getUUID();
			});
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p " + (base.getX() + 9) + " " + (base.getY() + 4) + " " + (base.getZ() - 9) + " facing " + base.getX() + " " + (base.getY() + 2) + " " + base.getZ());
			context.waitTicks(40);
			context.takeScreenshot("boss-" + id + "-asleep");
			server.runOnServer(s -> WardenBoss.wakeNear(s.overworld(), Vec3.atCenterOf(base), 16));
			context.waitTicks(60);
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 30 255 true");
			server.runCommand("effect give @p minecraft:regeneration 30 255 true");
			server.runCommand("tp @p " + (base.getX() + 6) + " " + base.getY() + " " + (base.getZ() - 6) + " facing " + base.getX() + " " + (base.getY() + 2) + " " + base.getZ());
			context.waitTicks(50);
			context.takeScreenshot("boss-" + id + "-fight-1");
			context.waitTicks(45);
			context.takeScreenshot("boss-" + id + "-fight-2");
			String state = server.computeOnServer(s -> {
				var e = s.overworld().getEntity(uuid);
				if (!(e instanceof WardenBoss b)) {
					return "gone";
				}
				return "alive=" + b.isAlive() + " hp=" + (int) b.getHealth() + "/" + (int) b.getMaxHealth() + " phase=" + b.phase() + " dormant=" + b.dormant()
					+ " at " + b.blockPosition().toShortString() + (b.isRemoved() ? " removed=" + b.getRemovalReason() : "");
			});
			Afterworld.LOG.info("LANDSTEST boss {}: {}", id, state);
			server.runCommand("gamemode spectator @p");
			server.runOnServer(s -> s.overworld().getEntitiesOfClass(WardenBoss.class, new AABB(base).inflate(60)).forEach(net.minecraft.world.entity.Entity::discard));
			server.runCommand("kill @e[type=minecraft:skeleton]");
			context.waitTicks(20);
		}
	}

	/** The bench trials on screen; the duel and the race in the world; Leyla's words. */
	private static void trials(ClientGameTestContext context, TestSingleplayerContext sp) {
		var server = sp.getServer();
		for (String id : new String[] {"smithing", "cooking", "brewing", "stars", "melody"}) {
			context.runOnClient(mc -> {
				CompoundTag data = new CompoundTag();
				data.putInt("seed", 7);
				mc.gui.setScreen(dev.afterworld.client.minigame.TrialScreens.create(id, data));
			});
			context.waitTicks(70);
			context.takeScreenshot("trial-" + id);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(5);
		}
		BlockPos base = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(96, 0, 32)));
		server.runCommand("gamemode survival @p");
		server.runCommand("tp @p " + base.getX() + " " + base.getY() + " " + (base.getZ() + 3) + " facing " + base.getX() + " " + (base.getY() + 1) + " " + base.getZ());
		context.waitTicks(20);
		// Akane's duel: she winds up; the panel calls for the parry.
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			var akane = Npcs.spawn(level, Npc.AKANE, base, 0);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Minigames.start(p, "duel", akane);
		});
		context.waitTicks(68);
		context.takeScreenshot("trial-duel");
		// Leyla, hiding: her words, with her portrait.
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			var leyla = Npcs.spawn(level, Npc.LEYLA, base.offset(2, 0, 0), 0);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			dev.afterworld.npc.Dialogue.start(p, leyla, Npc.LEYLA);
		});
		context.waitTicks(80);
		context.takeScreenshot("dialogue-leyla");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		// Odo's race: launched skyward, rings ahead.
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Minigames.start(p, "race", null);
		});
		context.waitTicks(40);
		context.takeScreenshot("trial-race");
		context.waitTicks(40);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int y, int z, int lx, int ly, int lz) {
		int floor = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
		int eye = Math.max(y, floor + 4);
		sp.getServer().runCommand("tp @p " + x + " " + eye + " " + z + " facing " + lx + " " + ly + " " + lz);
		context.waitTicks(80);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.runOnClient(mc -> mc.levelExtractor.allChanged());
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(10);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}
}
