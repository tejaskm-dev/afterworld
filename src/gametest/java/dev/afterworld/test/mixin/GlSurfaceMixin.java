package dev.afterworld.test.mixin;

import com.mojang.renderpearl.backend.opengl.GlSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Test client only: never wait for vsync. On macOS a locked screen stops display refreshes, and a
 * vsynced buffer swap then blocks forever, hanging unattended screenshot runs.
 */
@Mixin(GlSurface.class)
abstract class GlSurfaceMixin {
	@ModifyArg(method = "configure", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetSwapInterval(I)Z"))
	private int afterworldTest$noVsync(int interval) {
		return 0;
	}
}
