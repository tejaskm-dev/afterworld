package dev.afterworld.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.client.AfterworldClient;
import dev.afterworld.client.screen.ArrivalScreen;
import dev.afterworld.client.screen.JournalScreen;
import dev.afterworld.registry.AwItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * Plays the menus the way a person does: keys, typing, dragging and clicking, rather than calling
 * the screens directly. Covers the creator (turning the figure, typing a name), the Journal's
 * section ribbons, and the Journal staying in the pack through a death.
 */
public class AfterworldInputTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
			var input = context.getInput();
			context.waitFor(mc -> mc.gui.screen() instanceof ArrivalScreen, 400);
			context.waitTicks(40);
			// Intro (finish typing, then continue), then origin, discipline and affinity with Enter.
			for (int i = 0; i < 5 && step(context) < 4; i++) {
				input.pressKey(InputConstants.KEY_RETURN);
				context.waitTicks(25);
			}
			check(step(context) == 4, "reached the name step with Enter, at step " + step(context));
			boolean taking = context.computeOnClient(mc -> textInputOn(mc));
			check(taking, "the window takes text input on the name step");
			input.typeChars("Wren");
			context.waitTicks(5);
			String typed = context.computeOnClient(mc -> mc.gui.screen() instanceof ArrivalScreen s ? s.nameForTest() : "?");
			check(typed.equals("Wren"), "typed name arrived: '" + typed + "'");
			input.pressKey(InputConstants.KEY_BACKSPACE);
			context.waitTicks(2);
			typed = context.computeOnClient(mc -> mc.gui.screen() instanceof ArrivalScreen s ? s.nameForTest() : "?");
			check(typed.equals("Wre"), "backspace removed a letter: '" + typed + "'");
			input.typeChars("n");
			input.pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(25);
			boolean stopped = context.computeOnClient(mc -> !textInputOn(mc));
			check(stopped, "text input is released after the name step");
			// Drag the figure round on the stage.
			float yaw0 = context.computeOnClient(mc -> ((ArrivalScreen) mc.gui.screen()).yawForTest());
			int[] c = context.computeOnClient(mc -> ((ArrivalScreen) mc.gui.screen()).stageCenterForTest());
			double[] w = toWindow(context, c[0], c[1]);
			input.setCursorPos(w[0], w[1]);
			context.waitTick();
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			for (int i = 0; i < 6; i++) {
				input.moveCursor(25, 0);
				context.waitTick();
			}
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			float yaw1 = context.computeOnClient(mc -> ((ArrivalScreen) mc.gui.screen()).yawForTest());
			check(Math.abs(yaw1 - yaw0) > 40, "dragging turned the figure: " + yaw0 + " -> " + yaw1);
			context.takeScreenshot("input-creator-turned");
			// Garb, then confirm.
			for (int i = 0; i < 4 && context.computeOnClient(mc -> mc.gui.screen() instanceof ArrivalScreen); i++) {
				input.pressKey(InputConstants.KEY_RETURN);
				context.waitTicks(30);
			}
			context.waitFor(mc -> mc.gui.screen() == null, 400);
			String named = sp.getServer().computeOnServer(s -> dev.afterworld.player.PlayerState.of(s.getPlayerList().getPlayers().get(0)).travellerName);
			check("Wren".equals(named), "the traveller's name reached the world: " + named);
			context.waitTicks(40);

			// The Journal: open with its key, then click each section's ribbon.
			input.pressKey(AfterworldClient.JOURNAL);
			context.waitFor(mc -> mc.gui.screen() instanceof JournalScreen, 100);
			context.waitTicks(15);
			for (int tab = 1; tab < 5; tab++) {
				int t = tab;
				double[] r = context.computeOnClient(mc -> ((JournalScreen) mc.gui.screen()).ribbonForTest(t));
				double[] rw = toWindow(context, r[0], r[1]);
				input.setCursorPos(rw[0], rw[1]);
				context.waitTick();
				input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
				context.waitTicks(12);
				int now = context.computeOnClient(mc -> ((JournalScreen) mc.gui.screen()).tabForTest());
				check(now == t, "clicking ribbon " + t + " opened section " + now);
			}
			context.takeScreenshot("input-journal-clicked");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// Death: the Journal stays in the pack, ordinary things spill.
			var server = sp.getServer();
			server.runCommand("gamerule keep_inventory false");
			server.runCommand("give @p minecraft:dirt 5");
			server.runCommand("item replace entity @p weapon.offhand with afterworld:unfinished_compass");
			context.waitTicks(5);
			server.runCommand("kill @p");
			context.waitTicks(20);
			context.runOnClient(mc -> mc.player.respawn());
			// On a busy machine the respawn can take longer than a fixed wait: wait for it.
			context.waitFor(mc -> mc.player != null && mc.player.isAlive(), 400);
			context.waitTicks(20);
			String after = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var inv = p.getInventory();
				return "alive " + p.isAlive() + ", health " + p.getHealth() + ", journal " + inv.contains(st -> st.is(AwItems.JOURNAL)) + ", compass "
					+ inv.contains(st -> st.is(AwItems.UNFINISHED_COMPASS)) + ", dirt " + inv.contains(st -> st.is(net.minecraft.world.item.Items.DIRT));
			});
			Afterworld.LOG.info("INPUTTEST after the death: {}", after);
			boolean kept = server.computeOnServer(s -> {
				var inv = s.getPlayerList().getPlayers().get(0).getInventory();
				return inv.contains(st -> st.is(AwItems.JOURNAL)) && inv.contains(st -> st.is(AwItems.UNFINISHED_COMPASS))
					&& !inv.contains(st -> st.is(net.minecraft.world.item.Items.DIRT));
			});
			check(kept, "the Journal and the compass came back after death; the dirt did not");
		}
	}

	private static int step(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof ArrivalScreen s ? s.stepForTest() : -1);
	}

	private static double[] toWindow(ClientGameTestContext context, double guiX, double guiY) {
		return context.computeOnClient(mc -> new double[] {
			guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
			guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight()});
	}

	private static boolean textInputOn(Minecraft mc) {
		try {
			var f = mc.textInputManager().getClass().getDeclaredField("textInputEnabled");
			f.setAccessible(true);
			return f.getBoolean(mc.textInputManager());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("INPUTTEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
