package dev.afterworld.aspect;

import dev.afterworld.combat.Combat;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwEffects;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Acts;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import dev.afterworld.util.Scheduler;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Aspects: the ultimate powers. One is equipped at a time and fires when Resonance is full.
 * Resonance fills from fighting well (damage dealt, Edicts cast, kills, perfect parries and
 * dodges). Each Aspect is a short, decisive spectacle with a camera kick for everyone nearby.
 */
public final class Aspects {
	public static final float FULL = 100.0F;
	private static final Identifier STONE_SCALE = dev.afterworld.Afterworld.id("aspect_stone_scale");
	private static final Identifier STONE_ARMOR = dev.afterworld.Afterworld.id("aspect_stone_armor");

	private Aspects() {
	}

	public static Aspect equipped(PlayerState s) {
		return Aspect.byId(s.aspect);
	}

	/** Binds an Aspect (from its heart). Equips it if nothing is equipped. Returns false if already known. */
	public static boolean bind(ServerPlayer p, Aspect a) {
		PlayerState s = PlayerState.of(p);
		if (!s.aspects.add(a.id)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.aspect_known").withColor(0x9A9488));
			return false;
		}
		if (s.aspect.isEmpty()) {
			s.aspect = a.id;
		}
		// A Warden's first fall: two Insight for the skill tree.
		dev.afterworld.player.Skills.grantInsight(p, 2);
		Fx.burst(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 60, 0.6, 0.2);
		Fx.privateSound(p, AwSounds.UI_BOSS, 1.0F, 0.8F);
		if (ServerPlayNetworking.canSend(p, AwPayloads.Title.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.CHAPTER, Component.translatable("aspect.afterworld.bound"),
				Component.translatable("aspect.afterworld." + a.id), a.color));
		}
		Progress.legend(p, "milestone", "Bound the Aspect " + Component.translatable("aspect.afterworld." + a.id).getString() + ".");
		sync(p, s);
		return true;
	}

	public static void equip(ServerPlayer p, Aspect a) {
		PlayerState s = PlayerState.of(p);
		if (a != null && s.aspects.contains(a.id)) {
			s.aspect = a.id;
			sync(p, s);
		}
	}

	public static void addResonance(ServerPlayer p, float amount) {
		PlayerState s = PlayerState.of(p);
		if (s.aspect.isEmpty() || amount <= 0) {
			return;
		}
		float before = s.resonance;
		float mult = dev.afterworld.combat.SetBonuses.has(p, dev.afterworld.registry.AwGear.Set.WARDENS_REGALIA) ? 1.25F : 1.0F;
		if (dev.afterworld.player.Relics.has(p, "warden_glyph_ring")) {
			mult += 0.15F;
		}
		if (s.discipline == dev.afterworld.player.Discipline.ARCANIST) {
			mult += 0.25F;
		}
		if (p.hasEffect(AwEffects.STARLIT)) {
			mult += 0.2F;
		}
		if (s.skills.contains("arcanum.attune")) {
			mult += 0.15F;
		}
		mult += (float) dev.afterworld.player.Skills.bonus(s, dev.afterworld.player.Skills.Stat.RESONANCE);
		s.resonance = Math.min(FULL, s.resonance + amount * mult);
		if (before < FULL && s.resonance >= FULL) {
			Fx.privateSound(p, AwSounds.EDICT_READY, 1.0F, 0.6F);
		}
		if ((int) before != (int) s.resonance) {
			sync(p, s);
		}
	}

	public static void sync(ServerPlayer p, PlayerState s) {
		Aspect a = equipped(s);
		int owned = 0;
		for (String id : s.aspects) {
			Aspect x = Aspect.byId(id);
			if (x != null) {
				owned |= 1 << x.ordinal();
			}
		}
		if (ServerPlayNetworking.canSend(p, AwPayloads.AspectSync.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.AspectSync(a == null ? -1 : a.ordinal(), s.resonance, owned));
		}
	}

	/** The ultimate key. */
	public static void cast(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		Aspect a = equipped(s);
		if (a == null) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.no_aspect").withColor(0x9A9488));
			return;
		}
		if (s.resonance < FULL) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.resonance_low", (int) s.resonance).withColor(0x9A9488));
			return;
		}
		s.resonance = s.skills.contains("arcanum.overflow") ? 25 : 0;
		sync(p, s);
		ServerLevel level = p.level();
		// A camera kick and a beat of slow time for everyone watching.
		for (ServerPlayer watcher : PlayerLookup.around(level, p.position(), 48)) {
			ServerPlayNetworking.send(watcher, new AwPayloads.Cinematic(AwPayloads.Cinematic.ULTIMATE, a.ordinal(), p.getId(), a.color));
		}
		Fx.sound(level, p.position(), AwSounds.ASPECT_UNLEASH, 2.0F, 1.0F);
		Fx.shake(level, p.position(), 24, 4.0F, 14);
		// Everyone sees it begin: arms to the sky, a great sigil, a pillar of the Aspect's light.
		Acts.play(p, Acts.INVOKE, 24);
		Vfx.flash(p, 0.55F, a.color, 26);
		Vfx.sigil(level, p.position(), 5.0F, switch (a) {
			case STOPPED_HOUR -> Vfx.SIGIL_CLOCK;
			case GLYPH_OF_JUDGMENT, REQUIEM_TOLL -> Vfx.SIGIL_JUDGMENT;
			case RED_DOMINION, WHITE_WINTER -> Vfx.SIGIL_STAR;
			default -> Vfx.SIGIL_EDICT;
		}, 2.5F, a.color, 40, null);
		Vfx.pillar(level, p.position(), 1.1F, 9.0F, a.color, 24);
		Vfx.ring(level, p.position(), 9.0F, 1.2F, 2.0F, a.color, 18);
		switch (a) {
			case REQUIEM_TOLL -> requiemToll(level, p);
			case STOPPED_HOUR -> stoppedHour(level, p);
			case FOOTSTEPS_OF_STONE -> footstepsOfStone(level, p);
			case HUNDRED_LANTERNS -> hundredLanterns(level, p);
			case RED_DOMINION -> redDominion(level, p);
			case WHITE_WINTER -> whiteWinter(level, p);
			case CHAINS_OF_THE_DUNE -> chainsOfTheDune(level, p);
			case GLYPH_OF_JUDGMENT -> glyphOfJudgment(level, p);
		}
	}

	private static float power(ServerPlayer p, float base) {
		return base * (1.0F + dev.afterworld.player.Renown.level(PlayerState.of(p)) * 0.03F);
	}

	private static List<LivingEntity> foes(ServerLevel level, ServerPlayer p, Vec3 at, double r) {
		return Combat.foes(level, p, at, r);
	}

	/** A spectral bell falls on the spot you face: everything under it is struck, stunned and silenced. */
	private static void requiemToll(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
		for (int i = 0; i < 12; i++) {
			int k = i;
			Scheduler.after(i, () -> Fx.ring(level, Fx.dust(0x7AFFD8, 2.0F), at.add(0, 12 - k, 0), 3.5 + k * 0.1, 12));
			if (i % 3 == 0) {
				Scheduler.after(i, () -> Vfx.ring(level, at.add(0, 12 - k, 0), new Vec3(0, 1, 0), 3.6F + k * 0.1F, 0.4F, 0x7AFFD8, 6));
			}
		}
		Vfx.sigil(level, at, 4.0F, Vfx.SIGIL_JUDGMENT, 6.0F, 0x7AFFD8, 14, null);
		Scheduler.after(12, () -> {
			Fx.sound(level, at, AwSounds.BOSS_BELL, 3.0F, 0.6F);
			Fx.shake(level, at, 32, 6.0F, 20);
			for (int i = 0; i < 3; i++) {
				int k = i;
				Scheduler.after(i * 4, () -> Vfx.ring(level, at, 9.0F, 1.2F - k * 0.25F, k == 0 ? 2.4F : 0.8F, 0x7AFFD8, 18));
			}
			Vfx.flare(level, at.add(0, 1, 0), 3.5F, true, 0x7AFFD8, 12);
			Vfx.pillar(level, at, 2.0F, 12.0F, 0x7AFFD8, 18);
			for (LivingEntity e : foes(level, p, at, 8)) {
				e.hurtServer(level, level.damageSources().playerAttack(p), power(p, 18.0F) * (e instanceof BossMob ? 0.6F : 1.0F));
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 9), p);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), p);
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0), p);
			}
		});
	}

	/** Time stops for everything but you: creatures freeze and arrows hang for five seconds. */
	private static void stoppedHour(ServerLevel level, ServerPlayer p) {
		Fx.sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p.position(), p.position()).inflate(20), e -> e != p && !(e instanceof ServerPlayer))) {
			e.addEffect(new MobEffectInstance(AwEffects.STOPPED, e instanceof BossMob ? 50 : 100, 0), p);
		}
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(p.position(), p.position()).inflate(20))) {
			Vec3 v = pr.getDeltaMovement();
			pr.setDeltaMovement(Vec3.ZERO);
			pr.setNoGravity(true);
			Scheduler.after(100, () -> {
				if (!pr.isRemoved()) {
					pr.setNoGravity(false);
					pr.setDeltaMovement(v);
				}
			});
		}
		for (int i = 0; i < 100; i += 20) {
			Scheduler.after(i, () -> Fx.runeRing(level, p.position(), 0xF2D25C, 20, 20));
		}
		Vfx.sigil(level, p.position(), 20.0F, Vfx.SIGIL_CLOCK, 0.8F, 0xF2D25C, 100, null);
		Vfx.ring(level, p.position(), 20.0F, 1.0F, 2.5F, 0xF2D25C, 20);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p.position(), p.position()).inflate(20), e -> e != p && !(e instanceof ServerPlayer))) {
			Vfx.ring(level, e.position().add(0, e.getBbHeight() + 0.25, 0), new Vec3(0, 1, 0), Math.max(0.5F, e.getBbWidth() * 0.6F), 0.12F, 0xF2D25C, e instanceof BossMob ? 50 : 100);
		}
	}

	/** You become a stone colossus for twelve seconds: huge, armoured, and every landing a quake. */
	private static void footstepsOfStone(ServerLevel level, ServerPlayer p) {
		set(p.getAttribute(Attributes.SCALE), STONE_SCALE, 0.6);
		set(p.getAttribute(Attributes.ARMOR), STONE_ARMOR, 12);
		p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 240, 1));
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 240, 1));
		Fx.sound(level, p.position(), AwSounds.BOSS_STOMP, 2.0F, 0.6F);
		for (int i = 0; i < 240; i += 20) {
			Scheduler.after(i, () -> {
				if (!p.isAlive()) {
					return;
				}
				Fx.burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, p.position(), 6, 0.6, 0.02);
				if (p.onGround()) {
					Vfx.ring(level, p.position(), 5.5F, 0.9F, 1.2F, 0xD8C08A, 14);
					Vfx.flare(level, p.position().add(0, 0.3, 0), 1.6F, false, 0xD8C08A, 8);
					for (LivingEntity e : foes(level, p, p.position(), 5)) {
						e.hurtServer(level, level.damageSources().playerAttack(p), power(p, 5.0F));
						e.push(0, 0.5, 0);
					}
					Fx.shake(level, p.position(), 16, 2.0F, 6);
					Fx.sound(level, p.position(), AwSounds.BOSS_STOMP, 1.0F, 0.9F);
				}
			});
		}
		Scheduler.after(240, () -> {
			set(p.getAttribute(Attributes.SCALE), STONE_SCALE, 0);
			set(p.getAttribute(Attributes.ARMOR), STONE_ARMOR, 0);
		});
	}

	/** Dash through up to eight foes in ghostfire, then they burn. */
	private static void hundredLanterns(ServerLevel level, ServerPlayer p) {
		List<LivingEntity> targets = foes(level, p, p.position(), 14);
		targets.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		Vec3 home = p.position();
		int n = Math.min(8, targets.size());
		for (int i = 0; i < n; i++) {
			LivingEntity e = targets.get(i);
			Scheduler.after(i * 3, () -> {
				if (!e.isAlive() || !p.isAlive()) {
					return;
				}
				Vec3 to = e.position().add(e.getLookAngle().scale(-1.2));
				Vfx.beam(level, p.position().add(0, 1, 0), to.add(0, 1, 0), 0.4F, 0, 0x7AFFD8, 10);
				Vfx.pillar(level, p.position(), 0.4F, 2.2F, 0x7AFFD8, 12);
				p.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
				Acts.play(p, Acts.SWEEP, 7);
				Vfx.slash(level, e.position().add(0, e.getBbHeight() * 0.55, 0), p.getYRot(), 0, (e.getId() & 1) == 0 ? 30 : -30, 1.8F, 170, 0.7F, 0x7AFFD8, 8, null);
				Vfx.flare(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.2F, true, 0x7AFFD8, 8);
				e.hurtServer(level, level.damageSources().playerAttack(p), power(p, 10.0F));
				e.igniteForSeconds(5.0F);
				Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, e.position().add(0, 1, 0), 18, 0.4, 0.08);
				Fx.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.3F);
			});
		}
		Scheduler.after(n * 3 + 2, () -> {
			if (p.isAlive()) {
				p.teleportTo(level, home.x, home.y, home.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
			}
		});
	}

	/** Four crimson knights rise to fight for you; you drink a share of every wound you deal. */
	private static void redDominion(ServerLevel level, ServerPlayer p) {
		PlayerState.of(p).lifestealUntil = level.getGameTime() + 500;
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2;
			Vec3 at = p.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
			var knight = net.minecraft.world.entity.EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (knight == null) {
				continue;
			}
			knight.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
			knight.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
			knight.setCustomName(Component.translatable("aspect.afterworld.red_knight").withColor(0xD02040));
			knight.setPersistenceRequired();
			Combat.bindSummon(knight, p, 500);
			level.addFreshEntity(knight);
			Fx.burst(level, Fx.dust(0xD02040, 1.6F), at.add(0, 1, 0), 30, 0.5, 0.1);
			Vfx.pillar(level, at, 0.6F, 3.4F, 0xD02040, 20);
			Vfx.sigil(level, at, 1.3F, Vfx.SIGIL_STAR, 5.0F, 0xD02040, 24, null);
			Vfx.beam(level, p.position().add(0, 1, 0), at.add(0, 1, 0), 0.12F, 0.2F, 0xD02040, 10);
		}
		Vfx.helix(level, p.position(), 1.2F, 2.8F, 0xD02040, 40);
		Fx.sound(level, p.position(), SoundEvents.WITHER_SPAWN, 0.8F, 1.4F);
	}

	/** A blizzard dome: everything inside slows, freezes and bleeds cold for ten seconds. */
	private static void whiteWinter(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position();
		for (int i = 0; i < 200; i += 5) {
			int k = i;
			Scheduler.after(i, () -> {
				Fx.burst(level, ParticleTypes.SNOWFLAKE, at.add(0, 4, 0), 40, 12, 0.05);
				if (k % 20 == 0) {
					for (LivingEntity e : foes(level, p, at, 12)) {
						e.hurtServer(level, level.damageSources().freeze(), power(p, 2.0F));
						e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 100, e.getTicksFrozen() + 60));
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3), p);
					}
				}
			});
		}
		Fx.sound(level, at, SoundEvents.POWDER_SNOW_STEP, 3.0F, 0.5F);
		Vfx.sigil(level, at, 12.0F, Vfx.SIGIL_STAR, 1.0F, 0x8AE0FF, 200, null);
		for (int i = 0; i < 200; i += 20) {
			Scheduler.after(i, () -> Vfx.ring(level, at, 12.0F, 0.6F, 3.0F, 0x8AE0FF, 22));
		}
	}

	/** A sand vortex drags foes to its eye while stone fists fall on it. */
	private static void chainsOfTheDune(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(5));
		for (int i = 0; i < 120; i += 2) {
			int k = i;
			Scheduler.after(i, () -> {
				if (k % 4 == 0) {
					Fx.ring(level, Fx.dust(0xE8B33D, 1.5F), at.add(0, 0.3 + (k % 20) * 0.1, 0), 1.5 + (k % 10) * 0.4, 14);
				}
				if (k % 20 == 0) {
					Vfx.helix(level, at, 3.5F, 3.0F, 0xE8B33D, 22);
					Vfx.ring(level, at, 9.0F, 0.5F, 0.6F, 0xE8B33D, 20);
				}
				for (LivingEntity e : foes(level, p, at, 9)) {
					Vec3 pull = at.subtract(e.position()).normalize().scale(0.18);
					e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.02, pull.z));
				}
				if (k % 20 == 0) {
					Fx.sound(level, at, AwSounds.BOSS_STOMP, 1.2F, 1.3F);
					for (LivingEntity e : foes(level, p, at, 3.5)) {
						e.hurtServer(level, level.damageSources().playerAttack(p), power(p, 6.0F));
					}
					Fx.burst(level, ParticleTypes.POOF, at.add(0, 0.4, 0), 14, 1.0, 0.05);
					Vfx.flare(level, at.add(0, 0.6, 0), 2.2F, true, 0xE8B33D, 9);
					Vfx.ring(level, at, 3.5F, 0.7F, 1.0F, 0xFFD27A, 10);
				}
			});
		}
	}

	/** A golden dome turns arrows back for eight seconds; then judgment strikes every foe in sight. */
	private static void glyphOfJudgment(ServerLevel level, ServerPlayer p) {
		PlayerState.of(p).reflectUntil = level.getGameTime() + 160;
		for (int i = 0; i < 160; i += 16) {
			Scheduler.after(i, () -> Fx.ring(level, Fx.dust(0xFFD060, 1.4F), p.position().add(0, 1.2, 0), 2.5, 16));
		}
		Vfx.sigil(level, p.position(), 2.6F, Vfx.SIGIL_JUDGMENT, 3.0F, 0xFFD060, 160, p);
		for (int i = 0; i < 160; i += 40) {
			Scheduler.after(i, () -> Vfx.ring(level, p.position().add(0, 1.2, 0), new Vec3(0, 1, 0), 2.5F, 0.35F, 0xFFD060, 40));
		}
		Scheduler.after(160, () -> {
			if (!p.isAlive()) {
				return;
			}
			for (LivingEntity e : foes(level, p, p.position(), 18)) {
				Vfx.beam(level, e.position().add(0, 14, 0), e.position(), 0.7F, 0.25F, 0xFFD060, 10);
				Vfx.ring(level, e.position(), 2.4F, 0.5F, 1.0F, 0xFFD060, 12);
				Vfx.flare(level, e.position().add(0, 0.5, 0), 1.6F, true, 0xFFD060, 9);
				e.hurtServer(level, level.damageSources().magic(), power(p, 14.0F) * (e instanceof BossMob ? 0.6F : 1.0F));
			}
			Fx.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.5F, 1.2F);
		});
	}

	private static void set(AttributeInstance a, Identifier id, double v) {
		if (a == null) {
			return;
		}
		a.removeModifier(id);
		if (v != 0) {
			a.addTransientModifier(new AttributeModifier(id, v, a.getAttribute().value() == Attributes.SCALE.value()
				? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
