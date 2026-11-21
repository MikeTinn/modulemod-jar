package com.modulesmod;

import net.minecraft.client.gui.GuiGraphics;

/** A scrollbar you can drag with the mouse (and click on the track to jump). Driven by the screen's mouse events. */
final class ScrollBar {
	private boolean dragging = false;
	private int grab = 0;

	boolean isDragging() {
		return dragging;
	}

	private static int thumbHeight(int viewH, int contentH) {
		return Math.max(16, (int) ((long) viewH * viewH / Math.max(1, contentH)));
	}

	/** Mouse pressed. Returns true if the press was on the scrollbar. */
	boolean press(double mouseX, double mouseY, int scroll, int maxScroll, int barX, int top, int bottom, int contentH) {
		if (mouseX < barX - 3 || mouseX > barX + 9 || mouseY < top || mouseY > bottom) return false;
		int viewH = bottom - top;
		int thumbH = thumbHeight(viewH, contentH);
		int range = Math.max(1, viewH - thumbH);
		int thumbY = top + (int) ((long) range * scroll / Math.max(1, maxScroll));
		grab = (mouseY >= thumbY && mouseY <= thumbY + thumbH) ? (int) (mouseY - thumbY) : thumbH / 2;
		dragging = true;
		return true;
	}

	/** Mouse moved while pressed. Returns the new scroll position. */
	int drag(double mouseY, int maxScroll, int top, int bottom, int contentH) {
		int viewH = bottom - top;
		int thumbH = thumbHeight(viewH, contentH);
		int range = Math.max(1, viewH - thumbH);
		int newThumb = (int) Math.max(top, Math.min(top + range, mouseY - grab));
		return (int) Math.round((double) (newThumb - top) * maxScroll / range);
	}

	void release() {
		dragging = false;
	}

	void draw(GuiGraphics ctx, int barX, int top, int bottom, int contentH, int scroll, int maxScroll) {
		int viewH = bottom - top;
		int thumbH = thumbHeight(viewH, contentH);
		int range = Math.max(1, viewH - thumbH);
		int thumbY = top + (int) ((long) range * scroll / Math.max(1, maxScroll));
		ctx.fill(barX, top, barX + 5, bottom, 0x40FFFFFF);
		ctx.fill(barX, thumbY, barX + 5, thumbY + thumbH, dragging ? 0xFFFFFFFF : 0xFFAAAAAA);
	}
}
