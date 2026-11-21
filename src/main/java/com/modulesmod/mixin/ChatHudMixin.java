package com.modulesmod.mixin;

import com.modulesmod.ChatRenderHook;
import com.modulesmod.ChatState;
import com.modulesmod.ChatTransformer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * All injections use require = 0: if a Minecraft update renames something, the game still starts
 * and only that one chat feature stops working.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {
	/** Every message passes through here before it is split into lines and stored. */
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Component modulesmod$transform(Component message) {
		return ChatTransformer.transform(message);
	}

	/** Vanilla keeps only the last 100 messages. */
	@ModifyConstant(method = {"addMessage", "addMessageToDisplayQueue"},
			constant = @Constant(intValue = 100), require = 0)
	private int modulesmod$maxLines(int vanilla) {
		return ChatTransformer.maxLines(vanilla);
	}

	/** Chat size: multiplies vanilla's chat scale. */
	@Inject(method = "getScale", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$scale(CallbackInfoReturnable<Double> cir) {
		double value = cir.getReturnValue();
		double m = ChatTransformer.sizeMultiplier();
		if (m != 1.0) {
			value *= m;
			cir.setReturnValue(value);
		}
		ChatState.lastChatScale = value;
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V",
			at = @At("HEAD"), require = 0)
	private void modulesmod$renderHead(GuiGraphicsExtractor ctx, Font tr, int tick, int mouseX, int mouseY,
									   boolean a, boolean b, CallbackInfo ci) {
		ChatRenderHook.begin(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V",
			at = @At("RETURN"), require = 0)
	private void modulesmod$renderReturn(GuiGraphicsExtractor ctx, Font tr, int tick, int mouseX, int mouseY,
										 boolean a, boolean b, CallbackInfo ci) {
		ChatRenderHook.end(ctx);
	}
}
