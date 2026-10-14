package dev.afterworld.test;

import dev.afterworld.client.hud.TitleCards;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;

/** Edict cast effects and title cards, photographed. */
public class AfterworldFxTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.ARCANIST);
			var server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 14000");
			server.runCommand("effect give @p night_vision 999 0 true");
			server.runCommand("afterworld learn all");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.gui.hud.getChat().clearMessages(false);
			});
			sp.getConnection().waitForChunksRender();
			context.waitTicks(20);
			server.runCommand("execute as @p run afterworld cast stillness");
			context.waitTicks(6);
			context.takeScreenshot("fx-cast-stillness");
			context.waitTicks(30);
			server.runCommand("execute as @p run afterworld cast witness");
			context.waitTicks(8);
			context.takeScreenshot("fx-cast-witness");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.gui.hud.getChat().clearMessages(false);
				TitleCards.show(AwPayloads.Title.PLACE, Component.literal("The Crown Bastion"), Component.translatable("title.afterworld.site.bastion"), 0xC9A227);
			});
			context.waitTicks(30);
			context.takeScreenshot("fx-title-place");
			context.runOnClient(mc -> TitleCards.show(AwPayloads.Title.BOSS, Component.translatable("entity.afterworld.bell_ringer"),
				Component.translatable("boss.afterworld.bell_ringer.epithet"), 0xE8A33D));
			context.waitTicks(30);
			context.takeScreenshot("fx-title-boss");
			context.waitTicks(100);
		}
	}
}
