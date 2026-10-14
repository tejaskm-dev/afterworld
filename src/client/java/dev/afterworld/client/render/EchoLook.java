package dev.afterworld.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Colours (ARGB) used to draw spectral creatures. */
public final class EchoLook {
	public static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "afterworld:echo_tint");
	public static final int ECHO = 0x99B8C4FF;
	public static final int BETRAYER = 0x99FFB0A0;
	public static final int GHOST = 0x8CDCE6F0;

	private EchoLook() {
	}
}
