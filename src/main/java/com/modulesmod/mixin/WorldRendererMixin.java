package com.modulesmod.mixin;

import com.modulesmod.BlockOutlineRenderer;
import com.modulesmod.Diagnostics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the vanilla block outline while the Block Outline module is drawing its own. */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
	@Inject(method = "submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void modulesmod$replaceVanillaOutline(PoseStack poseStack, SubmitNodeCollector collector,
											  LevelRenderState state, CallbackInfo ci) {
		if (BlockOutlineRenderer.replacesVanilla()) {
			Diagnostics.outlineVanillaCancelled++;
			ci.cancel();
		}
	}
}
