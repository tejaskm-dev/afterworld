package dev.afterworld.edict;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.entity.ThrownWeapon;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwTags;
import dev.afterworld.registry.MobMark;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rule each Edict bends. Every method returns false (with feedback) when the rule cannot apply.
 * At Rank III each Edict also gains its capstone, a second way of bending its rule.
 */
final class EdictEffects {
	private EdictEffects() {
	}

	static boolean cast(ServerPlayer p, PlayerState s, Edict edict, float power) {
		return switch (edict) {
			case RETURN -> castReturn(p, s, power, false);
			case ECHOES -> castEchoes(p, s, power);
			case BORROWED_TIME -> castBorrowedTime(p, s);
			case WEIGHTLESSNESS -> castWeightlessness(p, s, power);
			case HUNGER -> castHunger(p, s, power);
			case HOSPITALITY -> castHospitality(p, s, power);
			case TRESPASS -> castTrespass(p, s, power);
			case STILLNESS -> castStillness(p, s, power);
			case WITNESS -> castWitness(p, s, power);
			case FORGOTTEN -> castForgotten(p, s, power);
		};
	}

	/** Whether this Edict has been raised to Rank III, where its capstone takes hold. */
	static boolean capstone(PlayerState s, Edict e) {
		return s.edictRank.getOrDefault(e.id, 1) >= 3;
	}

	static void fail(ServerPlayer p, String key) {
		p.sendOverlayMessage(Component.translatable("message.afterworld.fail." + key).withColor(0xB0A8A0));
		Fx.privateSound(p, AwSounds.EDICT_FAIL, 0.6F, 1.0F);
	}

	// ------------------------------------------------------------------ Return

