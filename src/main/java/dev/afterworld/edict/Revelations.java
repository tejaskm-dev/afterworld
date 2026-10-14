package dev.afterworld.edict;

import com.mojang.math.Transformation;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Witness III: hidden chests and ores near the caster shine through the stone for ten seconds.
 * Each is shown by a glowing block display over it (the outline of a glowing entity is drawn
 * through walls). They never outlive the moment: none is saved, and a stray one is removed on load.
 */
final class Revelations {
	private static final String TAG = "afterworld.revelation";
	private static final int MAX = 48;
	private static final int LIFE = 200;
	private static final List<Shown> SHOWN = new ArrayList<>();

	private record Shown(Display.BlockDisplay display, long until) {
	}

	private Revelations() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SHOWN.isEmpty() || server.getTickCount() % 10 != 0) {
				return;
			}
			Iterator<Shown> it = SHOWN.iterator();
			while (it.hasNext()) {
				Shown s = it.next();
				if (s.display.isRemoved() || s.until <= s.display.level().getGameTime()) {
					s.display.discard();
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SHOWN.forEach(s -> s.display.discard());
			SHOWN.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.BlockDisplay && entity.entityTags().contains(TAG) && SHOWN.stream().noneMatch(s -> s.display == entity)) {
				entity.discard();
			}
		});
	}

	static void shine(ServerPlayer p, ServerLevel level, int radius) {
		BlockPos c = p.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		int r2 = radius * radius;
		int minY = Math.max(level.getMinY(), c.getY() - 32);
		int maxY = Math.min(level.getMaxY(), c.getY() + 32);
		for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) {
			for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof RandomizableContainerBlockEntity && be.getBlockPos().distSqr(c) <= r2) {
						found.add(be.getBlockPos());
					}
				}
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(st -> st.is(ConventionalBlockTags.ORES))) {
						continue;
					}
					for (int y = 0; y < 16; y++) {
						int wy = (sy << 4) + y;
						if (wy < minY || wy > maxY) {
							continue;
						}
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (section.getBlockState(x, y, z).is(ConventionalBlockTags.ORES)) {
									BlockPos pos = new BlockPos((cx << 4) + x, wy, (cz << 4) + z);
									if (pos.distSqr(c) <= r2) {
										found.add(pos);
									}
								}
							}
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(pos -> pos.distSqr(c)));
		long until = level.getGameTime() + LIFE;
		int shown = 0;
		for (BlockPos pos : found) {
			if (shown >= MAX) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (d == null) {
				break;
			}
			d.setBlockState(state);
			d.setTransformation(new Transformation(new Vector3f(-0.01F, -0.01F, -0.01F), new Quaternionf(), new Vector3f(1.02F, 1.02F, 1.02F), new Quaternionf()));
			d.setPos(pos.getX(), pos.getY(), pos.getZ());
			d.setGlowingTag(true);
			d.setGlowColorOverride(colour(state));
			d.addTag(TAG);
			level.addFreshEntity(d);
			SHOWN.add(new Shown(d, until));
			shown++;
		}
		if (shown > 0) {
			Fx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.5F);
			p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.afterworld.witness_shine", shown).withColor(Edict.WITNESS.color));
		}
	}

	private static int colour(BlockState s) {
		if (!s.is(ConventionalBlockTags.ORES)) {
			return 0xE8A33D;
		}
		if (s.is(ConventionalBlockTags.DIAMOND_ORES)) {
			return 0x5FE3E8;
		}
		if (s.is(ConventionalBlockTags.EMERALD_ORES)) {
			return 0x3FD86B;
		}
		if (s.is(ConventionalBlockTags.GOLD_ORES)) {
			return 0xFFD24A;
		}
		if (s.is(ConventionalBlockTags.REDSTONE_ORES)) {
			return 0xE04A3A;
		}
		if (s.is(ConventionalBlockTags.LAPIS_ORES)) {
			return 0x3F6AE0;
		}
		if (s.is(ConventionalBlockTags.COPPER_ORES)) {
			return 0xE07F4A;
		}
		if (s.is(ConventionalBlockTags.IRON_ORES)) {
			return 0xD8C3A5;
		}
		return 0x9A9A9A;
	}
}
