package com.modulesmod;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Pick a color with the mouse: drag in the square for shade, drag the bar for hue, or click a preset. */
public class ColorPickerScreen extends Screen {
	private static final int SV = 128;     // size of the shade square
	private static final int CELL = 4;
	private static final int HUE_W = 14;
	private static final int[] PALETTE = {
			0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x5555FF, 0xAA00AA,
			0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF55FF, 0xFF8800, 0x8B4513, 0x00FFAA
	};

	private final Screen parent;
	private final Setting setting;
	private final String original;

	private float hue;
	private float sat;
	private float val;
	private int dragging = 0; // 0 none, 1 shade square, 2 hue bar
	private boolean updatingField = false;
	private EditBox hexField;
	private int svX;
	private int svY = 46;
	private int hueX;

	public ColorPickerScreen(Screen parent, Setting setting) {
		super(Component.literal("Pick a color"));
		this.parent = parent;
		this.setting = setting;
		this.original = setting.str;
		setFromRgb(HudUtil.rgb(setting.str, 0xFFFFFF));
	}

	@Override
	protected void init() {
		int total = SV + 12 + HUE_W;
		svX = (width - total) / 2;
		hueX = svX + SV + 12;

		hexField = new EditBox(font, svX, svY + SV + 8, 72, 20, Component.literal("Hex"));
		hexField.setMaxLength(6);
		hexField.setFilter(t -> t.matches("[0-9a-fA-F]{0,6}"));
		hexField.setValue(setting.str.toUpperCase(Locale.ROOT));
		hexField.setResponder(t -> {
			if (!updatingField && t.length() == 6) {
				setFromRgb(Integer.parseInt(t, 16));
				apply(false);
			}
		});
		addRenderableWidget(hexField);

		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(width / 2 - 105, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> minecraft.gui.setScreen(parent))
				.bounds(width / 2 + 5, height - 28, 100, 20).build());
	}

	/** Cancel: put the old color back. */
	@Override
	public void onClose() {
		setting.str = original;
		minecraft.gui.setScreen(parent);
	}

	// ---------- color maths ----------
	private static int hsvToRgb(float h, float s, float v) {
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
		int ri = Math.round((r + m) * 255f);
		int gi = Math.round((g + m) * 255f);
		int bi = Math.round((b + m) * 255f);
		return (ri << 16) | (gi << 8) | bi;
	}

	private void setFromRgb(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f;
		float g = ((rgb >> 8) & 255) / 255f;
		float b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		val = max;
		sat = max == 0 ? 0 : d / max;
		if (d > 0) {
			float h;
			if (max == r) h = ((g - b) / d) % 6f;
			else if (max == g) h = (b - r) / d + 2f;
			else h = (r - g) / d + 4f;
			h /= 6f;
			if (h < 0) h += 1f;
			hue = h;
		}
	}

	/** Writes the current color into the setting (and optionally the hex box). */
	private void apply(boolean updateField) {
		setting.str = String.format(Locale.ROOT, "%06X", hsvToRgb(hue, sat, val));
		if (updateField && hexField != null) {
			updatingField = true;
			hexField.setValue(setting.str);
			updatingField = false;
		}
	}

	// ---------- mouse ----------
	private boolean inSquare(double x, double y) {
		return x >= svX && x < svX + SV && y >= svY && y < svY + SV;
	}

	private boolean inHue(double x, double y) {
		return x >= hueX - 2 && x < hueX + HUE_W + 2 && y >= svY && y < svY + SV;
	}

	private int paletteAt(double x, double y) {
		int palY = svY + SV + 36;
		for (int i = 0; i < PALETTE.length; i++) {
			int px = svX + (i % 8) * 16;
			int py = palY + (i / 8) * 16;
			if (x >= px && x < px + 14 && y >= py && y < py + 14) return i;
		}
		return -1;
	}

	private void updateFromMouse(double x, double y) {
		if (dragging == 1) {
			sat = (float) Math.max(0, Math.min(1, (x - svX) / (SV - 1)));
			val = 1f - (float) Math.max(0, Math.min(1, (y - svY) / (SV - 1)));
		} else if (dragging == 2) {
			hue = (float) Math.max(0, Math.min(0.9999, (y - svY) / (SV - 1)));
		}
		apply(true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			double x = click.x();
			double y = click.y();
			if (inSquare(x, y)) {
				dragging = 1;
				updateFromMouse(x, y);
				return true;
			}
			if (inHue(x, y)) {
				dragging = 2;
				updateFromMouse(x, y);
				return true;
			}
			int p = paletteAt(x, y);
			if (p >= 0) {
				setFromRgb(PALETTE[p]);
				apply(true);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (dragging != 0) {
			updateFromMouse(click.x(), click.y());
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		dragging = 0;
		return super.mouseReleased(click);
	}

	// ---------- drawing ----------
	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		String title = "Pick a color";
		ctx.drawString(font, title, (width - font.width(title)) / 2, 8, 0xFFFFFFFF);
		String name = setting.label;
		ctx.drawString(font, name, (width - font.width(name)) / 2, 24, 0xFFAAAAAA);

		// shade square: saturation to the right, brightness upwards
		int n = SV / CELL;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				float s = i / (float) (n - 1);
				float v = 1f - j / (float) (n - 1);
				int x = svX + i * CELL;
				int y = svY + j * CELL;
				ctx.fill(x, y, x + CELL, y + CELL, 0xFF000000 | hsvToRgb(hue, s, v));
			}
		}
		// hue bar
		int rows = SV / 2;
		for (int j = 0; j < rows; j++) {
			float h = j / (float) (rows - 1);
			ctx.fill(hueX, svY + j * 2, hueX + HUE_W, svY + j * 2 + 2, 0xFF000000 | hsvToRgb(h, 1f, 1f));
		}

		// markers
		int mx = svX + Math.round(sat * (SV - 1));
		int my = svY + Math.round((1f - val) * (SV - 1));
		ctx.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFF000000);
		ctx.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
		ctx.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF000000 | hsvToRgb(hue, sat, val));
		int hy = svY + Math.round(hue * (SV - 1));
		ctx.fill(hueX - 2, hy - 2, hueX + HUE_W + 2, hy + 2, 0xFF000000);
		ctx.fill(hueX - 2, hy - 1, hueX + HUE_W + 2, hy + 1, 0xFFFFFFFF);

		// preview
		int px = svX + 80;
		int py = svY + SV + 8;
		ctx.fill(px - 1, py - 1, svX + SV + 1, py + 21, 0xFFFFFFFF);
		ctx.fill(px, py, svX + SV, py + 20, 0xFF000000 | hsvToRgb(hue, sat, val));

		// presets
		int palY = svY + SV + 36;
		for (int i = 0; i < PALETTE.length; i++) {
			int x = svX + (i % 8) * 16;
			int y = palY + (i / 8) * 16;
			ctx.fill(x - 1, y - 1, x + 15, y + 15, 0xFF555555);
			ctx.fill(x, y, x + 14, y + 14, 0xFF000000 | PALETTE[i]);
		}
	}
}
