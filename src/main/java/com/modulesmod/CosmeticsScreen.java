package com.modulesmod;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Pick cosmetics by category and see them on your player. Click a card to equip it (click again to take it
 * off). The preview is your real player model; drag it to turn it all the way round.
 */
public class CosmeticsScreen extends Screen {
	private static final int TAB_W = 80;
	private static final int TAB_H = 20;
	private static final int CARD_W = 84;
	private static final int CARD_H = 46;
	private static final int GAP = 6;
	private static final int BAR_W = 6;
	private static final int CARDS_W = CARD_W * 2 + GAP;
	private static final int PREVIEW_W = 150;
	private static final int TOTAL_W = TAB_W + 8 + CARDS_W + BAR_W + 8 + PREVIEW_W;
	private static final int TOP = 40;
	private static final int SWATCH = 16;
	private static final int SWATCH_GAP = 4;
	private static final int SWATCH_COLS = 7;
	private static final int COLOR_AREA = 54;

	private final Screen parent;
	private Cosmetics.Category tab = Cosmetics.Category.CAPE;
	private int scroll = 0;

	private float angle = 0f;          // preview turn, radians (0 = facing you)
	private boolean autoSpin = false;
	private boolean draggingPreview = false;

	private Button openFolder;
	private Button reload;
	private String status = "";

	public CosmeticsScreen(Screen parent) {
		super(Component.literal("Cosmetics"));
		this.parent = parent;
	}

	private int left() {
		return (width - TOTAL_W) / 2;
	}

	private int cardsX() {
		return left() + TAB_W + 8;
	}

	private int previewX() {
		return cardsX() + CARDS_W + BAR_W + 8;
	}

	private int bottom() {
		return height - 36;
	}

	private int viewH() {
		return bottom() - TOP;
	}

