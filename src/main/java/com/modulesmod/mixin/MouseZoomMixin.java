package com.modulesmod.mixin;

import com.modulesmod.ZoomController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zooming, the mouse wheel changes the zoom instead of switching hotbar slots. Optional mixin config. */
@Mixin(MouseHandler.class)
public class MouseZoomMixin {
	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void modulesmod$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (ZoomController.consumeScroll(vertical)) ci.cancel();
	}
}
