package dev.afterworld.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.afterworld.registry.AwSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Afterworld's score: the title theme in every menu, the boss theme while one of its bosses has a
 * bar on screen, and on alternate days the wilds theme in place of the overworld's usual music.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Unique
	private static final Music AFTERWORLD_TITLE = new Music(AwSounds.MUSIC_TITLE, 20, 600, true);
	@Unique
	private static final Music AFTERWORLD_BOSS = new Music(AwSounds.MUSIC_BOSS, 0, 0, true);
	@Unique
	private static final Music AFTERWORLD_WILDS = new Music(AwSounds.MUSIC_WILDS, 12000, 24000, false);

	@ModifyReturnValue(method = "getSituationalMusic", at = @At("RETURN"))
	private @Nullable Music afterworld$music(@Nullable Music original) {
		if (original == Musics.MENU) {
			return AFTERWORLD_TITLE;
		}
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player == null || original == null || mc.gui.screen() != null && mc.gui.screen().getBackgroundMusic() != null) {
			return original;
		}
		Level level = mc.player.level();
		if (level.dimension() != Level.END && mc.gui.hud.getBossOverlay().shouldPlayMusic()) {
			return AFTERWORLD_BOSS;
		}
		if (level.dimension() == Level.OVERWORLD && !original.replaceCurrentMusic() && !mc.player.isUnderWater()
				&& (level.getGameTime() / 24000L) % 2L == 0L) {
			return AFTERWORLD_WILDS;
		}
		return original;
	}
}
