package dev.afterworld.client.minigame;

import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The traveller's side of a trial. Bench trials open their screen; field trials get a small panel
 * at the top of the screen: what to do, how far along, the sand running out, and an arrow to the
 * next goal. A banner says how it went.
 */
public final class TrialClient {
	private static final Set<String> BENCH = Set.of("smithing", "cooking", "brewing", "stars", "melody");
	private static final int INK = 0xFFE8DCC0;
	private static final int DIM = 0xFFA89A7C;
	private static final int GOLDEN = 0xFFFFD27A;

	private static String field;
	private static CompoundTag open = new CompoundTag();
	private static CompoundTag state = new CompoundTag();
	private static long stateAt;
	private static long warnAt;
	private static String bannerId;
	private static boolean bannerWon;
	private static long bannerAt;

	private TrialClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.TrialOpen.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (BENCH.contains(payload.id())) {
				mc.gui.setScreen(TrialScreens.create(payload.id(), payload.data()));
			} else {
				field = payload.id();
				open = payload.data();
				state = new CompoundTag();
				stateAt = now();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.TrialState.TYPE, (payload, ctx) -> {
			if (payload.id().equals(field)) {
				int before = state.getIntOr("phase", 0);
				state = payload.data();
				stateAt = now();
				if (field.equals("duel") && state.getIntOr("phase", 0) == 1 && before == 0) {
					warnAt = now();
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AwPayloads.TrialEnd.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof TrialScreens.Base b && b.id.equals(payload.id())) {
				b.closeQuietly();
			}
			if (payload.id().equals(field)) {
				field = null;
			}
			bannerId = payload.id();
			bannerWon = payload.success();
			bannerAt = now();
			mc.getSoundManager().play(SimpleSoundInstance.forUI(payload.success() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
				payload.success() ? 1.2F : 0.7F, 0.6F));
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Afterworld.id("trial"), TrialClient::render);
	}

	public static void reset() {
		field = null;
		bannerId = null;
	}

	private static long now() {
		return net.minecraft.util.Util.getMillis();
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		if (field != null) {
			panel(g, mc, font, w);
		}
		if (bannerId != null) {
			long age = now() - bannerAt;
			if (age > 3200) {
				bannerId = null;
			} else {
				float in = Mth.clamp(age / 250F, 0, 1) * Mth.clamp((3200 - age) / 400F, 0, 1);
				Component t = Component.translatable(bannerWon ? "trial.afterworld.banner.won" : "trial.afterworld.banner.lost",
					Component.translatable("trial.afterworld." + bannerId));
				int tw = font.width(t);
				int y = g.guiHeight() / 3;
				g.fill(w / 2 - tw / 2 - 14, y - 6, w / 2 + tw / 2 + 14, y + 14, ARGB.color((int) (190 * in), 0x0E1014));
				g.fill(w / 2 - tw / 2 - 14, y + 13, w / 2 + tw / 2 + 14, y + 14, ARGB.color((int) (255 * in), bannerWon ? 0xFFD27A : 0xD8505A));
				g.centeredText(font, t, w / 2, y, ARGB.color((int) (255 * in), bannerWon ? 0xFFE8A0 : 0xE8B0A8));
			}
		}
	}

	private static void panel(GuiGraphicsExtractor g, Minecraft mc, Font font, int w) {
		int goal = Math.max(1, open.getIntOr("goal", 1));
		int a = state.getIntOr("a", 0);
		int b = state.getIntOr("b", 0);
		int phase = state.getIntOr("phase", 0);
		int total = Math.max(1, open.getIntOr("time", 1));
		int left = state.contains("time") ? state.getIntOr("time", 0) - (int) ((now() - stateAt) / 50) : total;
		Component title = Component.translatable("trial.afterworld." + field);
		Component hint = Component.translatable("trial.afterworld." + field + ".hint");
		Component count = switch (field) {
			case "race" -> Component.translatable("trial.afterworld.race.count", a, goal);
			case "waterwalk" -> Component.translatable(phase == 0 ? "trial.afterworld.waterwalk.out" : "trial.afterworld.waterwalk.back");
			case "tracking" -> a >= goal ? Component.translatable("trial.afterworld.tracking.alpha_hint")
				: Component.translatable("trial.afterworld.tracking.count", a, goal);
			case "archery" -> Component.translatable("trial.afterworld.archery.count", a, b, goal, open.getIntOr("need", 6));
			case "duel" -> Component.translatable("trial.afterworld.duel.count", a, goal, b, open.getIntOr("lives", 3));
			case "sighting" -> Component.translatable("trial.afterworld.sighting.hold");
			default -> Component.empty();
		};
		int pw = Math.max(170, Math.max(font.width(hint), font.width(count)) + 24);
		int x = w / 2 - pw / 2;
		int y = 6;
		g.fill(x, y, x + pw, y + 44, 0xC80E1014);
		g.fill(x, y, x + pw, y + 1, GOLDEN);
		g.centeredText(font, title, w / 2, y + 4, GOLDEN);
		g.centeredText(font, count, w / 2, y + 16, INK);
		g.centeredText(font, hint, w / 2, y + 27, DIM);
		// The sand running out, or (for a sighting) the needle settling.
		float frac = field.equals("sighting") ? Mth.clamp(a / (float) goal, 0, 1) : Mth.clamp(left / (float) total, 0, 1);
		g.fill(x + 6, y + 39, x + pw - 6, y + 41, 0xFF262B31);
		g.fill(x + 6, y + 39, x + 6 + (int) ((pw - 12) * frac), y + 41, field.equals("sighting") ? 0xFF7FE3D2 : frac < 0.25F ? 0xFFD8505A : GOLDEN);
		// The arrow to the next goal.
		if (state.contains("tx") && mc.player != null) {
			double dx = state.getDoubleOr("tx", 0) - mc.player.getX();
			double dz = state.getDoubleOr("tz", 0) - mc.player.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
			float rel = Mth.wrapDegrees(yaw - mc.player.getYRot());
			int ax = x + pw + 14;
			int ay = y + 22;
			g.fill(ax - 11, ay - 11, ax + 11, ay + 11, 0xC80E1014);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ax, ay);
			pose.rotate(rel * Mth.DEG_TO_RAD);
			for (int i = 0; i < 7; i++) {
				g.fill(-i / 2 - 1, -7 + i, i / 2 + 1, -6 + i, GOLDEN);
			}
			g.fill(-1, 0, 1, 6, GOLDEN);
			pose.popMatrix();
			int dist = (int) Math.sqrt(dx * dx + dz * dz);
			g.centeredText(font, Component.literal(dist + "m"), ax, ay + 13, DIM);
		}
		// Akane's wind-up: the moment to parry.
		if (field.equals("duel") && phase == 1) {
			long age = now() - warnAt;
			float pulse = 0.6F + 0.4F * Mth.sin(age / 60F);
			Component warn = Component.translatable("trial.afterworld.duel.warn");
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(w / 2F, g.guiHeight() / 2F - 30);
			pose.scale(2F, 2F);
			g.centeredText(font, warn, 0, 0, ARGB.color((int) (255 * pulse), 0xFF6A5A));
			pose.popMatrix();
		}
	}
}
