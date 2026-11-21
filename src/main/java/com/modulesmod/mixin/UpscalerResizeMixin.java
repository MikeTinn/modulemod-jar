package com.modulesmod.mixin;

import com.modulesmod.Upscaler;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Whenever the game resizes its main screen buffer to the window size (window resize, GUI scale change),
 * make the buffer the scaled size instead. Optional mixin config: if the call is not found the Upscaler
 * simply does nothing.
 */
@Mixin(Minecraft.class)
public abstract class UpscalerResizeMixin {
	@Redirect(method = "resizeDisplay", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;resize(II)V"), require = 0)
	private void modulesmod$scaledResize(RenderTarget framebuffer, int width, int height) {
		Upscaler.hookSeen = true;
		framebuffer.resize(Upscaler.scale(width), Upscaler.scale(height));
	}
}
