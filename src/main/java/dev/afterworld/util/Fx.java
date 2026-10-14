package dev.afterworld.util;

import net.minecraft.server.level.ServerPlayer;
import dev.afterworld.AwConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Particle and sound helpers that respect the particle density setting. */
public final class Fx {
	private static final Int2ObjectOpenHashMap<DustParticleOptions> DUST = new Int2ObjectOpenHashMap<>();

	private Fx() {
	}

	public static DustParticleOptions dust(int rgb, float scale) {
		int key = (rgb & 0xFFFFFF) | (Math.round(scale * 4) << 24);
		synchronized (DUST) {
			return DUST.computeIfAbsent(key, k -> new DustParticleOptions(rgb & 0xFFFFFF, scale));
		}
	}

	public static int scaled(int count) {
		double d = AwConfig.get().particleDensity;
		if (d <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(count * d));
	}

	public static void burst(ServerLevel level, ParticleOptions p, double x, double y, double z, int count, double spread, double speed) {
		int n = scaled(count);
		if (n > 0) {
			level.sendParticles(p, x, y, z, n, spread, spread, spread, speed);
		}
	}

	public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
		burst(level, p, at.x, at.y, at.z, count, spread, speed);
	}

	/** A ring of rising rune glyphs in a colour, around a point at the feet. */
	public static void runeRing(ServerLevel level, Vec3 at, int color, double radius, int count) {
		int n = scaled(count);
		var rune = dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.RUNE, color);
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(rune, at.x + Math.cos(a) * radius, at.y + 0.1, at.z + Math.sin(a) * radius, 0, -Math.cos(a) * 0.2, 1.0, -Math.sin(a) * 0.2, 0.06);
		}
	}

	public static void column(ServerLevel level, ParticleOptions p, Vec3 at, int count, double width, double height) {
		int n = scaled(count);
		if (n > 0) {
			level.sendParticles(p, at.x, at.y + height / 2, at.z, n, width, height / 2, width, 0.01);
		}
	}

	/** A flat ring of particles; {@code points} is scaled by density. */
	public static void ring(ServerLevel level, ParticleOptions p, Vec3 center, double radius, int points) {
		int n = scaled(points);
		for (int i = 0; i < n; i++) {
			double a = (Math.PI * 2 * i) / n;
			level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** A dotted line between two points, one particle every {@code step} blocks. */
	public static void line(ServerLevel level, ParticleOptions p, Vec3 from, Vec3 to, double step) {
		double dist = from.distanceTo(to);
		if (dist < 1e-3) {
			return;
		}
		double d = AwConfig.get().particleDensity;
		if (d <= 0) {
			return;
		}
		double s = step / Math.max(0.25, d);
		int n = Mth.clamp((int) (dist / s), 1, 96);
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			level.sendParticles(p, Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z), 1, 0, 0, 0, 0);
		}
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound.value(), SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, BlockPos at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
	}

	public static void hostileSound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	public static void hostileSound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound.value(), SoundSource.HOSTILE, volume, pitch);
	}

	/** A sound only this player hears, such as a failed cast. */
	public static void privateSound(ServerPlayer p, Holder<SoundEvent> sound, float volume, float pitch) {
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound, SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
	}

	/** Shakes the camera of every player near {@code at}, less with distance. */
	public static void shake(ServerLevel level, Vec3 at, double radius, float strength, int ticks) {
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(at);
			if (d < radius && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, dev.afterworld.net.AwPayloads.Shake.TYPE)) {
				float s = strength * (float) (1.0 - d / radius * 0.7);
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.afterworld.net.AwPayloads.Shake(s, ticks));
			}
		}
	}
}
