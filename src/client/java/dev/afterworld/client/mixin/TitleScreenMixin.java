package dev.afterworld.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.afterworld.Afterworld;
import dev.afterworld.client.title.TitleScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces the panorama and logo with Afterworld's night. The menu music lives in {@link MinecraftMixin}. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
	private void afterworld$scene(TitleScreen self, GuiGraphicsExtractor g, float a, Operation<Void> original, GuiGraphicsExtractor g2, int mouseX, int mouseY, float a2) {
		float time = (Util.getMillis() % 3_600_000L) / 1000.0F;
		TitleScene.render(g, this.width, this.height, mouseX, mouseY, time, 0);
		int top = this.height / 4 + 40;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Afterworld.id("title/panel"), this.width / 2 - 110, top, 220, 128, 0.85F);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
	private void afterworld$logo(LogoRenderer renderer, GuiGraphicsExtractor g, int width, float alpha, Operation<Void> original) {
		int logoWidth = Math.min(300, width - 40);
		int y = Math.max(8, this.height / 4 - 46);
		int h = TitleScene.logo(g, width / 2, y, logoWidth, alpha);
		Component sub = Component.translatable("title.afterworld.subtitle");
		g.text(this.font, sub, width / 2 - this.font.width(sub) / 2, y + h + 2, ARGB.color((int) (alpha * 220), 0xE8A33D), true);
	}
}
