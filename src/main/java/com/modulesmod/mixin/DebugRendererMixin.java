package com.modulesmod.mixin;

import com.modulesmod.BlockOutlineRenderer;
import com.modulesmod.HitboxRenderer;
import com.modulesmod.LyricsController;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs every frame inside the game's gizmo collector, which is where our hitboxes are drawn. */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
	@Inject(method = "emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V", at = @At("RETURN"), require = 0)
	private void modulesmod$hitboxes(Frustum frustum, double cameraX, double cameraY, double cameraZ,
									 float tickProgress, CallbackInfo ci) {
		HitboxRenderer.emit(tickProgress);
		BlockOutlineRenderer.emit(tickProgress);
		LyricsController.emit(tickProgress);
	}
}
