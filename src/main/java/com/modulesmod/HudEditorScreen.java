package com.modulesmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag your HUDs to where you want them. Positions are saved when you let go.
 * HUDs that are switched off are shown dimmed, so you can place them before turning them on.
 */
public class HudEditorScreen extends Screen {
	private static final int SNAP = 5;

	private final Screen parent;
	private Huds.Widget dragging = null;
	private int grabX;
	private int grabY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Reset Positions"), b -> {
			for (Huds.Widget w : Huds.ALL) w.resetPosition();
			Modules.save();
		}).bounds(width / 2 - 155, height - 28, 150, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 + 5, height - 28, 150, 20).build());
	}

	@Override
	public void onClose() {
		Modules.save();
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		String title = "Drag the HUDs where you want them";
		ctx.drawString(font, title, (width - font.width(title)) / 2, 8, 0xFFFFFFFF);
		String sub = "Dimmed ones are switched off";
		ctx.drawString(font, sub, (width - font.width(sub)) / 2, 20, 0xFF888888);

		Minecraft mc = Minecraft.getInstance();
		for (Huds.Widget w : Huds.ALL) {
			boolean on = w.active();
			w.render(ctx, true);

			int[] b = w.bounds(width, height, mc, true);
			int color = w == dragging ? 0xFFFFFF55 : (on ? 0xAAFFFFFF : 0x55FFFFFF);
			outline(ctx, b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, color);

			if (!on) ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x88000000);
			int labelY = b[1] > 12 ? b[1] - 11 : b[1] + b[3] + 3;
			ctx.drawString(font, w.name + (on ? "" : " (off)"), b[0], labelY, on ? 0xFFFFFFFF : 0xFF888888);
		}

		// guide lines while dragging
		if (dragging != null) {
			ctx.fill(width / 2, 0, width / 2 + 1, height, 0x33FFFFFF);
			ctx.fill(0, height / 2, width, height / 2 + 1, 0x33FFFFFF);
		}
	}

	private static void outline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			Minecraft mc = Minecraft.getInstance();
			// the last one in the list is drawn on top, so check it first
			for (int i = Huds.ALL.size() - 1; i >= 0; i--) {
				Huds.Widget w = Huds.ALL.get(i);
				int[] b = w.bounds(width, height, mc, true);
				if (click.x() >= b[0] && click.x() < b[0] + b[2] && click.y() >= b[1] && click.y() < b[1] + b[3]) {
					dragging = w;
					grabX = (int) click.x() - b[0];
					grabY = (int) click.y() - b[1];
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (dragging != null) {
			Minecraft mc = Minecraft.getInstance();
			int[] b = dragging.bounds(width, height, mc, true);
			int nx = (int) click.x() - grabX;
			int ny = (int) click.y() - grabY;

			// snap to the screen edges and the centre lines
			if (Math.abs(nx) < SNAP) nx = 0;
			if (Math.abs(nx + b[2] - width) < SNAP) nx = width - b[2];
			if (Math.abs(nx + b[2] / 2 - width / 2) < SNAP) nx = width / 2 - b[2] / 2;
			if (Math.abs(ny) < SNAP) ny = 0;
			if (Math.abs(ny + b[3] - height) < SNAP) ny = height - b[3];
			if (Math.abs(ny + b[3] / 2 - height / 2) < SNAP) ny = height / 2 - b[3] / 2;

			nx = Math.max(0, Math.min(width - b[2], nx));
			ny = Math.max(0, Math.min(height - b[3], ny));
			dragging.moveTo(nx, ny, width, height);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (dragging != null) {
			dragging = null;
			Modules.save();
		}
		return super.mouseReleased(click);
	}
}
