package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.client.minigame.TrialScreens;
import dev.afterworld.client.screen.ArrivalScreen;
import dev.afterworld.client.screen.BankScreen;
import dev.afterworld.client.screen.BoardScreen;
import dev.afterworld.client.screen.CharacterScreen;
import dev.afterworld.client.screen.DialogueScreen;
import dev.afterworld.client.screen.EdictWheelScreen;
import dev.afterworld.client.screen.JournalScreen;
import dev.afterworld.client.screen.ShopScreen;
import dev.afterworld.client.screen.WaystoneScreen;
import dev.afterworld.economy.Bank;
import dev.afterworld.economy.Board;
import dev.afterworld.economy.Shop;
import dev.afterworld.economy.Wallet;
import dev.afterworld.npc.Dialogue;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.world.Waystones;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every screen at the window sizes and GUI scales players actually have: the smallest the game
 * allows (320 by 240), a laptop on automatic scale, 1080p at scale 4 and at scale 2. Screenshots
 * only; the point is to look at them.
 */
public class AfterworldUiTest implements FabricClientGameTest {
	private record Size(String name, int w, int h, int gui) {
	}

	/** gui 0 is "auto": the largest scale the window allows. */
	private static final Size[] SIZES = {
		new Size("640x480-2x", 640, 480, 2),
		new Size("1080p-1x", 1920, 1080, 1),
		new Size("1080p-3x", 1920, 1080, 3),
		new Size("1080p-auto", 1920, 1080, 0),
		new Size("mac-7x", 2880, 1800, 7),
		new Size("mac-1x", 2880, 1800, 1),
	};

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
			// Something in the purse and the pack, so the shop and the ledger have numbers to show.
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				Wallet.add(p, 12345);
				p.getInventory().add(new ItemStack(Items.IRON_INGOT, 20));
				p.getInventory().add(new ItemStack(Items.BREAD, 12));
				p.getInventory().add(new ItemStack(Items.LEATHER, 9));
			});
			// Some of the skill tree grown, and Insight to spend, so the tree shows every kind of node.
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				dev.afterworld.player.PlayerState st = dev.afterworld.player.PlayerState.of(p);
				st.insight = 7;
				for (String k : new String[] {"blade.edge", "blade.might1", "blade.flow", "arcanum.attune", "arcanum.res1", "wayfarer.endurance"}) {
					st.skills.add(k);
				}
				dev.afterworld.player.CharacterServer.send(p);
			});
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender(false, 2400);
			int[] keepers = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				BlockPos at = p.blockPosition();
				Mannequin bram = Npcs.spawn(level, Npc.BRAM, at.offset(2, 0, 2), 0);
				Mannequin isolde = Npcs.spawn(level, Npc.ISOLDE, at.offset(-2, 0, 2), 0);
				return new int[] {bram.getId(), isolde.getId()};
			});
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
			if (board != null) {
				// Carry a notice, so the board's right-hand column has something in it.
				server.runOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayers().get(0);
					Board.open(p, board);
				});
				context.waitFor(mc -> mc.gui.screen() instanceof BoardScreen, 100);
				context.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new dev.afterworld.net.AwPayloads.BoardAction(dev.afterworld.net.AwPayloads.BoardAction.TAKE, 0, board)));
				context.waitTicks(10);
				context.runOnClient(mc -> mc.gui.setScreen(null));
			}
			for (Size size : SIZES) {
				context.getInput().resizeWindow(size.w(), size.h());
				context.runOnClient(mc -> {
					mc.options.guiScale().set(size.gui());
					mc.resizeGui();
				});
				context.waitTicks(5);
				context.runOnClient(mc -> mc.gui.toastManager().clear());
				context.getInput().setCursorPos(2, 2);
				String n = "ui-" + size.name() + "-";
				Afterworld.LOG.info("UITEST size {} gui {}x{}", size.name(), context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth()),
					context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight()));
				context.takeScreenshot(n + "hud");
				// Some way off, looking away: the tracker's arrow and distance, and the arrow round the crosshair.
				BlockPos home = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
				int ax = home.getX() + 40;
				int az = home.getZ() + 30;
				int ay = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az));
				server.runCommand(String.format("tp @p %d %d %d 60 0", ax, ay, az));
				context.waitTicks(25);
				context.takeScreenshot(n + "hud-away");
				server.runCommand(String.format("tp @p %d %d %d", home.getX(), home.getY(), home.getZ()));
				context.waitTicks(10);
				// Bram's counter: a ware picked, the haggling tactics out.
				server.runOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayers().get(0);
					Shop.open(p, s.overworld().getEntity(keepers[0]), Npc.BRAM);
				});
				context.waitFor(mc -> mc.gui.screen() instanceof ShopScreen, 100);
				context.runOnClient(mc -> ((ShopScreen) mc.gui.screen()).selectForTest(1, true));
				context.waitTicks(8);
				context.takeScreenshot(n + "shop");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				context.waitTicks(3);
				// The Guild Ledger.
				server.runOnServer(s -> Bank.open(s.getPlayerList().getPlayers().get(0)));
				context.waitFor(mc -> mc.gui.screen() instanceof BankScreen, 100);
				context.waitTicks(8);
				context.takeScreenshot(n + "bank");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				// The Warden's Board.
				if (board != null) {
					server.runOnServer(s -> Board.open(s.getPlayerList().getPlayers().get(0), board));
					context.waitFor(mc -> mc.gui.screen() instanceof BoardScreen, 100);
					context.waitTicks(8);
					context.takeScreenshot(n + "board");
					context.runOnClient(mc -> mc.gui.setScreen(null));
				}
				// The road between waystones.
				server.runOnServer(s -> Waystones.openTravel(s.getPlayerList().getPlayers().get(0), null, false));
				context.waitFor(mc -> mc.gui.screen() instanceof WaystoneScreen, 100);
				context.waitTicks(5);
				context.takeScreenshot(n + "waystones");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				// The Character screen: Aspects and Edicts.
				context.runOnClient(mc -> CharacterScreen.open());
				context.waitTicks(10);
				context.takeScreenshot(n + "character-aspects");
				context.runOnClient(mc -> ((CharacterScreen) mc.gui.screen()).tabForTest(1));
				context.waitTicks(3);
				context.takeScreenshot(n + "character-skills");
				// The skill tree, whole, then drawn in close on one limb.
				context.runOnClient(mc -> {
					dev.afterworld.client.screen.SkillTreeScreen.viewForTest(0.0F, 3.0F, 1.0F);
					mc.gui.setScreen(new dev.afterworld.client.screen.SkillTreeScreen((CharacterScreen) mc.gui.screen()));
				});
				context.waitTicks(5);
				context.takeScreenshot(n + "skilltree");
				context.runOnClient(mc -> dev.afterworld.client.screen.SkillTreeScreen.viewForTest(-3.4F, 3.6F, 1.6F));
				context.waitTicks(3);
				context.takeScreenshot(n + "skilltree-close");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				context.runOnClient(mc -> CharacterScreen.open());
				context.waitTicks(5);
				context.runOnClient(mc -> ((CharacterScreen) mc.gui.screen()).tabForTest(2));
				context.waitTicks(3);
				context.takeScreenshot(n + "character-edicts");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				// The Warden's Ladder's gate, part climbed.
				context.runOnClient(mc -> {
					net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
					t.putInt("best", 13);
					t.putInt("resume", 11);
					t.putInt("top", 50);
					t.putInt("party", 1);
					mc.gui.setScreen(new dev.afterworld.client.screen.LadderScreen(t));
				});
				context.waitTicks(5);
				context.takeScreenshot(n + "ladder");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				// The journal.
				context.runOnClient(mc -> JournalScreen.open(0));
				context.waitTicks(15);
				context.takeScreenshot(n + "journal-edicts");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				// A conversation: mid-line, then the answers.
				server.runOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayers().get(0);
					Dialogue.start(p, (Mannequin) s.overworld().getEntity(keepers[1]), Npc.ISOLDE);
				});
				context.waitFor(mc -> mc.gui.screen() instanceof DialogueScreen, 100);
				context.waitTicks(30);
				context.takeScreenshot(n + "dialogue");
				for (int i = 0; i < 12; i++) {
					boolean more = context.computeOnClient(mc -> mc.gui.screen() instanceof DialogueScreen d && d.lineForTest() < linesOf(d) - 1);
					if (!more) {
						break;
					}
					context.runOnClient(mc -> ((DialogueScreen) mc.gui.screen()).advanceForTest());
					context.waitTicks(2);
				}
				context.runOnClient(mc -> {
					if (mc.gui.screen() instanceof DialogueScreen d) {
						d.advanceForTest();
					}
				});
				context.waitTicks(12);
				context.takeScreenshot(n + "dialogue-answers");
				context.runOnClient(mc -> {
					if (mc.gui.screen() instanceof DialogueScreen d) {
						d.closeQuietly();
					}
				});
				context.waitTicks(3);
				// The Edict wheel, a bench trial and the wardrobe.
				context.runOnClient(mc -> mc.gui.setScreen(new EdictWheelScreen()));
				context.waitTicks(8);
				context.takeScreenshot(n + "wheel");
				context.runOnClient(mc -> {
					CompoundTag data = new CompoundTag();
					data.putInt("seed", 7);
					mc.gui.setScreen(TrialScreens.create("melody", data));
				});
				context.waitTicks(30);
				context.takeScreenshot(n + "trial");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				context.runOnClient(mc -> mc.gui.setScreen(new ArrivalScreen(true, Discipline.NONE)));
				context.waitTicks(40);
				context.takeScreenshot(n + "wardrobe");
				context.runOnClient(mc -> mc.gui.setScreen(null));
				context.waitTicks(3);
			}
			if (board != null) {
				// The Warden's Board in the town, seen from the plaza it faces.
				BlockPos anchor = server.computeOnServer(s -> dev.afterworld.block.BoardBlock.anchor(s.overworld(), board));
				context.runOnClient(mc -> {
					if (!mc.gui.hud.isHidden()) {
						mc.gui.hud.toggle();
					}
				});
				server.runCommand("gamemode spectator @p");
				// Out in front of it (whichever way the town was turned), a little to one side.
				net.minecraft.core.Direction face = server.computeOnServer(s -> s.overworld().getBlockState(anchor)
					.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
				net.minecraft.core.Direction side = face.getClockWise();
				int ex = anchor.getX() + face.getStepX() * 10 + side.getStepX() * 5;
				int ez = anchor.getZ() + face.getStepZ() * 10 + side.getStepZ() * 5;
				int lx = anchor.getX() - side.getStepX();
				int lz = anchor.getZ() - side.getStepZ();
				server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", ex, anchor.getY() + 2, ez, lx, anchor.getY() + 2, lz));
				context.waitTicks(40);
				sp.getConnection().waitForChunksRender(false, 2400);
				context.takeScreenshot("town-board");
			}
			Afterworld.LOG.info("UITEST done");
		}
	}

	private static int linesOf(DialogueScreen d) {
		try {
			var f = DialogueScreen.class.getDeclaredField("page");
			f.setAccessible(true);
			return ((dev.afterworld.net.AwPayloads.DialogueShow) f.get(d)).lines().size();
		} catch (ReflectiveOperationException e) {
			return 1;
		}
	}
}
