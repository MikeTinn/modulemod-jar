package com.modulesmod;

import net.minecraft.world.level.Level;

/**
 * Client-side only: changes the weather and time of day YOU see. The server and other players
 * are not affected. WorldMixin asks us for the value every time the game reads weather or time.
 */
public final class WeatherTweaks {
	/** Game ticks for the presets: 0 = 6:00, 6000 = noon, 12000 = 18:00, 18000 = midnight. */
	private static final long[] PRESET_TICKS = {
			-1, 23000, 1000, 6000, 12500, 14000, 18000
	};

	private WeatherTweaks() {}

	private static boolean active(Level world) {
		return Modules.WEATHER_CHANGER.isEnabled() && world != null && world.isClientSide();
	}

	public static float rain(Level world, float original) {
		if (!active(world)) return original;
		Diagnostics.weatherHook++;
		float intensity = (float) (Modules.WC_INTENSITY.num / 100.0);
		return switch (Modules.WC_WEATHER.index()) {
			case 1 -> 0f;
			case 2, 3 -> intensity;
			default -> original;
		};
	}

	public static float thunder(Level world, float original) {
		if (!active(world)) return original;
		float intensity = (float) (Modules.WC_INTENSITY.num / 100.0);
		return switch (Modules.WC_WEATHER.index()) {
			case 1, 2 -> 0f;
			case 3 -> intensity;
			default -> original;
		};
	}

	/** Returns the time of day (in ticks) the client should use. Keeps the day count so the moon phase stays right. */
	public static long timeOfDay(Level world, long original) {
		if (!active(world)) return original;
		int mode = Modules.WC_TIME.index();
		if (mode == 0) return original;
		Diagnostics.timeHook++;

		long dayStart = original - Math.floorMod(original, 24000L);
		long ticks;
		if (mode == 8) {
			double seconds = Math.max(1.0, Modules.WC_CYCLE.num);
			double progress = ((System.currentTimeMillis() / 1000.0) / seconds) % 1.0;
			ticks = (long) (progress * 24000.0);
		} else if (mode == 7) {
			double hour = Modules.WC_HOUR.num;
			ticks = Math.floorMod(Math.round((hour - 6.0) * 1000.0), 24000L);
		} else {
			ticks = PRESET_TICKS[mode];
		}
		return dayStart + ticks;
	}
}
