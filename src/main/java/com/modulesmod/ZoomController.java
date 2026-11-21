package com.modulesmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Smooth zoom.
 *  - Every time you start zooming you get the same FOV (the "Zoom FOV" setting), never the one you
 *    stopped at last time. Scrolling only changes the zoom while you hold it.
 *  - The zoom amount is eased towards its target every frame, in log space, so scrolling and
 *    releasing feel smooth at any zoom level.
 */
public final class ZoomController {
	private static KeyMapping.Category category;
	private static KeyMapping zoomKey;
	private static KeyMapping zoomInKey;
	private static KeyMapping zoomOutKey;

	private static boolean toggled = false;
	private static boolean active = false;
	private static boolean wasActive = false;

	/** The zoom factor you asked for (1 = no zoom). Reset to the default on every new zoom. */
	private static double wantedZoom = 1.0;
	/** log of the zoom factor that is actually applied right now. */
	private static double currentLog = 0.0;
	private static double lastBaseFov = 70.0;
	private static long lastNanos = 0;

	/** The "Modules Mod" key category; other keybinds of this mod go in it too. */
	static KeyMapping.Category category() {
		return category;
	}

	private ZoomController() {}

	public static void register() {
		category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ModulesMod.MOD_ID, "main"));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.modulesmod.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
		// unbound by default; handy if you don't have a scroll wheel
		zoomInKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.modulesmod.zoom_in", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		zoomOutKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.modulesmod.zoom_out", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// toggle mode: every press flips it
			while (zoomKey.consumeClick()) {
				if (Modules.ZM_MODE.index() == 1) toggled = !toggled;
			}
			if (Modules.ZM_MODE.index() == 0) toggled = false;

			while (zoomInKey.consumeClick()) step(1);
			while (zoomOutKey.consumeClick()) step(-1);
		});
	}

	private static boolean wantsZoom(Minecraft mc) {
		if (!Modules.ZOOM.isEnabled() || mc.player == null || mc.screen != null) return false;
		return Modules.ZM_MODE.index() == 1 ? toggled : zoomKey.isDown();
	}

	/** Called by GameRendererZoomMixin every frame with the normal FOV. Returns the FOV to use. */
	public static float apply(float baseFov) {
		Minecraft mc = Minecraft.getInstance();
		Diagnostics.zoomHook++;

		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
		lastNanos = now;
		lastBaseFov = baseFov;

		active = wantsZoom(mc);
		if (active && !wasActive) {
			// a fresh zoom always starts at the same FOV
			wantedZoom = Math.max(1.0, baseFov / Math.max(1.0, Modules.ZM_FOV.num));
		}
		wasActive = active;

		double targetLog = active ? Math.log(wantedZoom) : 0.0;
		double speed = Math.max(1.0, Modules.ZM_SPEED.num);
		currentLog += (targetLog - currentLog) * (1.0 - Math.exp(-speed * dt));
		if (!active && Math.abs(currentLog) < 0.0005) currentLog = 0.0;
		if (currentLog == 0.0) return baseFov;

		return (float) Math.max(1.0, baseFov / Math.exp(currentLog));
	}

	/** Mouse wheel. Returns true if the zoom used it (so the hotbar should not scroll). */
	public static boolean consumeScroll(double vertical) {
		if (!active || !Modules.ZM_SCROLL.bool || vertical == 0) return false;
		step(vertical > 0 ? 1 : -1);
		Diagnostics.zoomScrolls++;
		return true;
	}

	private static void step(int notches) {
		if (!active) return;
		if (Modules.ZM_INVERT.bool) notches = -notches;
		double factor = 1.0 + Modules.ZM_STEP.num / 100.0;
		double maxZoom = Math.max(1.0, lastBaseFov / Math.max(1.0, Modules.ZM_MIN_FOV.num));
		wantedZoom = Math.max(1.0, Math.min(maxZoom, wantedZoom * Math.pow(factor, notches)));
	}
}
