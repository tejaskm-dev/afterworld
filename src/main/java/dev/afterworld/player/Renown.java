package dev.afterworld.player;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Renown: the traveller's standing in the world, levels 1 to 30. Earned mostly from the story,
 * also from discoveries, contracts, elites and the great powers. Each level grants an Insight
 * point for the Discipline's skill tree; every fifth level adds a heart and a little strength.
 */
public final class Renown {
	public static final int MAX = 30;
	private static final Identifier HEALTH = dev.afterworld.Afterworld.id("renown_health");
	private static final Identifier DAMAGE = dev.afterworld.Afterworld.id("renown_damage");

	private Renown() {
	}

	/** Renown needed to go from level {@code l} to {@code l + 1}. */
	public static long step(int l) {
		return 120L * l + 30L * l * l;
	}

	public static int level(PlayerState s) {
		int l = 1;
		long xp = s.renown;
		while (l < MAX && xp >= step(l)) {
			xp -= step(l);
			l++;
		}
		return l;
	}

	/** Progress into the current level, 0..1. */
	public static float progress(PlayerState s) {
		int l = 1;
		long xp = s.renown;
		while (l < MAX && xp >= step(l)) {
			xp -= step(l);
			l++;
		}
		return l >= MAX ? 1.0F : xp / (float) step(l);
	}

	public static void grant(ServerPlayer p, int amount, String why) {
		if (amount <= 0) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		int before = level(s);
		s.renown += amount;
		int after = level(s);
		s.hudDirty = true;
		if (after > before) {
			s.insight += after - before;
			apply(p, s);
			Fx.burst(p.level(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 30, 0.5, 0.25);
			Fx.privateSound(p, AwSounds.UI_DISCOVERY, 1.0F, 1.3F);
			if (ServerPlayNetworking.canSend(p, AwPayloads.Title.TYPE)) {
				ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.RENOWN, Component.translatable("renown.afterworld.level", after),
					Component.translatable("renown.afterworld.insight", s.insight), 0xE8C873));
			}
			Progress.legend(p, "milestone", "Reached Renown " + after + ".");
		}
		Progress.notice(p, AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.renown", amount, Component.literal(why)));
	}

	/** The stat growth that comes with Renown: a heart every five levels, +2% damage too. */
	public static void apply(ServerPlayer p, PlayerState s) {
		int fifths = level(s) / 5;
		set(p.getAttribute(Attributes.MAX_HEALTH), HEALTH, fifths * 2.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, fifths * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void set(AttributeInstance a, Identifier id, double v, AttributeModifier.Operation op) {
		if (a == null) {
			return;
		}
		a.removeModifier(id);
		if (v != 0) {
			a.addPermanentModifier(new AttributeModifier(id, v, op));
		}
	}
}
