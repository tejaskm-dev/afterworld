package dev.afterworld.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;

/** Photographs the menus: the title screen and the world-loading screen. */
public class AfterworldTitleTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(40);
		context.takeScreenshot("menu-title");
		context.runOnClient(mc -> mc.gui.setScreen(new LevelLoadingScreen(new LevelLoadTracker(), LevelLoadingScreen.Reason.OTHER)));
		context.waitTicks(20);
		context.takeScreenshot("menu-loading-world");
		context.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		context.waitTicks(5);
	}
}
