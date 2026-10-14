package dev.afterworld.block;

import dev.afterworld.entity.wild.Species;
import dev.afterworld.entity.wild.WildEntities;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwGear;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A piece of the Crimson Lich's life, kept in a crypt. Break it and it gives up a shard; the Lich's
 * mourners come for whoever did it.
 */
public class PhylacteryBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);
	private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB01E2A, 1.2F);

	public PhylacteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
		super.playerDestroy(level, player, pos, state, blockEntity, tool);
		Vec3 c = Vec3.atCenterOf(pos);
		Fx.sound(level, c, SoundEvents.GLASS_BREAK, 1.4F, 0.6F);
		Fx.sound(level, c, SoundEvents.WITHER_HURT, 0.8F, 0.6F);
		Fx.burst(level, BLOOD, c, 40, 0.5, 0.1);
		Fx.burst(level, ParticleTypes.SOUL, c, 16, 0.4, 0.06);
		var data = dev.afterworld.world.AfterworldData.get(level.getServer());
		data.phylacteries++;
		data.setDirty();
		player.sendSystemMessage(Component.translatable("phylactery.afterworld.broken", Math.min(3, data.phylacteries)).withColor(0xD8505A));
		Progress.legend(player, "battle", Component.translatable("phylactery.afterworld.legend").getString());
		Progress.codex(player, "boss.lich.phylactery");
		// Its mourners rise to avenge it.
		for (int i = 0; i < 2; i++) {
			var type = WildEntities.type(Species.MOURNING_SHADE);
			Mob m = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
			if (m != null) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				m.snapTo(c.x + Math.cos(a) * 3, c.y + 1, c.z + Math.sin(a) * 3, level.getRandom().nextFloat() * 360, 0);
				m.setTarget(player);
				level.addFreshEntity(m);
				Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, m.position().add(0, 0.8, 0), 12, 0.3, 0.04);
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(BLOOD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.4 + random.nextDouble() * 0.6,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0, 0.03, 0);
		}
	}
}
