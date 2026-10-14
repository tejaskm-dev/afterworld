package dev.afterworld.command;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Disciplines;
import dev.afterworld.player.PlayerState;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.WorldSim;
import dev.afterworld.player.Progress;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Exercises every Edict, discipline signature and site builder on a fake player. Run with
 * {@code /afterworld selftest} on a test server; results go to the log.
 */
public final class SelfTest {
	private SelfTest() {
	}

	public static List<String> run(ServerLevel level) {
		List<String> out = new ArrayList<>();
		MinecraftServer server = level.getServer();
		BlockPos spawn = level.getRespawnData().pos();
		FakePlayer fp = FakePlayer.get(level);
		fp.snapTo(spawn.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5, 0, 0);
		PlayerState s = PlayerState.of(fp);
		int ok = 0;
		int failed = 0;
		for (Discipline d : Discipline.VALUES) {
			if (d == Discipline.NONE) {
				continue;
			}
			try {
				s.discipline = Discipline.NONE;
				Disciplines.choose(fp, d);
				out.add("discipline " + d.id + ": ok (" + s.discipline.id + ")");
				ok++;
			} catch (Exception e) {
				out.add("discipline " + d.id + ": FAILED " + e);
				Afterworld.LOG.error("selftest discipline", e);
				failed++;
			}
		}
		for (Edict e : Edict.VALUES) {
			s.learned.add(e);
			try {
				boolean cast = EdictSystem.tryCast(fp, s, e, 1.0F, true);
				out.add("edict " + e.id + ": " + (cast ? "cast" : "refused (rule could not apply here)"));
				ok++;
			} catch (Exception ex) {
				out.add("edict " + e.id + ": FAILED " + ex);
				Afterworld.LOG.error("selftest edict " + e.id, ex);
				failed++;
			}
		}
		int i = 0;
		for (SiteKind kind : SiteKind.VALUES) {
			try {
				int x = spawn.getX() + 120 + (i % 3) * 90;
				int z = spawn.getZ() + 120 + (i / 3) * 90;
				Site site = Sites.create(server, kind, level.dimension().identifier().toString(), x, z, -1, -1,
					kind == SiteKind.ANOMALY ? Edict.WEIGHTLESSNESS.ordinal() : -1, null);
				Sites.forceBuild(level, site);
				out.add("site " + kind.id + ": queued at " + x + "," + site.y + "," + z);
				ok++;
			} catch (Exception ex) {
				out.add("site " + kind.id + ": FAILED " + ex);
				Afterworld.LOG.error("selftest site " + kind.id, ex);
				failed++;
			}
			i++;
		}
		try {
			AfterworldData data = AfterworldData.get(server);
			long day = Progress.day(server);
			for (int d = 0; d < 5; d++) {
				WorldSim.simulateDay(server, data, day + 500 + d, false);
			}
			out.add("simulate: ok, chronicle=" + data.chronicle.size() + " settlements=" + data.settlements.size() + " sites=" + data.sites.size());
			ok++;
		} catch (Exception ex) {
			out.add("simulate: FAILED " + ex);
			Afterworld.LOG.error("selftest simulate", ex);
			failed++;
		}
		// Terrain: the biomes are registered and reachable, and every feature places.
		var biomes = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
		for (var key : dev.afterworld.world.gen.AwBiomes.ALL) {
			try {
				if (biomes.get(key).isEmpty()) {
					throw new IllegalStateException("not registered");
				}
				var hit = level.findClosestBiome3d(h -> h.is(key), spawn, 4800, 64, 64);
				out.add("biome " + key.identifier().getPath() + ": " + (hit == null ? "registered (none within 4800)" : "found at " + hit.getFirst().toShortString()));
				ok++;
			} catch (Exception ex) {
				out.add("biome " + key.identifier().getPath() + ": FAILED " + ex);
				Afterworld.LOG.error("selftest biome", ex);
				failed++;
			}
		}
		var features = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.FEATURE);
		int f = 0;
		for (String name : new String[] {"floating_island", "crystal_spire", "hollow_giant", "standing_stones", "ash_vent", "ashen_tree", "fen_tree",
				"moor_arch", "fen_causeway", "ashen_surface", "hollow_surface", "shard_surface", "fen_surface"}) {
			try {
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.FEATURE, Afterworld.id(name));
				var feature = features.get(key).orElseThrow(() -> new IllegalStateException("missing"));
				int x = spawn.getX() - 400 - (f % 4) * 48;
				int z = spawn.getZ() - 400 - (f / 4) * 48;
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
				boolean placed = feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), new BlockPos(x, y, z));
				out.add("feature " + name + ": " + (placed ? "placed" : "declined (ground unsuitable)"));
				ok++;
			} catch (Exception ex) {
				out.add("feature " + name + ": FAILED " + ex);
				Afterworld.LOG.error("selftest feature " + name, ex);
				failed++;
			}
			f++;
		}
		// Creatures spawn, loot tables load, garb survives a save.
		for (var type : java.util.List.of(dev.afterworld.registry.AwEntities.LANTERN_MOTH, dev.afterworld.registry.AwEntities.CROW, dev.afterworld.registry.AwEntities.MOSSBACK,
				dev.afterworld.registry.AwEntities.HOLLOW_STAG, dev.afterworld.registry.AwEntities.CLOCKWORK_SENTRY, dev.afterworld.registry.AwEntities.RULE_WRAITH,
				dev.afterworld.registry.AwEntities.WISP, dev.afterworld.registry.AwEntities.ASHEN_HUSK, dev.afterworld.registry.AwEntities.SHARDLING,
				dev.afterworld.registry.AwEntities.BOG_LURKER, dev.afterworld.registry.AwEntities.ECHO_STALKER, dev.afterworld.registry.AwEntities.GEARHOUND,
				dev.afterworld.registry.AwEntities.RIME_WIGHT, dev.afterworld.registry.AwEntities.SKYWHALE, dev.afterworld.registry.AwEntities.GLIMMERFIN,
				dev.afterworld.registry.AwEntities.EMBERFOX, dev.afterworld.registry.AwEntities.RIMEHORN, dev.afterworld.registry.AwEntities.PETAL_SPRITE,
				dev.afterworld.registry.AwEntities.MIRROR_RAY)) {
			try {
				var mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				mob.snapTo(spawn.getX() + 0.5, spawn.getY() + 2, spawn.getZ() + 0.5, 0, 0);
				level.addFreshEntity(mob);
				mob.tick();
				mob.discard();
				out.add("creature " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + ": ok");
				ok++;
			} catch (Exception ex) {
				out.add("creature " + type + ": FAILED " + ex);
				Afterworld.LOG.error("selftest creature", ex);
				failed++;
			}
		}
		for (String table : new String[] {"chests/bastion_armory", "chests/observatory_maps", "chests/mausoleum_crypt", "chests/containment_archive",
				"chests/archive_almost", "entities/clockwork_sentry", "entities/rule_wraith", "entities/lantern_moth",
				"chests/titan_offering", "chests/vesperhold_reliquary", "chests/vesperhold_house", "chests/citadel_vault",
				"chests/brassmere_storeroom", "chests/brassmere_airship", "chests/wardens_shrine"}) {
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Afterworld.id(table));
			var loot = server.reloadableRegistries().getLootTable(key);
			if (loot == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
				out.add("loot " + table + ": FAILED (missing or invalid)");
				failed++;
			} else {
				out.add("loot " + table + ": ok");
				ok++;
			}
		}
		try {
			var garb = new dev.afterworld.player.Garb(3, 5, 4, 2, 1, 12, 9, 6, true, 4);
			var tag = dev.afterworld.player.Garb.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, garb).getOrThrow();
			var back = dev.afterworld.player.Garb.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
			if (!garb.equals(back)) {
				throw new IllegalStateException(garb + " != " + back);
			}
			out.add("garb codec: ok");
			ok++;
		} catch (Exception ex) {
			out.add("garb codec: FAILED " + ex);
			failed++;
		}
		// Wonders: their blocks load and parse, and world generation knows where to put them.
		var structures = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var sets = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
		for (String name : dev.afterworld.world.wonder.Wonders.NAMES) {
			var data = dev.afterworld.world.wonder.WonderData.get(name);
			boolean registered = structures.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Afterworld.id(name))).isPresent()
				&& sets.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET, Afterworld.id(name))).isPresent();
			if (data == null || data.unresolved() > 0 || !registered) {
				out.add("wonder " + name + ": FAILED (" + (data == null ? "no data" : data.unresolved() + " unknown blocks") + (registered ? "" : ", not registered") + ")");
				failed++;
			} else {
				out.add("wonder " + name + ": ok (" + data.sx + "x" + data.sy + "x" + data.sz + ")");
				ok++;
			}
		}
		out.add("SELFTEST " + (failed == 0 ? "PASSED" : "FAILED") + ": " + ok + " ok, " + failed + " failed");
		for (String line : out) {
			Afterworld.LOG.info("[selftest] {}", line);
		}
		return out;
	}
}
