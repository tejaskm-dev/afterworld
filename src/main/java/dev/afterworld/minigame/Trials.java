package dev.afterworld.minigame;

import dev.afterworld.Afterworld;
import dev.afterworld.item.WeaponGrowth;
import dev.afterworld.movement.Gifts;
import dev.afterworld.movement.Moves;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.quest.SideQuests;
import dev.afterworld.quest.SideQuests.Side;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The trials. The quiet ones at a bench (the anvil, the stew pot, Mab's cauldron, the star dials,
 * the chapel bells) play out on the traveller's own screen and report a score. The ones out in the
 * world (Odo's glide race, the walk on water, the tracks in the snow, the archery contest, Akane's
 * duel, a sextant sighting) run here, tick by tick, with a small panel on the traveller's screen.
 * Winning one moves its keeper's questline on.
 */
public final class Trials {
	/** Bench trials: the score a traveller needs to pass, and the least time one can honestly take. */
	private static final Map<String, int[]> BENCH = Map.of(
		"smithing", new int[] {6, 60},
		"cooking", new int[] {14, 300},
		"brewing", new int[] {10, 300},
		"stars", new int[] {5, 100},
		"melody", new int[] {3, 160});
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD27A, 1.6F);
	private static final DustParticleOptions PALE = new DustParticleOptions(0xE8F4FF, 1.0F);
	private static final DustParticleOptions PRINT = new DustParticleOptions(0x4A3A2A, 1.3F);
	private static final String TARGET_TAG = "afterworld:archery";

	static final class Session {
		final String id;
		final Entity host;
		final long started;
		final long deadline;
		/** Counters: rings passed, targets hit, parries, steps followed, stillness. */
		int a;
		int b;
		int phase;
		long next;
		long until;
		final List<Vec3> points = new ArrayList<>();
		Entity target;
		Vec3 anchor;
		Vec3 last;
		float lastYaw;
		float lastPitch;
		int lent;

		Session(String id, Entity host, long started, long deadline) {
			this.id = id;
			this.host = host;
			this.started = started;
			this.deadline = deadline;
		}
	}

	private static final Map<UUID, Session> ACTIVE = new HashMap<>();

	private Trials() {
	}

	public static void init() {
		for (String id : BENCH.keySet()) {
			Minigames.register(id, (p, host) -> bench(p, id, host));
		}
		Minigames.register("race", Trials::race);
		Minigames.register("waterwalk", Trials::waterwalk);
		Minigames.register("tracking", Trials::tracking);
		Minigames.register("archery", Trials::archery);
		Minigames.register("duel", Trials::duel);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ACTIVE.isEmpty()) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Session s = ACTIVE.get(p.getUUID());
				if (s != null) {
					tick(p, s);
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.TrialReply.TYPE, (payload, ctx) -> reply(ctx.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Session s = ACTIVE.remove(handler.getPlayer().getUUID());
			if (s != null) {
				cleanup(handler.getPlayer(), s);
			}
		});
		// Practice targets left standing by a restart are cleared away when their chunk loads.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TARGET_TAG)) {
				entity.discard();
			}
		});
	}

	public static boolean active(ServerPlayer p) {
		return ACTIVE.containsKey(p.getUUID());
	}

	// ------------------------------------------------------------------ starting

	private static Session begin(ServerPlayer p, String id, Entity host, int ticks) {
		Session old = ACTIVE.remove(p.getUUID());
		if (old != null) {
			cleanup(p, old);
		}
		long now = p.level().getGameTime();
		Session s = new Session(id, host, now, now + ticks);
		ACTIVE.put(p.getUUID(), s);
		return s;
	}

	private static void open(ServerPlayer p, Session s, CompoundTag data) {
		data.putInt("time", (int) (s.deadline - s.started));
		if (ServerPlayNetworking.canSend(p, AwPayloads.TrialOpen.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.TrialOpen(s.id, data));
		}
		Fx.privateSound(p, AwSounds.UI_RUMOUR, 0.8F, 1.1F);
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Afterworld.id(id));
	}

	/** The bench trials: check what they need, take the ingredients, open the screen. */
	private static void bench(ServerPlayer p, String id, Entity host) {
		PlayerState st = PlayerState.of(p);
		switch (id) {
			case "smithing" -> {
				if (!WeaponGrowth.ready(p.getMainHandItem())) {
					p.sendSystemMessage(Component.translatable("trial.afterworld.smithing.not_ready").withColor(0xD8B45A));
					return;
				}
			}
			case "cooking" -> {
				if (SideQuests.stage(st, Side.MEAL) == 0) {
					Item core = item("ember_core");
					if (Inv.count(p, Items.BEEF) < 2 || Inv.count(p, Items.CARROT) < 3 || Inv.count(p, Items.POTATO) < 2 || Inv.count(p, core) < 1) {
						p.sendSystemMessage(Component.translatable("trial.afterworld.cooking.missing").withColor(0xD8B45A));
						return;
					}
					Inv.take(p, Items.BEEF, 2);
					Inv.take(p, Items.CARROT, 3);
					Inv.take(p, Items.POTATO, 2);
					Inv.take(p, core, 1);
					SideQuests.advance(p, Side.MEAL);
				}
			}
			default -> {
			}
		}
		Session s = begin(p, id, host, 20 * 60 * 5);
		CompoundTag data = new CompoundTag();
		data.putInt("seed", p.getRandom().nextInt());
		data.putInt("tier", WeaponGrowth.tier(p.getMainHandItem()));
		open(p, s, data);
	}

	/** Odo's glide race: a launch skyward, then eight rings to glide through before the sand runs out. */
	private static void race(ServerPlayer p, Entity host) {
		ServerLevel level = p.level();
		Session s = begin(p, "race", host, 20 * 75);
		Vec3 o = (host != null ? host : p).position();
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double top = surface(level, o) + 22;
		for (int i = 0; i < 8; i++) {
			Vec3 at = o.add(dir.scale(9 + i * 12)).add(side.scale(Math.sin(i * 0.9) * 8));
			double y = Math.max(surface(level, at) + 5, top - i * 1.8);
			s.points.add(new Vec3(at.x, y, at.z));
		}
		lend(p, s, Gifts.Gift.GLIDE);
		PlayerState.of(p).safeFallUntil = level.getGameTime() + 20 * 80;
		p.setDeltaMovement(dir.x * 0.3, 2.1, dir.z * 0.3);
		p.syncVelocity = true;
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
		Fx.sound(level, p.position(), SoundEvents.PHANTOM_FLAP, 1.2F, 0.8F);
		Fx.burst(level, ParticleTypes.CLOUD, p.position(), 16, 0.6, 0.08);
		CompoundTag data = new CompoundTag();
		data.putInt("goal", s.points.size());
		open(p, s, data);
	}

	/** Mab's test: run out across the water to the far marker and back without a splash. */
	private static void waterwalk(ServerPlayer p, Entity host) {
		ServerLevel level = p.level();
		Session s = begin(p, "waterwalk", host, 20 * 75);
		Vec3 o = (host != null ? host : p).position();
		BlockPos best = null;
		double bestD = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -22; dx <= 22; dx++) {
			for (int dz = -22; dz <= 22; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 9 || d > 22 || d <= bestD) {
					continue;
				}
				int x = Mth.floor(o.x) + dx;
				int z = Mth.floor(o.z) + dz;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				m.set(x, y, z);
				if (level.getFluidState(m).is(FluidTags.WATER) && level.getBlockState(m.above()).isAir()) {
					best = m.immutable();
					bestD = d;
				}
			}
		}
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
		s.anchor = best != null ? Vec3.atBottomCenterOf(best.above()) : o.add(dir.scale(14));
		lend(p, s, Gifts.Gift.WATER_WALK);
		CompoundTag data = new CompoundTag();
		data.putInt("goal", 2);
		open(p, s, data);
	}

	/** Torvald's hunt: fresh tracks out from the lodge; follow them to the rimehorn alpha and bring it down. */
	private static void tracking(ServerPlayer p, Entity host) {
		ServerLevel level = p.level();
		Session s = begin(p, "tracking", host, 20 * 60 * 8);
		Vec3 at = (host != null ? host : p).position();
		double ang = level.getRandom().nextDouble() * Math.PI * 2;
		for (int i = 0; i < 26; i++) {
			ang += (level.getRandom().nextDouble() - 0.5) * 0.7;
			at = at.add(Math.cos(ang) * 4.5, 0, Math.sin(ang) * 4.5);
			s.points.add(new Vec3(at.x, surface(level, at), at.z));
		}
		CompoundTag data = new CompoundTag();
		data.putInt("goal", s.points.size());
		open(p, s, data);
	}

	/** Torvald's contest: eight targets, one at a time, seven heartbeats each. Six hits make a hunter. */
	private static void archery(ServerPlayer p, Entity host) {
		Session s = begin(p, "archery", host, 20 * 100);
		if (Inv.count(p, Items.BOW) == 0 && Inv.count(p, Items.CROSSBOW) == 0 && Inv.count(p, item("duskpine_longbow")) == 0) {
			Inv.give(p, new ItemStack(Items.BOW));
		}
		Inv.give(p, new ItemStack(Items.ARROW, 12));
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		s.anchor = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
		s.next = p.level().getGameTime() + 50;
		CompoundTag data = new CompoundTag();
		data.putInt("goal", 8);
		data.putInt("need", 6);
		open(p, s, data);
	}

	/** Akane's duel: only a perfect parry counts. Five turned blades win; three taken lose. */
	private static void duel(ServerPlayer p, Entity host) {
		if (!(host instanceof LivingEntity)) {
			return;
		}
		Session s = begin(p, "duel", host, 20 * 150);
		s.next = p.level().getGameTime() + 60;
		CompoundTag data = new CompoundTag();
		data.putInt("goal", 5);
		data.putInt("lives", 3);
		open(p, s, data);
	}

	/** Wren's survey: stand by a waystone with the sextant raised and hold still until the needle settles. */
	public static void sighting(ServerPlayer p, BlockPos waystone) {
		Session s = begin(p, "sighting", null, 20 * 40);
		s.anchor = Vec3.atCenterOf(waystone);
		s.last = p.position();
		s.lastYaw = p.getYRot();
		s.lastPitch = p.getXRot();
		CompoundTag data = new CompoundTag();
		data.putInt("goal", 70);
		open(p, s, data);
	}

	// ------------------------------------------------------------------ running

	private static void tick(ServerPlayer p, Session s) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (!p.isAlive() || p.isSpectator() || s.host != null && s.host.level() != level) {
			end(p, s, false, "fell");
			return;
		}
		if (now > s.deadline) {
			end(p, s, false, "time");
			return;
		}
		switch (s.id) {
			case "race" -> race(p, s, level, now);
			case "waterwalk" -> waterwalk(p, s, level, now);
			case "tracking" -> tracking(p, s, level, now);
			case "archery" -> archery(p, s, level, now);
			case "duel" -> duel(p, s, level, now);
			case "sighting" -> sighting(p, s, level, now);
			default -> {
				return;
			}
		}
		if (ACTIVE.get(p.getUUID()) == s && (now - s.started) % 5 == 0) {
			state(p, s, now);
		}
	}

	private static void race(ServerPlayer p, Session s, ServerLevel level, long now) {
		if (s.a >= s.points.size()) {
			return;
		}
		Vec3 ring = s.points.get(s.a);
		if (p.position().add(0, 0.9, 0).distanceTo(ring) < 3.4) {
			s.a++;
			Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), 1.0F, 0.7F + s.a * 0.1F);
			level.sendParticles(p, ParticleTypes.END_ROD, true, true, ring.x, ring.y, ring.z, 24, 1.2, 1.2, 1.2, 0.05);
			state(p, s, now);
			if (s.a >= s.points.size()) {
				end(p, s, true, "");
				return;
			}
		}
		if (now % 4 == 0) {
			ring(p, level, s, s.a, GOLD, 22);
			if (s.a + 1 < s.points.size()) {
				ring(p, level, s, s.a + 1, PALE, 14);
			}
		}
	}

	/** Draws ring i of the course as a hoop standing across the line of flight. */
	private static void ring(ServerPlayer p, ServerLevel level, Session s, int i, ParticleOptions dust, int n) {
		Vec3 c = s.points.get(i);
		Vec3 f = i + 1 < s.points.size() ? s.points.get(i + 1).subtract(c) : c.subtract(s.points.get(Math.max(0, i - 1)));
		f = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
		Vec3 r = f.cross(new Vec3(0, 1, 0));
		r = r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
		Vec3 u = r.cross(f).normalize();
		for (int k = 0; k < n; k++) {
			double t = k * Math.PI * 2 / n;
			Vec3 q = c.add(r.scale(Math.cos(t) * 2.6)).add(u.scale(Math.sin(t) * 2.6));
			level.sendParticles(p, dust, true, true, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	private static void waterwalk(ServerPlayer p, Session s, ServerLevel level, long now) {
		if (p.isInWater()) {
			end(p, s, false, "wet");
			return;
		}
		Vec3 goal = s.phase == 0 ? s.anchor : s.host != null ? s.host.position() : s.anchor;
		if (now % 4 == 0) {
			for (int k = 0; k < 6; k++) {
				level.sendParticles(p, s.phase == 0 ? GOLD : PALE, true, true, goal.x, goal.y + k * 0.6, goal.z, 2, 0.15, 0.1, 0.15, 0);
			}
		}
		double flat = Math.hypot(p.getX() - goal.x, p.getZ() - goal.z);
		if (s.phase == 0 && flat < 2.0) {
			s.phase = 1;
			s.a = 1;
			Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), 1.0F, 1.2F);
			p.sendOverlayMessage(Component.translatable("trial.afterworld.waterwalk.back").withColor(0xE8DCC0));
			state(p, s, now);
		} else if (s.phase == 1 && flat < 3.0 && p.onGround()) {
			s.a = 2;
			end(p, s, true, "");
		}
	}

	private static void tracking(ServerPlayer p, Session s, ServerLevel level, long now) {
		int n = s.points.size();
		if (s.a < n) {
			Vec3 next = s.points.get(s.a);
			double d = Math.hypot(p.getX() - next.x, p.getZ() - next.z);
			if (d < 5.0) {
				s.a++;
				if (s.a % 5 == 0) {
					Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.SNOW_STEP), 1.0F, 0.8F);
				}
				state(p, s, now);
			} else if (d > 60) {
				end(p, s, false, "lost");
				return;
			}
			if (now % 8 == 0) {
				for (int i = s.a; i < Math.min(n, s.a + 5); i++) {
					Vec3 q = s.points.get(i);
					Vec3 prev = i > 0 ? s.points.get(i - 1) : q.add(1, 0, 0);
					Vec3 side = q.subtract(prev).cross(new Vec3(0, 1, 0)).normalize().scale(0.3);
					level.sendParticles(p, PRINT, true, false, q.x + side.x, q.y + 0.08, q.z + side.z, 2, 0.06, 0, 0.06, 0);
					level.sendParticles(p, PRINT, true, false, q.x - side.x, q.y + 0.08, q.z - side.z, 2, 0.06, 0, 0.06, 0);
				}
				if (now % 40 == 0) {
					level.sendParticles(p, ParticleTypes.SNOWFLAKE, true, false, next.x, next.y + 0.5, next.z, 8, 0.4, 0.3, 0.4, 0.01);
				}
			}
			return;
		}
		if (s.target == null) {
			Vec3 at = s.points.get(n - 1);
			Mob alpha = AwEntities.RIMEHORN.create(level, EntitySpawnReason.EVENT);
			if (alpha == null) {
				end(p, s, true, "");
				return;
			}
			alpha.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
			Npcs.makeElite(alpha);
			alpha.setCustomName(Component.translatable("trial.afterworld.tracking.alpha").withColor(0xE8C060));
			alpha.setPersistenceRequired();
			alpha.setTarget(p);
			level.addFreshEntity(alpha);
			s.target = alpha;
			Fx.sound(level, at, SoundEvents.RAVAGER_ROAR, 1.6F, 1.3F);
			p.sendOverlayMessage(Component.translatable("trial.afterworld.tracking.found").withColor(0xE8C060));
			state(p, s, now);
			return;
		}
		if (s.target instanceof LivingEntity le && le.isDeadOrDying() || s.target.getRemovalReason() == Entity.RemovalReason.KILLED) {
			end(p, s, true, "");
		} else if (s.target.isRemoved()) {
			end(p, s, false, "escaped");
		}
	}

	private static void archery(ServerPlayer p, Session s, ServerLevel level, long now) {
		if (s.target != null && (now >= s.until || !s.target.isAlive())) {
			if (s.target.isAlive()) {
				Fx.sound(level, s.target.position(), SoundEvents.ARMOR_STAND_BREAK, 0.8F, 0.8F);
				level.sendParticles(ParticleTypes.POOF, s.target.getX(), s.target.getY() + 1, s.target.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				s.target.discard();
			}
			s.target = null;
			s.next = now + 25;
			state(p, s, now);
		}
		if (s.target != null || now < s.next) {
			return;
		}
		if (s.b >= 8) {
			end(p, s, s.a >= 6, s.a >= 6 ? "" : "missed");
			return;
		}
		// The next target: somewhere out in front of the line, 14 to 28 blocks off, on the ground.
		Vec3 base = (s.host != null ? s.host : p).position();
		Vec3 dir = s.anchor;
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double dist = 14 + level.getRandom().nextDouble() * 14;
		double lat = (level.getRandom().nextDouble() - 0.5) * 18;
		Vec3 at = base.add(dir.scale(dist)).add(side.scale(lat));
		at = new Vec3(at.x, surface(level, at), at.z);
		Mob dummy = AwEntities.TRAINING_DUMMY.create(level, EntitySpawnReason.EVENT);
		if (dummy == null) {
			end(p, s, false, "");
			return;
		}
		dummy.snapTo(at.x, at.y, at.z, (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + 180, 0);
		dummy.addTag(TARGET_TAG);
		dummy.addTag(TARGET_TAG + ":" + p.getUUID());
		dummy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
		level.addFreshEntity(dummy);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 12, 0.3, 0.6, 0.3, 0.02);
		Fx.sound(level, at, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.4F);
		s.target = dummy;
		s.b++;
		s.until = now + 140;
		state(p, s, now);
	}

	private static void duel(ServerPlayer p, Session s, ServerLevel level, long now) {
		LivingEntity host = (LivingEntity) s.host;
		double d = p.distanceTo(host);
		if (d > 9) {
			end(p, s, false, "forfeit");
			return;
		}
		if (s.phase == 0 && now >= s.next) {
			// The wind-up: a lantern flares on her blade.
			s.phase = 1;
			s.until = now + 14;
			Vec3 blade = host.position().add(host.getLookAngle().scale(0.6)).add(0, 1.4, 0);
			level.sendParticles(ParticleTypes.END_ROD, blade.x, blade.y, blade.z, 10, 0.2, 0.2, 0.2, 0.02);
			level.sendParticles(ParticleTypes.FLAME, blade.x, blade.y, blade.z, 6, 0.15, 0.15, 0.15, 0.01);
			Fx.sound(level, blade, SoundEvents.TRIDENT_THROW.value(), 1.0F, 0.6F);
			state(p, s, now);
		} else if (s.phase == 1 && now >= s.until) {
			s.phase = 0;
			s.next = now + 26 + level.getRandom().nextInt(22);
			host.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Fx.sound(level, host.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.1F);
			if (d <= 4.5) {
				int before = s.a;
				boolean hurt = p.hurtServer(level, level.damageSources().mobAttack(host), 3.0F);
				if (s.a == before && hurt) {
					s.b++;
					p.sendOverlayMessage(Component.translatable("trial.afterworld.duel.struck", 3 - s.b).withColor(0xD8505A));
					if (s.b >= 3) {
						end(p, s, false, "struck");
						return;
					}
				}
			}
			state(p, s, now);
		}
	}

	private static void sighting(ServerPlayer p, Session s, ServerLevel level, long now) {
		if (p.position().distanceTo(s.anchor) > 10) {
			end(p, s, false, "wandered");
			return;
		}
		double moved = p.position().distanceTo(s.last);
		float turned = Math.abs(Mth.wrapDegrees(p.getYRot() - s.lastYaw)) + Math.abs(p.getXRot() - s.lastPitch);
		if (moved > 0.03 || turned > 1.2F) {
			if (s.a > 14) {
				Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.SPYGLASS_STOP_USING), 0.6F, 0.7F);
			}
			s.a = Math.max(0, s.a - 8);
		} else {
			s.a++;
		}
		s.last = p.position();
		s.lastYaw = p.getYRot();
		s.lastPitch = p.getXRot();
		if (s.a >= 70) {
			end(p, s, true, "");
		}
	}

	private static void state(ServerPlayer p, Session s, long now) {
		if (!ServerPlayNetworking.canSend(p, AwPayloads.TrialState.TYPE)) {
			return;
		}
		CompoundTag d = new CompoundTag();
		d.putInt("a", s.a);
		d.putInt("b", s.b);
		d.putInt("phase", s.phase);
		d.putInt("time", (int) Math.max(0, s.deadline - now));
		Vec3 goal = goal(s);
		if (goal != null) {
			d.putDouble("tx", goal.x);
			d.putDouble("ty", goal.y);
			d.putDouble("tz", goal.z);
		}
		ServerPlayNetworking.send(p, new AwPayloads.TrialState(s.id, d));
	}

	/** Where the panel's arrow should point. */
	private static Vec3 goal(Session s) {
		return switch (s.id) {
			case "race" -> s.a < s.points.size() ? s.points.get(s.a) : null;
			case "waterwalk" -> s.phase == 0 ? s.anchor : s.host != null ? s.host.position() : null;
			case "tracking" -> s.a < s.points.size() ? s.points.get(s.a) : s.target != null ? s.target.position() : null;
			case "archery" -> s.target != null ? s.target.position().add(0, 1, 0) : null;
			default -> null;
		};
	}

	// ------------------------------------------------------------------ hooks from combat

	/** A parry turned a blow. In Akane's duel, that is the only thing that counts. */
	public static void parried(ServerPlayer p, LivingEntity attacker) {
		Session s = ACTIVE.get(p.getUUID());
		if (s == null || !s.id.equals("duel") || attacker != s.host) {
			return;
		}
		s.a++;
		p.sendOverlayMessage(Component.translatable("trial.afterworld.duel.parried", s.a, 5).withColor(0xFFE8A0));
		if (s.a >= 5) {
			end(p, s, true, "");
		} else {
			state(p, s, p.level().getGameTime());
		}
	}

	/** An arrow into a practice target: a hit for the archer, no harm to the dummy. */
	public static boolean intercept(LivingEntity victim, DamageSource source) {
		if (!victim.entityTags().contains(TARGET_TAG)) {
			return false;
		}
		if (source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof ServerPlayer p) {
			Session s = ACTIVE.get(p.getUUID());
			if (s != null && s.id.equals("archery") && s.target == victim) {
				s.a++;
				ServerLevel level = p.level();
				Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.ARROW_HIT_PLAYER), 1.0F, 1.0F);
				level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.2, victim.getZ(), 16, 0.3, 0.4, 0.3, 0.2);
				victim.discard();
				s.target = null;
				s.next = level.getGameTime() + 25;
				state(p, s, level.getGameTime());
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ ending

	private static void reply(ServerPlayer p, AwPayloads.TrialReply r) {
		Session s = ACTIVE.get(p.getUUID());
		if (s == null || !s.id.equals(r.id())) {
			return;
		}
		if (r.code() == AwPayloads.TrialReply.QUIT) {
			end(p, s, false, "quit");
			return;
		}
		int[] rule = BENCH.get(s.id);
		if (rule == null) {
			return;
		}
		long took = p.level().getGameTime() - s.started;
		end(p, s, r.value() >= rule[0] && took >= rule[1], "score");
	}

	private static void end(ServerPlayer p, Session s, boolean won, String why) {
		ACTIVE.remove(p.getUUID());
		cleanup(p, s);
		if (won) {
			outcome(p, s);
			Fx.privateSound(p, AwSounds.UI_DISCOVERY, 1.0F, 1.0F);
			String what = Component.translatable("trial.afterworld." + s.id).getString();
			Progress.legend(p, "discovery", Component.translatable("trial.afterworld.legend", what).getString());
			Renown.grant(p, 20, what);
		} else {
			Fx.privateSound(p, net.minecraft.core.Holder.direct(SoundEvents.NOTE_BLOCK_BASS.value()), 1.0F, 0.6F);
			if (!why.equals("quit")) {
				p.sendSystemMessage(Component.translatable("trial.afterworld.lose." + (why.isEmpty() ? "score" : why)).withColor(0xD8B45A));
			}
		}
		if (ServerPlayNetworking.canSend(p, AwPayloads.TrialEnd.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.TrialEnd(s.id, won, why));
		}
	}

	/** What winning changes: the weapon wakes, the stew is ready, the questline moves on. */
	private static void outcome(ServerPlayer p, Session s) {
		PlayerState st = PlayerState.of(p);
		switch (s.id) {
			case "smithing" -> {
				ItemStack w = p.getMainHandItem();
				if (WeaponGrowth.evolve(w)) {
					Fx.sound(p.level(), p.position(), SoundEvents.ANVIL_USE, 1.0F, 0.8F);
					Fx.burst(p.level(), ParticleTypes.WAX_OFF, p.position().add(0, 1, 0), 30, 0.6, 0.2);
					p.sendSystemMessage(Component.translatable("trial.afterworld.smithing.woke", w.getHoverName()).withColor(0xFFD27A));
				}
				advanceFrom(p, st, Side.FORGE, 0);
			}
			case "cooking" -> {
				Inv.give(p, new ItemStack(item("hearty_stew"), 2));
				advanceFrom(p, st, Side.MEAL, 1);
			}
			case "brewing" -> advanceFrom(p, st, Side.BREW, 1);
			case "waterwalk" -> advanceFrom(p, st, Side.BREW, 2);
			case "stars" -> advanceFrom(p, st, Side.STARS, 1);
			case "melody" -> advanceFrom(p, st, Side.BELL, 1);
			case "race" -> advanceFrom(p, st, Side.WAYFARER, 1);
			case "tracking" -> advanceFrom(p, st, Side.HUNT, 0);
			case "archery" -> advanceFrom(p, st, Side.HUNT, 2);
			case "duel" -> advanceFrom(p, st, Side.DUEL, 0);
			case "sighting" -> {
				BlockPos at = BlockPos.containing(s.anchor);
				st.flags.add("sighting:" + at.getX() + "," + at.getZ());
				int taken = (int) st.flags.stream().filter(f -> f.startsWith("sighting:")).count();
				p.sendSystemMessage(Component.translatable("trial.afterworld.sighting.count", Math.min(3, taken)).withColor(0xE8DCC0));
				if (taken >= 3) {
					advanceFrom(p, st, Side.EDGES, 0);
				}
			}
			default -> {
			}
		}
		st.questDirty = true;
	}

	private static void advanceFrom(ServerPlayer p, PlayerState st, Side side, int stage) {
		if (SideQuests.stage(st, side) == stage) {
			SideQuests.advance(p, side);
		}
	}

	private static void cleanup(ServerPlayer p, Session s) {
		if (s.target != null && s.id.equals("archery")) {
			s.target.discard();
		}
		if (s.lent != 0) {
			PlayerState st = PlayerState.of(p);
			st.trialGifts &= ~s.lent;
			Moves.syncAll(p);
		}
	}

	private static void lend(ServerPlayer p, Session s, Gifts.Gift g) {
		PlayerState st = PlayerState.of(p);
		if ((st.gifts & g.bit()) == 0) {
			st.trialGifts |= g.bit();
			s.lent |= g.bit();
			Moves.syncAll(p);
		}
	}

	private static double surface(ServerLevel level, Vec3 at) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
	}
}
