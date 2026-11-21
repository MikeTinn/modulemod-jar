package com.modulesmod.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets us switch a post-processing effect on and off. Lives in the optional mixin config, so if
 * Minecraft renames these, only Motion Blur stops working.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {
	@Invoker("setPostEffect")
	void modulesmod$setPostProcessor(Identifier id);

	@Accessor("postEffectId")
	Identifier modulesmod$getPostProcessorId();
}
