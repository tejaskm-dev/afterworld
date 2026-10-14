package dev.afterworld.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import dev.afterworld.client.title.TitleScene;
import java.io.InputStream;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The resource-loading overlay in Afterworld's colours: midnight slate, the logo, and an amber
 * bar. The logo is loaded straight from the mod jar so it is ready before resources are.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Unique
	private static boolean afterworld$logoReady;
	@Unique
	private static int afterworld$frame;

	@Shadow
	private float currentProgress;

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int afterworld$background(IntSupplier supplier, Operation<Integer> original) {
		return 0xFF0E151B;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void afterworld$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h,
		int sw, int sh, int tw, int th, int color, Operation<Void> original) {
		if (!LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION.equals(tex) || !afterworld$ensureLogo()) {
			original.call(g, pipeline, tex, x, y, u, v, w, h, sw, sh, tw, th, color);
			return;
		}
		if (u < 0) {
			int width = Math.min(g.guiWidth() - 40, (int) (w * 2.2F));
			float alpha = ARGB.alpha(color) / 255.0F;
			TitleScene.logo(g, g.guiWidth() / 2, y + h / 2 - width * TitleScene.LOGO_H / TitleScene.LOGO_W / 2, width, alpha);
		}
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
	private void afterworld$bar(LoadingOverlay self, GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float fade, Operation<Void> original) {
		int a = Mth.ceil(fade * 255.0F);
		int width = Mth.ceil((x1 - x0 - 4) * this.currentProgress);
		g.fill(x0, y0 + 3, x1, y1 - 3, ARGB.color(a / 3, 0x5A4420));
		g.fill(x0 + 2, y0 + 4, x0 + 2 + width, y1 - 4, ARGB.color(a, 0xE8A33D));
		g.fill(x0 + 2, y0 + 4, x0 + 2 + width, y0 + 5, ARGB.color(a, 0xFFD27A));
		afterworld$frame++;
		int dot = x0 + 2 + width;
		g.fill(dot - 1, y0 + 2, dot + 1, y1 - 2, ARGB.color((int) (a * (0.6F + 0.4F * Mth.sin(afterworld$frame * 0.2F))), 0xFFF6DC));
	}

	@Unique
	private static boolean afterworld$ensureLogo() {
		if (afterworld$logoReady) {
			return true;
		}
		try (InputStream in = LoadingOverlayMixin.class.getResourceAsStream("/assets/afterworld/textures/gui/title/logo.png")) {
			if (in == null) {
				return false;
			}
			NativeImage image = NativeImage.read(in);
			Minecraft.getInstance().getTextureManager().register(TitleScene.LOGO, new DynamicTexture(() -> "afterworld logo", image));
			afterworld$logoReady = true;
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
