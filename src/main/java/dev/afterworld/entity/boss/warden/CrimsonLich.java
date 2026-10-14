package dev.afterworld.entity.boss.warden;

import dev.afterworld.aspect.Aspect;
import dev.afterworld.entity.boss.Hazards;
import dev.afterworld.entity.boss.WardenBoss;
import dev.afterworld.util.Fx;
import dev.afterworld.world.AfterworldData;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Crimson Lich: a theatrical old king who hid his life in three phylacteries. Blood beams,
 * a red nova, the dead raised to fight for him, a drain that feeds on you. Unless all three
 * phylacteries are broken, he does not stay down the first time.
 */
public class CrimsonLich extends WardenBoss {
	private static final int BLOOD = 0xD02040;
	private static final String MINION = "afterworld:lich_minion";
	private static final List<Move> MOVES = List.of(
		new Move("bolt", 14, 2, 10, 0, 24, 45, 1, 5, A_CAST, A_THROW),
		new Move("nova", 20, 2, 16, 0, 8, 90, 1, 4, A_RAISE, A_SLAM),
		new Move("blink", 8, 2, 6, 0, 4, 120, 1, 3, A_CAST, A_CAST),
		new Move("raise", 24, 4, 20, 0, 30, 300, 2, 2, A_CAST, A_CAST),
		new Move("drain", 16, 40, 16, 0, 12, 160, 2, 3, A_CAST, A_CAST));

	private boolean revived;

	public CrimsonLich(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.RED);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 800.0).add(Attributes.ARMOR, 8.0).add(Attributes.SCALE, 1.3)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.STEP_HEIGHT, 1.2);
	}

	@Override
	protected int bit() {
		return 1;
	}

	@Override
	public Aspect aspect() {
		return Aspect.RED_DOMINION;
	}

	@Override
	protected String wardenVoice() {
		return "lich";
	}

	@Override
	protected int color() {
		return BLOOD;
	}

	@Override
	protected double preferredRange() {
		return 9;
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
		Fx.burst(level, Fx.dust(BLOOD, 1.4F), position().add(0, getBbHeight() * 0.8, 0), 12, 0.6, 0.05);
		if (m.id().equals("bolt")) {
			Vec3 from = position().add(0, 0.2, 0);
			Vec3 dir = t.position().subtract(from);
			Hazards.line(level, this, from, dir, 22, 1.6, m.windup(), 10.0F, BLOOD);
			if (this.phase >= 3) {
				Hazards.line(level, this, from, dir.yRot(0.35F), 22, 1.6, m.windup() + 6, 10.0F, BLOOD);
				Hazards.line(level, this, from, dir.yRot(-0.35F), 22, 1.6, m.windup() + 6, 10.0F, BLOOD);
			}
			Fx.hostileSound(level, position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 0.6F);
		} else if (m.id().equals("raise")) {
			Fx.hostileSound(level, position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.4F, 0.6F);
		}
	}

	@Override
	protected void release(ServerLevel level, Move m, LivingEntity t) {
		switch (m.id()) {
			case "nova" -> {
				Hazards.wave(level, this, position(), 10, 0.45, 12.0F, BLOOD);
				Fx.hostileSound(level, position(), SoundEvents.WITHER_SHOOT, 1.4F, 0.6F);
			}
			case "blink" -> {
				for (int tries = 0; tries < 10; tries++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2;
					double r = 8 + level.getRandom().nextDouble() * 4;
					Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
					if (at.distanceTo(this.home) < 20) {
						Fx.burst(level, Fx.dust(BLOOD, 1.6F), position().add(0, 1.5, 0), 30, 0.6, 0.1);
						int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z));
						if (Math.abs(y - getY()) < 6) {
							teleportTo(at.x, y, at.z);
							Fx.hostileSound(level, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
							break;
						}
					}
				}
			}
			case "raise" -> {
				for (int i = 0; i < 3; i++) {
					Mob m2 = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (m2 == null) {
						continue;
					}
					double a = i * Math.PI * 2 / 3;
					Vec3 at = position().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
					m2.snapTo(at.x, at.y, at.z, getYRot(), 0);
					m2.finalizeSpawn(level, level.getCurrentDifficultyAt(m2.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
					m2.addTag(MINION);
					m2.setTarget(t);
					level.addFreshEntity(m2);
					Fx.burst(level, Fx.dust(BLOOD, 1.8F), at.add(0, 1, 0), 24, 0.4, 0.08);
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void during(ServerLevel level, Move m, int t, LivingEntity target) {
		if (m.id().equals("drain") && t % 8 == 0 && distanceTo(target) < 12 && hasLineOfSight(target)) {
			Fx.line(level, Fx.dust(BLOOD, 1.2F), position().add(0, getBbHeight() * 0.7, 0), target.position().add(0, 1, 0), 0.35);
			if (target instanceof ServerPlayer p && strike(level, p, 3.0F, 0, 0)) {
				heal(6.0F);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!this.revived && !dormant() && amount >= getHealth() && AfterworldData.get(level.getServer()).phylacteries < 3) {
			// "Did you think death was something I had not already survived?"
			this.revived = true;
			setHealth(getMaxHealth() * 0.45F);
			Fx.hostileSound(level, position(), SoundEvents.WITHER_SPAWN, 1.6F, 0.8F);
			Fx.column(level, Fx.dust(BLOOD, 2.0F), position(), 80, getBbWidth(), getBbHeight() * 1.5);
			dev.afterworld.npc.Barks.say(level, this, "lich", "revive", 64);
			for (ServerPlayer p : playersNear(48)) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("boss.afterworld.crimson_lich.revive").withColor(BLOOD));
			}
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level) {
			for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), e -> e.entityTags().contains(MINION))) {
				m.kill(level);
			}
		}
		super.die(source);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Revived", this.revived);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.revived = input.getBooleanOr("Revived", false);
	}

	@Override
	public boolean isNoGravity() {
		return false;
	}
}
