package dev.afterworld.client.model;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render states for Afterworld's great actors: the current animation and how far into it we are. */
public final class BossRenderStates {
	private BossRenderStates() {
	}

	public static class Boss extends LivingEntityRenderState {
		public int anim;
		public float animTime;
	}

	public static class Ringer extends Boss {
		public boolean bellHeld = true;
	}
}
