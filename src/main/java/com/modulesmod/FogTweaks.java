package com.modulesmod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import org.joml.Vector4f;

/**
 * The numbers behind the Fog Customizer. FogRendererMixin passes every fog value through here
 * just before vanilla writes it for the frame.
 *
 * Fair play: nothing changes while you are blind, in darkness, underwater or in lava, so this can
 * never be used to see through those effects.
 */
public final class FogTweaks {
	/** Fog values at or above this are vanilla's "no fog" settings and are never touched. */
	private static final float NO_FOG = 1.0E6f;
	private static final float REMOVED_START = 1.0E8f;
	private static final float REMOVED_END = 2.0E8f;

	private FogTweaks() {}

	private static boolean active() {
		if (!Modules.FOG_CUSTOMIZER.isEnabled()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		if (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS)) return false;
		if (mc.player.isUnderWater() || mc.player.isInLava()) return false;
		return true;
	}

	private static boolean removeDistance() {
		return Modules.FG_REMOVE_ALL.bool || Modules.FG_REMOVE_DISTANCE.bool;
	}

	private static boolean removeAtmospheric() {
		return Modules.FG_REMOVE_ALL.bool || Modules.FG_REMOVE_ENV.bool;
	}

	public static float environmentalStart(float v) {
		if (!active() || Math.abs(v) >= NO_FOG) return v;
		if (removeAtmospheric()) return REMOVED_START;
		return v * (float) (Modules.FG_ENV_SCALE.num / 100.0);
	}

	public static float environmentalEnd(float v) {
		if (!active() || Math.abs(v) >= NO_FOG) return v;
		if (removeAtmospheric()) return REMOVED_END;
		return v * (float) (Modules.FG_ENV_SCALE.num / 100.0);
	}

	public static float distanceStart(float v) {
		if (!active() || Math.abs(v) >= NO_FOG) return v;
		if (removeDistance()) return REMOVED_START;
		return v * (float) (Modules.FG_DIST_START.num / 100.0);
	}

	public static float distanceEnd(float v) {
		if (!active() || Math.abs(v) >= NO_FOG) return v;
		if (removeDistance()) return REMOVED_END;
		return v * (float) (Modules.FG_DIST_END.num / 100.0);
	}

	/** Sky and cloud fog are only removed by "Remove All Fog". */
	public static float skyOrCloudEnd(float v) {
		if (!active() || Math.abs(v) >= NO_FOG) return v;
		return Modules.FG_REMOVE_ALL.bool ? REMOVED_END : v;
	}

	/** Recolours the fog (and with it the horizon). Changes the vector in place. */
	public static void tint(Vector4f color) {
		if (!active() || !Modules.FG_COLOR_ON.bool) return;
		int rgb;
		if (Modules.FG_COLOR_MODE.index() == 1) {
			double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * Modules.FG_SPEED.num;
			rgb = HudUtil.hsvToRgb((float) ((t * 0.1) % 1.0), 0.6f, 1f);
		} else {
			rgb = HudUtil.rgb(Modules.FG_COLOR.str, 0xC0D8FF);
		}
		color.set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, color.w);
	}
}
