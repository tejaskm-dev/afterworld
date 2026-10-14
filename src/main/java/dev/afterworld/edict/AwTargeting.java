package dev.afterworld.edict;

import dev.afterworld.registry.AwAttachments;
import dev.afterworld.registry.MobMark;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Central "who may a creature consider a threat" rule, consulted from {@code Mob#asValidTarget}.
 * This is a hot path, so it bails out immediately while no Edict is bending targeting.
 */
public final class AwTargeting {
	/** Count of live echoes, pacts and forgotten windows; zero means nothing to check. */
	private static int active;
	private static final Object2LongOpenHashMap<UUID> FORGOTTEN = new Object2LongOpenHashMap<>();

	private AwTargeting() {
	}

	public static void retain() {
		active++;
	}

	public static void release() {
		if (active > 0) {
			active--;
		}
	}

	public static void reset() {
		active = 0;
		FORGOTTEN.clear();
	}

	public static void forget(UUID player, long until) {
		if (!FORGOTTEN.containsKey(player)) {
			retain();
		}
		FORGOTTEN.put(player, until);
	}

	public static void remember(UUID player) {
		if (FORGOTTEN.removeLong(player) != 0L) {
			release();
		}
	}

	public static boolean isForgotten(UUID player, long now) {
		long until = FORGOTTEN.getOrDefault(player, 0L);
		return until > now;
	}

	/** Called on the server tick to drop expired forgotten windows. */
	public static void prune(long now) {
		if (FORGOTTEN.isEmpty()) {
			return;
		}
		var it = FORGOTTEN.object2LongEntrySet().fastIterator();
		while (it.hasNext()) {
			if (it.next().getLongValue() <= now) {
				it.remove();
				release();
			}
		}
	}

	public static LivingEntity filter(Mob mob, LivingEntity target) {
		if (active == 0 || target == null || mob.level().isClientSide()) {
			return target;
		}
		MobMark self = mob.getAttached(AwAttachments.MOB);
		if (target instanceof Player player) {
			if (self != null) {
				if (self.has(MobMark.ECHO) || self.has(MobMark.GHOST)) {
					return null;
				}
				if (self.has(MobMark.PACT) && self.ownedBy(player.getUUID()) && self.expires() > mob.level().getGameTime()) {
					return null;
				}
			}
			if (isForgotten(player.getUUID(), mob.level().getGameTime()) && mob.getLastHurtByMob() != player) {
				return null;
			}
			return target;
		}
		if (self != null && self.has(MobMark.ECHO)) {
			MobMark other = target.getAttached(AwAttachments.MOB);
			if (other != null && other.has(MobMark.ECHO) && other.owner().equals(self.owner())) {
				return null;
			}
			if (target instanceof OwnableEntity ownable && self.owner().isPresent()
				&& ownable.getOwnerReference() != null && self.owner().get().equals(ownable.getOwnerReference().getUUID())) {
				return null;
			}
		}
		return target;
	}
}
