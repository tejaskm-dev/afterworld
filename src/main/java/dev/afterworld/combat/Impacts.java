package dev.afterworld.combat;

import dev.afterworld.Afterworld;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import java.util.Locale;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * What a blow feels like. Every creature is made of something (the entity type tags
 * {@code afterworld:material/*}, flesh when in none), and a hit on it sounds and breaks like that
 * material: chips of stone, sparks off metal, shards of crystal, splinters, bone dust, a wisp of
 * spirit. A critical hit adds a ringing edge, and the one who lands a killing blow hears it land.
 */
public final class Impacts {
	public enum Material {
		METAL, STONE, CRYSTAL, WOOD, BONE, CHITIN, SPIRIT, SLIME, ICE, SCALE, HIDE, FLESH;

		final String id = name().toLowerCase(Locale.ROOT);
		final TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, Afterworld.id("material/" + id));
	}

	private static final Material[] ORDER = Material.values();

	private Impacts() {
	}

	public static Material of(LivingEntity e) {
		for (Material m : ORDER) {
			if (m != Material.FLESH && e.is(m.tag)) {
				return m;
			}
		}
		return Material.FLESH;
	}

	/** A player's blow landing on {@code victim} for {@code amount}. */
	public static void hit(ServerLevel level, LivingEntity victim, ServerPlayer by, float amount, boolean crit, boolean killed) {
		Material m = of(victim);
		Vec3 mid = victim.getBoundingBox().getCenter();
		Vec3 toward = by.getEyePosition().subtract(mid);
		Vec3 at = toward.lengthSqr() > 1.0E-4 ? mid.add(toward.normalize().scale(victim.getBbWidth() * 0.5)) : mid;
		float weight = Mth.clamp(amount / 8.0F, 0.35F, 1.5F);
		// Big bodies sound deeper; every hit varies a little so a flurry never repeats.
		float size = Mth.clamp(victim.getBbHeight() * victim.getBbWidth(), 0.2F, 6.0F);
		float pitch = (1.12F - size * 0.04F) * (0.92F + level.getRandom().nextFloat() * 0.16F);
		level.playSound(null, at.x, at.y, at.z, AwSounds.impact(m.id), victim.getSoundSource(), 0.55F + 0.45F * weight, pitch);
		if (crit) {
			level.playSound(null, at.x, at.y, at.z, AwSounds.IMPACT_CRIT.value(), SoundSource.PLAYERS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		}
		chips(level, m, at, weight * (crit ? 1.5F : 1.0F));
		// A spark of light where the blow lands, bigger for a critical or a killing blow.
		int glow = glow(m);
		Vfx.flare(level, at, (crit ? 0.75F : 0.42F) * (0.8F + 0.4F * weight) * (killed ? 1.5F : 1.0F), crit || killed, glow, crit ? 7 : 5);
		if (crit) {
			Vfx.ring(level, at, toward.lengthSqr() > 1.0E-4 ? toward : new Vec3(0, 1, 0), 0.9F, 0.18F, glow, 6);
		}
		if (killed) {
			Fx.privateSound(by, AwSounds.IMPACT_KILL, 0.7F, 1.0F);
		}
	}

	/** The colour of the light a blow strikes from each material. */
	private static int glow(Material m) {
		return switch (m) {
			case METAL -> 0xFFC070;
			case CRYSTAL -> 0xE0C8FF;
			case SPIRIT -> 0xC0F0FF;
			case ICE -> 0xC8F4FF;
			case SLIME -> 0xB8F0A0;
			case STONE, BONE -> 0xF0EAD8;
			default -> 0xFFE6D8;
		};
	}

	private static void chips(ServerLevel level, Material m, Vec3 at, float weight) {
		int n = Math.max(2, Math.round(6 * weight));
		switch (m) {
			case METAL -> {
				Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, at, n, 0.08, 0.35);
				Fx.burst(level, Fx.dust(0xFFB347, 0.6F), at, n / 2, 0.12, 0.05);
			}
			case STONE -> Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at, n + 2, 0.12, 0.15);
			case CRYSTAL -> {
				Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), at, n, 0.12, 0.15);
				Fx.burst(level, ParticleTypes.END_ROD, at, Math.max(1, n / 3), 0.1, 0.03);
			}
			case WOOD -> Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), at, n + 2, 0.12, 0.15);
			case BONE -> {
				Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at, n, 0.12, 0.15);
				Fx.burst(level, ParticleTypes.CRIT, at, n / 2, 0.1, 0.2);
			}
			case CHITIN -> {
				Fx.burst(level, ParticleTypes.CRIT, at, n, 0.1, 0.25);
				Fx.burst(level, Fx.dust(0x2C3324, 0.8F), at, n / 2, 0.12, 0.02);
			}
			case SPIRIT -> {
				Fx.burst(level, ParticleTypes.WHITE_ASH, at, n * 2, 0.2, 0.02);
				Fx.burst(level, ParticleTypes.SOUL, at, Math.max(1, n / 3), 0.15, 0.02);
			}
			case SLIME -> Fx.burst(level, new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL), at, n, 0.12, 0.12);
			case ICE -> {
				Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at, n, 0.12, 0.15);
				Fx.burst(level, ParticleTypes.SNOWFLAKE, at, n / 2, 0.12, 0.04);
			}
			case SCALE -> {
				Fx.burst(level, ParticleTypes.CRIT, at, n, 0.1, 0.25);
				Fx.burst(level, Fx.dust(0x3E6E5E, 0.7F), at, n / 2, 0.12, 0.02);
			}
			case HIDE -> {
				Fx.burst(level, ParticleTypes.CRIT, at, n, 0.1, 0.25);
				Fx.burst(level, Fx.dust(0x5E4330, 0.8F), at, n / 2, 0.14, 0.02);
			}
			case FLESH -> {
				Fx.burst(level, ParticleTypes.CRIT, at, n, 0.1, 0.25);
				Fx.burst(level, Fx.dust(0x6E1C1C, 0.7F), at, n / 2, 0.12, 0.02);
			}
		}
	}
}
