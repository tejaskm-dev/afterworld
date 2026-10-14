package dev.afterworld.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import dev.afterworld.registry.AwSounds;
import com.mojang.blaze3d.platform.InputConstants;
import dev.afterworld.Afterworld;
import dev.afterworld.AwHooks;
import dev.afterworld.client.hud.EdictHud;
import dev.afterworld.client.hud.Notices;
import dev.afterworld.client.render.ThrownWeaponRenderer;
import dev.afterworld.client.screen.JournalScreen;
import dev.afterworld.item.Tooltipped;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class AfterworldClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Afterworld.id("afterworld"));
	public static final KeyMapping[] SLOT_KEYS = {
		key("edict_1", InputConstants.KEY_R),
		key("edict_2", InputConstants.KEY_V),
		key("edict_3", InputConstants.KEY_Z),
		key("edict_4", InputConstants.KEY_B)
	};
	public static final KeyMapping SIGNATURE = key("signature", InputConstants.KEY_H);
	public static final KeyMapping JOURNAL = key("journal", InputConstants.KEY_J);
	public static final KeyMapping WHEEL = key("wheel", InputConstants.KEY_X);
	public static final KeyMapping FOCUS = key("focus", InputConstants.KEY_G);
	public static final KeyMapping DODGE = key("dodge", InputConstants.KEY_LALT);
	public static final KeyMapping ULTIMATE = key("ultimate", InputConstants.KEY_C);
	public static final KeyMapping ASPECT = key("aspect", InputConstants.KEY_N);
	public static final KeyMapping CHARACTER = key("character", InputConstants.KEY_K);

	private static int signatureHeld;

	private static KeyMapping key(String name, int code) {
		return new KeyMapping("key.afterworld." + name, InputConstants.Type.KEYBOARD, code, CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		for (KeyMapping k : SLOT_KEYS) {
			KeyMappingHelper.registerKeyMapping(k);
		}
		KeyMappingHelper.registerKeyMapping(SIGNATURE);
		KeyMappingHelper.registerKeyMapping(JOURNAL);
		KeyMappingHelper.registerKeyMapping(WHEEL);
		KeyMappingHelper.registerKeyMapping(FOCUS);
		KeyMappingHelper.registerKeyMapping(DODGE);
		KeyMappingHelper.registerKeyMapping(ULTIMATE);
		KeyMappingHelper.registerKeyMapping(ASPECT);
		KeyMappingHelper.registerKeyMapping(CHARACTER);
		dev.afterworld.movement.Moves.CLIENT_WATER_WALK = ClientMoves::waterWalk;
		dev.afterworld.item.gear.SetArmorItem.VIEWER = () -> Minecraft.getInstance().player;

		AwHooks.openJournal = JournalScreen::open;
		dev.afterworld.client.particle.AwParticle.registerProviders();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			ClientState.tick();
			ClientMoves.tick(mc);
			dev.afterworld.client.hud.TitleCards.tickRegion(mc);
			dev.afterworld.client.hud.Voices.tick();
			// The arrival screen waits until the world has finished loading and nothing else is open.
			if (ClientState.pendingArrival != null && mc.gui.screen() == null) {
				var open = ClientState.pendingArrival;
				ClientState.pendingArrival = null;
				mc.gui.setScreen(new dev.afterworld.client.screen.ArrivalScreen(open.wardrobe(), dev.afterworld.player.Discipline.byOrdinal(open.discipline())));
			}
			for (int i = 0; i < SLOT_KEYS.length; i++) {
				while (SLOT_KEYS[i].consumeClick()) {
					if (i < ClientState.slotCount) {
						ClientPlayNetworking.send(new AwPayloads.Cast(i));
					}
				}
			}
			// Tap for the signature; hold (half a second) for its second form.
			boolean sigClicked = false;
			while (SIGNATURE.consumeClick()) {
				sigClicked = true;
			}
			if (SIGNATURE.isDown()) {
				if (++signatureHeld == 10) {
					ClientPlayNetworking.send(new AwPayloads.Cast(AwPayloads.Cast.SIGNATURE_HELD));
				}
			} else {
				if (signatureHeld > 0 && signatureHeld < 10 || sigClicked && signatureHeld == 0) {
					ClientPlayNetworking.send(new AwPayloads.Cast(AwPayloads.Cast.SIGNATURE));
				}
				signatureHeld = 0;
			}
			while (WHEEL.consumeClick()) {
				if (mc.gui.screen() == null) {
					mc.gui.setScreen(new dev.afterworld.client.screen.EdictWheelScreen());
				}
			}
			while (ULTIMATE.consumeClick()) {
				if (ClientState.aspect >= 0 && ClientState.resonance >= 100) {
					ClientPlayNetworking.send(new AwPayloads.MoveAction(AwPayloads.MoveAction.ULTIMATE, 0, 0));
				} else {
					ClientPlayNetworking.send(new AwPayloads.MoveAction(AwPayloads.MoveAction.ULTIMATE, 0, 0));
					mc.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.EDICT_FAIL.value(), 1.0F, 0.4F));
				}
			}
			while (ASPECT.consumeClick()) {
				ClientPlayNetworking.send(new AwPayloads.MoveAction(AwPayloads.MoveAction.NEXT_ASPECT, 0, 0));
			}
			while (CHARACTER.consumeClick()) {
				if (mc.gui.screen() == null) {
					dev.afterworld.client.screen.CharacterScreen.open();
				}
			}
			while (FOCUS.consumeClick()) {
				dev.afterworld.client.hud.QuestHud.cycleFocus();
				mc.getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.UI_SELECT.value(), 1.6F, 0.3F));
			}
			while (JOURNAL.consumeClick()) {
				if (mc.gui.screen() == null) {
					JournalScreen.open();
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Hud.TYPE, (payload, ctx) -> {
			int[] before = ClientState.cdRemain.clone();
			ClientState.apply(payload);
			for (int i = 0; i < 4; i++) {
				if (before[i] > 0 && payload.cdRemain()[i] == 0 && payload.slots()[i] >= 0) {
					EdictHud.pulse(payload.slots()[i]);
					ctx.client().getSoundManager().play(SimpleSoundInstance.forUI(AwSounds.EDICT_READY.value(), 1.0F + i * 0.06F, 0.5F));
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Journal.TYPE, (payload, ctx) -> {
			if (payload.tab() >= 0 && payload.tab() < ClientState.journal.length) {
				ClientState.journal[payload.tab()] = payload.data();
				ClientState.journalVersion++;
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.OpenArrival.TYPE, (payload, ctx) -> ClientState.pendingArrival = payload);
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Shake.TYPE, (payload, ctx) -> dev.afterworld.client.fx.Shake.kick(payload.strength(), payload.ticks()));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Title.TYPE,
			(payload, ctx) -> dev.afterworld.client.hud.TitleCards.show(payload.style(), payload.title(), payload.subtitle(), payload.color()));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Notice.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.level != null) {
				ClientState.notice(payload.kind(), payload.text(), mc.level.getGameTime());
				var sound = switch (payload.kind()) {
					case AwPayloads.Notice.CODEX -> AwSounds.UI_DISCOVERY;
					case AwPayloads.Notice.RUMOUR -> AwSounds.UI_RUMOUR;
					case AwPayloads.Notice.CHRONICLE, AwPayloads.Notice.WORLD -> AwSounds.UI_CHRONICLE;
					case AwPayloads.Notice.EDICT -> AwSounds.EDICT_READY;
					case AwPayloads.Notice.ADAPT -> AwSounds.BOSS_LOOP;
					default -> AwSounds.UI_SELECT;
				};
				mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), 1.0F, 0.6F));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.DialogueShow.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			var screen = mc.gui.screen();
			if (payload.close()) {
				if (screen instanceof dev.afterworld.client.screen.DialogueScreen d) {
					d.closeQuietly();
				}
				return;
			}
			if (screen instanceof dev.afterworld.client.screen.DialogueScreen d && d.entity() == payload.entity()) {
				d.show(payload);
			} else {
				mc.gui.setScreen(new dev.afterworld.client.screen.DialogueScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.QuestSync.TYPE, (payload, ctx) -> dev.afterworld.client.hud.QuestHud.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.WaystoneList.TYPE,
			(payload, ctx) -> ctx.client().gui.setScreen(new dev.afterworld.client.screen.WaystoneScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.WalletSync.TYPE,
			(payload, ctx) -> dev.afterworld.client.hud.WalletHud.apply(payload.marks(), payload.banked(), payload.delta()));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.ShopView.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof dev.afterworld.client.screen.ShopScreen shop && shop.entity() == payload.entity()) {
				shop.update(payload);
			} else {
				mc.gui.setScreen(new dev.afterworld.client.screen.ShopScreen(payload));
			}
			dev.afterworld.client.hud.WalletHud.apply(payload.wallet(), dev.afterworld.client.hud.WalletHud.banked, 0);
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.BankView.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof dev.afterworld.client.screen.BankScreen bank) {
				bank.update(payload);
			} else {
				mc.gui.setScreen(new dev.afterworld.client.screen.BankScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.MoveSync.TYPE, (payload, ctx) -> {
			ClientState.stamina = payload.stamina();
			ClientState.staminaMax = payload.max();
			ClientState.gifts = payload.gifts();
			ClientState.sliding = payload.sliding();
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.AspectSync.TYPE, (payload, ctx) -> {
			ClientState.aspect = payload.aspect();
			ClientState.resonance = payload.resonance();
			ClientState.aspectsOwned = payload.owned();
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.VoiceLine.TYPE, (payload, ctx) -> dev.afterworld.client.hud.Voices.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.CharacterView.TYPE,
			(payload, ctx) -> dev.afterworld.client.screen.CharacterScreen.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Cinematic.TYPE, (payload, ctx) -> dev.afterworld.client.fx.Cinematics.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.DamageNumber.TYPE, (payload, ctx) -> dev.afterworld.client.hud.DamageNumbers.add(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.PoseSync.TYPE, (payload, ctx) -> dev.afterworld.client.render.PlayerPoses.remote(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Vfx.TYPE, (payload, ctx) -> dev.afterworld.client.fx.VfxRenderer.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Act.TYPE, (payload, ctx) -> dev.afterworld.client.render.PlayerPoses.act(payload));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.BoardOpen.TYPE, (payload, ctx) -> {
			var mc = ctx.client();
			if (mc.gui.screen() instanceof dev.afterworld.client.screen.BoardScreen board) {
				board.update(payload.data());
			} else {
				mc.gui.setScreen(new dev.afterworld.client.screen.BoardScreen(payload.data()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.LadderOpen.TYPE,
			(payload, ctx) -> ctx.client().gui.setScreen(new dev.afterworld.client.screen.LadderScreen(payload.data())));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.LadderState.TYPE, (payload, ctx) -> dev.afterworld.client.hud.LadderHud.set(payload.data()));
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.Fade.TYPE,
			(payload, ctx) -> dev.afterworld.client.fx.Fade.start(payload.in(), payload.hold(), payload.out(), payload.color()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			dev.afterworld.client.render.PlayerPoses.clear();
			dev.afterworld.client.fx.VfxRenderer.clear();
			dev.afterworld.client.hud.LadderHud.reset();
			ClientState.reset();
			dev.afterworld.client.hud.TitleCards.reset();
			dev.afterworld.client.hud.QuestHud.reset();
			dev.afterworld.client.hud.WalletHud.reset();
			ClientMoves.reset();
			dev.afterworld.client.hud.PowerHud.reset();
			dev.afterworld.client.hud.DamageNumbers.reset();
			dev.afterworld.client.fx.Cinematics.reset();
			dev.afterworld.client.hud.VoiceSubtitles.reset();
			dev.afterworld.client.hud.Voices.reset();
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Afterworld.id("edicts"), EdictHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Afterworld.id("notices"), Notices::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Afterworld.id("title_cards"), dev.afterworld.client.hud.TitleCards::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Afterworld.id("quest"), (g, d) -> {
			if (dev.afterworld.AwConfig.get().questTracker) {
				dev.afterworld.client.hud.QuestHud.render(g, d);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Afterworld.id("wallet"), dev.afterworld.client.hud.WalletHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Afterworld.id("ladder"), dev.afterworld.client.hud.LadderHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Afterworld.id("powers"), dev.afterworld.client.hud.PowerHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Afterworld.id("damage_numbers"), dev.afterworld.client.hud.DamageNumbers::render);
		HudElementRegistry.attachElementAfter(Afterworld.id("title_cards"), Afterworld.id("cinematics"), dev.afterworld.client.fx.Cinematics::render);
		HudElementRegistry.attachElementAfter(Afterworld.id("cinematics"), Afterworld.id("voices"), dev.afterworld.client.hud.VoiceSubtitles::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Afterworld.id("flash"), dev.afterworld.client.fx.ScreenFlash::render);
		HudElementRegistry.attachElementAfter(Afterworld.id("cinematics"), Afterworld.id("fade"), dev.afterworld.client.fx.Fade::render);
		dev.afterworld.client.minigame.TrialClient.init();
		dev.afterworld.client.fx.QuestBeam.register();
		dev.afterworld.client.fx.VfxRenderer.register();

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof Tooltipped t) {
				java.util.List<Component> extra = new java.util.ArrayList<>();
				t.lore(stack, extra::add);
				lines.addAll(Math.min(1, lines.size()), extra);
			}
			if (dev.afterworld.player.Soulbound.is(stack)) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.afterworld.soulbound").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
			}
		});

		EntityRenderers.register(AwEntities.THROWN_WEAPON, ThrownWeaponRenderer::new);
		EntityRenderers.register(AwEntities.CRYSTAL_SHARD, ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0F, true));
		dev.afterworld.client.render.WildRenderers.register();
		dev.afterworld.client.render.WardenRenderers.register();
		EntityRenderers.register(AwEntities.MAGIC_PROJECTILE, ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0F, true));
		AfterworldModels.register();
	}
}
