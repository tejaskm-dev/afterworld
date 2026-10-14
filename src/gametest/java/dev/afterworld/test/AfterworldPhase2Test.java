package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.economy.Board;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Phase 2's pieces in play: the Warden's Board at the camp (open it, take two notices, see them in
 * the tracker), hits landing on creatures of different materials, and the Edicts at Rank III.
 */
public class AfterworldPhase2Test implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			board(context, sp);
			hits(context, sp);
			capstones(context, sp);
			Afterworld.LOG.info("PHASE2TEST done");
		}
	}

	private static void board(ClientGameTestContext context, TestSingleplayerContext sp) {
		var server = sp.getServer();
		BlockPos board = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			// The board stands in the town's market, a little way off the plaza.
			BlockPos mid = BlockPos.of(dev.afterworld.world.AfterworldData.get(s).campCenter);
			for (BlockPos p : BlockPos.betweenClosed(mid.offset(-40, -8, -40), mid.offset(40, 8, 40))) {
				if (level.getBlockState(p).is(AwBlocks.WARDENS_BOARD)) {
					return p.immutable();
				}
			}
			return null;
		});
		Afterworld.LOG.info("PHASE2TEST board at {}", board);
		if (board == null) {
			return;
		}
		server.runCommand("tp @p " + board.getX() + ".5 " + board.getY() + " " + (board.getZ() + 3) + ".5 facing " + board.getX() + ".5 " + (board.getY() + 1) + " " + board.getZ() + ".5");
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(20);
		context.takeScreenshot("p2-board-world");
		server.runOnServer(s -> Board.open(s.getPlayerList().getPlayers().get(0), board));
		context.waitTicks(20);
		context.takeScreenshot("p2-board-open");
		context.runOnClient(mc -> ClientPlayNetworking.send(new AwPayloads.BoardAction(AwPayloads.BoardAction.TAKE, 0, board)));
		context.waitTicks(10);
		context.runOnClient(mc -> ClientPlayNetworking.send(new AwPayloads.BoardAction(AwPayloads.BoardAction.TAKE, 1, board)));
		context.waitTicks(20);
		context.takeScreenshot("p2-board-taken");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(30);
		context.takeScreenshot("p2-board-tracker");
		String held = server.computeOnServer(s -> {
			PlayerState st = PlayerState.of(s.getPlayerList().getPlayers().get(0));
			List<String> out = new ArrayList<>();
			st.contracts.forEach(c -> out.add(c.kind.id + "(" + c.who + "/" + c.what + " x" + c.count + " " + c.marks + "M)"));
			return String.join(", ", out);
		});
		Afterworld.LOG.info("PHASE2TEST contracts: {}", held);
	}

	/** A row of creatures of different materials, each struck once by the traveller. */
	private static void hits(ClientGameTestContext context, TestSingleplayerContext sp) {
		var server = sp.getServer();
		String[] ids = {"afterworld:rust_golemite", "afterworld:prism_golem", "afterworld:vine_strangler", "afterworld:gloomweaver",
			"afterworld:mourning_shade", "afterworld:fen_toad", "afterworld:thornback_boar", "minecraft:skeleton"};
		BlockPos base = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(140, 0, 40)));
		server.runCommand("gamemode survival @p");
		server.runCommand("effect give @p minecraft:resistance 600 255 true");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword");
		List<Integer> spawned = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			int idx = i;
			int id = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(ids[idx]));
				Entity e = type.create(level, EntitySpawnReason.COMMAND);
				if (!(e instanceof Mob mob)) {
					return -1;
				}
				mob.snapTo(base.getX() + idx * 3, base.getY(), base.getZ() + 3, 180, 0);
				mob.setNoAi(true);
				level.addFreshEntity(mob);
				return mob.getId();
			});
			spawned.add(id);
		}
		for (int i = 0; i < ids.length; i++) {
			int id = spawned.get(i);
			if (id < 0) {
				continue;
			}
			int x = base.getX() + i * 3;
			server.runCommand("tp @p " + x + ".5 " + base.getY() + " " + (base.getZ() + 1) + ".5 facing " + x + ".5 " + (base.getY() + 1) + " " + (base.getZ() + 3) + ".5");
			context.waitTicks(i == 0 ? 60 : 22);
			context.runOnClient(mc -> {
				Entity e = mc.level.getEntity(id);
				if (e != null) {
					mc.gameMode.attack(mc.player, e);
				}
			});
			context.waitTicks(2);
			if (i % 3 == 0) {
				context.takeScreenshot("p2-hit-" + ids[i].substring(ids[i].indexOf(':') + 1));
			}
		}
		String health = server.computeOnServer(s -> {
			List<String> out = new ArrayList<>();
			for (int id : spawned) {
				Entity e = s.overworld().getEntity(id);
				if (e instanceof LivingEntity l) {
					out.add(BuiltInRegistries.ENTITY_TYPE.getKey(l.getType()).getPath() + "=" + (int) l.getHealth() + "/" + (int) l.getMaxHealth());
				}
			}
			return String.join(" ", out);
		});
		Afterworld.LOG.info("PHASE2TEST struck: {}", health);
	}

	/** Every Edict at Rank III, cast among a few zombies. */
	private static void capstones(ClientGameTestContext context, TestSingleplayerContext sp) {
		var server = sp.getServer();
		BlockPos base = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(140, 0, 90)));
		server.runCommand("tp @p " + base.getX() + ".5 " + base.getY() + " " + base.getZ() + ".5 facing " + base.getX() + ".5 " + (base.getY() + 1) + " " + (base.getZ() + 6) + ".5");
		context.waitTicks(60);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			PlayerState st = PlayerState.of(p);
			for (Edict e : Edict.VALUES) {
				st.edictRank.put(e.id, 3);
			}
		});
		for (Edict e : new Edict[] {Edict.STILLNESS, Edict.WEIGHTLESSNESS, Edict.RETURN, Edict.WITNESS, Edict.HUNGER, Edict.HOSPITALITY, Edict.ECHOES,
			Edict.FORGOTTEN, Edict.TRESPASS, Edict.BORROWED_TIME}) {
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(base).inflate(30), m -> m.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE)
					.forEach(Entity::discard);
				for (int i = 0; i < 4; i++) {
					var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					z.snapTo(base.getX() - 3 + i * 2, base.getY(), base.getZ() + 5, 180, 0);
					level.addFreshEntity(z);
				}
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				p.setYRot(0);
				p.setXRot(10);
			});
			context.waitTicks(e == Edict.BORROWED_TIME ? 80 : 10);
			boolean ok = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				return EdictSystem.tryCast(p, PlayerState.of(p), e, 1.0F, true);
			});
			context.waitTicks(e == Edict.WEIGHTLESSNESS ? 70 : 25);
			context.takeScreenshot("p2-cap-" + e.id);
			String state = server.computeOnServer(s -> {
				List<String> out = new ArrayList<>();
				for (Mob m : s.overworld().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(base).inflate(30),
					m -> m.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE)) {
					out.add((int) m.getHealth() + "@" + m.blockPosition().getY());
				}
				return String.join(" ", out);
			});
			Afterworld.LOG.info("PHASE2TEST capstone {}: cast={} zombies {}", e.id, ok, state);
		}
	}
}
