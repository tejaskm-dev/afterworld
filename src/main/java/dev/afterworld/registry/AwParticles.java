package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Afterworld's particles. All take a colour, so one sprite serves every Edict and biome. */
public final class AwParticles {
	/** A glyph that rises and fades: Edict casts, rules taking hold. */
	public static final ParticleType<ColorParticleOption> RUNE = register("rune");
	/** A soft drifting light: Shardfields air, wisp trails, moth dust. */
	public static final ParticleType<ColorParticleOption> MOTE = register("mote");
	/** A flickering spark rising from the Ashen Moor's vents. */
	public static final ParticleType<ColorParticleOption> EMBER = register("ember");
	/** A spinning sliver of crystal that falls: shards, broken rules. */
	public static final ParticleType<ColorParticleOption> SHARD = register("shard");
	/** A tumbling brass gear: clockwork taking damage. */
	public static final ParticleType<ColorParticleOption> GEAR = register("gear");
	/** A spreading blot of dark ink: the Rule-Wraith, things forgotten. */
	public static final ParticleType<ColorParticleOption> INK = register("ink");

	private AwParticles() {
	}

	private static ParticleType<ColorParticleOption> register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Afterworld.id(name), FabricParticleTypes.complex(ColorParticleOption::codec, ColorParticleOption::streamCodec));
	}

	public static void init() {
	}

	public static ColorParticleOption of(ParticleType<ColorParticleOption> type, int rgb) {
		return ColorParticleOption.create(type, 0xFF000000 | rgb);
	}
}
