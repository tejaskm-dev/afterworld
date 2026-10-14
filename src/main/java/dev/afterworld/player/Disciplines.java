package dev.afterworld.player;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.edict.HuntingEchoes;
import dev.afterworld.util.Acts;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Discipline passives (as attribute modifiers) and signature techniques. */
public final class Disciplines {
	private static final Identifier HEALTH = Afterworld.id("vanguard_health");
	private static final Identifier SPEED = Afterworld.id("wayfarer_speed");
	private static final Identifier SAFE_FALL = Afterworld.id("wayfarer_safe_fall");

	private Disciplines() {
	}

	/** Chooses (or changes) a discipline. The first choice is free; later ones cost 10 levels. */
	public static boolean choose(ServerPlayer p, Discipline d) {
		PlayerState s = PlayerState.of(p);
		if (d == Discipline.NONE || d == s.discipline) {
			return false;
		}
		if (s.discipline != Discipline.NONE) {
			if (!p.isCreative() && p.experienceLevel < 10) {
				p.sendSystemMessage(Component.translatable("message.afterworld.discipline_cost").withStyle(ChatFormatting.RED));
				return false;
			}
			if (!p.isCreative()) {
				p.giveExperienceLevels(-10);
			}
		}
		Discipline old = s.discipline;
		s.discipline = d;
		s.disciplineChanges++;
		s.signatureReadyAt = 0;
		if (old == Discipline.ARCANIST && s.slots[3] != null) {
			s.slots[3] = null;
		}
		if (d.starter != null && !s.learned.contains(d.starter)) {
			Progress.learn(p, d.starter);
		}
		applyPassives(p, s);
		s.hudDirty = true;
		Progress.codex(p, "discipline." + d.id);
		Progress.legend(p, "milestone", old == Discipline.NONE ? "Took up the way of the " + d.displayName().getString() + "."
			: "Set aside the " + old.displayName().getString() + " for the way of the " + d.displayName().getString() + ".");
		if (!Progress.isQuiet()) {
			p.level().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
			p.sendSystemMessage(Component.translatable("message.afterworld.discipline_chosen", d.displayName().copy().withColor(d.color)));
		}
		return true;
	}

