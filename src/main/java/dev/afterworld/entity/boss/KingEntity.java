package dev.afterworld.entity.boss;

import net.minecraft.world.item.ItemStack;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The King of Seconds. Locks challengers into loops of their own movement, sweeps with his
 * pendulum, and is kept on time by the escapements around his throne.
 */
public class KingEntity extends BossMob implements Enemy {
	@Override
	protected String voice() {
		return "king";
	}

	@Override
	public dev.afterworld.aspect.Aspect aspect() {
		return dev.afterworld.aspect.Aspect.STOPPED_HOUR;
	}

	public static final int ANIM_WALK = 0;
	public static final int ANIM_SWEEP_WINDUP = 1;
	public static final int ANIM_SWEEP = 2;
	public static final int ANIM_LOOP = 3;
	public static final int ANIM_STAGGER = 4;
	public static final int ANIM_KNEEL = 5;

	private record Loop(UUID player, Vec3 mark, long trigger) {
	}

	private final List<Loop> loops = new ArrayList<>();
	BlockPos[] escapements = new BlockPos[0];
	int siteId = -1;
	int intact = 4;
	private int stagger;
	private int sweepCooldown = 30;
	private int loopCooldown = 120;
	private int windup;
	private boolean summoned66;
	private boolean summoned33;
	boolean bargainOffered;
	long bargainUntil;
	boolean fast;
	private int specialTimer = 160;
	/** Ticks left channelling the clock hands (he stands at the centre while they sweep). */
	private int channel;

