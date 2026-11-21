package com.modulesmod;

import net.minecraft.client.gui.GuiGraphics;

/** Little pictograms for each module, drawn with plain rectangles (no image files needed). */
final class ModuleIcons {
	private static final int LIGHT = 0xFFDCE6FF;
	private static final int BG = 0xFF0B1226;

	private ModuleIcons() {}

	static int accent(String id) {
		return switch (id) {
			case "scoreboard" -> 0xFF5AA9FF;
			case "tab" -> 0xFF7CE38B;
			case "aspect_ratio" -> 0xFFFFB454;
			case "chat" -> 0xFFC792EA;
			case "hitbox" -> 0xFFFF6B6B;
			case "motion_blur" -> 0xFFFF7ADB;
			case "armor_status" -> 0xFFB8C4D6;
			case "block_outline" -> 0xFFF2D16B;
			case "fog_customizer" -> 0xFF90A4B4;
			case "weather_changer" -> 0xFF4FC3F7;
			case "fps_display" -> 0xFFB6E35A;
			case "reach_display" -> 0xFFFF8F5A;
			case "ping_display" -> 0xFF5FE0C0;
			case "inventory_hud" -> 0xFFE0A96D;
			case "zoom" -> 0xFF6EC1FF;
			case "lyrics" -> 0xFFFF7AA8;
			case "copy_give" -> 0xFF8AE234;
			case "upscaler" -> 0xFFFFC857;
			case "cosmetics" -> 0xFFFFD24A;
			default -> 0xFF9CC3FF;
		};
	}

