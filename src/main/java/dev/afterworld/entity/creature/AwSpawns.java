package dev.afterworld.entity.creature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Spawn rules for Afterworld's creatures. The world's own ground is ash, moss, snow, sand and
 * stone as often as grass, so wildlife may stand on anything solid. A few monsters roam by day,
 * thinly, so the wilds are never empty while the sun is up.
 */
public final class AwSpawns {
	private AwSpawns() {
	}

	/** Wildlife: solid ground and some daylight (or any light, when spawned by hand). */
	public static <T extends Mob> boolean wild(EntityType<T> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		boolean ground = below.isFaceSturdy(level, pos.below(), Direction.UP) && !below.getFluidState().is(FluidTags.LAVA);
		return ground && (EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 7);
	}

	/** Night monsters: vanilla's rule (darkness). */
	public static <T extends Monster> boolean night(EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	/**
	 * Roaming monsters: anywhere at night, and by day too, but only one try in three and never
	 * more than a few of the kind close together.
	 */
	public static <T extends Monster> boolean roaming(EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
			return true;
		}
		if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return random.nextInt(3) == 0 && fewNear(level, type, pos, 48, 3);
	}

	public static boolean fewNear(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius, int most) {
		return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius), e -> e.getType() == type).size() < most;
	}
}
