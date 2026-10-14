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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Gilded Warden: the last guardian, a turtle of stone whose shell is written in gold. It
 * judges with beams of glyph-light, rolls in its shell, stamps the ground; it raises a golden
 * dome that turns arrows back; at the end it passes judgment on the whole vault.
 */
public class GildedWarden extends WardenBoss {
	private static final int GOLD = 0xFFD060;
	private static final List<Move> MOVES = List.of(
		new Move("beams", 20, 3, 16, 4, 28, 70, 1, 5, A_CAST, A_CAST),
		new Move("stomp", 18, 3, 18, 0, 7, 60, 1, 4, A_RAISE, A_SLAM),
		new Move("roll", 20, 10, 20, 0, 16, 110, 1, 3, A_SPIN, A_SPIN),
		new Move("dome", 16, 160, 10, 0, 40, 260, 2, 2, A_CAST, A_CAST),
		new Move("judgment", 30, 4, 30, 0, 40, 300, 3, 3, A_CAST, A_CAST));

	private Vec3 rollTo;
	private long domeUntil;

	public GildedWarden(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 1024.0).add(Attributes.ARMOR, 16.0).add(Attributes.ARMOR_TOUGHNESS, 6.0)
			.add(Attributes.SCALE, 1.5).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.18)
			.add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ATTACK_DAMAGE, 16.0).add(Attributes.STEP_HEIGHT, 1.6);
	}

	@Override
	protected int bit() {
		return 4;
	}

	@Override
	public Aspect aspect() {
		return Aspect.GLYPH_OF_JUDGMENT;
	}

	@Override
	protected String wardenVoice() {
		return "warden";
	}

	@Override
	protected int color() {
		return GOLD;
	}

	@Override
	protected double preferredRange() {
		return 5;
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
		Fx.runeRing(level, position().add(0, 0.2, 0), GOLD, getBbWidth() * 0.8, 18);
		Fx.hostileSound(level, position(), SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.6F);
		Vec3 from = position().add(0, 0.2, 0);
		Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
		switch (m.id()) {
			case "beams" -> {
				int n = this.phase >= 3 ? 5 : 3;
				for (int i = 0; i < n; i++) {
					float off = (i - (n - 1) / 2F) * 0.35F;
					Hazards.line(level, this, from, dir.yRot(off), 24, 1.8, m.windup(), 14.0F, GOLD);
				}
			}
			case "roll" -> {
				Vec3 d = dir.normalize();
				this.rollTo = position().add(d.scale(14));
				Hazards.line(level, this, from, d, 14, 3.8, m.windup(), 16.0F, GOLD);
			}
			default -> {
			}
		}
	}

	@Override
	protected void release(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "stomp" -> {
				Hazards.wave(level, this, position(), 10, 0.55, 14.0F, GOLD);
				Fx.shake(level, position(), 32, 4.0F, 14);
			}
			case "roll" -> {
				if (this.rollTo != null) {
					lunge(this.rollTo);
				}
			}
			case "dome" -> {
				this.domeUntil = level.getGameTime() + 160;
				Fx.hostileSound(level, position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
			}
			case "judgment" -> {
				Hazards.blades(level, this, this.home, 16, 4, 0.03, 30, 200, 12.0F, GOLD);
				for (ServerPlayer p : playersNear(40)) {
					Hazards.strike(level, this, p.position(), 2.6, 30, 14.0F, GOLD, ParticleTypes.END_ROD);
				}
				Fx.hostileSound(level, position(), SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.7F);
			}
			default -> {
			}
		}
	}

	@Override
	protected void during(ServerLevel level, Move m, int t, LivingEntity target) {
		if (m.id().equals("roll") && this.rollTo != null && t < 8) {
			lunge(this.rollTo);
			if (t == 7) {
				Hazards.wave(level, this, position(), 5, 0.5, 10.0F, GOLD);
			}
		}
		if (m.id().equals("dome") && t % 5 == 0) {
			for (int k = 0; k < 3; k++) {
				Fx.ring(level, Fx.dust(GOLD, 1.3F), position().add(0, 1 + k * 1.4, 0), 4.8 - k * 1.2, 24);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.domeUntil > level.getGameTime() && source.getDirectEntity() instanceof Projectile pr) {
			// The golden dome turns arrows and bolts back where they came from.
			pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.0));
			Fx.hostileSound(level, pr.position(), SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.2F);
			return false;
		}
		return super.hurtServer(level, source, amount);
	}
}
