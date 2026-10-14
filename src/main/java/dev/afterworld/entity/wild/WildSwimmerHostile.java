package dev.afterworld.entity.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/** A swimmer that hunts. */
public class WildSwimmerHostile extends WildSwimmer implements Enemy {
	public WildSwimmerHostile(EntityType<? extends WildSwimmerHostile> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}
}
