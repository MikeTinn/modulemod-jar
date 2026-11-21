package com.modulesmod.mixin;

import com.modulesmod.AspectRatioHook;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps both projection-matrix builders. If one calls the other, only the outermost
 * call is changed (see AspectRatioHook), so the stretch is never applied twice.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "getProjectionMatrix", at = @At("HEAD"), require = 0)
	private void modulesmod$basicEnter(float fov, CallbackInfoReturnable<Matrix4f> cir) {
		AspectRatioHook.enter();
	}

	@Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$basicExit(float fov, CallbackInfoReturnable<Matrix4f> cir) {
		Matrix4f m = AspectRatioHook.exit(cir.getReturnValue());
		if (m != null) cir.setReturnValue(m);
	}

	@Inject(method = "getProjectionMatrixForCulling", at = @At("HEAD"), require = 0)
	private void modulesmod$projEnter(float fov, CallbackInfoReturnable<Matrix4f> cir) {
		AspectRatioHook.enter();
	}

	@Inject(method = "getProjectionMatrixForCulling", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$projExit(float fov, CallbackInfoReturnable<Matrix4f> cir) {
		Matrix4f m = AspectRatioHook.exit(cir.getReturnValue());
		if (m != null) cir.setReturnValue(m);
	}
}
