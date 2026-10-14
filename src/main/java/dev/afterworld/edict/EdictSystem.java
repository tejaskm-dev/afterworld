package dev.afterworld.edict;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.AwConfig;
import dev.afterworld.entity.ThrownWeapon;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Disciplines;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.MobMark;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.util.Acts;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Vfx;
import dev.afterworld.util.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Casting, cooldowns, rule collisions and the event hooks that make Edicts interact with the
 * world. All per-player bookkeeping is staggered so each player costs one check per second.
 */
public final class EdictSystem {
	/** Boss or quest systems may veto Hospitality bargains (and react to them). */
	public static final List<BiPredicate<ServerPlayer, Mob>> PACT_VETOES = new ArrayList<>();
	/** Systems that react when a heavy creature stands in a Weightlessness rift. */
	public static final List<BiPredicate<LivingEntity, Zones.Zone>> RIFT_LISTENERS = new ArrayList<>();
	/** Damage scalers contributed by other systems (bosses, vault adaptations). */
	public static final List<DamageScaler> DAMAGE_SCALERS = new ArrayList<>();
	/** Witness III: creatures that take a quarter more harm, until the time given. */
	private static final java.util.Map<LivingEntity, Long> WITNESSED = new java.util.IdentityHashMap<>();

	@FunctionalInterface
	public interface DamageScaler {
		float scale(LivingEntity target, DamageSource source, float damage);
	}

	private EdictSystem() {
	}

