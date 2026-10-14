package dev.afterworld.client;

import dev.afterworld.client.render.BossRenderers;

/** Entity models and renderers for Afterworld's creatures. */
public final class AfterworldModels {
	private AfterworldModels() {
	}

	public static void register() {
		BossRenderers.register();
		dev.afterworld.client.render.GarbRender.register();
		dev.afterworld.client.render.CreatureRenderers.register();
	}
}
