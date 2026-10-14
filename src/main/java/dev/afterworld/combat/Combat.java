package dev.afterworld.combat;

import dev.afterworld.aspect.Aspects;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.entity.boss.BossMob;
import dev.afterworld.item.WeaponGrowth;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Relics;
import dev.afterworld.player.Renown;
import dev.afterworld.quest.Quests;
import dev.afterworld.registry.AwEffects;
import dev.afterworld.registry.AwGear;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The action layer under every fight: dodge frames, parries, Resonance from fighting well,
 * weapon oils, lifesteal and reflection, summoned allies, set bonuses and the numbers that rise
 * from what you strike. Everything that turns "click on a mob" into a fight lives here.
 */
public final class Combat {
	/** Summoned allies: who they serve and when they crumble. */
	private static final Map<UUID, Summon> SUMMONS = new HashMap<>();

	private record Summon(UUID owner, long until, ServerLevel level) {
	}

	private Combat() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Combat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Combat::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(Combat::tick);
		EdictSystem.DAMAGE_SCALERS.add(Combat::scale);
	}

	// ---------------------------------------------------------------- who is a foe

	/** Who a power may hit: monsters, and anything hunting the player. Never players, pets or the player's summons. */
	public static boolean isFoe(ServerPlayer p, Entity e) {
		if (e == p || !(e instanceof LivingEntity l) || !l.isAlive() || e instanceof Player || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwner() == p) {
			return false;
		}
		Summon s = SUMMONS.get(e.getUUID());
		if (s != null) {
			return false;
		}
		if (dev.afterworld.npc.Npcs.npcOf(e) != null) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob m && m.getTarget() == p;
	}

	public static List<LivingEntity> foes(ServerLevel level, ServerPlayer p, Vec3 at, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> isFoe(p, e) && e.position().distanceToSqr(at) < radius * radius);
	}

	/** Foes in a cone in front of the player. */
	public static List<LivingEntity> cone(ServerLevel level, ServerPlayer p, double range, double halfAngleDeg) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		double cos = Math.cos(Math.toRadians(halfAngleDeg));
		return foes(level, p, p.position(), range).stream().filter(e -> {
			Vec3 d = e.position().subtract(p.position()).multiply(1, 0, 1);
			return d.lengthSqr() < 1.0 || d.normalize().dot(look) >= cos;
		}).toList();
	}

	// ---------------------------------------------------------------- summons

	/** Makes {@code mob} fight for {@code owner} for {@code ticks}, then crumble. */
	public static void bindSummon(Mob mob, ServerPlayer owner, int ticks) {
		if (PlayerState.of(owner).discipline == dev.afterworld.player.Discipline.KEEPER) {
			ticks = ticks * 3 / 2;
		}
		SUMMONS.put(mob.getUUID(), new Summon(owner.getUUID(), owner.level().getGameTime() + ticks, owner.level()));
		var targets = ((dev.afterworld.mixin.MobAccessor) mob).afterworld$targetSelector();
		targets.removeAllGoals(g -> true);
		mob.setTarget(null);
	}

	public static boolean isSummonOf(Entity e, ServerPlayer p) {
		Summon s = SUMMONS.get(e.getUUID());
		return s != null && s.owner.equals(p.getUUID());
	}

	private static void tickSummons(MinecraftServer server) {
		if (SUMMONS.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Summon>> it = SUMMONS.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			Summon s = en.getValue();
			Entity e = s.level.getEntity(en.getKey());
			ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
			if (!(e instanceof Mob mob) || !mob.isAlive()) {
				it.remove();
				continue;
			}
			if (owner == null || s.level.getGameTime() > s.until || owner.level() != s.level) {
				Fx.burst(s.level, ParticleTypes.SOUL, mob.position().add(0, 1, 0), 20, 0.4, 0.05);
				Fx.sound(s.level, mob.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
				mob.discard();
				it.remove();
				continue;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || t == owner || !isFoe(owner, t) || t.distanceToSqr(mob) > 400) {
				LivingEntity best = null;
				double bd = Double.MAX_VALUE;
				for (LivingEntity f : foes(s.level, owner, mob.position(), 16)) {
					double d = f.distanceToSqr(mob);
					if (d < bd) {
						bd = d;
						best = f;
					}
				}
				mob.setTarget(best);
				if (best == null && mob.distanceToSqr(owner) > 36) {
					mob.getNavigation().moveTo(owner, 1.2);
				}
			}
		}
	}

	// ---------------------------------------------------------------- damage

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (dev.afterworld.minigame.Trials.intercept(victim, source)) {
			return false;
		}
		Entity attacker = source.getEntity();
		// Summons never hurt their master, and the master never hurts them.
		if (attacker instanceof ServerPlayer p && isSummonOf(victim, p) || victim instanceof ServerPlayer p2 && attacker != null && isSummonOf(attacker, p2)) {
			return false;
		}
		if (!(victim instanceof ServerPlayer p)) {
			return true;
		}
		PlayerState s = PlayerState.of(p);
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (source.is(DamageTypeTags.IS_FALL) && (s.safeFallUntil > now || s.gliding || s.climbing)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FIRE) && SetBonuses.has(p, AwGear.Set.EMBERFORGED)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FREEZING) && SetBonuses.has(p, AwGear.Set.RIMEGUARD)) {
			return false;
		}
		boolean hostile = attacker instanceof LivingEntity || source.getDirectEntity() instanceof Projectile;
		// Dodging: the frames of a roll can't be touched. A blow that lands inside them is a perfect dodge.
		if (hostile && s.dodgeUntil > now) {
			if (!s.dodgePerfect) {
				s.dodgePerfect = true;
				perfect(p, attacker instanceof LivingEntity l ? l : null, false);
			}
			return false;
		}
		// Riposte: Moonsilver's stance turns the blow into a counter.
		if (hostile && s.riposteUntil > now && attacker instanceof LivingEntity enemy) {
			s.riposteUntil = 0;
			Arts.riposte(level, p, enemy);
			return false;
		}
		// The parry: a heartbeat-long window; anything caught in it is turned and the attacker staggers.
		if (hostile && s.parryUntil > now) {
			s.parryUntil = 0;
			s.parryReadyAt = now + 4;
			if (source.getDirectEntity() instanceof Projectile pr) {
				pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.2));
				pr.setOwner(p);
			}
			LivingEntity enemy = attacker instanceof LivingEntity l ? l : null;
			if (enemy != null && enemy.distanceToSqr(p) < 36) {
				if (enemy instanceof BossMob) {
					enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3), p);
				} else {
					enemy.addEffect(new MobEffectInstance(AwEffects.STOPPED, 25, 0), p);
				}
				Vec3 d = enemy.position().subtract(p.position()).multiply(1, 0, 1).normalize();
				enemy.knockback(0.7, -d.x, -d.z, level.damageSources().playerAttack(p), 0);
			}
			perfect(p, enemy, true);
			if (enemy != null) {
				dev.afterworld.minigame.Trials.parried(p, enemy);
			}
			if (enemy != null && s.skills.contains("blade.riposte")) {
				enemy.hurtServer(level, level.damageSources().playerAttack(p), 4.0F);
			}
			Quests.onParry(p);
			return false;
		}
		// Gilded glyphmail turns a quarter of all arrows aside.
		if (source.is(DamageTypeTags.IS_PROJECTILE) && SetBonuses.has(p, AwGear.Set.GILDED_GLYPHMAIL) && p.getRandom().nextFloat() < 0.25F) {
			Fx.burst(level, Fx.dust(0xFFD060, 1.2F), p.position().add(0, 1.2, 0), 12, 0.4, 0.05);
			Vfx.flare(level, p.position().add(0, 1.2, 0), 1.0F, true, 0xFFD060, 8);
			Fx.sound(level, p.position(), SoundEvents.SHIELD_BLOCK.value(), 0.8F, 1.4F);
			return false;
		}
		if (dev.afterworld.player.Disciplines.lastWordSaves(p, s, amount)) {
			return false;
		}
		// The Ring of Second Breath: once every ten minutes, a killing blow leaves you standing.
		if (amount >= p.getHealth() + p.getAbsorptionAmount() && Relics.has(p, "ring_of_second_breath") && now >= s.secondBreathAt) {
			s.secondBreathAt = now + 20 * 600;
			p.setHealth(Math.max(p.getHealth(), 8.0F));
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 3));
			Fx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 60, 0.5, 0.4);
			Vfx.pillar(level, p.position(), 0.9F, 4.0F, 0x7FE3D2, 20);
			Vfx.helix(level, p.position(), 1.1F, 2.6F, 0x7FE3D2, 30);
			Vfx.flash(p, 0.45F, 0x7FE3D2, 18);
			Fx.sound(level, p.position(), SoundEvents.TOTEM_USE, 0.8F, 1.3F);
			p.sendOverlayMessage(Component.translatable("message.afterworld.second_breath").withColor(0x7FE3D2));
			return false;
		}
		return true;
	}

	/** A perfect dodge or parry: a flash, a clang, Resonance and a beat of slowed time on the attacker. */
	private static void perfect(ServerPlayer p, LivingEntity attacker, boolean parry) {
		ServerLevel level = p.level();
		Aspects.addResonance(p, parry ? 15 : 10);
		dev.afterworld.movement.Stamina.refund(p, parry ? 15 : 10);
		if (parry) {
			Fx.burst(level, ParticleTypes.CRIT, p.position().add(p.getLookAngle().scale(0.8)).add(0, 1.3, 0), 20, 0.2, 0.4);
			Vec3 clash = p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.25, 0);
			Vfx.flare(level, clash, 1.4F, true, 0xFFF4DC, 9);
			Vfx.ring(level, clash, p.getLookAngle(), 1.3F, 0.25F, 0xFFE8A0, 8);
			Vfx.flash(p, 0.22F, 0xFFE8A0, 8);
			Fx.burst(level, net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4DC), p.position().add(p.getLookAngle().scale(0.8)).add(0, 1.3, 0), 1, 0, 0);
			Fx.sound(level, p.position(), AwSounds.PARRY, 1.2F, 1.0F);
			Fx.privateSound(p, AwSounds.PERFECT, 0.8F, 1.1F);
		} else {
			Fx.burst(level, Fx.dust(0xB8E8FF, 1.0F), p.position().add(0, 1, 0), 10, 0.5, 0.05);
			Vfx.pillar(level, p.position(), 0.45F, 2.0F, 0xB8E8FF, 10);
			Vfx.ring(level, p.position(), 2.2F, 0.4F, 0, 0xB8E8FF, 9);
			Vfx.flash(p, 0.15F, 0xB8E8FF, 8);
			Fx.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.8F);
			Fx.privateSound(p, AwSounds.PERFECT, 0.8F, 1.0F);
			if (attacker != null && !(attacker instanceof BossMob)) {
				attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), p);
			}
		}
		if (ServerPlayNetworking.canSend(p, AwPayloads.Cinematic.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.Cinematic(AwPayloads.Cinematic.PERFECT, parry ? 1 : 0, p.getId(), parry ? 0xFFE8A0 : 0xB8E8FF));
			ServerPlayNetworking.send(p, new AwPayloads.DamageNumber(p.getId(), 0, parry ? AwPayloads.DamageNumber.PARRY : AwPayloads.DamageNumber.DODGE));
		}
	}

	/** Damage the player deals or takes, scaled by sets, Renown, oils and stances. */
	private static float scale(LivingEntity target, DamageSource source, float damage) {
		if (source.getEntity() instanceof ServerPlayer p && target != p) {
			PlayerState s = PlayerState.of(p);
			// Renown: +2% damage every five levels.
			damage *= 1.0F + 0.02F * (Renown.level(s) / 5);
			if (source.getDirectEntity() instanceof Projectile && SetBonuses.has(p, AwGear.Set.DUSKPINE_RANGER)) {
				damage *= 1.3F;
			}
			if (target.isFullyFrozen() && p.getMainHandItem().is(AwGear.RIMEBACK_CLEAVER)) {
				damage *= 1.5F;
			}
			if (source.getDirectEntity() == p) {
				if (s.skills.contains("blade.edge")) {
					damage *= 1.08F;
				}
				damage *= 1.0F + (float) dev.afterworld.player.Skills.bonus(s, dev.afterworld.player.Skills.Stat.MELEE);
				// Duelist's Oath: the foe that has chosen you takes more.
				if (s.skills.contains("blade.duelist") && target instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) {
					damage *= 1.15F;
				}
				if (s.skills.contains("blade.flow") && s.comboTarget == target.getId()) {
					damage *= 1.0F + 0.04F * Math.min(5, s.combo);
				}
			}
			if (s.skills.contains("blade.execution") && target.getHealth() < target.getMaxHealth() * 0.25F) {
				damage *= 1.4F;
			}
		}
		if (target instanceof ServerPlayer p) {
			if (p.level().getGameTime() < PlayerState.of(p).guardUntil) {
				damage *= 0.5F;
			}
			if (source.is(DamageTypeTags.IS_FIRE) && Relics.has(p, "emberheart_pendant")) {
				damage *= 0.5F;
			}
			if (source.is(DamageTypeTags.IS_FALL) && PlayerState.of(p).skills.contains("wayfarer.featherfall")) {
				damage *= 0.5F;
			}
		}
		return damage;
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer p && victim != p && taken > 0) {
			PlayerState s = PlayerState.of(p);
			ServerLevel level = p.level();
			long now = level.getGameTime();
			Aspects.addResonance(p, Math.min(8.0F, taken * 0.6F));
			if (s.lifestealUntil > now) {
				p.heal(taken * 0.25F);
			}
			boolean melee = source.getDirectEntity() == p;
			if (melee || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
				Impacts.hit(level, victim, p, taken, melee && p.fallDistance > 0 && !p.onGround(), victim.isDeadOrDying());
			}
			if (melee) {
				oil(p, victim);
				if (Relics.has(p, "emberheart_pendant")) {
					victim.igniteForSeconds(2.0F);
				}
				if (Relics.has(p, "rimecore_charm")) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), p);
				}
				if (s.comboTarget == victim.getId() && now - s.comboAt < 40) {
					s.combo++;
				} else {
					s.combo = 1;
					s.comboTarget = victim.getId();
				}
				s.comboAt = now;
			}
			if (ServerPlayNetworking.canSend(p, AwPayloads.DamageNumber.TYPE)) {
				int kind = source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING) || source.getMsgId().contains("magic")
					? AwPayloads.DamageNumber.MAGIC : melee && p.fallDistance > 0 && !p.onGround() ? AwPayloads.DamageNumber.CRIT : AwPayloads.DamageNumber.NORMAL;
				ServerPlayNetworking.send(p, new AwPayloads.DamageNumber(victim.getId(), taken, kind));
			}
		}
		// A Keeper's wolves mend their Keeper with every bite.
		if (attacker != null && taken > 0) {
			Summon sm = SUMMONS.get(attacker.getUUID());
			if (sm != null && attacker instanceof net.minecraft.world.entity.animal.wolf.Wolf && victim.level() instanceof ServerLevel sl) {
				ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(sm.owner());
				if (owner != null) {
					owner.heal(1.0F);
				}
				if (victim instanceof net.minecraft.world.entity.Mob mob && !(victim instanceof BossMob)) {
					mob.setTarget((LivingEntity) attacker);
				}
			}
		}
		if (victim instanceof ServerPlayer p && taken > 0) {
			Aspects.addResonance(p, Math.min(4.0F, taken * 0.3F));
			if (attacker instanceof LivingEntity enemy && enemy != p) {
				if (SetBonuses.has(p, AwGear.Set.EMBERFORGED)) {
					enemy.igniteForSeconds(3.0F);
				}
				if (SetBonuses.has(p, AwGear.Set.RIMEGUARD)) {
					enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
					enemy.setTicksFrozen(Math.min(enemy.getTicksRequiredToFreeze() + 20, enemy.getTicksFrozen() + 40));
				}
			}
		}
	}

	/** Oils coat a blade for sixty hits: fire, frost or venom on each one. */
	private static void oil(ServerPlayer p, LivingEntity victim) {
		ItemStack weapon = p.getMainHandItem();
		CompoundTag t = weapon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String oil = t.getStringOr("oil", "");
		if (oil.isEmpty()) {
			return;
		}
		ServerLevel level = p.level();
		switch (oil) {
			case "ember_oil" -> {
				victim.igniteForSeconds(4.0F);
				Fx.burst(level, ParticleTypes.FLAME, victim.position().add(0, 1, 0), 6, 0.3, 0.02);
			}
			case "frost_oil" -> {
				victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze() + 60, victim.getTicksFrozen() + 50));
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), p);
				Fx.burst(level, ParticleTypes.SNOWFLAKE, victim.position().add(0, 1, 0), 6, 0.3, 0.02);
			}
			case "venom_oil" -> {
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), p);
				Fx.burst(level, Fx.dust(0x7AB040, 1.0F), victim.position().add(0, 1, 0), 6, 0.3, 0.02);
			}
			default -> {
			}
		}
		int left = t.getIntOr("oil_hits", 0) - 1;
		CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> {
			if (left <= 0) {
				tag.remove("oil");
				tag.remove("oil_hits");
			} else {
				tag.putInt("oil_hits", left);
			}
		});
		if (left <= 0) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.oil_gone").withColor(0x9A9488));
		}
	}

	private static final Item[] ELITE_SPOILS = {AwGear.WYRM_SCALE, AwGear.PALE_ECTOPLASM, AwGear.ANGLER_LURE, AwGear.NARWHAL_TUSK, AwGear.KITSUNE_TAIL,
		AwGear.EDICT_SHARD, AwGear.STAR_FRAGMENT, AwGear.GLYPH_GOLD, AwGear.RIME_PEARL, AwGear.MOONSILVER_INGOT};

	private static void afterDeath(LivingEntity dead, DamageSource source) {
		SUMMONS.remove(dead.getUUID());
		// Bloodthirst: a killing blow struck by hand mends the striker.
		if (source.getEntity() instanceof ServerPlayer killer && source.getDirectEntity() == killer && killer.isAlive()
			&& PlayerState.of(killer).skills.contains("blade.bloodthirst")) {
			killer.heal(2.0F);
		}
		if (dead.entityTags().contains("afterworld:elite") && dead.level() instanceof ServerLevel level && source.getEntity() instanceof ServerPlayer p) {
			// An elite carries better spoils: a rare material, an Edict Shard sometimes, and a purse.
			java.util.List<ItemStack> spoils = new java.util.ArrayList<>();
			spoils.add(new ItemStack(ELITE_SPOILS[level.getRandom().nextInt(ELITE_SPOILS.length)]));
			if (level.getRandom().nextFloat() < 0.35F) {
				spoils.add(new ItemStack(AwGear.EDICT_SHARD));
			}
			spoils.add(new ItemStack(dev.afterworld.registry.AwGoods.SILVER_MARK, 2 + level.getRandom().nextInt(4)));
			for (ItemStack st : spoils) {
				var item = new net.minecraft.world.entity.item.ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), st);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
			dev.afterworld.player.Renown.grant(p, 25, Component.translatable("creature.afterworld.elite_slain", dead.getType().getDescription()).getString());
		}
		if (source.getEntity() instanceof ServerPlayer p && dead != p) {
			boolean big = dead instanceof BossMob;
			Aspects.addResonance(p, big ? 25 : 5);
			int lesson = big ? 120 : Math.max(1, (int) (dead.getMaxHealth() / 4));
			WeaponGrowth.earn(p, p.getMainHandItem(), lesson);
			if (Relics.has(p, "lich_finger_bone")) {
				p.heal(4.0F);
				Fx.burst(p.level(), Fx.dust(0x9A40C0, 1.0F), p.position().add(0, 1, 0), 10, 0.4, 0.05);
			}
		}
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		tickSummons(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			long now = p.level().getGameTime();
			if (s.gliding || s.climbing || s.safeFallUntil > now) {
				p.fallDistance = 0;
			}
			if (s.reflectUntil > now) {
				reflect(p);
			}
			if ((now + p.getId()) % 20 == 0) {
				SetBonuses.tick(p);
				Relics.tick(p);
				dev.afterworld.player.Skills.tick(p);
			}
		}
	}

	/** The Glyph of Judgment's dome: arrows and spells that come close are turned back on their makers. */
	private static void reflect(ServerPlayer p) {
		ServerLevel level = p.level();
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5), pr -> pr.getOwner() != p)) {
			Vec3 v = pr.getDeltaMovement();
			Vec3 toMe = p.position().add(0, 1, 0).subtract(pr.position());
			if (v.lengthSqr() < 0.01 || v.dot(toMe) <= 0) {
				continue;
			}
			pr.setDeltaMovement(v.scale(-1.3));
			pr.setOwner(p);
			Fx.burst(level, Fx.dust(0xFFD060, 1.3F), pr.position(), 8, 0.1, 0.05);
			Fx.sound(level, pr.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
		}
	}
}
