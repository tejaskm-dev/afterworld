package dev.afterworld.client.particle;

import dev.afterworld.registry.AwParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** One particle class, six behaviours. Glowing kinds ignore world light. */
public final class AwParticle extends SingleQuadParticle {
	public enum Kind {
		RUNE(true, false), MOTE(true, false), EMBER(true, false), SHARD(false, true), GEAR(false, true), INK(false, false);

		final boolean glows;
		final boolean animated;

		Kind(boolean glows, boolean animated) {
			this.glows = glows;
			this.animated = animated;
		}
	}

	private final Kind kind;
	private final SpriteSet sprites;
	private final float baseSize;
	private final float spin;
	private final float phase;

	AwParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, ColorParticleOption color, SpriteSet sprites, Kind kind, RandomSource random) {
		super(level, x, y, z, xa, ya, za, sprites.get(random));
		this.kind = kind;
		this.sprites = sprites;
		this.setColor(color.getRed(), color.getGreen(), color.getBlue());
		this.phase = random.nextFloat() * Mth.TWO_PI;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		switch (kind) {
			case RUNE -> {
				this.lifetime = 28 + random.nextInt(14);
				this.quadSize = 0.16F + random.nextFloat() * 0.08F;
				this.gravity = -0.02F;
				this.friction = 0.92F;
				this.hasPhysics = false;
				this.spin = (random.nextFloat() - 0.5F) * 0.06F;
			}
			case MOTE -> {
				this.lifetime = 40 + random.nextInt(50);
				this.quadSize = 0.05F + random.nextFloat() * 0.06F;
				this.gravity = -0.002F;
				this.friction = 0.96F;
				this.hasPhysics = false;
				this.spin = 0;
			}
			case EMBER -> {
				this.lifetime = 30 + random.nextInt(30);
				this.quadSize = 0.04F + random.nextFloat() * 0.04F;
				this.gravity = -0.015F;
				this.friction = 0.95F;
				this.spin = 0;
			}
			case SHARD -> {
				this.lifetime = 30 + random.nextInt(20);
				this.quadSize = 0.07F + random.nextFloat() * 0.05F;
				this.gravity = 0.6F;
				this.friction = 0.98F;
				this.spin = (random.nextFloat() - 0.5F) * 0.5F;
			}
			case GEAR -> {
				this.lifetime = 30 + random.nextInt(20);
				this.quadSize = 0.08F + random.nextFloat() * 0.05F;
				this.gravity = 0.8F;
				this.friction = 0.98F;
				this.spin = (random.nextBoolean() ? 1 : -1) * (0.2F + random.nextFloat() * 0.3F);
			}
			default -> {
				this.lifetime = 24 + random.nextInt(16);
				this.quadSize = 0.12F + random.nextFloat() * 0.1F;
				this.gravity = -0.005F;
				this.friction = 0.9F;
				this.hasPhysics = false;
				this.spin = (random.nextFloat() - 0.5F) * 0.04F;
			}
		}
		this.baseSize = this.quadSize;
	}

	@Override
	public void tick() {
		super.tick();
		this.oRoll = this.roll;
		this.roll += this.spin;
		float t = this.age / (float) this.lifetime;
		switch (this.kind) {
			case RUNE -> {
				this.alpha = t < 0.2F ? t / 0.2F : 1 - (t - 0.2F) / 0.8F;
				this.quadSize = this.baseSize * (0.8F + 0.4F * t);
			}
			case MOTE -> {
				this.alpha = (0.5F + 0.5F * Mth.sin(this.age * 0.3F + this.phase)) * (1 - t * t);
				this.xd += Mth.sin(this.age * 0.1F + this.phase) * 0.0015;
				this.zd += Mth.cos(this.age * 0.1F + this.phase) * 0.0015;
			}
			case EMBER -> {
				this.alpha = 1 - t;
				this.xd += (this.random.nextFloat() - 0.5F) * 0.004;
				this.zd += (this.random.nextFloat() - 0.5F) * 0.004;
			}
			case INK -> {
				this.alpha = 0.9F * (1 - t);
				this.quadSize = this.baseSize * (1 + t * 1.4F);
			}
			default -> this.alpha = t > 0.7F ? (1 - t) / 0.3F : 1;
		}
		if (this.kind.animated) {
			this.setSpriteFromAge(this.sprites);
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float a) {
		return this.kind.glows ? 15728880 : super.getLightCoords(a);
	}

	public static void registerProviders() {
		provider(AwParticles.RUNE, Kind.RUNE);
		provider(AwParticles.MOTE, Kind.MOTE);
		provider(AwParticles.EMBER, Kind.EMBER);
		provider(AwParticles.SHARD, Kind.SHARD);
		provider(AwParticles.GEAR, Kind.GEAR);
		provider(AwParticles.INK, Kind.INK);
	}

	private static void provider(ParticleType<ColorParticleOption> type, Kind kind) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (ParticleProvider<ColorParticleOption>)
			(options, level, x, y, z, xa, ya, za, random) -> new AwParticle(level, x, y, z, xa, ya, za, options, sprites, kind, random));
	}
}
