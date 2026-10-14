package dev.afterworld.edict;

import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Hospitality: an honoured bargain forbids hostility. Breaking it has consequences. At Rank III a
 * guest also takes up arms for its host for a while, hunting the foes around them.
 */
public final class Pacts {
	private static final class Pact {
		final Mob mob;
		final UUID owner;
		final long until;
		long fightUntil;
		GuestGoal goal;

		Pact(Mob mob, UUID owner, long until) {
			this.mob = mob;
			this.owner = owner;
			this.until = until;
		}

		void standDown() {
			if (goal != null) {
				((dev.afterworld.mixin.MobAccessor) mob).afterworld$targetSelector().removeGoal(goal);
				goal = null;
				mob.setTarget(null);
			}
		}
	}

	private static final List<Pact> PACTS = new ArrayList<>();

	private Pacts() {
	}

	public static void offer(ServerPlayer player, Mob mob, long until) {
		offer(player, mob, until, 0);
	}

	/** Offers a bargain; with {@code fightUntil} set, the guest also fights for its host until then. */
	public static void offer(ServerPlayer player, Mob mob, long until, long fightUntil) {
		MobMark mark = MobMark.of(mob).with(MobMark.PACT).withOwner(player.getUUID()).withExpires(until);
		MobMark.set(mob, mark);
		if (mob.getTarget() == player) {
			mob.setTarget(null);
		}
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		Pact pact = new Pact(mob, player.getUUID(), until);
		if (fightUntil > 0) {
			pact.fightUntil = fightUntil;
			pact.goal = new GuestGoal(mob, player.getUUID());
			((dev.afterworld.mixin.MobAccessor) mob).afterworld$targetSelector().addGoal(0, pact.goal);
			Fx.burst(player.level(), net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, mob.position().add(0, mob.getBbHeight(), 0), 5, 0.3, 0);
		}
		PACTS.add(pact);
		AwTargeting.retain();
		Fx.burst(player.level(), Fx.dust(Edict.HOSPITALITY.color, 1.0F), mob.position().add(0, mob.getBbHeight() + 0.3, 0), 6, 0.2, 0);
	}

	public static boolean hasActivePact(ServerPlayer player) {
		long now = player.level().getGameTime();
		for (Pact p : PACTS) {
			if (p.owner.equals(player.getUUID()) && p.until > now && p.mob.isAlive()) {
				return true;
			}
		}
		return false;
	}

	public static void tick(long now) {
		if (PACTS.isEmpty()) {
			return;
		}
		Iterator<Pact> it = PACTS.iterator();
		while (it.hasNext()) {
			Pact p = it.next();
			if (p.goal != null && (p.fightUntil <= now || p.until <= now || !p.mob.isAlive())) {
				p.standDown();
			}
			if (p.until <= now || !p.mob.isAlive()) {
				it.remove();
				AwTargeting.release();
				MobMark m = MobMark.of(p.mob);
				if (m.has(MobMark.PACT) && m.expires() <= now) {
					MobMark.set(p.mob, m.without(MobMark.PACT));
				}
			}
		}
	}

	/** The player struck something while a bargain held. */
	public static void breakPacts(ServerPlayer player, PlayerState s) {
		long now = player.level().getGameTime();
		if (s.pactUntil <= now) {
			return;
		}
		s.pactUntil = 0;
		s.oathbreakerUntil = now + 6000;
		s.hudDirty = true;
		int angered = 0;
		Iterator<Pact> it = PACTS.iterator();
		while (it.hasNext()) {
			Pact p = it.next();
			if (!p.owner.equals(player.getUUID())) {
				continue;
			}
			it.remove();
			AwTargeting.release();
			p.standDown();
			Mob mob = p.mob;
			if (!mob.isAlive()) {
				continue;
			}
			MobMark.set(mob, MobMark.of(mob).without(MobMark.PACT).with(MobMark.ENRAGED).withExpires(now + 600));
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
			mob.setTarget(player);
			Fx.burst((ServerLevel) mob.level(), ParticleTypes.ANGRY_VILLAGER, mob.position().add(0, mob.getBbHeight(), 0), 3, 0.3, 0);
			angered++;
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
		Fx.sound(player.level(), player.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.5F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.afterworld.oathbreaker", angered).withStyle(ChatFormatting.RED));
		Progress.codex(player, "rule.oathbreaker");
		Progress.legend(player, "choice", "Broke a bargain sworn under Hospitality. " + angered + " creatures remember.");
	}

	public static void clear() {
		PACTS.clear();
	}

	/** A guest's fighting goal: the foes near its host, those hunting the host first. */
	static final class GuestGoal extends net.minecraft.world.entity.ai.goal.Goal {
		private final Mob mob;
		private final UUID owner;
		private net.minecraft.world.entity.LivingEntity found;
		private int cooldown;

		GuestGoal(Mob mob, UUID owner) {
			this.mob = mob;
			this.owner = owner;
			this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET));
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
			var t = mob.getTarget();
			return t != null && t.isAlive() && t.distanceToSqr(mob) < 20 * 20;
		}

		@Override
		public void start() {
			mob.setTarget(found);
		}

		@Override
		public void stop() {
			mob.setTarget(null);
		}

		private net.minecraft.world.entity.LivingEntity find() {
			net.minecraft.world.entity.LivingEntity best = null;
			double bestDist = Double.MAX_VALUE;
			for (Mob e : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16), e -> e.isAlive() && e != mob
				&& e instanceof net.minecraft.world.entity.monster.Enemy)) {
				MobMark mark = MobMark.of(e);
				if ((mark.has(MobMark.PACT) || mark.has(MobMark.ECHO)) && mark.ownedBy(owner)) {
					continue;
				}
				double d = e.distanceToSqr(mob);
				if (e.getTarget() != null && e.getTarget().getUUID().equals(owner)) {
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
