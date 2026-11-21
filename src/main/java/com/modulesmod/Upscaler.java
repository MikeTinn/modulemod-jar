package com.modulesmod;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * FPS upscaler: the game draws into a main screen buffer that is a percentage of the window size and
 * the result is stretched to the window when it is shown. Fewer pixels to shade = more FPS. The whole
 * screen (HUD included) gets the lower resolution; menus can stay at full resolution.
 * No sharpening or fancy reconstruction on purpose: this one is about speed, not looks.
 * Does nothing at all (not even touching the game's buffers) while the module is off.
 */
public final class Upscaler {
	/** Set by UpscalerResizeMixin the first time the game's resize call goes through it. */
	public static volatile boolean hookSeen = false;

	private static double applied = 1.0;
	private static boolean failed = false;

	private Upscaler() {}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(Upscaler::tick);
	}

	/** The scaled size of one window dimension, used for the main buffer. */
	public static int scale(int pixels) {
		return Math.max(1, (int) Math.round(pixels * applied));
	}

	private static double wanted(Minecraft mc) {
		if (failed || !Modules.UPSCALER.isEnabled()) return 1.0;
		if (mc.gui.screen() != null && Modules.UP_MENUS.bool) return 1.0;
		return Math.max(0.25, Math.min(1.0, Modules.UP_SCALE.num / 100.0));
	}

	private static void tick(Minecraft mc) {
		boolean on = Modules.UPSCALER.isEnabled();
		if (!on && applied == 1.0) {
			Diagnostics.upscaler = failed ? "turned itself off after an error" : "off";
			return;
		}
		try {
			Window window = mc.getWindow();
			RenderTarget fb = mc.getMainRenderTarget();
			if (window == null || fb == null) return;

			double want = wanted(mc);
			if (Math.abs(want - applied) > 0.001) {
				applied = want;
				fb.resize(scale(window.getWidth()), scale(window.getHeight()));
			}

			Diagnostics.upscaler = "3D buffer " + fb.width + "x" + fb.height + " of window "
					+ window.getWidth() + "x" + window.getHeight()
					+ (hookSeen ? "" : " (resize hook not seen yet)");
		} catch (Throwable t) {
			// never take the game down: go back to full size and stay off
			failed = true;
			applied = 1.0;
			Diagnostics.upscaler = "turned itself off after an error";
			try {
				Window window = mc.getWindow();
				mc.getMainRenderTarget().resize(window.getWidth(), window.getHeight());
			} catch (Throwable ignored) {
			}
			if (t instanceof Exception e) Diagnostics.error("upscaler", e);
		}
	}
}