	public KingEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level, BossEvent.BossBarColor.WHITE);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 700.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.SCALE, 1.15)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0);
	}

	public boolean bargaining() {
		return bargainUntil > level().getGameTime();
	}

	public void stagger(int ticks) {
		stagger = Math.max(stagger, ticks);
		windup = 0;
		setAnim(ANIM_STAGGER);
		getNavigation().stop();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (tickCount % 20 == 0) {
			intact = 0;
			for (BlockPos p : escapements) {
				if (level.getBlockState(p).is(AwBlocks.ESCAPEMENT)) {
					intact++;
				}
			}
			if (intact > 0 && getHealth() < getMaxHealth()) {
				heal(intact * 0.5F);
			}
		}
		resolveLoops(level, now);
		if (bargaining()) {
			getNavigation().stop();
			if (tickCount % 10 == 0) {
				Fx.burst(level, ParticleTypes.END_ROD, position().add(0, 2.5, 0), 3, 0.5, 0.02);
			}
			return;
		}
		if (anim() == ANIM_KNEEL) {
			setAnim(ANIM_WALK);
		}
		if (stagger > 0) {
			stagger--;
			if (stagger == 0) {
				setAnim(ANIM_WALK);
			}
			return;
		}
		float ratio = getHealth() / getMaxHealth();
		if (!summoned66 && ratio < 0.66F) {
			summoned66 = true;
			summon(level);
		}
		if (!summoned33 && ratio < 0.33F) {
			summoned33 = true;
			summon(level);
		}
		if (!bargainOffered && ratio < 0.35F) {
			bargainOffered = true;
			bargainUntil = now + 200;
			if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
				dev.afterworld.npc.Barks.say(sl, this, "king", "bargain", 64);
			}
			setAnim(ANIM_KNEEL);
			for (ServerPlayer p : playersNear(40)) {
				p.sendSystemMessage(Component.translatable("message.afterworld.king_offer").withColor(0xE8D7A0));
				Progress.codex(p, "boss.king.bargain");
			}
			Fx.hostileSound(level, position(), AwSounds.BOSS_TICK, 1.5F, 0.8F);
			return;
		}
		if (inTransition()) {
			getNavigation().stop();
			if (transition % 8 == 0) {
				Fx.hostileSound(level, position(), AwSounds.BOSS_TICK, 2.0F, 0.5F + transition * 0.02F);
				Fx.runeRing(level, position().add(0, 0.2, 0), 0xE8D7A0, 3 + (50 - transition) * 0.12, 24);
			}
			return;
		}
		if (channel > 0) {
			channel--;
			getNavigation().stop();
			if (channel % 6 == 0) {
				Fx.burst(level, ParticleTypes.END_ROD, position().add(0, getBbHeight() + 0.4, 0), 4, 0.4, 0.02);
			}
			return;
		}
		ServerPlayer target = nearestPlayer(30);
		if (sweepCooldown > 0) {
			sweepCooldown--;
		}
		if (target != null && phase >= 2 && windup == 0 && --specialTimer <= 0) {
			specialTimer = phase >= 3 ? 150 : 200;
			switch (random.nextInt(phase >= 3 ? 3 : 2)) {
				case 0 -> clockHands(level);
				case 1 -> hourglasses(level);
				default -> stoppedMoment(level, target);
			}
			return;
		}
		if (loopCooldown > 0) {
			loopCooldown--;
		}
		if (windup > 0) {
			windup--;
			if (windup == 0 && anim() == ANIM_SWEEP_WINDUP) {
				sweep(level);
			}
			return;
		}
		if (target == null) {
			return;
		}
		getLookControl().setLookAt(target, 30, 30);
		double dist = Math.sqrt(distanceToSqr(target));
		if (tickCount % 10 == 0 && dist > 3.0) {
			getNavigation().moveTo(target, fast ? 1.25 : 1.0);
		}
		if (dist < 4.3 && sweepCooldown == 0) {
			windup = 14;
			sweepCooldown = 45;
			setAnim(ANIM_SWEEP_WINDUP);
			Fx.hostileSound(level, position(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.4F, 0.6F);
		} else if (loopCooldown == 0) {
			List<ServerPlayer> near = playersNear(22);
			if (!near.isEmpty()) {
				ServerPlayer victim = near.get(random.nextInt(near.size()));
				loops.add(new Loop(victim.getUUID(), victim.position(), now + 60));
				loopCooldown = fast ? 170 : 220;
				setAnim(ANIM_LOOP);
				victim.sendOverlayMessage(Component.translatable("message.afterworld.king_loop").withColor(0xE8D7A0));
				Fx.sound(level, victim.position(), AwSounds.BOSS_TICK, 1.0F, 1.4F);
			}
		}
	}

	/** He plants himself and the hands of a great clock sweep the floor around him. */
	private void clockHands(ServerLevel level) {
		int arms = phase >= 3 ? 3 : 2;
		channel = 150;
		setAnim(ANIM_LOOP);
		Fx.hostileSound(level, position(), AwSounds.BOSS_TICK, 3.0F, 0.6F);
		Hazards.blades(level, this, position(), 13, arms, phase >= 3 ? 0.05 : 0.035, 30, 120, 9.0F, 0xE8D7A0);
	}

	/** Hourglasses drop over each challenger; whoever is still inside when the sand runs is held. */
	private void hourglasses(ServerLevel level) {
		setAnim(ANIM_LOOP);
		windup = 10;
		for (ServerPlayer p : playersNear(26)) {
			Hazards.zone(level, p.position(), 2.6, 30, 60, 0xE8C060, q -> {
				q.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 25, 4), this);
				q.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 25, 2), this);
			});
			Fx.sound(level, p.position(), AwSounds.BOSS_TICK, 1.2F, 1.5F);
		}
	}

	/** Tick. Tick. Tick. The world stops for everyone but him, and he is suddenly at your back. */
	private void stoppedMoment(ServerLevel level, ServerPlayer target) {
		channel = 48;
		setAnim(ANIM_LOOP);
		for (int i = 0; i < 3; i++) {
			int n = i;
			dev.afterworld.util.Scheduler.after(i * 12, () -> Fx.hostileSound(level, position(), AwSounds.BOSS_TICK, 3.0F, 0.6F + n * 0.2F));
		}
		dev.afterworld.util.Scheduler.after(36, () -> {
			if (!isAlive() || !target.isAlive()) {
				return;
			}
			for (ServerPlayer p : playersNear(24)) {
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 9), this);
				Fx.burst(level, ParticleTypes.END_ROD, p.position().add(0, 1, 0), 20, 0.5, 0.0);
			}
			Fx.shake(level, position(), 32, 2.0F, 20);
			Vec3 back = target.getLookAngle().multiply(1, 0, 1).normalize().scale(-2.5);
			Vec3 to = target.position().add(back);
			Vec3 from = position();
			if (randomTeleport(to.x, target.getY(), to.z, false, state -> false)) {
				Fx.line(level, Fx.dust(0xE8D7A0, 1.4F), from.add(0, 1.5, 0), position().add(0, 1.5, 0), 0.4);
				lookAt(target, 180, 180);
				windup = 8;
				setAnim(ANIM_SWEEP_WINDUP);
			}
		});
	}

	@Override
	protected void onPhase(ServerLevel level, int phase) {
		if (phase == 3) {
			fast = true;
		}
		specialTimer = 60;
	}

	@Override
	protected void onReset(ServerLevel level) {
		specialTimer = 160;
		channel = 0;
	}

	private void sweep(ServerLevel level) {
		setAnim(ANIM_SWEEP);
		Vec3 look = getLookAngle();
		Vec3 pos = position();
		for (ServerPlayer p : playersNear(5)) {
			Vec3 to = p.position().subtract(pos);
			double d = to.length();
			if (d < 4.8 && (d < 1.5 || to.normalize().dot(look) > 0.15)) {
				var src = level.damageSources().mobAttack(this);
				if (p.hurtServer(level, src, 11.0F)) {
					p.knockback(1.1, -to.x / Math.max(0.1, d), -to.z / Math.max(0.1, d), src, 11.0F);
				}
			}
		}
		Fx.ring(level, ParticleTypes.SWEEP_ATTACK, pos.add(look.scale(2)).add(0, 1.2, 0), 1.5, 5);
		Fx.hostileSound(level, pos, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
	}

	private void resolveLoops(ServerLevel level, long now) {
		Iterator<Loop> it = loops.iterator();
		while (it.hasNext()) {
			Loop l = it.next();
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(l.player);
			if (p == null || p.level() != level || !p.isAlive()) {
				it.remove();
				continue;
			}
			long left = l.trigger - now;
			if (left > 0) {
				if (left % 10 == 0) {
					Fx.ring(level, Fx.dust(0xE8D7A0, 1.1F), l.mark.add(0, 0.15, 0), 1.2, 16);
					Fx.sound(level, p.position(), AwSounds.BOSS_TICK, 0.6F, 0.8F + (60 - left) * 0.012F);
				}
				continue;
			}
			it.remove();
			if (stagger > 0 || p.position().distanceToSqr(l.mark) < 2.25) {
				p.sendOverlayMessage(Component.translatable("message.afterworld.king_loop_held").withColor(0x9BD08A));
				continue;
			}
			Fx.line(level, Fx.dust(0xE8D7A0, 1.0F), p.position().add(0, 1, 0), l.mark.add(0, 1, 0), 0.6);
			p.teleportTo(level, l.mark.x, l.mark.y, l.mark.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
			p.hurtServer(level, level.damageSources().indirectMagic(this, this), 4.0F);
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
			Fx.sound(level, l.mark, AwSounds.BOSS_LOOP, 1.0F, 1.0F);
			Progress.codex(p, "boss.king");
		}
	}

	private void summon(ServerLevel level) {
		for (int i = 0; i < 2; i++) {
			Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (vex == null) {
				continue;
			}
			vex.snapTo(getX() + random.nextInt(5) - 2, getY() + 2, getZ() + random.nextInt(5) - 2, 0, 0);
			vex.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			vex.setOwner(this);
			vex.setLimitedLife(600);
			vex.setCustomName(Component.translatable("entity.afterworld.second_hand"));
			MobMark.set(vex, MobMark.of(vex).with(MobMark.VAULT_MINION));
			level.addFreshEntity(vex);
		}
		Fx.hostileSound(level, position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 1.4F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (bargaining() && source.getEntity() instanceof ServerPlayer p) {
			bargainUntil = 0;
			p.sendSystemMessage(Component.translatable("message.afterworld.king_refused").withColor(0xC4553B));
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			VaultSystem.kingDied(level, this, source);
			if (source.getEntity() instanceof net.minecraft.world.entity.player.Player) {
				this.spawnAtLocation(level, new ItemStack(dev.afterworld.registry.AwItems.SECOND_HAND), 1.0F);
			}
		}
	}

	@Override
	protected void registerGoals() {
	}
}