	private static int dim(int argb) {
		int r = ((argb >> 16) & 0xFF) / 2;
		int g = ((argb >> 8) & 0xFF) / 2;
		int b = (argb & 0xFF) / 2;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** Same color with a different opacity. */
	private static int alpha(int argb, int a) {
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static void outline(GuiGraphics c, int x1, int y1, int x2, int y2, int color) {
		c.fill(x1, y1, x2, y1 + 1, color);
		c.fill(x1, y2 - 1, x2, y2, color);
		c.fill(x1, y1, x1 + 1, y2, color);
		c.fill(x2 - 1, y1, x2, y2, color);
	}

	/** Draws a 28x28 icon with its top-left corner at (x, y). Dimmed when the module is off. */
	static void draw(GuiGraphics c, String id, int x, int y, boolean enabled) {
		int accent = enabled ? accent(id) : dim(accent(id));
		int light = enabled ? LIGHT : 0xFF707070;

		c.fill(x, y, x + 28, y + 28, BG);
		outline(c, x, y, x + 28, y + 28, (0x66 << 24) | (accent & 0xFFFFFF));

		switch (id) {
			case "scoreboard" -> {
				c.fill(x + 6, y + 6, x + 22, y + 9, accent);
				for (int i = 0; i < 3; i++) {
					int ry = y + 12 + i * 5;
					c.fill(x + 6, ry, x + 15, ry + 2, light);
					c.fill(x + 17, ry, x + 22, ry + 2, accent);
				}
			}
			case "tab" -> {
				for (int col = 0; col < 2; col++) {
					for (int row = 0; row < 3; row++) {
						int cx = x + 5 + col * 10;
						int cy = y + 6 + row * 6;
						c.fill(cx, cy, cx + 3, cy + 3, accent);
						c.fill(cx + 4, cy + 1, cx + 9, cy + 3, light);
					}
				}
			}
			case "aspect_ratio" -> {
				outline(c, x + 5, y + 8, x + 23, y + 20, accent);
				c.fill(x + 9, y + 13, x + 19, y + 15, light);
				c.fill(x + 8, y + 12, x + 10, y + 16, light);
				c.fill(x + 18, y + 12, x + 20, y + 16, light);
			}
			case "chat" -> {
				c.fill(x + 5, y + 6, x + 23, y + 18, accent);
				c.fill(x + 8, y + 18, x + 13, y + 20, accent);
				c.fill(x + 8, y + 20, x + 10, y + 22, accent);
				for (int i = 0; i < 3; i++) {
					c.fill(x + 9 + i * 4, y + 11, x + 11 + i * 4, y + 13, BG);
				}
			}
			case "hitbox" -> {
				outline(c, x + 7, y + 11, x + 19, y + 23, accent);
				outline(c, x + 11, y + 7, x + 23, y + 19, light);
				for (int i = 0; i < 4; i++) {
					c.fill(x + 7 + i, y + 11 - i, x + 8 + i, y + 12 - i, accent);
					c.fill(x + 18 + i, y + 22 - i, x + 19 + i, y + 23 - i, accent);
				}
				c.fill(x + 12, y + 16, x + 14, y + 18, light);
			}
			case "motion_blur" -> {
				// a block moving to the right, leaving fading copies behind it
				c.fill(x + 5, y + 10, x + 11, y + 18, alpha(accent, 0x44));
				c.fill(x + 9, y + 10, x + 15, y + 18, alpha(accent, 0x88));
				c.fill(x + 13, y + 10, x + 19, y + 18, alpha(accent, 0xCC));
				c.fill(x + 17, y + 10, x + 23, y + 18, accent);
				c.fill(x + 5, y + 21, x + 23, y + 22, alpha(light, 0x55));
			}
			case "armor_status" -> {
				// a shield / chestplate
				c.fill(x + 6, y + 6, x + 22, y + 15, accent);
				c.fill(x + 7, y + 15, x + 21, y + 17, accent);
				c.fill(x + 9, y + 17, x + 19, y + 19, accent);
				c.fill(x + 11, y + 19, x + 17, y + 21, accent);
				c.fill(x + 13, y + 21, x + 15, y + 23, accent);
				c.fill(x + 13, y + 8, x + 15, y + 19, BG);
				c.fill(x + 9, y + 11, x + 19, y + 13, BG);
			}
			case "block_outline" -> {
				// a block with a bold outline around it
				c.fill(x + 8, y + 8, x + 20, y + 20, alpha(accent, 0x33));
				outline(c, x + 6, y + 6, x + 22, y + 22, accent);
				outline(c, x + 7, y + 7, x + 21, y + 21, accent);
				c.fill(x + 12, y + 12, x + 16, y + 16, light);
			}
			case "fog_customizer" -> {
				// layers of fog
				c.fill(x + 5, y + 7, x + 23, y + 10, alpha(accent, 0xCC));
				c.fill(x + 8, y + 12, x + 25, y + 15, alpha(accent, 0x99));
				c.fill(x + 3, y + 17, x + 20, y + 20, alpha(accent, 0x77));
				c.fill(x + 7, y + 22, x + 22, y + 24, alpha(accent, 0x44));
			}
			case "weather_changer" -> {
				// a cloud with rain
				c.fill(x + 6, y + 10, x + 22, y + 16, light);
				c.fill(x + 9, y + 7, x + 17, y + 10, light);
				c.fill(x + 4, y + 12, x + 6, y + 16, light);
				c.fill(x + 22, y + 12, x + 24, y + 16, light);
				for (int i = 0; i < 3; i++) {
					c.fill(x + 8 + i * 5, y + 18, x + 9 + i * 5, y + 22, accent);
					c.fill(x + 7 + i * 5, y + 22, x + 8 + i * 5, y + 24, accent);
				}
			}
			case "fps_display" -> {
				// a little screen with a rising bar graph
				outline(c, x + 4, y + 6, x + 24, y + 20, accent);
				int[] h = {3, 5, 8, 10};
				for (int i = 0; i < 4; i++) {
					c.fill(x + 7 + i * 4, y + 18 - h[i], x + 10 + i * 4, y + 18, light);
				}
				c.fill(x + 10, y + 21, x + 18, y + 23, accent);
			}
			case "reach_display" -> {
				// a ruler with an arrow at each end
				c.fill(x + 5, y + 13, x + 23, y + 15, accent);
				c.fill(x + 5, y + 10, x + 7, y + 18, light);
				c.fill(x + 21, y + 10, x + 23, y + 18, light);
				for (int i = 1; i < 4; i++) {
					c.fill(x + 5 + i * 4, y + 15, x + 6 + i * 4, y + 18, light);
				}
				c.fill(x + 8, y + 7, x + 20, y + 8, alpha(accent, 0x88));
			}
			case "ping_display" -> {
				// signal bars
				int[] h = {4, 8, 12, 16};
				for (int i = 0; i < 4; i++) {
					c.fill(x + 6 + i * 5, y + 22 - h[i], x + 9 + i * 5, y + 22, i < 3 ? accent : alpha(accent, 0x55));
				}
			}
			case "inventory_hud" -> {
				// a grid of item slots, one with an item in it
				for (int col = 0; col < 3; col++) {
					for (int row = 0; row < 2; row++) {
						outline(c, x + 3 + col * 8, y + 7 + row * 8, x + 10 + col * 8, y + 14 + row * 8, accent);
					}
				}
				c.fill(x + 12, y + 9, x + 17, y + 14, light);
				c.fill(x + 4, y + 17, x + 9, y + 22, alpha(accent, 0x99));
			}
			case "zoom" -> {
				// a magnifying glass with a plus in it
				c.fill(x + 9, y + 5, x + 17, y + 6, accent);
				c.fill(x + 9, y + 17, x + 17, y + 18, accent);
				c.fill(x + 6, y + 8, x + 7, y + 15, accent);
				c.fill(x + 19, y + 8, x + 20, y + 15, accent);
				c.fill(x + 7, y + 6, x + 9, y + 8, accent);
				c.fill(x + 17, y + 6, x + 19, y + 8, accent);
				c.fill(x + 7, y + 15, x + 9, y + 17, accent);
				c.fill(x + 17, y + 15, x + 19, y + 17, accent);
				c.fill(x + 10, y + 11, x + 16, y + 12, light);
				c.fill(x + 12, y + 9, x + 14, y + 14, light);
				for (int i = 0; i < 6; i++) {
					c.fill(x + 18 + i, y + 17 + i, x + 20 + i, y + 19 + i, accent);
				}
			}
			case "lyrics" -> {
				// a music note with two lyric lines under it
				c.fill(x + 13, y + 5, x + 15, y + 15, accent);
				c.fill(x + 15, y + 5, x + 20, y + 7, accent);
				c.fill(x + 9, y + 14, x + 14, y + 18, accent);
				c.fill(x + 8, y + 15, x + 9, y + 17, accent);
				c.fill(x + 6, y + 20, x + 15, y + 22, light);
				c.fill(x + 17, y + 20, x + 22, y + 22, alpha(light, 0x88));
			}
			case "copy_give" -> {
				// two stacked pages with a little item square: "copy"
				outline(c, x + 5, y + 9, x + 17, y + 23, accent);
				outline(c, x + 11, y + 5, x + 23, y + 19, light);
				c.fill(x + 14, y + 9, x + 20, y + 15, alpha(accent, 0xAA));
			}
			case "upscaler" -> {
				// a small square growing into a big one
				outline(c, x + 4, y + 4, x + 24, y + 24, alpha(light, 0x88));
				c.fill(x + 7, y + 14, x + 15, y + 22, accent);
				c.fill(x + 15, y + 8, x + 22, y + 9, accent);
				c.fill(x + 21, y + 8, x + 22, y + 15, accent);
				c.fill(x + 17, y + 12, x + 18, y + 13, accent);
				c.fill(x + 19, y + 10, x + 20, y + 11, accent);
			}
			case "cosmetics" -> {
				// a little crown
				c.fill(x + 5, y + 17, x + 23, y + 22, accent);
				c.fill(x + 5, y + 9, x + 8, y + 17, accent);
				c.fill(x + 12, y + 6, x + 16, y + 17, accent);
				c.fill(x + 20, y + 9, x + 23, y + 17, accent);
				c.fill(x + 13, y + 18, x + 15, y + 20, light);
			}
			default -> c.fill(x + 8, y + 8, x + 20, y + 20, accent);
		}
	}
}
