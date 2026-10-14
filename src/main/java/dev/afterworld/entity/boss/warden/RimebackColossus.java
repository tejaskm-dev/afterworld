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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Rimeback Colossus: the snow standing up. Swipes that throw you, slams that roll the ground
 * out in a wave (jump it), boulders of ice, a freezing breath, a charge; at the end, a blizzard.
 */
public class RimebackColossus extends WardenBoss {
	private static final int ICE = 0x8AE0FF;
	private static final List<Move> MOVES = List.of(
		new Move("swipe", 14, 3, 14, 0, 5.5, 30, 1, 5, A_WINDUP, A_STRIKE),
		new Move("slam", 22, 3, 20, 0, 9, 70, 1, 4, A_RAISE, A_SLAM),
		new Move("boulder", 22, 3, 16, 8, 28, 80, 1, 4, A_THROW, A_THROW),
		new Move("charge", 18, 10, 20, 8, 20, 120, 1, 3, A_WINDUP, A_SPIN),
		new Move("breath", 16, 30, 16, 0, 9, 140, 2, 3, A_CAST, A_CAST));

	private Vec3 chargeTo;

	public RimebackColossus(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.WHITE);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 1024.0).add(Attributes.ARMOR, 10.0).add(Attributes.SCALE, 1.4)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 18.0).add(Attributes.STEP_HEIGHT, 1.6);
	}

	@Override
	protected int bit() {
		return 2;
	}

	@Override
	public Aspect aspect() {
		return Aspect.WHITE_WINTER;
	}

	@Override
	protected int color() {
		return ICE;
	}

	@Override
	protected double preferredRange() {
		return 3.5;
	}

	@Override
	protected List<Move> moves() {
		return MOVES;
	}

	@Override
	protected void windup(ServerLevel level, Move m, LivingEntity t) {
		Fx.hostileSound(level, position(), SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.5F);
		if (m.id().equals("charge")) {
			Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
			this.chargeTo = position().add(dir.scale(16));
			Hazards.line(level, this, position(), dir, 16, 3.4, m.windup(), 18.0F, ICE);
		}
	}

	@Override
	protected void release(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "swipe" -> {
				Fx.hostileSound(level, position(), SoundEvents.RAVAGER_ATTACK, 1.4F, 0.7F);
				for (ServerPlayer p : inCone(5.5, 65)) {
					strike(level, p, 16.0F, 1.2, 0.4);
				}
			}
			case "slam" -> {
				Hazards.wave(level, this, position(), 12, 0.5, 14.0F, ICE);
				Fx.shake(level, position(), 32, 4.0F, 16);
				Fx.hostileSound(level, position(), SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.6F);
			}
			case "boulder" -> {
				int n = this.phase >= 2 ? 3 : 1;
				for (int i = 0; i < n; i++) {
					Vec3 at = t.position().add(i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 7, 0, i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 7);
					Hazards.strike(level, this, at, 3.0, 26 + i * 6, 16.0F, ICE, ParticleTypes.ITEM_SNOWBALL);
				}
				Fx.hostileSound(level, position(), SoundEvents.SNOW_GOLEM_SHOOT, 1.6F, 0.5F);
			}
			case "charge" -> {
				if (this.chargeTo != null) {
					lunge(this.chargeTo);
					Fx.hostileSound(level, position(), SoundEvents.RAVAGER_ROAR, 1.6F, 0.7F);
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void during(ServerLevel level, Move m, int t, LivingEntity target) {
		if (m.id().equals("charge") && this.chargeTo != null && t < 8) {
			lunge(this.chargeTo);
		}
		if (m.id().equals("breath") && t % 5 == 0) {
			Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0, getYRot());
			for (int k = 1; k <= 9; k += 2) {
				Fx.burst(level, ParticleTypes.SNOWFLAKE, position().add(look.scale(k)).add(0, 1.5, 0), 6, k * 0.15, 0.02);
			}
			for (ServerPlayer p : inCone(9, 30)) {
				p.hurtServer(level, damageSources().freeze(), 2.0F);
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), this);
				p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 30));
			}
		}
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		if (phase == 3) {
			// The blizzard: the whole hollow freezes; keep moving, keep fighting.
			Hazards.zone(level, this.home, 14, 20, 300, ICE, p -> {
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), this);
				p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 20, p.getTicksFrozen() + 12));
			});
		}
	}
}
