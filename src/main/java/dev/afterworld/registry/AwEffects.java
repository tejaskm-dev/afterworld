package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Afterworld's status effects. */
public final class AwEffects {
	/** Caught in a stopped moment: no thinking, no moving, no striking until it passes. */
	public static final Holder<MobEffect> STOPPED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Afterworld.id("stopped"),
		new Plain(MobEffectCategory.HARMFUL, 0xE8D7A0));

	/** Starlight tea: Resonance builds a fifth faster and stamina returns sooner. */
	public static final Holder<MobEffect> STARLIT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Afterworld.id("starlit"),
		new Plain(MobEffectCategory.BENEFICIAL, 0xB8C8FF));

	private AwEffects() {
	}

	public static void init() {
	}

	private static final class Plain extends MobEffect {
		Plain(MobEffectCategory category, int color) {
			super(category, color);
		}
	}
}
