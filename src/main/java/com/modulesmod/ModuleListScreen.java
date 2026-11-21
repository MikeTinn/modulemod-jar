package com.modulesmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ModuleListScreen extends Screen {
	private static final int W = 360;
	private static final int ROW_H = 36;
	private static final int GAP = 4;
	private static final int TOP = 46;
	private static final int TOGGLE_W = 36;

	private final Screen parent;
	private String query = "";
	private EditBox search;
	private final List<Row> rows = new ArrayList<>();
	private final ScrollBar scrollBar = new ScrollBar();
	private Button upButton;
	private Button downButton;
	private int scroll = 0;
	private int maxScroll = 0;

	private static class Row {
		final Module module;
		final Button button;
		final Button toggle;
		int y;

		Row(Module module, Button button, Button toggle) {
			this.module = module;
			this.button = button;
			this.toggle = toggle;
		}
	}

	public ModuleListScreen(Screen parent) {
		super(Component.literal("Modules"));
		this.parent = parent;
	}

	private static Component stateText(Module m) {
		boolean on = m.isEnabled();
		return Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private int bottom() {
		return height - 34;
	}

	private int contentH() {
		return rows.size() * (ROW_H + GAP);
	}

	private int barX() {
		return (width - W) / 2 + W + 8;
	}

	@Override
	protected void init() {
		rows.clear();
		int left = (width - W) / 2;

		search = new EditBox(font, left, 16, W - 152, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search..."));
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			rebuildWidgets();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		// the cosmetics screen: capes, wings, hats, jackets, pets
		addRenderableWidget(Button.builder(Component.literal("Cosmetics"), b -> minecraft.gui.setScreen(new CosmeticsScreen(this)))
				.bounds(left + W - 148, 16, 74, 20).build());

		// opens the HUD editor, where you drag the HUDs to where you want them
		addRenderableWidget(Button.builder(Component.literal("Move HUDs"), b -> minecraft.gui.setScreen(new HudEditorScreen(this)))
				.bounds(left + W - 70, 16, 70, 20).build());

		for (Module m : Modules.ALL) {
			if (!query.isEmpty() && !m.name.toLowerCase().contains(query.toLowerCase())) continue;

			Button button = Button.builder(Component.empty(),
					b -> minecraft.gui.setScreen(new ModuleSettingsScreen(this, m)))
					.bounds(left, 0, W - TOGGLE_W - 6, ROW_H).build();
			addRenderableWidget(button);

			// quick on/off without opening the module
			Button toggle = Button.builder(stateText(m), b -> {
				Setting enabled = m.settings.get(0);
				enabled.bool = !enabled.bool;
				b.setMessage(stateText(m));
				Modules.save();
			}).bounds(left + W - TOGGLE_W, 0, TOGGLE_W, 20).build();
			addRenderableWidget(toggle);

			rows.add(new Row(m, button, toggle));
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());

		upButton = Button.builder(Component.literal("\u25B2"), b -> scrollBy(-(ROW_H + GAP)))
				.bounds(barX() - 4, 18, 14, 14).build();
		downButton = Button.builder(Component.literal("\u25BC"), b -> scrollBy(ROW_H + GAP))
				.bounds(barX() - 4, bottom() + 4, 14, 14).build();
		addRenderableWidget(upButton);
		addRenderableWidget(downButton);

		layout();
	}

	private void scrollBy(int delta) {
		scroll = Math.max(0, Math.min(maxScroll, scroll + delta));
		layout();
	}

	private void layout() {
		int bottom = bottom();
		int viewH = bottom - TOP;
		maxScroll = Math.max(0, contentH() - viewH);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int y = TOP + i * (ROW_H + GAP) - scroll;
			boolean vis = y >= TOP - 2 && y + ROW_H <= bottom;
			r.y = y;
			r.button.setY(y);
			r.button.visible = vis;
			r.button.active = vis;
			r.toggle.setY(y + (ROW_H - 20) / 2);
			r.toggle.visible = vis;
			r.toggle.active = vis;
		}

		boolean arrows = maxScroll > 0;
		if (upButton != null) {
			upButton.visible = arrows;
			upButton.active = arrows;
			downButton.visible = arrows;
			downButton.active = arrows;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScroll > 0) {
			scrollBy(-(int) (verticalAmount * 14));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (maxScroll > 0 && click.button() == 0
				&& scrollBar.press(click.x(), click.y(), scroll, maxScroll, barX(), TOP, bottom(), contentH())) {
			scroll = scrollBar.drag(click.y(), maxScroll, TOP, bottom(), contentH());
			layout();
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (scrollBar.isDragging()) {
			scroll = scrollBar.drag(click.y(), maxScroll, TOP, bottom(), contentH());
			layout();
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		scrollBar.release();
		return super.mouseReleased(click);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.text(font, "Modules", (width - font.width("Modules")) / 2, 4, 0xFFFFFFFF);

		int bottom = bottom();
		for (Row r : rows) {
			if (r.y >= TOP - 2 && r.y + ROW_H <= bottom) drawRow(ctx, r);
		}

		if (rows.isEmpty()) {
			String msg = "No modules found";
			ctx.text(font, msg, (width - font.width(msg)) / 2, 60, 0xFFAAAAAA);
		}

		if (maxScroll > 0) {
			scrollBar.draw(ctx, barX(), TOP, bottom, contentH(), scroll, maxScroll);
		}
	}

	private void drawRow(GuiGraphicsExtractor ctx, Row r) {
		Module m = r.module;
		int x = r.button.getX();
		int y = r.button.getY();
		boolean on = m.isEnabled();

		ModuleIcons.draw(ctx, m.id, x + 4, y + 4, on);

		ctx.text(font, m.name, x + 38, y + 7, on ? 0xFFFFFFFF : 0xFF888888);
		String desc = font.plainSubstrByWidth(m.description, W - TOGGLE_W - 6 - 38 - 8);
		ctx.text(font, desc, x + 38, y + 21, 0xFF8A8A8A);
	}
}
