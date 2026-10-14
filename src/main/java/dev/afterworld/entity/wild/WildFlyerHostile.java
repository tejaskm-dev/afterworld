package dev.afterworld.entity.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** A flyer that hunts. */
public class WildFlyerHostile extends WildFlyer implements Enemy {
	public WildFlyerHostile(EntityType<? extends WildFlyerHostile> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return true;
	}
}
