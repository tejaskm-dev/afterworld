package dev.afterworld.item;

import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Ground;
import dev.afterworld.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A thimbleful of somebody else's weather. Makes a tiny rainstorm that waters crops, puts out
 * fires and shows tracks the rain has uncovered. Things that follow rain sometimes follow it.
 */
public class ThimbleItem extends LoreItem {
	public ThimbleItem(Properties properties) {
		super(properties, 2, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel sl = p.level();
		Vec3 center = p.position();
		p.getCooldowns().addCooldown(player.getItemInHand(hand), 900);
		Fx.sound(sl, center, SoundEvents.WEATHER_RAIN_ABOVE, 1.0F, 1.3F);
		Progress.codex(p, "relic.thimble");
		PlayerState s = PlayerState.of(p);
		RumourEntry lead = EdictSystem.nearestLead(p, s);
		for (int i = 0; i < 60; i++) {
			int step = i;
			Scheduler.after(1 + i * 10, () -> rain(sl, center, step, lead));
		}
		if (sl.getRandom().nextFloat() < 0.2F) {
			Scheduler.after(200, () -> {
				double a = sl.getRandom().nextDouble() * Math.PI * 2;
				var drowned = Ground.spawn(sl, EntityTypes.DROWNED, (int) (center.x + Math.cos(a) * 12), (int) (center.z + Math.sin(a) * 12), EntitySpawnReason.EVENT);
				if (drowned != null && p.isAlive()) {
					drowned.setTarget(p);
					p.sendOverlayMessage(Component.translatable("message.afterworld.thimble_follower").withColor(0x5B8DB8));
				}
			});
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void rain(ServerLevel level, Vec3 c, int step, RumourEntry lead) {
		var r = level.getRandom();
		for (int i = 0; i < Fx.scaled(10); i++) {
			level.sendParticles(ParticleTypes.FALLING_WATER, c.x + r.nextGaussian() * 3, c.y + 5 + r.nextDouble(), c.z + r.nextGaussian() * 3, 1, 0, 0, 0, 0);
		}
		if (step % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 6, c.z, Fx.scaled(3), 1.5, 0.2, 1.5, 0.0);
		}
		BlockPos base = BlockPos.containing(c);
		for (int i = 0; i < 6; i++) {
			BlockPos pos = base.offset(r.nextInt(11) - 5, r.nextInt(5) - 2, r.nextInt(11) - 5);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock && r.nextFloat() < 0.5F) {
				state.randomTick(level, pos, r);
			} else if (state.getBlock() instanceof BaseFireBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		if (lead != null && step % 4 == 0) {
			Vec3 dir = new Vec3(lead.x - c.x, 0, lead.z - c.z).normalize();
			for (int i = 1; i <= 8; i++) {
				Vec3 at = c.add(dir.scale(i * 1.4));
				BlockPos g = Ground.surface(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
				level.sendParticles(ParticleTypes.SPLASH, at.x, g.getY() + 0.05, at.z, 2, 0.1, 0, 0.1, 0);
			}
		}
	}
}
