package com.modulesmod;

import com.modulesmod.mixin.GameRendererAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Motion blur as a post-processing effect (assets/modulesmod/post_effect/motion_blur_*.json).
 * Each frame is blended with the previous one. This class turns the effect on and off as the
 * module is toggled, and leaves any other effect (like the spectator creeper view) alone.
 */
public final class MotionBlurController {
	private static int failures = 0;
	private static boolean gaveUp = false;
	private static boolean attempted = false;

	private MotionBlurController() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				tick(client);
			} catch (Throwable t) {
				gaveUp = true;
				Diagnostics.motionBlur = "unavailable (" + t.getClass().getSimpleName() + ")";
				Diagnostics.error("motion blur", t);
			}
		});
	}

	private static void tick(Minecraft mc) {
		if (gaveUp || mc.gameRenderer == null) return;

		GameRendererAccess access = (GameRendererAccess) (Object) mc.gameRenderer;
		Identifier current = access.modulesmod$getPostProcessorId();
		boolean currentIsOurs = current != null && ModulesMod.MOD_ID.equals(current.getNamespace());

		boolean want = Modules.MOTION_BLUR.isEnabled() && mc.level != null;
		if (!want) {
			if (currentIsOurs) mc.gameRenderer.clearPostEffect();
			attempted = false;
			failures = 0;
			Diagnostics.motionBlur = "off";
			return;
		}

		Identifier target = Identifier.fromNamespaceAndPath(ModulesMod.MOD_ID, "motion_blur_" + Modules.MB_STRENGTH.index());

		if (current == null) {
			// something else may be using the slot only if current != null, so we are free to start
			if (attempted) {
				// we asked last tick but nothing is set: the effect failed to load
				failures++;
				if (failures >= 3) {
					gaveUp = true;
					Diagnostics.motionBlur = "failed to load the shader (see the game log)";
					return;
				}
			}
			access.modulesmod$setPostProcessor(target);
			attempted = true;
			Diagnostics.motionBlur = "starting";
		} else if (currentIsOurs) {
			if (!current.equals(target)) access.modulesmod$setPostProcessor(target);
			attempted = false;
			failures = 0;
			Diagnostics.motionBlur = "on (" + Modules.MB_STRENGTH.choice() + ")";
		} else {
			Diagnostics.motionBlur = "waiting: another screen effect is active";
		}
	}
}
