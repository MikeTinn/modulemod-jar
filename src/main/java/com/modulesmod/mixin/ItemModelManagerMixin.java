package com.modulesmod.mixin;

import com.modulesmod.ToolSkins;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets Cosmetics swap the model of the tool in your hand. Optional mixin config: if it fails, only tool skins stop working. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelManagerMixin {
	@ModifyVariable(method = {"appendItemLayers", "updateForTopItem"}, at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack modulesmod$toolSkin(ItemStack stack) {
		return ToolSkins.apply(stack);
	}
}