	static boolean castReturn(ServerPlayer p, PlayerState s, float power, boolean spectral) {
		ServerLevel level = p.level();
		if (!spectral && dev.afterworld.entity.boss.BellRingerSystem.tryStealBell(p)) {
			return true;
		}
		ItemStack held = p.getMainHandItem();
		if (!held.isEmpty() && held.isDamageableItem()) {
			float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * Math.max(0.6F, power);
			if (spectral) {
				ThrownWeapon.launch(p, held.copy(), -1, damage * 0.6F, power, true);
				return true;
			}
			int slot = p.getInventory().getSelectedSlot();
			ItemStack thrown = held.copy();
			p.getInventory().setItem(slot, ItemStack.EMPTY);
			ThrownWeapon w = ThrownWeapon.launch(p, thrown, slot, damage, power, false);
			// Rank III: it leaps from foe to foe, and lands home with a shockwave.
			if (capstone(s, Edict.RETURN)) {
				w.ricochet(6);
			}
			return true;
		}
		// Nothing to throw: recall marked objects (your dropped items and fired arrows) instead.
		AABB box = p.getBoundingBox().inflate(32 * power);
		int recalled = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, i -> i.getOwner() == p)) {
			if (recalled < 12) {
				Vfx.beam(level, item.position(), p.position().add(0, 1, 0), 0.12F, 0, Edict.RETURN.color, 8);
			}
			item.setPos(p.getX(), p.getY() + 0.3, p.getZ());
			item.setNoPickUpDelay();
			recalled++;
		}
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, box, a -> a.getOwner() == p)) {
			if (recalled < 12) {
				Vfx.beam(level, arrow.position(), p.position().add(0, 1, 0), 0.12F, 0, Edict.RETURN.color, 8);
			}
			arrow.setPos(p.getX(), p.getY() + 0.5, p.getZ());
			arrow.setDeltaMovement(Vec3.ZERO);
			recalled++;
		}
		if (recalled == 0) {
			fail(p, "return_nothing");
			return false;
		}
		Fx.sound(level, p.position(), SoundEvents.TRIDENT_RETURN, 1.0F, 0.9F);
		return true;
	}

	// ------------------------------------------------------------------ Echoes

	static boolean castEchoes(ServerPlayer p, PlayerState s, float power) {
		long now = p.level().getGameTime();
		// Rank III: the afterimage lingers ten seconds, strikes at three quarters and repeats two Edicts.
		boolean cap = capstone(s, Edict.ECHOES);
		s.echoCapstone = cap;
		s.echoEdicts = cap ? 2 : 1;
		s.echoWindowUntil = now + (cap ? Math.max(200, Math.round(120 * power)) : Math.round(120 * power));
		s.hudDirty = true;
		Fx.ring(p.level(), Fx.dust(Edict.ECHOES.color, 1.0F), p.position().add(0, 0.1, 0), 1.4, 16);
		Vfx.pillar(p.level(), p.position(), 0.5F, 2.2F, Edict.ECHOES.color, 20);
		Vfx.ring(p.level(), p.position(), 2.4F, 0.4F, 0.9F, Edict.ECHOES.color, 12);
		Fx.sound(p.level(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
		return true;
	}

	// ------------------------------------------------------------------ Borrowed Time

	static boolean castBorrowedTime(ServerPlayer p, PlayerState s) {
		long now = p.level().getGameTime();
		PlayerState.Snapshot best = null;
		for (PlayerState.Snapshot snap : s.history) {
			if (snap == null) {
				continue;
			}
			long age = now - snap.time();
			if (age >= 40 && age <= 130 && (best == null || snap.time() < best.time())) {
				best = snap;
			}
		}
		if (best == null || !best.dim().equals(p.level().dimension().identifier().toString())) {
			fail(p, "borrowed_time_none");
			return false;
		}
		ServerLevel level = p.level();
		Vec3 from = p.position();
		Vec3 to = new Vec3(best.x(), best.y(), best.z());
		boolean cap = capstone(s, Edict.BORROWED_TIME);
		if (cap) {
			timeBomb(p, level, from);
		}
		Fx.line(level, Fx.dust(Edict.BORROWED_TIME.color, 1.0F), from.add(0, 1, 0), to.add(0, 1, 0), 1.2);
		Vfx.beam(level, from.add(0, 1, 0), to.add(0, 1, 0), 0.3F, 0, Edict.BORROWED_TIME.color, 12);
		Vfx.pillar(level, from, 0.45F, 2.2F, Edict.BORROWED_TIME.color, 12);
		p.teleportTo(level, to.x, to.y, to.z, Set.of(), best.yRot(), best.xRot(), false);
		p.setHealth(Math.min(p.getMaxHealth(), Math.max(1.0F, best.health())));
		p.getFoodData().setFoodLevel(best.food());
		p.getFoodData().setSaturation(best.saturation());
		p.setRemainingFireTicks(best.fire());
		p.resetFallDistance();
		p.setDeltaMovement(Vec3.ZERO);
		Fx.column(level, Fx.dust(Edict.BORROWED_TIME.color, 1.2F), to, 12, 0.3, 2.0);
		Vfx.pillar(level, to, 0.6F, 2.8F, Edict.BORROWED_TIME.color, 16);
		Vfx.sigil(level, to, 1.8F, Vfx.SIGIL_CLOCK, -8.0F, Edict.BORROWED_TIME.color, 24, null);
		Vfx.flash(p, 0.3F, Edict.BORROWED_TIME.color, 12);
		Fx.sound(level, to, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8F, 1.6F);
		// Rule collision: time you borrow back includes the Edicts you spent in it.
		int refunded = 0;
		for (Edict e : Edict.VALUES) {
			// Rank III gives back every cooldown, not only those spent in the borrowed moments.
			if (e != Edict.BORROWED_TIME && (cap || s.castAt[e.ordinal()] >= now - 110) && s.cooldownUntil[e.ordinal()] > now) {
				s.cooldownUntil[e.ordinal()] = now;
				refunded++;
			}
		}
		if (refunded > 0) {
			Progress.codex(p, "combo.rewind_refund");
			p.sendOverlayMessage(Component.translatable("message.afterworld.rewind_refund", refunded).withColor(Edict.BORROWED_TIME.color));
		}
		return true;
	}

	/** Borrowed Time III: the moment you leave behind goes off a second later. */
	private static void timeBomb(ServerPlayer p, ServerLevel level, Vec3 at) {
		Fx.ring(level, Fx.dust(Edict.BORROWED_TIME.color, 1.6F), at.add(0, 0.2, 0), 4.5, 16);
		Vfx.sigil(level, at, 4.5F, Vfx.SIGIL_CLOCK, 9.0F, Edict.BORROWED_TIME.color, 26, null);
		Fx.sound(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.7F);
		dev.afterworld.util.Scheduler.after(24, () -> {
			if (p.isRemoved() || p.level() != level) {
				return;
			}
			float power = 1.0F + 0.3F * (PlayerState.of(p).edictRank.getOrDefault(Edict.BORROWED_TIME.id, 1) - 1);
			for (LivingEntity e : dev.afterworld.combat.Combat.foes(level, p, at, 5)) {
				e.hurtServer(level, level.damageSources().indirectMagic(p, p), 12.0F * power);
				Vec3 push = e.position().subtract(at);
				if (push.lengthSqr() > 1.0E-4) {
					e.knockback(0.9, -push.x, -push.z, level.damageSources().indirectMagic(p, p), 12.0F);
				}
			}
			Fx.burst(level, ParticleTypes.POOF, at.add(0, 1, 0), 16, 0.8, 0.05);
			Fx.burst(level, Fx.dust(Edict.BORROWED_TIME.color, 1.8F), at.add(0, 1, 0), 24, 1.6, 0.1);
			Vfx.ring(level, at, 5.5F, 1.0F, 1.6F, Edict.BORROWED_TIME.color, 14);
			Vfx.flare(level, at.add(0, 1, 0), 2.4F, true, Edict.BORROWED_TIME.color, 10);
			Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.3F);
			Fx.sound(level, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0F, 0.6F);
		});
	}

	// ------------------------------------------------------------------ Weightlessness

	static boolean castWeightlessness(ServerPlayer p, PlayerState s, float power) {
		ServerLevel level = p.level();
		Vec3 center = aimPoint(p, 24);
		double radius = 5 * Math.min(1.5F, power);
		// Rank III: the rift drags foes into its eye, then slams them down for double the fall.
		Zones.open(Zones.Kind.RIFT, level, center, radius, Math.round(120 * power), p.getUUID(), power, capstone(s, Edict.WEIGHTLESSNESS));
		Fx.ring(level, Fx.dust(Edict.WEIGHTLESSNESS.color, 1.4F), center, radius, 16);
		Fx.burst(level, ParticleTypes.REVERSE_PORTAL, center, 30, radius * 0.4, 0.05);
		Vfx.beam(level, p.getEyePosition().add(0, -0.3, 0), center.add(0, 0.5, 0), 0.12F, 0, Edict.WEIGHTLESSNESS.color, 8);
		Vfx.sigil(level, center, (float) radius, Vfx.SIGIL_STAR, 2.0F, Edict.WEIGHTLESSNESS.color, Math.round(120 * power), null);
		Vfx.ring(level, center, (float) radius, 0.5F, 1.2F, Edict.WEIGHTLESSNESS.color, 14);
		Vfx.helix(level, center, (float) radius * 0.5F, 4.0F, Edict.WEIGHTLESSNESS.color, 36);
		Fx.sound(level, center, SoundEvents.BEACON_ACTIVATE, 0.9F, 1.7F);
		return true;
	}

	// ------------------------------------------------------------------ Hunger

	static boolean castHunger(ServerPlayer p, PlayerState s, float power) {
		long now = p.level().getGameTime();
		s.hungerUntil = now + Math.round(400 * power);
		// Rank III: five echoes, striking at half your strength and bursting as they fade.
		s.hungerCapstone = capstone(s, Edict.HUNGER);
		s.hudDirty = true;
		Fx.burst(p.level(), ParticleTypes.SCULK_SOUL, p.position().add(0, 1, 0), 12, 0.4, 0.02);
		Vfx.ring(p.level(), p.position(), 4.0F, 0.5F, 1.2F, Edict.HUNGER.color, 12);
		Vfx.pillar(p.level(), p.position(), 0.6F, 2.4F, Edict.HUNGER.color, 14);
		Fx.sound(p.level(), p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 1.4F);
		return true;
	}

	// ------------------------------------------------------------------ Hospitality

	static boolean castHospitality(ServerPlayer p, PlayerState s, float power) {
		long now = p.level().getGameTime();
		if (s.oathbreakerUntil > now) {
			fail(p, "oathbreaker");
			return false;
		}
		int duration = Math.round(900 * power * (s.discipline == Discipline.KEEPER ? 1.5F : 1.0F));
		long until = now + duration;
		ServerLevel level = p.level();
		Entity looked = lookedAt(p, 16);
		List<Mob> targets;
		if (looked instanceof Mob mob) {
			targets = List.of(mob);
		} else {
			targets = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6), m -> m instanceof Enemy && m.isAlive());
		}
		int offered = 0;
		// Rank III: guests fight for you for twenty seconds instead of only standing down.
		boolean cap = capstone(s, Edict.HOSPITALITY);
		for (Mob mob : targets) {
			if (EdictSystem.bossRefusesPact(p, mob)) {
				continue;
			}
			Pacts.offer(p, mob, until, cap ? now + 400 : 0);
			offered++;
		}
		if (offered == 0) {
			fail(p, "hospitality_none");
			return false;
		}
		s.pactUntil = until;
		s.pactAllies = cap;
		for (Mob mob : targets) {
			if (mob.isAlive() && mob.position().distanceToSqr(p.position()) < 400) {
				Vfx.helix(level, mob.position(), Math.max(0.6F, mob.getBbWidth() * 0.7F), mob.getBbHeight() + 0.6F, Edict.HOSPITALITY.color, 26);
				Vfx.beam(level, p.position().add(0, 1.2, 0), mob.position().add(0, mob.getBbHeight() * 0.6, 0), 0.08F, 0, Edict.HOSPITALITY.color, 10);
			}
		}
		Vfx.ring(level, p.position(), 6.0F, 0.5F, 0.6F, Edict.HOSPITALITY.color, 14);
		s.hudDirty = true;
		Fx.sound(level, p.position(), SoundEvents.VILLAGER_CELEBRATE, 0.8F, 0.8F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.pact", offered, duration / 20).withColor(Edict.HOSPITALITY.color));
		return true;
	}

	// ------------------------------------------------------------------ Trespass

	static boolean castTrespass(ServerPlayer p, PlayerState s, float power) {
		ServerLevel level = p.level();
		Vec3 look = p.getLookAngle();
		// Rank III: any direction, up and down through floors too, and even through open air.
		boolean cap = capstone(s, Edict.TRESPASS);
		Vec3 dir = cap ? look : new Vec3(look.x, 0, look.z);
		if (dir.lengthSqr() < 1.0E-4) {
			fail(p, "trespass_up");
			return false;
		}
		dir = dir.normalize();
		Vec3 start = p.position();
		double max = (4.6 * Math.min(1.5F, power) + 1.0) * (cap ? 1.3 : 1.0);
		Vec3 lastFree = null;
		boolean passedSolid = false;
		for (double d = 0.5; d <= max; d += 0.25) {
			Vec3 pos = start.add(dir.scale(d));
			BlockPos feet = BlockPos.containing(pos.x, pos.y + 0.2, pos.z);
			if (!level.isLoaded(feet)) {
				break;
			}
			BlockState fs = level.getBlockState(feet);
			BlockState hs = level.getBlockState(feet.above());
			if (fs.is(AwTags.WARDED) || hs.is(AwTags.WARDED)) {
				fail(p, "trespass_warded");
				Fx.burst(level, ParticleTypes.WAX_OFF, Vec3.atCenterOf(feet), 8, 0.4, 0.02);
				return false;
			}
			AABB box = p.getBoundingBox().move(pos.subtract(start));
			if (!level.noCollision(p, box)) {
				passedSolid = true;
				continue;
			}
			lastFree = pos;
			if (passedSolid) {
				return trespassTo(p, level, start, pos, cap);
			}
		}
		if (cap && lastFree != null && lastFree.distanceTo(start) > 2.0) {
			return trespassTo(p, level, start, lastFree, true);
		}
		fail(p, "trespass_none");
		return false;
	}

	private static boolean trespassTo(ServerPlayer p, ServerLevel level, Vec3 start, Vec3 pos, boolean wake) {
		Fx.column(level, Fx.dust(Edict.TRESPASS.color, 1.0F), start, 10, 0.3, 1.8);
		p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), p.getYRot(), p.getXRot(), false);
		p.resetFallDistance();
		Fx.column(level, Fx.dust(Edict.TRESPASS.color, 1.0F), pos, 10, 0.3, 1.8);
		Vfx.pillar(level, start, 0.5F, 2.3F, Edict.TRESPASS.color, 14);
		Vfx.pillar(level, pos, 0.5F, 2.3F, Edict.TRESPASS.color, 14);
		Vfx.beam(level, start.add(0, 1, 0), pos.add(0, 1, 0), 0.35F, 0.1F, Edict.TRESPASS.color, 10);
		Vfx.flare(level, pos.add(0, 1, 0), 1.0F, true, Edict.TRESPASS.color, 8);
		Fx.sound(level, pos, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.5F);
		if (wake) {
			riftWake(p, level, start.add(0, 1, 0), pos.add(0, 1, 0));
		}
		return true;
	}

	/** Trespass III: the tear you leave behind cuts whatever follows through it, for three seconds. */
	private static void riftWake(ServerPlayer p, ServerLevel level, Vec3 a, Vec3 b) {
		for (int i = 0; i < 12; i++) {
			dev.afterworld.util.Scheduler.after(i * 5, () -> {
				if (p.isRemoved() || p.level() != level) {
					return;
				}
				Vfx.beam(level, a, b, 0.25F, 0.25F, Edict.TRESPASS.color, 6);
				AABB box = new AABB(a, b).inflate(1.2);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != p && e instanceof Enemy)) {
					Vec3 c = e.getBoundingBox().getCenter();
					if (distanceToSegment(c, a, b) < 1.1 + e.getBbWidth() * 0.5) {
						e.hurtServer(level, level.damageSources().indirectMagic(p, p), 3.0F);
						Fx.burst(level, ParticleTypes.CRIT, c, 6, 0.2, 0.2);
					}
				}
			});
		}
	}

	private static double distanceToSegment(Vec3 c, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len));
		return c.distanceTo(a.add(ab.scale(t)));
	}

	// ------------------------------------------------------------------ Stillness

	static boolean castStillness(ServerPlayer p, PlayerState s, float power) {
		ServerLevel level = p.level();
		// Rank III: creatures inside freeze outright for three seconds; what you deal them lands at the end, half again as hard.
		Zones.open(Zones.Kind.STILL, level, p.position().add(0, 1, 0), 7 * Math.min(1.4F, power), Math.round(80 * power), p.getUUID(), power,
			capstone(s, Edict.STILLNESS));
		Fx.ring(level, Fx.dust(Edict.STILLNESS.color, 1.4F), p.position().add(0, 0.2, 0), 7, 16);
		Vfx.ring(level, p.position(), 7.0F * Math.min(1.4F, power), 0.6F, 1.4F, Edict.STILLNESS.color, 14);
		Vfx.sigil(level, p.position(), 7.0F * Math.min(1.4F, power), Vfx.SIGIL_CLOCK, 0.6F, Edict.STILLNESS.color, Math.round(80 * power), null);
		Fx.sound(level, p.position(), SoundEvents.BELL_RESONATE, 0.8F, 1.8F);
		return true;
	}

	// ------------------------------------------------------------------ Witness

	static boolean castWitness(ServerPlayer p, PlayerState s, float power) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		int glowTicks = Math.round(200 * power);
		int revealed = 0;
		// Rank III: what you witness takes a quarter more harm for fifteen seconds, and hidden
		// chests and ores within 48 blocks shine through the stone.
		boolean cap = capstone(s, Edict.WITNESS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32 * power), e -> e != p && e.isAlive())) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0, false, false));
			if (cap && !(e instanceof net.minecraft.world.entity.player.Player)) {
				EdictSystem.witness(e, now + 300);
			}
			revealed++;
		}
		if (cap) {
			Revelations.shine(p, level, 48);
		}
		// Containers and spawners within reach pulse once. Block entities are already indexed per chunk.
		int pulses = 0;
		int r = 24;
		BlockPos c = p.blockPosition();
		for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4 && pulses < 24; cx++) {
			for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4 && pulses < 24; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if ((be instanceof RandomizableContainerBlockEntity || be instanceof SpawnerBlockEntity) && be.getBlockPos().distSqr(c) <= r * r) {
						Fx.column(level, ParticleTypes.END_ROD, Vec3.atBottomCenterOf(be.getBlockPos()), 4, 0.1, 2.5);
						Vfx.pillar(level, Vec3.atBottomCenterOf(be.getBlockPos()), 0.3F, 3.0F, Edict.WITNESS.color, 30);
						if (++pulses >= 24) {
							break;
						}
					}
				}
			}
		}
		s.revealedUntil = now + glowTicks;
		// The nearest unresolved lead flares toward its source.
		RumourEntry lead = EdictSystem.nearestLead(p, s);
		if (lead != null) {
			Vec3 target = new Vec3(lead.x + 0.5, lead.y, lead.z + 0.5);
			Vec3 dir = target.subtract(p.position());
			dir = new Vec3(dir.x, 0, dir.z).normalize();
			Vec3 from = p.position().add(0, 1.2, 0);
			Fx.line(level, Fx.dust(Edict.WITNESS.color, 1.2F), from, from.add(dir.scale(14)), 1.4);
			Vfx.beam(level, from, from.add(dir.scale(14)), 0.25F, 0, Edict.WITNESS.color, 30);
		}
		Vfx.ring(level, p.position(), Math.min(40.0F, 32.0F * power), 0.7F, 0, Edict.WITNESS.color, 26);
		if (now < s.forgottenUntil) {
			Progress.codex(p, "combo.ambush");
		}
		Fx.sound(level, p.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.3F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.witness", revealed, pulses).withColor(Edict.WITNESS.color));
		return true;
	}

	// ------------------------------------------------------------------ Forgotten

	static boolean castForgotten(ServerPlayer p, PlayerState s, float power) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		long until = now + Math.round(160 * power);
		int dropped = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16 * power), Entity::isAlive)) {
			if (mob.getTarget() == p) {
				mob.setTarget(null);
				dropped++;
			}
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			if (mob.getLastHurtByMob() == p) {
				mob.setLastHurtByMob(null);
			}
		}
		AwTargeting.forget(p.getUUID(), until);
		s.forgottenUntil = until;
		// Rank III: the first strike from hiding lands three times as hard and keeps you hidden a moment longer.
		s.forgottenCapstone = capstone(s, Edict.FORGOTTEN);
		s.forgottenStrikeUsed = false;
		s.forgottenBonusPending = false;
		s.hudDirty = true;
		Fx.burst(level, ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 18, 0.4, 0.01);
		Fx.burst(level, Fx.dust(Edict.FORGOTTEN.color, 1.2F), p.position().add(0, 1, 0), 12, 0.6, 0);
		Vfx.pillar(level, p.position(), 0.7F, 2.4F, Edict.FORGOTTEN.color, 12);
		Vfx.ring(level, p.position(), 3.0F, 0.5F, 0, Edict.FORGOTTEN.color, 10);
		Fx.sound(level, p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 0.8F);
		if (now < s.revealedUntil) {
			Progress.codex(p, "combo.ambush");
		}
		p.sendOverlayMessage(Component.translatable("message.afterworld.forgotten", dropped).withColor(Edict.FORGOTTEN.color));
		return true;
	}

	// ------------------------------------------------------------------ helpers

	static Entity lookedAt(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), eye.distanceToSqr(end));
		return hit == null ? null : hit.getEntity();
	}

	static Vec3 aimPoint(ServerPlayer p, double range) {
		Entity e = lookedAt(p, range);
		if (e != null) {
			return e.position().add(0, 0.5, 0);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		return point.add(0, 1.0, 0);
	}

	static boolean isMarked(Entity e) {
		return MobMark.has(e, MobMark.ECHO);
	}
}
