package com.modulesmod.mixin;

import com.modulesmod.ChatState;
import com.modulesmod.Diagnostics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/**
 * Chat stacking. When a message repeats, the new copy (with its "(x2)") is added as normal and
 * then the old copy's lines are removed. Both lists are newest-first.
 * This class is in the optional mixin config: if Minecraft renames those lists, only stacking stops working.
 */
@Mixin(ChatComponent.class)
public class ChatHudStackMixin {
	private static final String ADD =
			"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V";

	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;

	@Unique private int modulesmod$linesBefore = 0;

	@Inject(method = ADD, at = @At("HEAD"), require = 0)
	private void modulesmod$stackHead(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
		modulesmod$linesBefore = trimmedMessages.size();
	}

	@Inject(method = ADD, at = @At("RETURN"), require = 0)
	private void modulesmod$stackReturn(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
		Diagnostics.chatStackHook++;
		int after = trimmedMessages.size();
		int added = Math.max(1, after - modulesmod$linesBefore);
		int remove = ChatState.stackRemove;
		ChatState.stackRemove = 0;

		if (remove > 0 && after >= added + remove) {
			trimmedMessages.subList(added, added + remove).clear();
			if (allMessages.size() > 1) allMessages.remove(1);
		}
		ChatState.lastVisibleLines = added;
	}
}
