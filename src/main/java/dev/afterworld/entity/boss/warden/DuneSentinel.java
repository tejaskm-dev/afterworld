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
 * The Dune Sentinel: a sand golem a thousand years asleep under the Sun Temple, its fists floating
 * on chains. They fall where you stand; its chains drag you in; in its second act the sand itself
 * turns round it; in its last, three arms of sand sweep the temple floor.
 */
public class DuneSentinel extends WardenBoss {
	private static final int SAND = 0xE8B33D;
	private static final List<Move> MOVES = List.of(
		new Move("fists", 18, 3, 16, 0, 26, 60, 1, 5, A_CAST, A_SLAM),
		new Move("stomp", 20, 3, 18, 0, 7, 60, 1, 4, A_RAISE, A_SLAM),
		new Move("chains", 16, 3, 14, 6, 20, 120, 1, 3, A_THROW, A_THROW),
		new Move("vortex", 20, 60, 20, 0, 14, 200, 2, 3, A_CAST, A_CAST));

	public DuneSentinel(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 14.0).add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.SCALE, 1.5).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ATTACK_DAMAGE, 16.0).add(Attributes.STEP_HEIGHT, 1.6);
	}

	@Override
	protected int bit() {
		return 3;
	}

	@Override
	public Aspect aspect() {
		return Aspect.CHAINS_OF_THE_DUNE;
	}

	@Override
	protected String wardenVoice() {
		return "sentinel";
	}

	@Override
	protected int color() {
		return SAND;
	}

	@Override
	protected double preferredRange() {
		return 4.5;
	}

	@Override
	protected double chaseSpeed() {
		return 0.9;
	}

	@Override
	protected List<Move> moves() {
		return MOVES;
	}

	@Override
	protected void windup(ServerLevel level, Move m, LivingEntity t) {
		Fx.hostileSound(level, position(), SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.4F);
		Fx.burst(level, ParticleTypes.CLOUD, position().add(0, getBbHeight() * 0.5, 0), 10, getBbWidth() * 0.6, 0.02);
		if (m.id().equals("fists")) {
			int n = this.phase >= 3 ? 4 : 2;
			for (int i = 0; i < n; i++) {
				Vec3 at = t.position().add(i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 8, 0, i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 8);
				Hazards.strike(level, this, at, 2.6, m.windup() + 8 + i * 5, 16.0F, SAND, ParticleTypes.CLOUD);
			}
		}
	}

	@Override
	protected void release(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "stomp" -> {
				Hazards.wave(level, this, position(), 9, 0.5, 14.0F, SAND);
				Fx.shake(level, position(), 32, 4.0F, 14);
			}
			case "chains" -> {
				Fx.line(level, ParticleTypes.CRIT, position().add(0, getBbHeight() * 0.6, 0), t.position().add(0, 1, 0), 0.4);
				Fx.hostileSound(level, position(), SoundEvents.CHAIN_BREAK, 1.6F, 0.5F);
				if (t instanceof ServerPlayer p && strike(level, p, 6.0F, 0, 0)) {
					Vec3 pull = position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.6);
					p.setDeltaMovement(pull.x, 0.45, pull.z);
					p.syncVelocity = true;
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this);
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void during(ServerLevel level, Move m, int t, LivingEntity target) {
		if (!m.id().equals("vortex")) {
			return;
		}
		if (t % 4 == 0) {
			Fx.ring(level, Fx.dust(SAND, 1.6F), position().add(0, 0.5 + (t % 20) * 0.1, 0), 3 + (t % 12) * 0.8, 24);
		}
		for (ServerPlayer p : playersNear(14)) {
			Vec3 pull = position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.07);
			p.push(pull.x, 0, pull.z);
			p.syncVelocity = true;
		}
		if (t == m.active() - 1) {
			Hazards.wave(level, this, position(), 8, 0.6, 12.0F, SAND);
		}
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		if (phase == 3) {
			Hazards.blades(level, this, this.home, 14, 3, 0.025, 40, 300, 8.0F, SAND);
		}
	}
}
