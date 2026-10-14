package dev.afterworld;

import dev.afterworld.edict.EdictSystem;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.AwTabs;
import dev.afterworld.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Afterworld implements ModInitializer {
	public static final String MOD_ID = "afterworld";
	public static final Logger LOG = LoggerFactory.getLogger("Afterworld");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		AwConfig.load();
		AwAttachments.init();
		dev.afterworld.registry.AwSounds.init();
		dev.afterworld.registry.AwParticles.init();
		dev.afterworld.registry.AwComponents.init();
		AwBlocks.init();
		dev.afterworld.world.gen.AwFeatures.init();
		dev.afterworld.world.wonder.Wonders.init();
		dev.afterworld.world.atlas.Atlas.init();
		dev.afterworld.registry.AwEffects.init();
		AwItems.init();
		dev.afterworld.registry.AwGoods.init();
		dev.afterworld.registry.AwGear.init();
		dev.afterworld.economy.Values.init();
		AwEntities.init();
		AwTabs.init();
		AwPayloads.register();
		Scheduler.init();
		EdictSystem.init();
		dev.afterworld.world.Settlements.init();
		dev.afterworld.world.SpawnFinder.init();
		dev.afterworld.world.Camp.init();
		dev.afterworld.world.WorldSim.init();
		dev.afterworld.site.Builds.init();
		dev.afterworld.site.Sites.init();
		dev.afterworld.rumour.Rumours.init();
		dev.afterworld.player.Bonds.init();
		dev.afterworld.net.JournalServer.init();
		dev.afterworld.player.Welcome.init();
		dev.afterworld.player.Soulbound.init();
		dev.afterworld.entity.creature.Bestiary.init();
		dev.afterworld.entity.boss.Hazards.init();
		dev.afterworld.player.Arrival.init();
		dev.afterworld.player.Titles.init();
		dev.afterworld.command.AwCommands.init();
		dev.afterworld.player.Rewards.init();
		dev.afterworld.entity.boss.PilgrimSystem.init();
		dev.afterworld.entity.boss.BellRingerSystem.init();
		dev.afterworld.entity.boss.VaultSystem.init();
		dev.afterworld.site.Unremembered.init();
		dev.afterworld.registry.AwLoot.init();
		dev.afterworld.npc.Npcs.init();
		dev.afterworld.npc.Dialogue.init();
		dev.afterworld.npc.Barks.init();
		dev.afterworld.quest.Quests.init();
		dev.afterworld.world.Waystones.init();
		dev.afterworld.block.Kindling.init();
		dev.afterworld.minigame.Trials.init();
		dev.afterworld.dungeon.Ladder.init();
		dev.afterworld.economy.Board.init();
		dev.afterworld.economy.Merchants.init();
		dev.afterworld.economy.Coins.init();
		dev.afterworld.economy.Shop.init();
		dev.afterworld.economy.Bank.init();
		dev.afterworld.combat.Combat.init();
		dev.afterworld.combat.Projectiles.init();
		dev.afterworld.movement.Stamina.init();
		dev.afterworld.movement.Moves.init();
		dev.afterworld.player.CharacterServer.init();
		dev.afterworld.entity.TrainingDummy.init();
		if (Boolean.getBoolean("afterworld.selftest")) {
			// Used by the production test task: run every system once, then stop the server.
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				var lines = dev.afterworld.command.SelfTest.run(server.overworld());
				boolean passed = lines.get(lines.size() - 1).contains("PASSED");
				LOG.info("Production self-test {}", passed ? "PASSED" : "FAILED");
				dev.afterworld.util.Scheduler.after(200, () -> server.halt(false));
			});
		}
		LOG.info("Afterworld initialised: the world is listening.");
	}
}
