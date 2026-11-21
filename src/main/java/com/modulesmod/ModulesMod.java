package com.modulesmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModulesMod implements ClientModInitializer {
	public static final String MOD_ID = "modulesmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		Modules.load();
		AspectPresets.load();
		ScoreboardHud.register();
		TabHud.register();
		MotionBlurController.register();
		Huds.register();
		ZoomController.register();
		GiveCopier.register();
		Upscaler.register();
		Cosmetics.init();

		// :heart: -> a symbol every player can see (only for chat messages, never commands)
		ClientSendMessageEvents.MODIFY_CHAT.register(message -> {
			if (!Modules.CHAT.isEnabled() || !Modules.CH_EMOJI.bool) return message;
			return Emojis.replace(message, Modules.CH_FACES.bool);
		});

		// /modulesmod status : shows which hooks are actually running (useful when something doesn't work)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("modulesmod")
						.then(ClientCommands.literal("status").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal("Modules Mod status (counts since the game started):"));
							ctx.getSource().sendFeedback(Component.literal(" chat messages processed: " + Diagnostics.chatTransform));
							ctx.getSource().sendFeedback(Component.literal(" chat stacking hook ran: " + Diagnostics.chatStackHook));
							ctx.getSource().sendFeedback(Component.literal(" chat draw hook ran: " + Diagnostics.chatRenderHook));
							ctx.getSource().sendFeedback(Component.literal(" aspect ratio hook ran: " + Diagnostics.projectionHook));
							ctx.getSource().sendFeedback(Component.literal(" hitbox frames drawn: " + Diagnostics.hitboxEmit
									+ " (vanilla boxes switched off: " + Diagnostics.hitboxVanillaCancelled + ")"));
							ctx.getSource().sendFeedback(Component.literal(" block outline frames drawn: " + Diagnostics.outlineEmit
									+ " (vanilla outline switched off: " + Diagnostics.outlineVanillaCancelled + ")"));
							ctx.getSource().sendFeedback(Component.literal(" fog hook ran: " + Diagnostics.fogHook));
							ctx.getSource().sendFeedback(Component.literal(" weather hook ran: " + Diagnostics.weatherHook
									+ ", time hook ran: " + Diagnostics.timeHook));
							ctx.getSource().sendFeedback(Component.literal(" zoom FOV hook ran: " + Diagnostics.zoomHook
									+ ", scroll caught: " + Diagnostics.zoomScrolls));
							ctx.getSource().sendFeedback(Component.literal(" motion blur: " + Diagnostics.motionBlur));
							ctx.getSource().sendFeedback(Component.literal(" lyrics: " + Diagnostics.lyrics));
							ctx.getSource().sendFeedback(Component.literal(" upscaler: " + Diagnostics.upscaler));
							ctx.getSource().sendFeedback(Component.literal(" last error: " + Diagnostics.lastError));
							return 1;
						}))));

		// /chatsearch [text]
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("chatsearch")
						.executes(ctx -> {
							openSearch("");
							return 1;
						})
						.then(ClientCommands.argument("text", StringArgumentType.greedyString())
								.executes(ctx -> {
									openSearch(StringArgumentType.getString(ctx, "text"));
									return 1;
								}))));
	}

	private static void openSearch(String query) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> mc.gui.setScreen(new ChatSearchScreen(null, query)));
	}
}
