package dev.afterworld.site;

import dev.afterworld.Afterworld;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places structure plans over several ticks so building a cathedral never stalls the server.
 */
public final class Builds {
	private static final int BLOCKS_PER_TICK = 1800;

	private static final class Job {
		final ServerLevel level;
		final Plan plan;
		final ObjectIterator<Long2ObjectMap.Entry<BlockState>> it;
		final Runnable done;
		final long started = System.nanoTime();
		int ticks;

		Job(ServerLevel level, Plan plan, Runnable done) {
			this.level = level;
			this.plan = plan;
			this.it = plan.blocks.long2ObjectEntrySet().fastIterator();
			this.done = done;
		}
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();

	private Builds() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Builds::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Finish anything in flight so a site is never saved half-built.
			while (!JOBS.isEmpty()) {
				tick(server);
			}
		});
	}

	public static void submit(ServerLevel level, Plan plan, Runnable done) {
		JOBS.add(new Job(level, plan, done));
	}

	public static boolean busy() {
		return !JOBS.isEmpty();
	}

	private static void tick(MinecraftServer server) {
		int budget = BLOCKS_PER_TICK;
		while (budget > 0 && !JOBS.isEmpty()) {
			Job job = JOBS.peek();
			job.ticks++;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (budget > 0 && job.it.hasNext()) {
				Long2ObjectMap.Entry<BlockState> e = job.it.next();
				pos.set(e.getLongKey());
				job.level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				budget--;
			}
			if (!job.it.hasNext()) {
				JOBS.poll();
				Afterworld.LOG.debug("Built {} blocks at {} in {} ticks ({} ms of work)", job.plan.size(), job.plan.origin.toShortString(), job.ticks,
					(System.nanoTime() - job.started) / 1_000_000);
				for (Consumer<ServerLevel> action : job.plan.after) {
					try {
						action.accept(job.level);
					} catch (Exception ex) {
						Afterworld.LOG.error("Afterworld site finishing step failed", ex);
					}
				}
				if (job.done != null) {
					job.done.run();
				}
			}
		}
	}
}
