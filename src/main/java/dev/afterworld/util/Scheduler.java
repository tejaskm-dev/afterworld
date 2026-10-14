package dev.afterworld.util;

import java.util.PriorityQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Tiny delayed-task queue driven by the server tick. Used for afterimages, delayed echoes and
 * telegraphed attacks so nothing has to poll every tick.
 */
public final class Scheduler {
	private record Task(long due, long seq, Runnable action) implements Comparable<Task> {
		@Override
		public int compareTo(Task o) {
			int c = Long.compare(due, o.due);
			return c != 0 ? c : Long.compare(seq, o.seq);
		}
	}

	private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>();
	private static long now;
	private static long seq;

	private Scheduler() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Scheduler::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			now = 0;
		});
	}

	/** Runs {@code action} on the server thread after {@code ticks} ticks. */
	public static void after(int ticks, Runnable action) {
		QUEUE.add(new Task(now + Math.max(1, ticks), seq++, action));
	}

	public static long now() {
		return now;
	}

	private static void tick(MinecraftServer server) {
		now++;
		Task task;
		while ((task = QUEUE.peek()) != null && task.due <= now) {
			QUEUE.poll();
			try {
				task.action.run();
			} catch (Exception e) {
				dev.afterworld.Afterworld.LOG.error("Afterworld scheduled task failed", e);
			}
		}
	}
}
