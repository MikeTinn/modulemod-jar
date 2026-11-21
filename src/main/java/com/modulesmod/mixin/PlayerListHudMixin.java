package com.modulesmod.mixin;

import com.modulesmod.TabHud;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla keeps the header/footer private, so we copy them when the server sends them. */
@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
	@Inject(method = "setHeader", at = @At("HEAD"), require = 0)
	private void modulesmod$header(Component header, CallbackInfo ci) {
		TabHud.header = header;
	}

	@Inject(method = "setFooter", at = @At("HEAD"), require = 0)
	private void modulesmod$footer(Component footer, CallbackInfo ci) {
		TabHud.footer = footer;
	}

	@Inject(method = "reset", at = @At("HEAD"), require = 0)
	private void modulesmod$clear(CallbackInfo ci) {
		TabHud.header = null;
		TabHud.footer = null;
	}
}
