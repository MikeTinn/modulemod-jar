package com.modulesmod.mixin;

import com.modulesmod.ZoomController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The zoom changes the FOV the game uses for the 3D world (not the hand). Optional mixin config. */
@Mixin(GameRenderer.class)
public class GameRendererZoomMixin {
	@Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$zoom(Camera camera, float tickProgress, boolean changingFov,
								 CallbackInfoReturnable<Float> cir) {
		if (changingFov) cir.setReturnValue(ZoomController.apply(cir.getReturnValue()));
	}
}