	public static void applyPassives(ServerPlayer p, PlayerState s) {
		set(p.getAttribute(Attributes.MAX_HEALTH), HEALTH, s.discipline == Discipline.VANGUARD ? 4.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, s.discipline == Discipline.WAYFARER ? 0.1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(p.getAttribute(Attributes.SAFE_FALL_DISTANCE), SAFE_FALL, s.discipline == Discipline.WAYFARER ? 4.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		if (p.getHealth() > p.getMaxHealth()) {
			p.setHealth(p.getMaxHealth());
		}
	}

	private static void set(AttributeInstance attr, Identifier id, double amount, AttributeModifier.Operation op) {
		if (attr == null) {
			return;
		}
		if (amount == 0) {
			attr.removeModifier(id);
		} else {
			attr.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** The held signature key: the Wayfarer's Wrong Turn (mark a place, then recall to it). */
	public static boolean signatureAlt(ServerPlayer p, PlayerState s) {
		if (s.discipline != Discipline.WAYFARER) {
			return signature(p, s);
		}
		long now = p.level().getGameTime();
		boolean used = wrongTurn(p, s, now);
		if (used) {
			s.signatureReadyAt = Math.max(s.signatureReadyAt, now + 200);
			s.hudDirty = true;
		}
		return used;
	}

	/** Runs the discipline signature. Returns true when it went on cooldown. */
	public static boolean signature(ServerPlayer p, PlayerState s) {
		long now = p.level().getGameTime();
		if (s.discipline == Discipline.NONE) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.fail.no_discipline").withColor(0xB0A8A0));
			return false;
		}
		if (now < s.signatureReadyAt && !(s.discipline == Discipline.WAYFARER && s.wrongTurnUntil > now)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.fail.cooldown", s.discipline.signatureName()).withColor(0xB0A8A0));
			return false;
		}
		boolean used = switch (s.discipline) {
			case VANGUARD -> unbroken(p, s);
			case WAYFARER -> gambit(p, s);
			case ARCANIST -> EdictSystem.ruleCollision(p, s);
			case KEEPER -> callThePack(p, s);
			default -> false;
		};
		if (used) {
			s.signatureReadyAt = now + s.discipline.signatureCooldown;
			s.hudDirty = true;
		}
		return used;
	}

	// ------------------------------------------------------------------ Vanguard: Unbroken

	/** A leap and a slam that throws everything nearby into the air; then five seconds of standing firm and drawing every eye. */
	private static boolean unbroken(ServerPlayer p, PlayerState s) {
		ServerLevel level = p.level();
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		p.setDeltaMovement(look.x * 0.6, 0.9, look.z * 0.6);
		p.syncVelocity = true;
		s.safeFallUntil = level.getGameTime() + 60;
		Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
		Acts.play(p, Acts.LEAP, 18);
		Vfx.trail(level, p, 0.8F, Discipline.VANGUARD.color, 26);
		Vfx.ring(level, p.position(), 2.0F, 0.5F, 0, Discipline.VANGUARD.color, 8);
		boolean[] done = {false};
		for (int i = 1; i <= 30; i++) {
			int n = i;
			dev.afterworld.util.Scheduler.after(i, () -> {
				if (done[0] || !p.isAlive()) {
					return;
				}
				p.fallDistance = 0;
				if (n > 6 && (p.onGround() || n == 30)) {
					done[0] = true;
					slam(level, p, s);
				}
			});
		}
		return true;
	}

	private static void slam(ServerLevel level, ServerPlayer p, PlayerState s) {
		Vec3 at = p.position();
		float damage = 10.0F + 1.5F * Renown.level(s);
		Fx.shake(level, at, 20, 4.0F, 14);
		Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.8F);
		Fx.sound(level, at, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
		Acts.play(p, Acts.SLAM, 10);
		Vfx.slash(level, p, 88, 2.4F, 150, Discipline.VANGUARD.color, 8);
		Vfx.ring(level, at, 5.5F, 1.1F, 1.8F, Discipline.VANGUARD.color, 16);
		Vfx.ring(level, at, 3.0F, 0.5F, 0, 0xFFD0A0, 10);
		Vfx.flare(level, at.add(0, 0.5, 0), 2.4F, true, Discipline.VANGUARD.color, 10);
		Vfx.flash(p, 0.25F, Discipline.VANGUARD.color, 10);
		for (LivingEntity e : dev.afterworld.combat.Combat.foes(level, p, at, 5.0)) {
			if (e.hurtServer(level, level.damageSources().playerAttack(p), damage)) {
				if (!(e instanceof dev.afterworld.entity.boss.BossMob)) {
					e.setDeltaMovement(e.getDeltaMovement().add(0, 0.85, 0));
					e.syncVelocity = true;
					e.addEffect(new MobEffectInstance(dev.afterworld.registry.AwEffects.STOPPED, 15, 0), p);
				}
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), p);
			}
		}
		// Unbroken: half damage for five seconds, and everything hostile nearby wants you and only you.
		s.guardUntil = level.getGameTime() + 100;
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, false, true));
		for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(12),
			m -> m instanceof net.minecraft.world.entity.monster.Enemy)) {
			m.setTarget(p);
		}
		Fx.column(level, Fx.dust(0xC9714A, 1.4F), at, 16, 0.6, 2.5);
		Vfx.ring(level, p.position().add(0, 1.0, 0), new Vec3(0, 1, 0), 1.2F, 0.3F, Discipline.VANGUARD.color, 100);
		Progress.codex(p, "discipline.vanguard.unbroken");
	}

	/** Last Word, now the Vanguard's passive: once every three minutes, a killing blow leaves you standing at one heart's edge. */
	public static boolean lastWordSaves(ServerPlayer p, PlayerState s, float amount) {
		long now = p.level().getGameTime();
		if (s.discipline != Discipline.VANGUARD || amount < p.getHealth() + p.getAbsorptionAmount() || now < s.lastWordAt) {
			return false;
		}
		s.lastWordAt = now + 20 * 180;
		p.setHealth(1.0F);
		ServerLevel level = p.level();
		for (LivingEntity e : dev.afterworld.combat.Combat.foes(level, p, p.position(), 5.0)) {
			e.hurtServer(level, level.damageSources().playerAttack(p), 6.0F);
			Vec3 d = e.position().subtract(p.position()).normalize();
			e.knockback(1.4, -d.x, -d.z, level.damageSources().playerAttack(p), 0);
		}
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2));
		Fx.ring(level, Fx.dust(0xC9714A, 1.6F), p.position().add(0, 0.3, 0), 4.5, 16);
		Vfx.ring(level, p.position(), 5.0F, 1.0F, 1.6F, Discipline.VANGUARD.color, 16);
		Vfx.pillar(level, p.position(), 0.8F, 3.5F, 0xFFD0A0, 16);
		Vfx.flash(p, 0.5F, 0xC0302A, 18);
		Fx.sound(level, p.position(), SoundEvents.TOTEM_USE, 0.6F, 0.7F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.last_word").withColor(0xC9714A));
		Progress.codex(p, "discipline.vanguard.last_word");
		return true;
	}

	// ------------------------------------------------------------------ Wayfarer: Wayfarer's Gambit

	/** Blink from foe to foe (up to five within twelve blocks), striking each, then step back to where you began. */
	private static boolean gambit(ServerPlayer p, PlayerState s) {
		ServerLevel level = p.level();
		java.util.List<LivingEntity> targets = new java.util.ArrayList<>(dev.afterworld.combat.Combat.foes(level, p, p.position(), 12));
		if (targets.isEmpty()) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.fail.gambit").withColor(0xB0A8A0));
			return false;
		}
		targets.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		Vec3 home = p.position();
		float yaw = p.getYRot();
		float pitch = p.getXRot();
		float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
		int n = Math.min(5, targets.size());
		Fx.sound(level, home, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.6F);
		Vfx.pillar(level, home, 0.5F, 2.4F, Discipline.WAYFARER.color, 2 + n * 3 + 10);
		for (int i = 0; i < n; i++) {
			LivingEntity e = targets.get(i);
			int cut = i;
			dev.afterworld.util.Scheduler.after(1 + i * 3, () -> {
				if (!e.isAlive() || !p.isAlive()) {
					return;
				}
				Vec3 behind = e.position().subtract(Vec3.directionFromRotation(0, e.getYRot()).multiply(1, 0, 1).normalize().scale(e.getBbWidth() * 0.5 + 1.0));
				if (!level.noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) {
					behind = e.position();
				}
				Vfx.beam(level, p.position().add(0, 1, 0), behind.add(0, 1, 0), 0.3F, 0.08F, Discipline.WAYFARER.color, 7);
				p.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), p.getYRot(), p.getXRot(), false);
				Acts.play(p, Acts.SWEEP, 6);
				Vfx.slash(level, e.position().add(0, e.getBbHeight() * 0.55, 0), p.getYRot(), 0, cut % 2 == 0 ? 32 : -32, 1.7F, cut % 2 == 0 ? 160 : -160, 0.6F, Discipline.WAYFARER.color, 7, null);
				e.setInvulnerableTime(0);
				e.hurtServer(level, level.damageSources().playerAttack(p), dmg);
				Fx.burst(level, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 1, 0, 0);
				Fx.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
			});
		}
		dev.afterworld.util.Scheduler.after(2 + n * 3, () -> {
			if (p.isAlive()) {
				Vfx.beam(level, p.position().add(0, 1, 0), home.add(0, 1, 0), 0.3F, 0, Discipline.WAYFARER.color, 8);
				p.teleportTo(level, home.x, home.y, home.z, Set.of(), yaw, pitch, false);
				Vfx.flare(level, home.add(0, 1, 0), 1.2F, true, Discipline.WAYFARER.color, 8);
				p.resetFallDistance();
				Fx.sound(level, home, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.3F);
			}
		});
		return true;
	}

	// ------------------------------------------------------------------ Keeper: Call the Pack

	/** Three spectral wolves for thirty seconds. They draw foes onto themselves, and every bite mends their Keeper. */
	private static boolean callThePack(ServerPlayer p, PlayerState s) {
		ServerLevel level = p.level();
		for (int i = 0; i < 3; i++) {
			double a = i * Math.PI * 2 / 3;
			Vec3 at = p.position().add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
			var wolf = net.minecraft.world.entity.EntityTypes.WOLF.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			if (wolf == null) {
				continue;
			}
			wolf.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
			wolf.tame(p);
			wolf.setCustomName(Component.translatable("discipline.afterworld.keeper.wolf").withColor(0xD9B45A));
			var hp = wolf.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				hp.setBaseValue(30.0);
			}
			wolf.setHealth(30.0F);
			var atk = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
			if (atk != null) {
				atk.setBaseValue(6.0);
			}
			wolf.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
			wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 1, false, false));
			level.addFreshEntity(wolf);
			dev.afterworld.combat.Combat.bindSummon(wolf, p, 600);
			Fx.burst(level, Fx.dust(0xD9B45A, 1.4F), at.add(0, 0.6, 0), 16, 0.4, 0.05);
			Vfx.pillar(level, at, 0.5F, 2.4F, Discipline.KEEPER.color, 18);
			Vfx.ring(level, at, 1.6F, 0.35F, 0.5F, Discipline.KEEPER.color, 14);
		}
		Acts.play(p, Acts.INVOKE, 16);
		Vfx.sigil(level, p.position(), 2.6F, Vfx.SIGIL_STAR, 3.0F, Discipline.KEEPER.color, 24, null);
		Fx.sound(level, p.position(), SoundEvents.WOLF_SHAKE, 1.0F, 0.7F);
		Fx.sound(level, p.position(), dev.afterworld.registry.AwSounds.MOB_HOUND.value(), 1.0F, 1.3F);
		Progress.codex(p, "discipline.keeper.pack");
		return true;
	}

	private static boolean lastWord(ServerPlayer p, PlayerState s) {
		float ratio = p.getHealth() / p.getMaxHealth();
		if (ratio > 0.4F) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.fail.last_word").withColor(0xB0A8A0));
			return false;
		}
		ServerLevel level = p.level();
		float missing = p.getMaxHealth() - p.getHealth();
		float damage = 6.0F + missing * 0.6F;
		DamageSource source = level.damageSources().playerAttack(p);
		int struck = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5), e -> e != p && e.isAlive())) {
			if (e instanceof Player other && !p.canHarmPlayer(other)) {
				continue;
			}
			if (e.hurtServer(level, source, damage)) {
				Vec3 d = e.position().subtract(p.position()).normalize();
				e.knockback(1.3, -d.x, -d.z, source, damage);
				struck++;
			}
		}
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
		Fx.ring(level, ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1, 0), 2.5, 6);
		Acts.play(p, Acts.SPIN, 12);
		Vfx.slash(level, p.position().add(0, 1.0, 0), p.getYRot(), 0, 2, 2.4F, 355, 0.9F, Discipline.VANGUARD.color, 10, p);
		Vfx.ring(level, p.position(), 4.5F, 0.9F, 1.0F, Discipline.VANGUARD.color, 14);
		Fx.sound(level, p.position(), SoundEvents.ANVIL_LAND, 0.8F, 0.7F);
		Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
		if (struck > 0) {
			Progress.codex(p, "discipline.vanguard.last_word");
		}
		return true;
	}

	private static boolean wrongTurn(ServerPlayer p, PlayerState s, long now) {
		ServerLevel level = p.level();
		String dim = level.dimension().identifier().toString();
		if (s.wrongTurnUntil > now && dim.equals(s.wrongTurnDim)) {
			Vec3 mark = new Vec3(s.wrongTurnX, s.wrongTurnY, s.wrongTurnZ);
			if (mark.distanceToSqr(p.position()) > 128 * 128) {
				p.sendOverlayMessage(Component.translatable("message.afterworld.fail.wrong_turn_far").withColor(0xB0A8A0));
				return false;
			}
			Vfx.beam(level, p.position().add(0, 1, 0), mark.add(0, 1, 0), 0.3F, 0.1F, Discipline.WAYFARER.color, 10);
			Vfx.pillar(level, p.position(), 0.5F, 2.4F, Discipline.WAYFARER.color, 12);
			p.teleportTo(level, mark.x, mark.y, mark.z, Set.of(), p.getYRot(), p.getXRot(), false);
			p.resetFallDistance();
			Fx.column(level, Fx.dust(0x7FB069, 1.2F), mark, 10, 0.3, 2);
			Vfx.pillar(level, mark, 0.6F, 2.8F, Discipline.WAYFARER.color, 16);
			Vfx.flash(p, 0.2F, Discipline.WAYFARER.color, 10);
			Fx.sound(level, mark, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.2F);
			s.wrongTurnUntil = 0;
			Progress.codex(p, "discipline.wayfarer.wrong_turn");
			return true;
		}
		s.wrongTurnX = p.getX();
		s.wrongTurnY = p.getY();
		s.wrongTurnZ = p.getZ();
		s.wrongTurnDim = dim;
		s.wrongTurnUntil = now + 600;
		s.hudDirty = true;
		Fx.column(level, Fx.dust(0x7FB069, 1.2F), p.position(), 8, 0.2, 2.5);
		Vfx.sigil(level, p.position(), 1.3F, Vfx.SIGIL_STAR, 2.0F, Discipline.WAYFARER.color, 600, null);
		Fx.sound(level, p.position(), SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, 1.0F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.wrong_turn_marked").withColor(0x7FB069));
		return false;
	}
}
