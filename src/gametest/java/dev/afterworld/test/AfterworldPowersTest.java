package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.aspect.Aspects;
import dev.afterworld.combat.Arts;
import dev.afterworld.entity.boss.Hazards;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.PlayerState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Powers in motion: a run of weapon Arts, Edicts, an Aspect and a boss's telegraphed blows on a
 * stage in daylight, from behind and in front of the traveller; then swings and an Art seen from
 * the traveller's own eyes. Photographs each at its telling moment.
 */
public class AfterworldPowersTest implements FabricClientGameTest {
	/** Art, its weapon, and the ticks after which to photograph it (one or two moments). */
	private record Shot(Arts.Art art, String item, int[] at) {
	}

	private static final Shot[] ARTS = {
		new Shot(Arts.Art.CINDER_CLEAVE, "emberbrand", new int[] {6, 17}),
		new Shot(Arts.Art.CRIMSON_REAP, "crimson_requiem", new int[] {5}),
		new Shot(Arts.Art.THUNDERCLAP, "thunderdrum_gauntlets", new int[] {4}),
		new Shot(Arts.Art.TOLL, "bellhammer", new int[] {7}),
		new Shot(Arts.Art.COIL_SLAM, "gearspring_maul", new int[] {3, 9}),
		new Shot(Arts.Art.FROST_LANCE, "rimefang_spear", new int[] {5}),
		new Shot(Arts.Art.GLYPH_BEAM, "glyphwarden_aegis", new int[] {13}),
		new Shot(Arts.Art.CHAKRAM, "mirrorglass_chakram", new int[] {6}),
		new Shot(Arts.Art.STOPPED_MOMENT, "second_hand", new int[] {8}),
		new Shot(Arts.Art.LANTERN_CUTS, "hitodama", new int[] {6}),
		new Shot(Arts.Art.AVALANCHE, "rimeback_cleaver", new int[] {10, 24}),
		new Shot(Arts.Art.RALLY, "ashen_warbanner", new int[] {14}),
		new Shot(Arts.Art.FANG_FLURRY, "wayfarers_fang", new int[] {11}),
		new Shot(Arts.Art.SPORE_LASH, "sporelash_whip", new int[] {6}),
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			// A flat stage in the sky.
			server.runCommand("gamemode creative @p");
			server.runCommand("tp @p 0 205 0");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender(false, 1200);
			server.runCommand("fill -26 199 -26 26 199 34 grass_block");
			server.runCommand("fill -26 200 -26 26 214 34 air");
			context.waitTicks(10);
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 999999 4 true");
			server.runCommand("effect give @p saturation 999999 0 true");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			for (Shot s : ARTS) {
				foes(server);
				server.runCommand("item replace entity @p weapon.mainhand with afterworld:" + s.item);
				server.runCommand("tp @p 0 200 -3 facing 0 201 6");
				context.waitTicks(8);
				server.runOnServer(m -> {
					ServerPlayer p = player(m);
					Arts.perform(s.art, p.level(), p, p.getMainHandItem());
				});
				int waited = 0;
				for (int i = 0; i < s.at.length; i++) {
					context.waitTicks(s.at[i] - waited);
					waited = s.at[i];
					context.takeScreenshot("powers-" + s.art.id + (s.at.length > 1 ? "-" + i : ""));
				}
				context.waitTicks(40);
				clear(server);
			}

			// Edicts, seen from in front: the body casting, the sigil underfoot.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			server.runCommand("afterworld learn all");
			for (String e : new String[] {"stillness", "weightlessness", "hunger"}) {
				foes(server);
				server.runCommand("tp @p 0 200 -3 facing 0 201 6");
				context.waitTicks(10);
				server.runCommand("execute as @p run afterworld cast " + e);
				context.waitTicks(5);
				context.takeScreenshot("powers-edict-" + e);
				context.waitTicks(60);
				clear(server);
			}

			// An Aspect let loose.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			foes(server);
			server.runCommand("tp @p 0 200 -3 facing 0 201 6");
			context.waitTicks(10);
			server.runOnServer(m -> {
				ServerPlayer p = player(m);
				Aspects.bind(p, Aspect.REQUIEM_TOLL);
				Aspects.equip(p, Aspect.REQUIEM_TOLL);
				PlayerState.of(p).resonance = Aspects.FULL;
				Aspects.cast(p);
			});
			context.waitTicks(6);
			context.takeScreenshot("powers-aspect-0");
			context.waitTicks(10);
			context.takeScreenshot("powers-aspect-1");
			context.waitTicks(60);
			clear(server);

			// A boss's blows: a circle filling before the strike, a shockwave to jump.
			foes(server);
			server.runCommand("tp @p 0 200 -6 facing 0 200 4");
			context.waitTicks(10);
			server.runOnServer(m -> {
				var level = m.overworld();
				var src = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(-20, 195, -20, 20, 210, 20)).get(0);
				Hazards.strike(level, src, new Vec3(-3, 200, 2), 3.0, 30, 0.0F, 0xE0503A, null);
				Hazards.strike(level, src, new Vec3(3.5, 200, 4), 2.5, 40, 0.0F, 0xE0503A, null);
			});
			context.waitTicks(16);
			context.takeScreenshot("powers-hazard-mark");
			context.waitTicks(16);
			context.takeScreenshot("powers-hazard-strike");
			server.runOnServer(m -> {
				var level = m.overworld();
				var src = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(-20, 195, -20, 20, 210, 20)).get(0);
				Hazards.wave(level, src, new Vec3(0, 200, 6), 14, 0.45, 0.0F, 0xE8A33D);
			});
			context.waitTicks(12);
			context.takeScreenshot("powers-hazard-wave");
			context.waitTicks(30);
			clear(server);

			// From the traveller's own eyes: three swings of a sword, then an Art. (The hand only
			// shows with the HUD on. The arrival's name card waits for a visible HUD, and the recipe
			// toasts pile up meanwhile: neither belongs in these pictures, so both are cleared, after
			// the maul has passed through the off hand to bring its own recipe toast forward.)
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
			server.runCommand("item replace entity @p weapon.offhand with afterworld:gearspring_maul");
			server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(5);
			context.runOnClient(mc -> {
				dev.afterworld.client.hud.TitleCards.reset();
				mc.gui.toastManager().clear();
			});
			// A block in reach, to see its outline drawn too.
			server.runCommand("setblock 1 200 -1 mossy_cobblestone");
			server.runCommand("tp @p 0 200 -3 facing 0.8 200.6 -1");
			context.waitTicks(10);
			for (int i = 0; i < 3; i++) {
				int k = i;
				context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false));
				context.waitTicks(2);
				context.takeScreenshot("powers-fp-swing-" + k);
				context.waitTicks(6);
			}
			server.runCommand("item replace entity @p weapon.mainhand with afterworld:gearspring_maul");
			context.waitTicks(4);
			server.runOnServer(m -> {
				ServerPlayer p = player(m);
				Arts.perform(Arts.Art.COIL_SLAM, p.level(), p, p.getMainHandItem());
			});
			context.waitTicks(4);
			context.takeScreenshot("powers-fp-slam-windup");
			context.waitTicks(4);
			context.takeScreenshot("powers-fp-slam-strike");
			context.waitTicks(20);

			// The same light at night.
			server.runCommand("time set 18000");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			foes(server);
			server.runCommand("item replace entity @p weapon.mainhand with afterworld:crimson_requiem");
			server.runCommand("tp @p 0 200 -3 facing 0 201 6");
			context.waitTicks(10);
			server.runOnServer(m -> {
				ServerPlayer p = player(m);
				Arts.perform(Arts.Art.CRIMSON_REAP, p.level(), p, p.getMainHandItem());
			});
			context.waitTicks(5);
			context.takeScreenshot("powers-night-reap");
			context.waitTicks(20);
			Afterworld.LOG.info("POWERSTEST done");
		}
	}

	/** Three creatures standing in a loose line ahead, holding still. */
	private static void foes(TestServerContext server) {
		server.runCommand("summon afterworld:mire_shambler -2.5 200 3 {NoAI:1b,Rotation:[180f,0f],attributes:[{id:\"minecraft:max_health\",base:400}],Health:400f}");
		server.runCommand("summon afterworld:mire_shambler 0.5 200 5 {NoAI:1b,Rotation:[180f,0f],attributes:[{id:\"minecraft:max_health\",base:400}],Health:400f}");
		server.runCommand("summon afterworld:mire_shambler 3.5 200 3.5 {NoAI:1b,Rotation:[180f,0f],attributes:[{id:\"minecraft:max_health\",base:400}],Health:400f}");
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(m -> {
			for (var e : m.overworld().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(-40, 190, -40, 40, 230, 40),
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				e.discard();
			}
		});
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}
}
