package com.modulesmod.mixin;

import com.modulesmod.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides your own real cape while the Cosmetics module is on, so your cosmetic cape is the only one. */
@Mixin(AvatarRenderer.class)
public abstract class PlayerCapeHideMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void modulesmod$hideOwnCape(AbstractClientPlayer player, AvatarRenderState state,
										float tickProgress, CallbackInfo ci) {
		if (!Modules.COSMETICS.isEnabled() || !Modules.CO_HIDE_CAPE.bool) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && player == mc.player) state.showCape = false;
	}
}
