package com.modulesmod.mixin;

import com.modulesmod.Diagnostics;
import com.modulesmod.Modules;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the Hitbox module is on, vanilla's own F3+B hitboxes are switched off so they don't draw twice. */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
	@Inject(method = "emitGizmos(DDDLnet/minecraft/util/debug/DebugValueAccess;Lnet/minecraft/client/renderer/culling/Frustum;F)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void modulesmod$replaceVanilla(double cameraX, double cameraY, double cameraZ, DebugValueAccess store,
										   Frustum frustum, float tickProgress, CallbackInfo ci) {
		if (Modules.HITBOX.isEnabled()) {
			Diagnostics.hitboxVanillaCancelled++;
			ci.cancel();
		}
	}
}
