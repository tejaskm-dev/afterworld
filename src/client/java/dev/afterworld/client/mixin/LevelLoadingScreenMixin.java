package dev.afterworld.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.afterworld.client.title.TitleScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a world loads: the night scene, the logo, an amber progress bar and a rotating line of lore. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
	@Shadow
	private LevelLoadingScreen.Reason reason;

	protected LevelLoadingScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void afterworld$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (this.reason != LevelLoadingScreen.Reason.OTHER) {
			return;
		}
		ci.cancel();
		long millis = Util.getMillis();
		TitleScene.render(g, this.width, this.height, this.width / 2.0, this.height / 2.0, (millis % 3_600_000L) / 1000.0F, 0.35F);
		TitleScene.logo(g, this.width / 2, Math.max(10, this.height / 6 - 20), Math.min(260, this.width - 60), 1.0F);

		// A new tip every eight seconds, cross-faded.
		long phase = millis % 8000L;
		int tip = (int) ((millis / 8000L) % 12);
		float alpha = phase < 600 ? phase / 600.0F : phase > 7400 ? (8000 - phase) / 600.0F : 1.0F;
		Component label = Component.translatable("loading.afterworld.tip_label");
		Component line = Component.translatable("loading.afterworld.tip." + tip);
		int w = Math.min(this.width - 40, 320);
		int lines = this.font.split(line, w).size();
		int y = this.height - 30 - lines * 10;
		g.fill(this.width / 2 - w / 2 - 8, y - 18, this.width / 2 + w / 2 + 8, y + lines * 10 + 6, 0x99070A0E);
		g.fill(this.width / 2 - w / 2 - 8, y - 18, this.width / 2 + w / 2 + 8, y - 17, 0x66E8A33D);
		g.centeredText(this.font, label, this.width / 2, y - 12, ARGB.color((int) (alpha * 200), 0xE8A33D));
		g.textWithWordWrap(this.font, line, this.width / 2 - w / 2, y, w, ARGB.color(Math.max(4, (int) (alpha * 235)), 0xE8DCC0));
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;extractChunksForRendering(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
	private void afterworld$chunkMap(GuiGraphicsExtractor g, int x, int y, int size, int margin, ChunkLoadStatusView view, Operation<Void> original) {
		if (this.reason != LevelLoadingScreen.Reason.OTHER) {
			original.call(g, x, y, size, margin, view);
		}
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void afterworld$progressBar(GuiGraphicsExtractor g, int left, int top, int width, int height, float progress, CallbackInfo ci) {
		if (this.reason != LevelLoadingScreen.Reason.OTHER) {
			return;
		}
		ci.cancel();
		int filled = Math.round(progress * width);
		g.fill(left - 1, top - 1, left + width + 1, top + height + 2, 0xAA070A0E);
		g.fill(left, top, left + filled, top + height + 1, 0xFFC4802A);
		g.fill(left, top, left + filled, top + 1, 0xFFFFD27A);
		if (filled > 2) {
			g.fill(left + filled - 2, top - 1, left + filled, top + height + 2, 0xFFFFF0C8);
		}
	}
}
