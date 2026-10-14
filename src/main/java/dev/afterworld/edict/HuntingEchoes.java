package dev.afterworld.edict;

import dev.afterworld.mixin.MobAccessor;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Hunting echoes: spectral copies of defeated foes that hunt for their summoner for a short
 * time. They are real vanilla mobs with their targeting rewired, so every creature type fights
 * the way it always has.
 */
public final class HuntingEchoes {
	public static final int MAX_PER_PLAYER = 3;
	private static final List<Mob> LIVE = new ArrayList<>();
	/** Hunger III echoes: they strike at half their summoner's strength and burst when they fade. */
	private static final java.util.Set<Mob> EMPOWERED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private HuntingEchoes() {
	}

	public static boolean echoable(Entity killed) {
		return killed instanceof Zombie || killed instanceof AbstractSkeleton || killed instanceof Spider || killed instanceof Blaze
			|| killed instanceof Pillager || killed instanceof Vindicator || killed instanceof Witch;
	}

	public static int count(UUID owner) {
		int n = 0;
		for (Mob m : LIVE) {
			if (m.isAlive() && MobMark.of(m).ownedBy(owner)) {
				n++;
			}
		}
		return n;
	}

	/** Spawns an echo of {@code type} at the killed creature's position. */
	public static Mob spawn(ServerPlayer owner, EntityType<?> type, LivingEntity origin, int duration, boolean bound) {
		ServerLevel level = owner.level();
		Entity created = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(created instanceof Mob mob)) {
			return null;
		}
		mob.snapTo(origin.getX(), origin.getY(), origin.getZ(), origin.getYRot(), 0);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(origin.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
		boolean betrayer = !bound && level.getRandom().nextFloat() < 0.15F;
		configure(owner, mob, duration, bound, betrayer);
		level.addFreshEntity(mob);
		Fx.burst(level, ParticleTypes.SOUL, mob.position().add(0, 0.8, 0), 14, 0.35, 0.03);
		Fx.sound(level, mob.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
		return mob;
	}

	/** Turns an existing mob into an echo bound to {@code owner}. Also used by the Keeper's favours. */
	public static void configure(ServerPlayer owner, Mob mob, int duration, boolean bound, boolean betrayer) {
		long now = owner.level().getGameTime();
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setSilent(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		mob.setHealth(Math.max(4.0F, mob.getMaxHealth() * 0.6F));
		mob.setCustomName(Component.translatable("entity.afterworld.echo_of", mob.getType().getDescription()));
		int flags = MobMark.ECHO | (bound ? MobMark.BOUND : 0) | (betrayer ? MobMark.BETRAYER : 0);
		MobMark.set(mob, new MobMark(flags, Optional.of(owner.getUUID()), now + duration, 0));
		var targets = ((MobAccessor) mob).afterworld$targetSelector();
		targets.removeAllGoals(g -> true);
		targets.addGoal(1, new EchoHuntGoal(mob, owner.getUUID(), betrayer));
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.setTarget(null);
		LIVE.add(mob);
		AwTargeting.retain();
	}

	public static void tick(MinecraftServer server, long now) {
		if (LIVE.isEmpty()) {
			return;
		}
		Iterator<Mob> it = LIVE.iterator();
		while (it.hasNext()) {
			Mob mob = it.next();
			MobMark mark = MobMark.of(mob);
			if (!mob.isAlive() || mark.expires() <= now || mob.isRemoved()) {
				it.remove();
				AwTargeting.release();
				if (mob.isAlive() && mob.level() instanceof ServerLevel level) {
					Fx.burst(level, ParticleTypes.SOUL, mob.position().add(0, 0.8, 0), 10, 0.3, 0.02);
					burst(mob);
					mob.discard();
				}
				EMPOWERED.remove(mob);
				continue;
			}
			if (mob.isOnFire()) {
				mob.clearFire();
			}
			if (now % 10 == 0 && mob.level() instanceof ServerLevel level) {
				Fx.burst(level, Fx.dust(Edict.HUNGER.color, 0.7F), mob.position().add(0, mob.getBbHeight() * 0.6, 0), 1, 0.2, 0);
			}
		}
	}

	public static void clear() {
		LIVE.clear();
		EMPOWERED.clear();
	}

	/** Makes an echo a Hunger III echo. */
	public static void empower(Mob mob) {
		EMPOWERED.add(mob);
		if (mob.level() instanceof ServerLevel level) {
			Fx.burst(level, Fx.dust(Edict.HUNGER.color, 1.3F), mob.position().add(0, mob.getBbHeight() * 0.6, 0), 12, 0.3, 0.02);
		}
	}

	/** An empowered echo's blows land at no less than half its summoner's strength. */
	public static float echoStrength(net.minecraft.world.damagesource.DamageSource source, float damage) {
		if (EMPOWERED.isEmpty() || !(source.getEntity() instanceof Mob mob) || !EMPOWERED.contains(mob)) {
			return damage;
		}
		ServerPlayer owner = owner(mob);
		if (owner == null) {
			return damage;
		}
		return Math.max(damage, (float) owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5F);
	}

	private static ServerPlayer owner(Mob mob) {
		var id = MobMark.of(mob).owner();
		return id.isPresent() && mob.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(id.get()) : null;
	}

	/** Hunger III: an empowered echo bursts as it fades, harming the foes round it. */
	private static void burst(Mob mob) {
		if (!EMPOWERED.remove(mob) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = owner(mob);
		if (owner == null) {
			return;
		}
		float damage = 6.0F + (float) owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5F;
		for (LivingEntity e : dev.afterworld.combat.Combat.foes(level, owner, mob.position(), 3.5)) {
			if (e != mob) {
				e.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage);
			}
		}
		Fx.burst(level, ParticleTypes.SCULK_SOUL, mob.position().add(0, 0.8, 0), 16, 0.6, 0.1);
		Fx.burst(level, Fx.dust(Edict.HUNGER.color, 1.6F), mob.position().add(0, 0.8, 0), 24, 1.2, 0.1);
		Fx.sound(level, mob.position(), SoundEvents.SCULK_SHRIEKER_BREAK, 1.0F, 0.6F);
		Fx.sound(level, mob.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.6F);
	}

	public static boolean isLive(Entity e) {
		for (Mob m : LIVE) {
			if (m == e) {
				return true;
			}
		}
		return false;
	}

	/** Called when an echo would die: it simply fades, leaving no drops (an empowered one bursts). */
	public static void fade(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			Fx.burst(level, ParticleTypes.SOUL, mob.position().add(0, 0.8, 0), 10, 0.3, 0.02);
		}
		burst(mob);
		mob.discard();
	}

	/** A Keeper's Unpaid Favour: two bound spectral wolves for thirty seconds. */
	public static void summonFavour(ServerPlayer owner, PlayerState s, float power) {
		ServerLevel level = owner.level();
		int duration = Math.round(600 * power);
		for (int i = 0; i < 2; i++) {
			Wolf wolf = net.minecraft.world.entity.EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (wolf == null) {
				continue;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			wolf.snapTo(owner.getX() + Math.cos(a) * 1.5, owner.getY(), owner.getZ() + Math.sin(a) * 1.5, owner.getYRot(), 0);
			wolf.tame(owner);
			configure(owner, wolf, duration, true, false);
			wolf.setHealth(wolf.getMaxHealth());
			level.addFreshEntity(wolf);
			Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, wolf.position().add(0, 0.5, 0), 10, 0.3, 0.02);
		}
		Fx.sound(level, owner.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.8F, 0.6F);
		s.favourOwed += 6;
		s.favourDeadline = level.getGameTime() + 12000;
		s.hudDirty = true;
		owner.sendSystemMessage(Component.translatable("message.afterworld.favour_owed", 6).withColor(0xD9B45A));
		Progress.codex(owner, "discipline.keeper.favour");
	}

	/** Target goal for echoes: hunt hostiles near the summoner; betrayers prey on the gentle. */
	static final class EchoHuntGoal extends Goal {
		private final Mob mob;
		private final UUID owner;
		private final boolean betrayer;
		private LivingEntity found;
		private int cooldown;

		EchoHuntGoal(Mob mob, UUID owner, boolean betrayer) {
			this.mob = mob;
			this.owner = owner;
			this.betrayer = betrayer;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--cooldown > 0) {
				return false;
			}
			cooldown = 10;
			found = find();
			return found != null;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity t = mob.getTarget();
			return t != null && t.isAlive() && t.distanceToSqr(mob) < 24 * 24;
		}

		@Override
		public void start() {
			mob.setTarget(found);
		}

		@Override
		public void stop() {
			mob.setTarget(null);
		}

		private LivingEntity find() {
			AABB box = mob.getBoundingBox().inflate(betrayer ? 12 : 16);
			LivingEntity best = null;
			double bestDist = Double.MAX_VALUE;
			for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != mob)) {
				if (e instanceof Player) {
					continue;
				}
				MobMark mark = MobMark.of(e);
				if (mark.has(MobMark.ECHO) && mark.ownedBy(owner)) {
					continue;
				}
				boolean prey = betrayer ? e instanceof Animal || e instanceof AbstractVillager : e instanceof Enemy;
				if (!prey) {
					continue;
				}
				double d = e.distanceToSqr(mob);
				if (e instanceof Mob m && m.getTarget() != null && m.getTarget().getUUID().equals(owner)) {
					d *= 0.25;
				}
				if (d < bestDist) {
					bestDist = d;
					best = e;
				}
			}
			return best;
		}
	}
}