	@Override
	protected void init() {
		int px = previewX();
		int by = bottom() - 20;
		int bw = (PREVIEW_W - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Front"), b -> {
			angle = 0f;
			autoSpin = false;
		}).bounds(px, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
			angle = (float) Math.PI;
			autoSpin = false;
		}).bounds(px + bw + 4, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Spin"), b -> autoSpin = !autoSpin)
				.bounds(px + (bw + 4) * 2, by, bw, 20).build());

		int ly = TOP + Cosmetics.Category.values().length * (TAB_H + 2) + 8;
		openFolder = addRenderableWidget(Button.builder(Component.literal("Open Folder"), b -> openCapesFolder())
				.bounds(left(), ly, TAB_W, 20).build());
		reload = addRenderableWidget(Button.builder(Component.literal("Reload"), b -> {
			Cosmetics.reloadCustom();
			status = Cosmetics.in(Cosmetics.Category.CAPE).stream().filter(d -> d.image() != null).count() + " picture(s) loaded";
		}).bounds(left(), ly + 24, TAB_W, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void openCapesFolder() {
		try {
			CustomImages.makeFolders();
			java.awt.Desktop.getDesktop().open(CustomImages.capesFolder().toFile());
		} catch (Throwable t) {
			status = "Folder: " + CustomImages.capesFolder();
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.gui.getChat().addMessage(Component.literal("[Cosmetics] put pictures in " + CustomImages.capesFolder()));
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	// ------------------------------------------------------------------ cards

	private List<Cosmetics.Def> items() {
		return Cosmetics.in(tab);
	}

	private int rows() {
		return (items().size() + 1 + 1) / 2;   // +1 for the "None" card
	}

	private int contentH() {
		return Math.max(0, rows() * (CARD_H + GAP) - GAP);
	}

	private int maxScroll() {
		return Math.max(0, contentH() - viewH());
	}

	private int cardX(int index) {
		return cardsX() + (index % 2) * (CARD_W + GAP);
	}

	private int cardY(int index) {
		return TOP + (index / 2) * (CARD_H + GAP) - scroll;
	}

	// ------------------------------------------------------------------ input

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private int previewH() {
		return bottom() - 24 - TOP;
	}

	/** The cosmetic equipped in this tab if it can be recoloured, else null. */
	private Cosmetics.Def tintable() {
		String id = Cosmetics.selectedId(tab);
		if (id == null) return null;
		for (Cosmetics.Def d : items()) if (d.id().equals(id) && d.tint() != 0) return d;
		return null;
	}

	/** Height of the part of the preview that shows the player (the rest, if any, holds the colour swatches). */
	private int modelH() {
		return tintable() != null ? previewH() - COLOR_AREA : previewH();
	}

	private int swatchX(int i) {
		return previewX() + 7 + (i % SWATCH_COLS) * (SWATCH + SWATCH_GAP);
	}

	private int swatchY(int i) {
		return TOP + modelH() + 14 + (i / SWATCH_COLS) * (SWATCH + SWATCH_GAP);
	}

	private boolean overPreview(double mx, double my) {
		return inside(mx, my, previewX(), TOP, PREVIEW_W, modelH());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			double mx = click.x();
			double my = click.y();

			if (tintable() != null) {
				for (int i = 0; i < Cosmetics.COLOR_NAMES.length; i++) {
					if (inside(mx, my, swatchX(i), swatchY(i), SWATCH, SWATCH)) {
						Cosmetics.setColor(tab, i);
						return true;
					}
				}
			}

			if (overPreview(mx, my)) {
				draggingPreview = true;
				autoSpin = false;
				return true;
			}

			Cosmetics.Category[] tabs = Cosmetics.Category.values();
			for (int i = 0; i < tabs.length; i++) {
				if (inside(mx, my, left(), TOP + i * (TAB_H + 2), TAB_W, TAB_H)) {
					tab = tabs[i];
					scroll = 0;
					return true;
				}
			}

			if (inside(mx, my, cardsX(), TOP, CARDS_W, viewH())) {
				List<Cosmetics.Def> items = items();
				for (int i = 0; i <= items.size(); i++) {
					if (!inside(mx, my, cardX(i), cardY(i), CARD_W, CARD_H)) continue;
					if (i == 0) {
						Cosmetics.select(tab, null);
					} else {
						Cosmetics.Def d = items.get(i - 1);
						boolean already = d.id().equals(Cosmetics.selectedId(tab));
						Cosmetics.select(tab, already ? null : d.id());
					}
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (draggingPreview) {
			angle += (float) offsetX * 0.02f;
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		draggingPreview = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inside(mouseX, mouseY, cardsX(), TOP, CARDS_W + BAR_W, viewH()) && maxScroll() > 0) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (verticalAmount * 24)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		if (autoSpin) angle += delta * 0.08f;
		scroll = Math.max(0, Math.min(maxScroll(), scroll));

		boolean capes = tab == Cosmetics.Category.CAPE;
		openFolder.visible = capes;
		reload.visible = capes;

		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.drawString(font, "Cosmetics", (width - font.width("Cosmetics")) / 2, 8, 0xFFFFFFFF);
		String sub = "Only you can see these";
		ctx.drawString(font, sub, (width - font.width(sub)) / 2, 20, 0xFF888888);

		drawTabs(ctx, mouseX, mouseY);
		drawLeftNotes(ctx);
		drawCards(ctx, mouseX, mouseY);
		drawPreview(ctx);
	}

	private void drawTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		Cosmetics.Category[] tabs = Cosmetics.Category.values();
		for (int i = 0; i < tabs.length; i++) {
			int x = left();
			int y = TOP + i * (TAB_H + 2);
			boolean on = tabs[i] == tab;
			boolean hover = inside(mouseX, mouseY, x, y, TAB_W, TAB_H);
			ctx.fill(x, y, x + TAB_W, y + TAB_H, on ? 0xCC2A2A44 : (hover ? 0xAA1E1E2C : 0x99101018));
			if (on) ctx.fill(x, y, x + 2, y + TAB_H, 0xFFFF7AA8);
			ctx.drawString(font, tabs[i].label, x + 8, y + 6, on ? 0xFFFFFFFF : 0xFFAAAAAA);
		}
	}

	private void drawLeftNotes(GuiGraphicsExtractor ctx) {
		int ly = TOP + Cosmetics.Category.values().length * (TAB_H + 2) + 8;
		if (tab == Cosmetics.Category.CAPE) {
			String[] lines = {".png .jpg .gif", "go in the folder,", "then press Reload"};
			for (int i = 0; i < lines.length; i++) {
				ctx.drawString(font, lines[i], left(), ly + 50 + i * 10, 0xFF8A8A8A);
			}
			if (!status.isEmpty()) {
				ctx.drawString(font, font.plainSubstrByWidth(status, TAB_W + 40), left(), ly + 84, 0xFF7BE06A);
			}
		} else if (tab == Cosmetics.Category.TOOL) {
			String[] lines = {"Hold a sword,", "pickaxe, axe,", "shovel or hoe", "to see a skin."};
			for (int i = 0; i < lines.length; i++) {
				ctx.drawString(font, lines[i], left(), ly + i * 10, 0xFF8A8A8A);
			}
		}
	}

	private void drawCards(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		List<Cosmetics.Def> items = items();
		String current = Cosmetics.selectedId(tab);

		ctx.enableScissor(cardsX(), TOP, cardsX() + CARDS_W, TOP + viewH());
		for (int i = 0; i <= items.size(); i++) {
			int x = cardX(i);
			int y = cardY(i);
			if (y + CARD_H < TOP || y > TOP + viewH()) continue;

			Cosmetics.Def d = i == 0 ? null : items.get(i - 1);
			boolean equipped = d == null ? current == null : d.id().equals(current);
			boolean hover = inside(mouseX, mouseY, x, y, CARD_W, CARD_H) && inside(mouseX, mouseY, cardsX(), TOP, CARDS_W, viewH());

			ctx.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xCC20202E : 0xAA101018);
			int border = equipped ? (d == null ? 0xFF888888 : d.accent()) : 0x55FFFFFF;
			outline(ctx, x, y, x + CARD_W, y + CARD_H, border);

			if (d == null) {
				ctx.drawString(font, "None", x + 8, y + 8, 0xFFFFFFFF);
				ctx.drawString(font, equipped ? "Equipped" : "Take it off", x + 8, y + 30,
						equipped ? 0xFF7BE06A : 0xFF8A8A8A);
			} else {
				ctx.fill(x + 6, y + 6, x + 26, y + 26, 0xFF1A1A22);
				if (d.icon() != null) {
					drawIcon(ctx, d.icon(), x + 8, y + 8);
				} else {
					ctx.fill(x + 8, y + 8, x + 24, y + 24, d.accent());
				}
				outline(ctx, x + 6, y + 6, x + 26, y + 26, 0xAA000000);
				String name = font.plainSubstrByWidth(d.name(), CARD_W - 34);
				ctx.drawString(font, name, x + 30, y + 12, 0xFFFFFFFF);
				ctx.drawString(font, equipped ? "Equipped" : "Click to equip", x + 8, y + 31,
						equipped ? 0xFF7BE06A : 0xFF8A8A8A);
			}
		}
		ctx.disableScissor();

		if (maxScroll() > 0) {
			int bx = cardsX() + CARDS_W + 2;
			ctx.fill(bx, TOP, bx + 3, TOP + viewH(), 0x66000000);
			int thumbH = Math.max(14, viewH() * viewH() / contentH());
			int thumbY = TOP + (int) ((viewH() - thumbH) * (scroll / (float) maxScroll()));
			ctx.fill(bx, thumbY, bx + 3, thumbY + thumbH, 0xFFAAAAAA);
		}
	}

	/** Draws a 16x16 ARGB picture with filled rectangles (one per run of the same colour). */
	private static void drawIcon(GuiGraphicsExtractor ctx, int[] argb, int x, int y) {
		for (int row = 0; row < 16; row++) {
			int col = 0;
			while (col < 16) {
				int c = argb[row * 16 + col];
				int end = col + 1;
				while (end < 16 && argb[row * 16 + end] == c) end++;
				if ((c >>> 24) > 0) ctx.fill(x + col, y + row, x + end, y + row + 1, c);
				col = end;
			}
		}
	}

	private void drawPreview(GuiGraphicsExtractor ctx) {
		int px = previewX();
		int py = TOP;
		int pw = PREVIEW_W;
		int ph = previewH();
		int mh = modelH();
		ctx.fill(px, py, px + pw, py + ph, 0x99101018);
		outline(ctx, px, py, px + pw, py + ph, 0x55FFFFFF);

		Cosmetics.Def tintable = tintable();
		if (tintable != null) drawSwatches(ctx, tintable, px, py + mh, pw);

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			String msg = "Join a world to preview";
			ctx.drawString(font, msg, px + (pw - font.width(msg)) / 2, py + mh / 2, 0xFF9A9A9A);
			return;
		}

		float cx = px + pw / 2f;
		int size = (int) (mh * 0.4f);
		PreviewSpin.radians = angle;
		PreviewSpin.active = true;
		try {
			// the mouse position is fixed in the middle so the player looks straight ahead; turning is done by the angle
			InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, px + 2, py + 2, px + pw - 2, py + mh - 2, size, 0.0625f,
					cx, py + mh * 0.35f, mc.player);
		} finally {
			PreviewSpin.active = false;
		}
		String hint = "Drag to turn";
		ctx.drawString(font, hint, px + (pw - font.width(hint)) / 2, py + mh - 11, 0xFF777777);
	}

	private void drawSwatches(GuiGraphicsExtractor ctx, Cosmetics.Def def, int px, int y, int pw) {
		ctx.fill(px + 1, y, px + pw - 1, y + 1, 0x33FFFFFF);
		int chosen = Cosmetics.colorIndex(tab);
		String label = "Color: " + Cosmetics.COLOR_NAMES[Math.max(0, Math.min(chosen, Cosmetics.COLOR_NAMES.length - 1))];
		ctx.drawString(font, label, px + 7, y + 3, 0xFFCCCCCC);
		for (int i = 0; i < Cosmetics.COLOR_NAMES.length; i++) {
			int x = swatchX(i);
			int sy = swatchY(i);
			if (i == Cosmetics.RAINBOW) {
				int[] bars = {0xFFFF4D4D, 0xFFFF9E3D, 0xFFFFE94D, 0xFF5FE06A, 0xFF4DC3FF, 0xFFA066FF};
				for (int k = 0; k < bars.length; k++) ctx.fill(x + k * 3, sy, x + k * 3 + 3, sy + SWATCH, bars[k]);
			} else {
				ctx.fill(x, sy, x + SWATCH, sy + SWATCH, Cosmetics.swatchColor(def, i));
			}
			if (i == 0) ctx.fill(x + 6, sy + 6, x + 10, sy + 10, 0xCC000000);   // "Original" has a dot
			outline(ctx, x - 1, sy - 1, x + SWATCH + 1, sy + SWATCH + 1, i == chosen ? 0xFFFFFFFF : 0x88000000);
		}
	}

	private static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}
}
