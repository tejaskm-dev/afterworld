package dev.afterworld.entity.boss.warden;

import dev.afterworld.aspect.Aspect;
import dev.afterworld.entity.boss.Hazards;
import dev.afterworld.entity.boss.WardenBoss;
import dev.afterworld.util.Fx;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Ashen Shogun: once the gentlest of men, now an oath walking in lacquered armour. A ghost
 * lantern hangs from his naginata. He cuts, he dashes in a line of ghostfire, he throws lanterns
 * that fall where you stand, and in his last act a hundred lanterns rain on the dojo.
 */
public class AshenShogun extends WardenBoss {
	private static final int SOUL = 0x7AFFD8;
	private static final List<Move> MOVES = List.of(
		new Move("cut", 12, 4, 14, 0, 4.5, 30, 1, 5, A_WINDUP, A_STRIKE),
		new Move("dash", 16, 8, 16, 5, 15, 80, 1, 4, A_WINDUP, A_SPIN),
		new Move("volley", 20, 4, 18, 5, 24, 110, 1, 3, A_CAST, A_THROW),
		new Move("iaido", 30, 4, 22, 0, 6, 160, 2, 3, A_WINDUP, A_SPIN),
		new Move("hundred", 30, 10, 30, 0, 32, 260, 3, 4, A_CAST, A_CAST));

	private Vec3 dashTo;

	public AshenShogun(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.BLUE);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 900.0).add(Attributes.ARMOR, 12.0).add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.SCALE, 1.3).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.32)
			.add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ATTACK_DAMAGE, 14.0).add(Attributes.STEP_HEIGHT, 1.2);
	}

	@Override
	protected int bit() {
		return 0;
	}

	@Override
	public Aspect aspect() {
		return Aspect.HUNDRED_LANTERNS;
	}

	@Override
	protected String wardenVoice() {
		return "shogun";
	}

	@Override
	protected int color() {
		return SOUL;
	}

	@Override
	protected double chaseSpeed() {
		return 1.15;
	}

	@Override
	protected List<Move> moves() {
		return MOVES;
	}

	private Vec3 blade() {
		return position().add(getLookAngle().scale(1.2)).add(0, getBbHeight() * 0.7, 0);
	}

	@Override
	protected void windup(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "cut" -> {
				Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, blade(), 8, 0.2, 0.02);
				Fx.hostileSound(level, position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.6F);
			}
			case "dash" -> {
				Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
				double len = Math.min(16, distanceTo(t) + 3);
				this.dashTo = position().add(dir.scale(len));
				Hazards.line(level, this, position(), dir, len, 2.4, m.windup(), 12.0F, SOUL);
				Fx.hostileSound(level, position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 0.7F);
			}
			case "volley", "hundred" -> {
				Fx.column(level, ParticleTypes.SOUL_FIRE_FLAME, position(), 30, getBbWidth(), getBbHeight() * 1.5);
				Fx.hostileSound(level, position(), SoundEvents.SOUL_ESCAPE.value(), 1.6F, 0.6F);
			}
			case "iaido" -> {
				// He sheathes the blade and the ground around him goes red. Get out, or parry.
				Hazards.strike(level, this, position(), 5.5, m.windup(), 18.0F, 0xFF5A4A, null);
				Fx.hostileSound(level, position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1.4F, 0.5F);
			}
			default -> {
			}
		}
	}

	@Override
	protected void release(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "cut" -> {
				Fx.hostileSound(level, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.8F);
				Fx.burst(level, ParticleTypes.SWEEP_ATTACK, position().add(getLookAngle().scale(2)).add(0, 1.4, 0), 3, 0.6, 0.0);
				for (ServerPlayer p : inCone(4.5, 60)) {
					if (strike(level, p, 14.0F, 0.8, 0.3)) {
						p.igniteForSeconds(3);
					}
				}
			}
			case "dash" -> {
				if (this.dashTo != null) {
					Fx.line(level, ParticleTypes.SOUL_FIRE_FLAME, position().add(0, 1, 0), this.dashTo.add(0, 1, 0), 0.5);
					teleportTo(this.dashTo.x, this.dashTo.y, this.dashTo.z);
					Fx.hostileSound(level, this.dashTo, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.7F);
				}
			}
			case "volley" -> {
				int n = this.phase >= 2 ? 5 : 3;
				for (int i = 0; i < n; i++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2;
					double r = i == 0 ? 0 : 1.5 + level.getRandom().nextDouble() * 2.5;
					Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
					Hazards.strike(level, this, at, 2.0, 22 + i * 4, 10.0F, SOUL, ParticleTypes.SOUL_FIRE_FLAME);
				}
			}
			case "iaido" -> {
				Fx.hostileSound(level, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
				Fx.ring(level, ParticleTypes.SWEEP_ATTACK, position().add(0, 1.2, 0), 4.5, 16);
			}
			case "hundred" -> {
				for (int i = 0; i < 14; i++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2;
					double r = level.getRandom().nextDouble() * 14;
					Vec3 at = this.home.add(Math.cos(a) * r, 0, Math.sin(a) * r);
					Hazards.strike(level, this, at, 2.2, 20 + level.getRandom().nextInt(40), 10.0F, SOUL, ParticleTypes.SOUL_FIRE_FLAME);
				}
				for (ServerPlayer p : playersNear(32)) {
					for (int k = 0; k < 3; k++) {
						Hazards.strike(level, this, p.position(), 2.0, 20 + k * 15, 10.0F, SOUL, ParticleTypes.SOUL_FIRE_FLAME);
					}
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		if (phase == 3) {
			// The lantern circle: two arms of ghostfire sweep the dojo while he fights.
			Hazards.blades(level, this, this.home, 11, 2, 0.02, 40, 400, 8.0F, SOUL);
		}
	}
}
