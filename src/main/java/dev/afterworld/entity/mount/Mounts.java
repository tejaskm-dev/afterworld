package dev.afterworld.entity.mount;

import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The Wayfarer's Whistle: one Duskstrider per traveller, called to your side wherever you are. */
public final class Mounts {
	private Mounts() {
	}

	public static boolean whistle(ServerLevel level, ServerPlayer p) {
		if (p.isPassenger()) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.whistle_riding").withColor(0x9A9488));
			return false;
		}
		PlayerState s = PlayerState.of(p);
		Fx.sound(level, p.position(), SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.5F, 1.6F);
		Vec3 behind = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(-3));
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(behind));
		if (Math.abs(ground.getY() - p.getY()) > 6) {
			ground = p.blockPosition();
		}
		Entity old = s.mount == null ? null : level.getEntity(s.mount);
		Duskstrider d;
		if (old instanceof Duskstrider existing && existing.isAlive()) {
			d = existing;
			d.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		} else {
			d = AwEntities.DUSKSTRIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (d == null) {
				return false;
			}
			d.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, p.getYRot(), 0);
			d.setTamed(true);
			d.setOwner(p);
			d.setPersistenceRequired();
			level.addFreshEntity(d);
			s.mount = d.getUUID();
		}
		Fx.burst(level, Fx.dust(0x6A5A9A, 1.6F), d.position().add(0, 1, 0), 50, 0.7, 0.08);
		Fx.sound(level, d.position(), SoundEvents.HORSE_AMBIENT, 1.0F, 0.7F);
		return true;
	}
}
