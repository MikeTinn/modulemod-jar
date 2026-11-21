package com.modulesmod.mixin;

import com.modulesmod.PreviewSpin;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The game's "draw a player in a menu" helper only lets the player turn about 30 degrees. While our Cosmetics
 * screen is drawing, this turns the model around its vertical axis by our own angle instead (a full 360).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenSpinMixin {
	@ModifyVariable(method = "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
			at = @At("STORE"), ordinal = 0, require = 0)
	private static Quaternionf modulesmod$spin(Quaternionf rotation) {
		return PreviewSpin.active ? rotation.rotateY(PreviewSpin.radians) : rotation;
	}
}
