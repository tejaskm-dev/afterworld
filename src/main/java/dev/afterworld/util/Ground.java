package dev.afterworld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

public final class Ground {
	private Ground() {
	}

	/** First open block above solid ground (ignoring leaves). */
	public static BlockPos surface(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	public static boolean loaded(ServerLevel level, int x, int z) {
		return level.hasChunk(x >> 4, z >> 4);
	}

	/** Spawns and initialises a mob standing on the surface near (x, z). Returns null if the chunk is not loaded. */
	public static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, int x, int z, EntitySpawnReason reason) {
		if (!loaded(level, x, z)) {
			return null;
		}
		BlockPos pos = surface(level, x, z);
		T e = type.create(level, reason);
		if (e == null) {
			return null;
		}
		e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
		if (e instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, null);
		}
		level.addFreshEntity(e);
		return e;
	}

	/** Spawns at an exact position (no surface search). */
	public static <T extends Entity> T spawnExact(ServerLevel level, EntityType<T> type, double x, double y, double z, EntitySpawnReason reason) {
		T e = type.create(level, reason);
		if (e == null) {
			return null;
		}
		e.snapTo(x, y, z, level.getRandom().nextFloat() * 360F, 0);
		if (e instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), reason, null);
		}
		level.addFreshEntity(e);
		return e;
	}
}
