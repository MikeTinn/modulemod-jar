package com.modulesmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Wraps the vanilla chat drawing: slides new messages in and optionally overrides the background opacity. */
public final class ChatRenderHook {
	private static boolean pushed = false;
	private static Double savedBackground = null;

	private ChatRenderHook() {}

	public static void begin(GuiGraphics ctx) {
		double chatScale = ChatState.lastChatScale;
		pushed = false;
		savedBackground = null;
		if (!Modules.CHAT.isEnabled()) return;
		Diagnostics.chatRenderHook++;

		if (Modules.CH_SMOOTH.bool) {
			long age = System.currentTimeMillis() - ChatState.lastAddedMs;
			double duration = Math.max(1.0, Modules.CH_SMOOTH_MS.num);
			if (age >= 0 && age < duration) {
				double t = age / duration;
				double ease = 1.0 - Math.pow(1.0 - t, 3.0); // fast start, soft landing
				float dy = (float) (ChatState.lastAddedLines * 9.0 * chatScale * (1.0 - ease));
				ctx.pose().pushMatrix();
				ctx.pose().translate(0f, dy);
				pushed = true;
			}
		}

		if (Modules.CH_BG_ON.bool) {
			var option = Minecraft.getInstance().options.textBackgroundOpacity();
			savedBackground = option.get();
			option.set(Math.max(0.0, Math.min(100.0, Modules.CH_BG.num)) / 100.0);
		}
	}

	public static void end(GuiGraphics ctx) {
		if (pushed) {
			ctx.pose().popMatrix();
			pushed = false;
		}
		if (savedBackground != null) {
			Minecraft.getInstance().options.textBackgroundOpacity().set(savedBackground);
			savedBackground = null;
		}
	}
}
