package dev.afterworld.test;

import dev.afterworld.client.screen.ArrivalScreen;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Garb;
import dev.afterworld.player.Origin;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Answers the arrival's questions one by one, then looks at the traveller in the world. */
public class AfterworldArrivalTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			context.waitFor(mc -> mc.gui.screen() instanceof ArrivalScreen, 400);
			context.waitTicks(50);
			context.takeScreenshot("arrival-0-intro");
			screen(context, ArrivalScreen::skipIntro);
			screen(context, s -> s.selectOrigin(Origin.GUILD_CARTOGRAPHER));
			context.waitTicks(60);
			context.takeScreenshot("arrival-1-origin");
			screen(context, ArrivalScreen::confirm);
			screen(context, s -> s.selectDiscipline(Discipline.KEEPER));
			context.waitTicks(50);
			context.takeScreenshot("arrival-2-discipline");
			screen(context, ArrivalScreen::confirm);
			screen(context, s -> s.selectAffinity(Edict.ECHOES));
			context.waitTicks(60);
			context.takeScreenshot("arrival-3-affinity");
			screen(context, ArrivalScreen::confirm);
			screen(context, s -> s.setName("Wren"));
			context.waitTicks(60);
			context.takeScreenshot("arrival-4-name");
			screen(context, ArrivalScreen::confirm);
			screen(context, s -> s.selectGarb(new Garb(1, 5, 4, 1, 1, 3, 9, 3, true)));
			context.waitTicks(60);
			context.takeScreenshot("arrival-5-garb-head");
			screen(context, s -> s.selectCategory(1));
			context.waitTicks(50);
			context.takeScreenshot("arrival-6-garb-cloak");
			screen(context, ArrivalScreen::confirm);
			context.waitTicks(60);
			context.takeScreenshot("arrival-7-confirm");
			screen(context, ArrivalScreen::confirm);
			context.waitTicks(40);
			context.takeScreenshot("arrival-8-outro");
			context.waitFor(mc -> mc.gui.screen() == null, 300);
			context.waitTicks(20);
			context.takeScreenshot("arrival-9-dawn");
			context.waitTicks(40);
			sp.getServer().runCommand("time set 13000");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			sp.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("arrival-10-world-front");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("arrival-11-world-back");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(mc -> mc.gui.setScreen(new ArrivalScreen(true, Discipline.NONE)));
			context.waitTicks(50);
			context.takeScreenshot("arrival-12-wardrobe");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void screen(ClientGameTestContext context, Consumer<ArrivalScreen> action) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof ArrivalScreen s) {
				action.accept(s);
			}
		});
	}
}