	public static void init() {
		Revelations.init();
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.Cast.TYPE, (payload, ctx) -> handleCast(ctx.player(), payload.slot()));
		ServerTickEvents.END_SERVER_TICK.register(EdictSystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ThrownWeapon.deliverAll(null);
			Zones.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WITNESSED.clear();
			HuntingEchoes.clear();
			Pacts.clear();
			AwTargeting.reset();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			ThrownWeapon.deliverAll(player.getUUID());
			AwTargeting.remember(player.getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerState s = PlayerState.of(newPlayer);
			Disciplines.applyPassives(newPlayer, s);
			s.hudDirty = true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Echoes never outlive the moment that summoned them: a reloaded copy simply fades.
			if (MobMark.has(entity, MobMark.ECHO) && !HuntingEchoes.isLive(entity)) {
				entity.discard();
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && !player.isSpectator()) {
				onPlayerAggression(sp, entity);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(EdictSystem::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(EdictSystem::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (MobMark.has(entity, MobMark.ECHO) && entity instanceof Mob mob) {
				HuntingEchoes.fade(mob);
				return false;
			}
			return true;
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(EdictSystem::afterKill);
	}

	// ------------------------------------------------------------------ casting

	public static int cooldownFor(PlayerState s, Edict e) {
		double mult = AwConfig.get().edictCooldownMultiplier * (s.discipline == Discipline.ARCANIST ? 0.75 : 1.0) * (s.affinity == e ? 0.8 : 1.0)
			* (s.starweave ? 0.8 : 1.0) * (1.0 - 0.15 * (s.edictRank.getOrDefault(e.id, 1) - 1))
			* (s.skills.contains("arcanum.edictweaver") ? 0.85 : 1.0) * (1.0 - dev.afterworld.player.Skills.bonus(s, dev.afterworld.player.Skills.Stat.EDICT_RECOVERY));
		return Math.max(10, (int) Math.round(e.cooldown * mult));
	}

	private static void handleCast(ServerPlayer p, int slot) {
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		if (slot == AwPayloads.Cast.SIGNATURE_HELD) {
			Disciplines.signatureAlt(p, s);
			syncHud(p, s);
			return;
		}
		if (slot == AwPayloads.Cast.SIGNATURE) {
			Disciplines.signature(p, s);
			syncHud(p, s);
			return;
		}
		if (slot < 0 || slot >= s.slotCount()) {
			return;
		}
		Edict e = s.slots[slot];
		if (e == null) {
			EdictEffects.fail(p, "empty_slot");
			return;
		}
		long now = p.level().getGameTime();
		if ((s.suppressedMask & (1 << slot)) != 0 && s.suppressedUntil > now) {
			EdictEffects.fail(p, "suppressed");
			Fx.sound(p.level(), p.position(), SoundEvents.BELL_BLOCK, 0.3F, 0.5F);
			return;
		}
		if (s.cooldownUntil[e.ordinal()] > now) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.fail.cooldown", e.displayName()).withColor(0xB0A8A0));
			return;
		}
		tryCast(p, s, e, 1.0F, false);
		syncHud(p, s);
	}

	/**
	 * Casts an Edict. {@code free} casts (echoes, collisions) skip the cooldown bookkeeping.
	 * Returns true when the rule took hold.
	 */
	public static boolean tryCast(ServerPlayer p, PlayerState s, Edict e, float power, boolean free) {
		long now = p.level().getGameTime();
		boolean echoPending = !free && e != Edict.ECHOES && s.echoWindowUntil > now && s.echoEdicts > 0;
		// Each rank an Edict has been raised makes it bend its rule a third harder.
		boolean ok = EdictEffects.cast(p, s, e, power * (1.0F + 0.3F * (s.edictRank.getOrDefault(e.id, 1) - 1)));
		if (ok && !free) {
			dev.afterworld.aspect.Aspects.addResonance(p, 6);
		}
		if (!ok) {
			return false;
		}
		Fx.sound(p.level(), p.position(), AwSounds.edict(e), 1.0F, free ? 1.2F : 1.0F);
		Fx.runeRing(p.level(), p.position(), e.color, free ? 0.9 : 1.4, free ? 8 : 14);
		Fx.burst(p.level(), dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.MOTE, e.color), p.position().add(0, 1.0, 0), 10, 0.5, 0.02);
		// The rule taking hold: a sigil underfoot, light at the hand, the body casting it.
		int pattern = switch (e) {
			case BORROWED_TIME -> Vfx.SIGIL_CLOCK;
			case WITNESS -> Vfx.SIGIL_JUDGMENT;
			case HUNGER, FORGOTTEN -> Vfx.SIGIL_STAR;
			default -> Vfx.SIGIL_EDICT;
		};
		Vfx.sigil(p.level(), p.position(), free ? 1.1F : 1.7F, pattern, 3.0F, e.color, free ? 16 : 26, p);
		Vfx.flare(p.level(), p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.3, 0), free ? 0.5F : 0.8F, true, e.color, 8);
		if (!free) {
			Acts.play(p, switch (e) {
				case RETURN -> Acts.THROW;
				case TRESPASS -> Acts.THRUST;
				case WEIGHTLESSNESS, WITNESS -> Acts.POINT;
				case BORROWED_TIME -> Acts.INVOKE;
				default -> Acts.CAST;
			}, e == Edict.BORROWED_TIME ? 16 : 12);
			Vfx.flash(p, 0.16F, e.color, 10);
		}
		if (!free) {
			s.cooldownUntil[e.ordinal()] = now + cooldownFor(s, e);
			s.castAt[e.ordinal()] = now;
			s.lastCast = e;
			s.lastCastTime = now;
			s.tactic(PlayerState.TACTIC_EDICT_BASE + e.ordinal(), 1);
			s.hudDirty = true;
			// Echoing Will: every Edict spoken feeds the Aspect.
			if (s.skills.contains("arcanum.echo")) {
				dev.afterworld.aspect.Aspects.addResonance(p, 5);
			}
			Progress.codex(p, "edict." + e.id);
			dev.afterworld.quest.Quests.onCast(p);
		}
		if (echoPending) {
			s.echoEdicts--;
			// At Rank III the afterimage stays for its whole window; otherwise one echoed Edict ends it.
			if (!s.echoCapstone) {
				s.echoWindowUntil = 0;
			}
			s.hudDirty = true;
			Progress.codex(p, "combo.echoed_edict");
			Fx.burst(p.level(), Fx.dust(Edict.ECHOES.color, 1.2F), p.position().add(0, 1, 0), 10, 0.4, 0);
			Vfx.pillar(p.level(), p.position(), 0.45F, 2.2F, Edict.ECHOES.color, 30);
			Scheduler.after(30, () -> {
				if (p.isAlive() && !p.isRemoved()) {
					Fx.sound(p.level(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
					Vfx.flare(p.level(), p.position().add(0, 1.2, 0), 1.0F, true, Edict.ECHOES.color, 8);
					if (e == Edict.RETURN) {
						EdictEffects.castReturn(p, s, 0.6F, true);
					} else {
						EdictEffects.cast(p, s, e, 0.6F);
					}
				}
			});
		}
		return true;
	}

	/** Arcanist signature: the first two slots fire together at greater strength. */
	public static boolean ruleCollision(ServerPlayer p, PlayerState s) {
		Edict a = s.slots[0];
		Edict b = s.slots[1];
		long now = p.level().getGameTime();
		if (a == null || b == null) {
			EdictEffects.fail(p, "collision_slots");
			return false;
		}
		if (s.cooldownUntil[a.ordinal()] > now || s.cooldownUntil[b.ordinal()] > now) {
			EdictEffects.fail(p, "collision_cooldown");
			return false;
		}
		if (a == Edict.ECHOES || b == Edict.ECHOES) {
			EdictEffects.castEchoes(p, s, 1.5F);
		}
		boolean any = false;
		for (Edict e : new Edict[] {a, b}) {
			if (e == Edict.ECHOES) {
				any = true;
				s.cooldownUntil[e.ordinal()] = now + cooldownFor(s, e);
				continue;
			}
			if (tryCast(p, s, e, 2.0F, false)) {
				any = true;
			}
		}
		if (any) {
			fusion(p, s, a, b);
			// Half of both cooldowns comes back.
			for (Edict e : new Edict[] {a, b}) {
				s.cooldownUntil[e.ordinal()] = now + cooldownFor(s, e) / 2;
			}
			Progress.codex(p, "combo.rule_collision");
			Fx.burst(p.level(), ParticleTypes.ENCHANT, p.position().add(0, 1.2, 0), 40, 0.6, 0.5);
		}
		return any;
	}

	/** What two Edicts make when they collide, by their kinds. */
	private static void fusion(ServerPlayer p, PlayerState s, Edict a, Edict b) {
		ServerLevel level = p.level();
		java.util.EnumSet<Edict.Kind> kinds = java.util.EnumSet.of(a.kind, b.kind);
		int color = 0x9E86D9;
		String name;
		if (kinds.contains(Edict.Kind.OFFENSIVE) && kinds.size() == 1) {
			name = "overload";
			for (var e : dev.afterworld.combat.Combat.foes(level, p, p.position(), 7)) {
				e.hurtServer(level, level.damageSources().indirectMagic(p, p), 10.0F);
			}
			Fx.ring(level, Fx.dust(color, 1.8F), p.position().add(0, 0.4, 0), 7, 25);
			Vfx.ring(level, p.position(), 7.0F, 1.0F, 1.6F, color, 16);
			Vfx.flare(level, p.position().add(0, 1, 0), 2.4F, true, color, 10);
			Fx.sound(level, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);
		} else if (kinds.contains(Edict.Kind.DEFENSIVE)) {
			name = "bulwark";
			p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 8.0F));
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 1));
			Vfx.helix(level, p.position(), 1.1F, 2.6F, color, 30);
			Vfx.ring(level, p.position().add(0, 1.0, 0), new Vec3(0, 1, 0), 1.4F, 0.3F, color, 30);
		} else if (kinds.contains(Edict.Kind.TRAVERSAL)) {
			name = "comet";
			p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2));
			p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1));
			s.safeFallUntil = level.getGameTime() + 200;
			Vfx.trail(level, p, 0.8F, color, 80);
			Vfx.ring(level, p.position(), 4.0F, 0.5F, 0.8F, color, 12);
		} else if (kinds.contains(Edict.Kind.SOCIAL)) {
			name = "accord";
			Vfx.ring(level, p.position(), 12.0F, 0.6F, 0.8F, color, 20);
			for (var e : dev.afterworld.combat.Combat.foes(level, p, p.position(), 12)) {
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2), p);
				if (e instanceof net.minecraft.world.entity.Mob m && !(e instanceof dev.afterworld.entity.boss.BossMob)) {
					m.setTarget(null);
				}
			}
		} else {
			name = "revelation";
			Vfx.ring(level, p.position(), 32.0F, 0.8F, 0, color, 26);
			for (var e : dev.afterworld.combat.Combat.foes(level, p, p.position(), 32)) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), p);
			}
			dev.afterworld.aspect.Aspects.addResonance(p, 15);
		}
		p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("edict.afterworld.fusion." + name).withColor(color));
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Zones.tick(server);
		HuntingEchoes.tick(server, now);
		if (now % 20 == 0) {
			Pacts.tick(now);
			AwTargeting.prune(now);
			if (!WITNESSED.isEmpty()) {
				WITNESSED.entrySet().removeIf(w -> !w.getKey().isAlive() || w.getValue() < w.getKey().level().getGameTime());
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			if (((now + p.getId()) % 20) == 0) {
				secondTick(p, s, now);
			}
			if (s.hudDirty) {
				syncHud(p, s);
			}
		}
	}

	private static void secondTick(ServerPlayer p, PlayerState s, long now) {
		if (p.isAlive()) {
			s.history[(int) ((now / 20) % s.history.length)] = new PlayerState.Snapshot(
				now, p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
				p.getHealth(), p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(), p.getRemainingFireTicks());
		}
		boolean windows = s.echoWindowUntil > 0 && s.echoWindowUntil <= now
			|| s.pactUntil > 0 && s.pactUntil <= now
			|| s.forgottenUntil > 0 && s.forgottenUntil <= now
			|| s.hungerUntil > 0 && s.hungerUntil <= now
			|| s.wrongTurnUntil > 0 && s.wrongTurnUntil <= now
			|| s.suppressedUntil > 0 && s.suppressedUntil <= now;
		if (windows) {
			if (s.echoWindowUntil <= now) {
				s.echoWindowUntil = 0;
				s.echoCapstone = false;
				s.echoEdicts = 0;
			}
			if (s.pactUntil <= now) {
				s.pactUntil = 0;
				s.pactAllies = false;
			}
			if (s.forgottenUntil <= now) {
				s.forgottenUntil = 0;
				s.forgottenCapstone = false;
				s.forgottenBonusPending = false;
			}
			if (s.hungerUntil <= now) {
				s.hungerUntil = 0;
				s.hungerCapstone = false;
			}
			if (s.wrongTurnUntil <= now) s.wrongTurnUntil = 0;
			if (s.suppressedUntil <= now) {
				s.suppressedUntil = 0;
				s.suppressedMask = 0;
			}
			s.hudDirty = true;
		}
		if (s.wrongTurnUntil > now && (now / 20) % 2 == 0 && p.level().dimension().identifier().toString().equals(s.wrongTurnDim)) {
			Fx.column(p.level(), Fx.dust(0x7FB069, 0.9F), new Vec3(s.wrongTurnX, s.wrongTurnY, s.wrongTurnZ), 4, 0.1, 2.0);
		}
		if (s.favourOwed > 0 && s.favourDeadline > 0 && now > s.favourDeadline) {
			collectFavour(p, s);
		}
		if (p.isCrouching()) {
			s.tactic(PlayerState.TACTIC_STEALTH, 1);
		}
	}

	private static void collectFavour(ServerPlayer p, PlayerState s) {
		int levels = Math.min(p.experienceLevel, 3);
		p.giveExperienceLevels(-levels);
		p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
		Fx.sound(p.level(), p.position(), SoundEvents.RAVAGER_ROAR, 1.0F, 0.5F);
		p.sendSystemMessage(Component.translatable("message.afterworld.favour_collected", levels).withColor(0xC4553B));
		Progress.legend(p, "bond", "Failed to repay an Unpaid Favour. It took " + levels + " levels of interest.");
		s.favourOwed = 0;
		s.favourDeadline = 0;
		s.hudDirty = true;
	}

	public static void syncHud(ServerPlayer p, PlayerState s) {
		s.hudDirty = false;
		if (!ServerPlayNetworking.canSend(p, AwPayloads.Hud.TYPE)) {
			return;
		}
		long now = p.level().getGameTime();
		int[] slots = new int[4];
		int[] rem = new int[4];
		int[] tot = new int[4];
		for (int i = 0; i < 4; i++) {
			Edict e = i < s.slotCount() ? s.slots[i] : null;
			slots[i] = e == null ? -1 : e.ordinal();
			if (e != null) {
				rem[i] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, s.cooldownUntil[e.ordinal()] - now));
				tot[i] = cooldownFor(s, e);
			}
		}
		int flags = 0;
		if (s.echoWindowUntil > now) flags |= AwPayloads.Hud.FLAG_ECHO;
		if (s.pactUntil > now) flags |= AwPayloads.Hud.FLAG_PACT;
		if (s.oathbreakerUntil > now) flags |= AwPayloads.Hud.FLAG_OATHBREAKER;
		if (s.forgottenUntil > now) flags |= AwPayloads.Hud.FLAG_FORGOTTEN;
		if (s.hungerUntil > now) flags |= AwPayloads.Hud.FLAG_HUNGER;
		if (s.wrongTurnUntil > now) flags |= AwPayloads.Hud.FLAG_WRONG_TURN;
		if (s.favourOwed > 0) flags |= AwPayloads.Hud.FLAG_FAVOUR;
		if (s.suppressedUntil > now) flags |= (s.suppressedMask & 0xF) << AwPayloads.Hud.SUPPRESS_SHIFT;
		int learned = 0;
		for (Edict e : s.learned) {
			learned |= 1 << e.ordinal();
		}
		int sigRem = (int) Math.max(0, s.signatureReadyAt - now);
		ServerPlayNetworking.send(p, new AwPayloads.Hud(s.discipline.ordinal(), s.slotCount(), slots, rem, tot, sigRem, s.discipline.signatureCooldown, flags, learned));
	}

	// ------------------------------------------------------------------ hooks

	private static void onPlayerAggression(ServerPlayer p, Entity target) {
		PlayerState s = PlayerState.of(p);
		long now = p.level().getGameTime();
		// Hospitality III: your guests fight beside you, so only striking one of them breaks faith.
		boolean strikesGuest = MobMark.has(target, MobMark.PACT) && MobMark.of(target).ownedBy(p.getUUID());
		if (s.pactUntil > now && target instanceof LivingEntity && (!s.pactAllies || strikesGuest)) {
			Pacts.breakPacts(p, s);
		}
		if (s.forgottenUntil > now) {
			// Forgotten III: the first strike from hiding lands three times as hard and you stay hidden a second more.
			if (s.forgottenCapstone && !s.forgottenStrikeUsed && target instanceof LivingEntity) {
				s.forgottenStrikeUsed = true;
				s.forgottenBonusPending = true;
				s.forgottenUntil = now + 20;
				AwTargeting.forget(p.getUUID(), now + 20);
				s.hudDirty = true;
				return;
			}
			s.forgottenUntil = 0;
			AwTargeting.remember(p.getUUID());
			s.hudDirty = true;
		}
	}

	/** Witness III marks a creature to take a quarter more harm until {@code until}. */
	static void witness(LivingEntity e, long until) {
		WITNESSED.merge(e, until, Math::max);
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		// Stillness III: the frozen cannot strike, and the harm dealt them waits for the thaw.
		if (attacker != null && Zones.isStilled(attacker) && source.getDirectEntity() == attacker) {
			return false;
		}
		if (Zones.storeStilled(victim, amount)) {
			if (attacker instanceof ServerPlayer p && source.getDirectEntity() != p) {
				onPlayerAggression(p, victim);
			}
			return false;
		}
		if (attacker instanceof ServerPlayer p && source.getDirectEntity() != p && !(source.getDirectEntity() instanceof ThrownWeapon)) {
			onPlayerAggression(p, victim);
		}
		if (victim instanceof ServerPlayer p && attacker instanceof LivingEntity enemy && enemy != p) {
			parry(p, enemy);
		}
		if (victim instanceof ServerPlayer p && amount > 0) {
			dev.afterworld.item.ClockworkHeartItem.wind(p);
		}
		return true;
	}

	private static void parry(ServerPlayer p, LivingEntity enemy) {
		if (!p.isBlocking() || p.getTicksUsingItem() > 7) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		boolean blade = p.getMainHandItem().is(AwItems.APOLOGETIC_BLADE);
		if (s.discipline != Discipline.VANGUARD && !blade) {
			return;
		}
		ServerLevel level = p.level();
		enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 2));
		enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0));
		Vec3 d = enemy.position().subtract(p.position()).normalize();
		enemy.knockback(0.6, -d.x, -d.z, level.damageSources().playerAttack(p), 0);
		Fx.burst(level, ParticleTypes.CRIT, enemy.position().add(0, enemy.getBbHeight() * 0.7, 0), 12, 0.3, 0.3);
		Fx.sound(level, p.position(), SoundEvents.ANVIL_PLACE, 0.4F, 1.9F);
		Progress.codex(p, "discipline.vanguard.parry");
		if (blade) {
			dev.afterworld.item.ApologeticBladeItem.counter(p, enemy);
		}
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer p) || taken <= 0) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		Entity direct = source.getDirectEntity();
		if (direct == p) {
			s.tactic(PlayerState.TACTIC_MELEE, 1);
			long now = p.level().getGameTime();
			if (s.echoWindowUntil > now) {
				afterimage(p, victim, taken * (s.echoCapstone ? 0.75F : 0.5F));
			}
		} else if (direct instanceof Projectile && !(direct instanceof ThrownWeapon)) {
			s.tactic(PlayerState.TACTIC_RANGED, 1);
		}
	}

	/** Echoes: a spectral afterimage repeats the strike a second later. */
	private static void afterimage(ServerPlayer p, LivingEntity victim, float amount) {
		ServerLevel level = p.level();
		Vec3 ghost = p.position();
		Scheduler.after(20, () -> {
			if (!victim.isAlive() || victim.level() != level) {
				return;
			}
			Fx.column(level, Fx.dust(Edict.ECHOES.color, 1.1F), ghost, 8, 0.25, 1.8);
			Vfx.pillar(level, ghost, 0.45F, 2.1F, Edict.ECHOES.color, 12);
			Vfx.beam(level, ghost.add(0, 1.2, 0), victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.18F, 0.15F, Edict.ECHOES.color, 7);
			Vfx.flare(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.9F, true, Edict.ECHOES.color, 8);
			victim.hurtServer(level, level.damageSources().indirectMagic(p, p), amount);
			Fx.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 1.6F);
		});
	}

	public static void onEdictHit(ServerPlayer owner, LivingEntity target, Edict source) {
		PlayerState s = PlayerState.of(owner);
		if (s.echoWindowUntil > owner.level().getGameTime() && source == Edict.RETURN) {
			Progress.codex(owner, "combo.returning_echo");
		}
	}

	private static void afterKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		ServerPlayer p = null;
		if (killer instanceof ServerPlayer sp) {
			p = sp;
		} else if (MobMark.has(killer, MobMark.ECHO)) {
			var owner = MobMark.of(killer).owner();
			if (owner.isPresent()) {
				p = level.getServer().getPlayerList().getPlayer(owner.get());
			}
		}
		if (p == null) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		long now = level.getGameTime();
		if (killed instanceof Enemy && s.favourOwed > 0) {
			s.favourOwed--;
			s.hudDirty = true;
			if (s.favourOwed == 0) {
				s.favourDeadline = 0;
				p.sendOverlayMessage(Component.translatable("message.afterworld.favour_paid").withColor(0xD9B45A));
			}
		}
		if (killer == p && s.hungerUntil > now && HuntingEchoes.echoable(killed) && !MobMark.has(killed, MobMark.ECHO)) {
			int max = (s.hungerCapstone ? 5 : HuntingEchoes.MAX_PER_PLAYER) + (s.discipline == Discipline.KEEPER ? 1 : 0);
			if (HuntingEchoes.count(p.getUUID()) < max) {
				boolean bound = s.pactUntil > now;
				int duration = (int) (300 * (s.discipline == Discipline.KEEPER ? 1.5 : 1.0) * (bound ? 2 : 1));
				var echo = HuntingEchoes.spawn(p, killed.getType(), killed, duration, bound);
				if (echo != null && s.hungerCapstone) {
					HuntingEchoes.empower(echo);
				}
				if (bound) {
					Progress.codex(p, "combo.kept_promise");
				}
			}
		}
	}

	public static float scaleDamage(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0 || target.level().isClientSide()) {
			return damage;
		}
		MobMark mark = target.getAttached(dev.afterworld.registry.AwAttachments.MOB);
		if (mark != null && mark.has(MobMark.SUSPENDED) && mark.expires() > target.level().getGameTime()) {
			damage *= 1.5F;
		}
		if (source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p) {
			PlayerState s = PlayerState.of(p);
			if (s.forgottenBonusPending && s.forgottenUntil > p.level().getGameTime()) {
				s.forgottenBonusPending = false;
				damage *= 3.0F;
				Fx.burst((ServerLevel) target.level(), ParticleTypes.CRIT, target.position().add(0, target.getBbHeight() * 0.6, 0), 18, 0.3, 0.3);
				Fx.burst((ServerLevel) target.level(), Fx.dust(Edict.FORGOTTEN.color, 1.4F), target.position().add(0, target.getBbHeight() * 0.6, 0), 12, 0.3, 0.05);
				Fx.sound((ServerLevel) target.level(), target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
			}
		}
		Long witnessed = WITNESSED.get(target);
		if (witnessed != null && witnessed >= target.level().getGameTime()) {
			damage *= 1.25F;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
			damage *= Zones.fallScale(target);
		}
		damage = HuntingEchoes.echoStrength(source, damage);
		if (source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && target.hasEffect(MobEffects.GLOWING)) {
			PlayerState s = PlayerState.of(p);
			long now = p.level().getGameTime();
			if (s.forgottenUntil > now && s.revealedUntil > now) {
				damage *= 2.0F;
				Fx.burst((ServerLevel) target.level(), ParticleTypes.CRIT, target.position().add(0, target.getBbHeight() * 0.6, 0), 10, 0.3, 0.2);
			}
		}
		for (DamageScaler scaler : DAMAGE_SCALERS) {
			damage = scaler.scale(target, source, damage);
		}
		return damage;
	}

	public static void onRiftTouchHeavy(LivingEntity living, Zones.Zone zone) {
		for (BiPredicate<LivingEntity, Zones.Zone> l : RIFT_LISTENERS) {
			l.test(living, zone);
		}
	}

	public static boolean bossRefusesPact(ServerPlayer p, Mob mob) {
		for (BiPredicate<ServerPlayer, Mob> veto : PACT_VETOES) {
			if (veto.test(p, mob)) {
				return true;
			}
		}
		return mob.getMaxHealth() >= 150;
	}

	/** The tracked rumour if active, otherwise the nearest active one in this dimension. */
	public static RumourEntry nearestLead(ServerPlayer p, PlayerState s) {
		String dim = p.level().dimension().identifier().toString();
		RumourEntry best = null;
		double bestDist = Double.MAX_VALUE;
		for (RumourEntry r : s.rumours) {
			if (!r.active() || !r.dim.equals(dim)) {
				continue;
			}
			if (r.id == s.trackedRumour) {
				return r;
			}
			double d = p.distanceToSqr(r.x, r.y, r.z);
			if (d < bestDist) {
				bestDist = d;
				best = r;
			}
		}
		return best;
	}
}
