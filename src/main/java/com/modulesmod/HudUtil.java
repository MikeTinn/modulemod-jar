package com.modulesmod;

final class HudUtil {
	private HudUtil() {}

	/** "FF8800" -> 0xFF8800, falls back to def if the text isn't a 6 digit hex. */
	static int rgb(String hex, int def) {
		try {
			if (hex != null && hex.length() == 6) return Integer.parseInt(hex, 16);
		} catch (NumberFormatException ignored) {
		}
		return def;
	}

	/** 0-100 percent -> 0-255 alpha. */
	static int alpha(double percent) {
		return (int) Math.round(Math.max(0, Math.min(100, percent)) / 100.0 * 255.0);
	}

	/** Hue/saturation/brightness (each 0-1) to 0xRRGGBB. */
	static int hsvToRgb(float h, float s, float v) {
		h = Math.max(0f, Math.min(0.9999f, h));
		float c = v * s;
		float hh = h * 6f;
		float x = c * (1f - Math.abs(hh % 2f - 1f));
		float m = v - c;
		float r;
		float g;
		float b;
		switch ((int) Math.floor(hh)) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return (Math.round((r + m) * 255f) << 16) | (Math.round((g + m) * 255f) << 8) | Math.round((b + m) * 255f);
	}

	/** Blend two 0xRRGGBB colors (t = 0 gives a, t = 1 gives b). */
	static int lerpRgb(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
