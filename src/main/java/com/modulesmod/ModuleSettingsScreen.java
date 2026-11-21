package com.modulesmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class ModuleSettingsScreen extends Screen {
	private static final int W = 380;
	private static final int ROW_H = 28;
	private static final int TOP = 44;

	private final Screen parent;
	private final Module module;
	private final List<Object> snapshot = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();
	private final ScrollBar scrollBar = new ScrollBar();
	private Button upButton;
	private Button downButton;
	private String query = "";
	private int scroll = 0;
	private int maxScroll = 0;

	private static class Entry {
		final Setting setting;
		final AbstractWidget control;
		final Button reset; // null for rows without a Reset button
		int y;

		Entry(Setting setting, AbstractWidget control, Button reset) {
			this.setting = setting;
			this.control = control;
			this.reset = reset;
		}
	}

	public ModuleSettingsScreen(Screen parent, Module module) {
		super(Component.literal(module.name));
		this.parent = parent;
		this.module = module;
		for (Setting s : module.settings) snapshot.add(s.snapshot());
	}

	private int bottom() {
		return height - 34;
	}

	private int contentH() {
		return entries.size() * ROW_H;
	}

	private int barX() {
		return (width - W) / 2 + W + 8;
	}

	@Override
	protected void init() {
		entries.clear();
		int left = (width - W) / 2;

		EditBox search = new EditBox(font, left, 16, W, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search..."));
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			rebuildWidgets();
		});
		addRenderableWidget(search);

		int controlX = left + W - 50 - 5 - 145;
		int resetX = left + W - 50;

		for (Setting s : module.settings) {
			if (!query.isEmpty() && !s.label.toLowerCase().contains(query.toLowerCase())) continue;

			AbstractWidget control = createControl(s, controlX);
			addRenderableWidget(control);

			Button reset = null;
			if (s.type != Setting.Type.PRESET && s.type != Setting.Type.ACTION) {
				reset = Button.builder(Component.literal("Reset"), b -> {
					s.reset();
					rebuildWidgets();
				}).bounds(resetX, 0, 50, 20).build();
				addRenderableWidget(reset);
			}

			entries.add(new Entry(s, control, reset));
		}

		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(width / 2 - 155, height - 28, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save & Quit"), b -> {
			Modules.save();
			minecraft.setScreen(parent);
		}).bounds(width / 2 + 5, height - 28, 150, 20).build());

		// up / down arrows next to the scrollbar (shown only when the list is longer than the screen)
		upButton = Button.builder(Component.literal("\u25B2"), b -> scrollBy(-ROW_H * 2))
				.bounds(barX() - 4, 18, 14, 14).build();
		downButton = Button.builder(Component.literal("\u25BC"), b -> scrollBy(ROW_H * 2))
				.bounds(barX() - 4, bottom() + 4, 14, 14).build();
		addRenderableWidget(upButton);
		addRenderableWidget(downButton);

		layout();
	}

	private AbstractWidget createControl(Setting s, int x) {
		return switch (s.type) {
			case BOOL -> Button.builder(boolText(s), b -> {
				s.bool = !s.bool;
				b.setMessage(boolText(s));
			}).bounds(x, 0, 145, 20).build();

			case CHOICE -> Button.builder(Component.literal(s.choice()), b -> {
				s.num = (s.index() + 1) % s.options.length;
				b.setMessage(Component.literal(s.choice()));
			}).bounds(x, 0, 145, 20).build();

			case SLIDER -> new SettingSlider(x, 0, 145, 20, s);

			case INT, TEXT -> textField(s, x);

			// colors open a real color picker instead of asking for hex codes
			case HEX -> Button.builder(colorText(s), b -> minecraft.setScreen(new ColorPickerScreen(this, s)))
					.bounds(x, 0, 145, 20).build();

			case PRESET -> Button.builder(Component.literal(AspectPresets.label(s.linked.num)), b -> {
				s.linked.num = AspectPresets.next(s.linked.num);
				rebuildWidgets();
			}).bounds(x, 0, 145, 20).build();

			case ACTION -> Button.builder(Component.literal(s.buttonText), b -> {
				s.action.run();
				rebuildWidgets();
			}).bounds(x, 0, 145, 20).build();
		};
	}

	private EditBox textField(Setting s, int x) {
		EditBox f = new EditBox(font, x, 0, 145, 20, Component.literal(s.label));
		if (s.type == Setting.Type.INT) {
			f.setFilter(t -> t.matches("-?\\d{0,9}"));
		} else {
			f.setMaxLength(60);
		}
		f.setValue(s.type == Setting.Type.INT ? String.valueOf((int) s.num) : s.str);
		f.setResponder(t -> {
			if (s.type == Setting.Type.INT) {
				try {
					s.num = Integer.parseInt(t);
				} catch (NumberFormatException ignored) {
				}
			} else {
				s.str = t;
			}
		});
		return f;
	}

	private static Component boolText(Setting s) {
		return Component.literal(s.bool ? "Yes" : "No").withStyle(s.bool ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	static Component colorText(Setting s) {
		int rgb = HudUtil.rgb(s.str, 0xFFFFFF);
		return Component.literal("\u2588\u2588\u2588 ").setStyle(Style.EMPTY.withColor(rgb))
				.append(Component.literal("#" + s.str.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.WHITE));
	}

	private void scrollBy(int delta) {
		scroll = Math.max(0, Math.min(maxScroll, scroll + delta));
		layout();
	}

	/** Positions every row for the current scroll and hides rows outside the visible area. */
	private void layout() {
		int bottom = bottom();
		int viewH = bottom - TOP;
		maxScroll = Math.max(0, contentH() - viewH);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int y = TOP + 2 + i * ROW_H - scroll;
			boolean vis = y >= TOP && y + 20 <= bottom;
			e.y = y;
			e.control.setY(y);
			e.control.visible = vis;
			e.control.active = vis;
			if (e.reset != null) {
				e.reset.setY(y);
				e.reset.visible = vis;
				e.reset.active = vis;
			}
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

	/** Close = Cancel: throw away unsaved changes. */
	@Override
	public void onClose() {
		for (int i = 0; i < module.settings.size(); i++) {
			module.settings.get(i).restore(snapshot.get(i));
		}
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawString(font, module.name, (width - font.width(module.name)) / 2, 4, 0xFFFFFFFF);

		int left = (width - W) / 2;
		int bottom = bottom();
		for (Entry e : entries) {
			if (e.y >= TOP && e.y + 20 <= bottom) {
				ctx.drawString(font, e.setting.label, left, e.y + 6, 0xFFFFFFFF);
			}
		}

		if (maxScroll > 0) {
			scrollBar.draw(ctx, barX(), TOP, bottom, contentH(), scroll, maxScroll);
		}
	}

	private static class SettingSlider extends AbstractSliderButton {
		private final Setting s;

		SettingSlider(int x, int y, int w, int h, Setting s) {
			super(x, y, w, h, Component.empty(), (s.num - s.min) / (s.max - s.min));
			this.s = s;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (s == null) return;
			setMessage(Component.literal("Value: " + String.format(Locale.ROOT, "%." + s.decimals + "f", s.num)));
		}

		@Override
		protected void applyValue() {
			double p = Math.pow(10, s.decimals);
			s.num = Math.round((s.min + value * (s.max - s.min)) * p) / p;
		}
	}
}
