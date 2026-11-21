package com.modulesmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Armor Status HUD. Two styles: "Vanilla" (plain item icons with the normal durability bar) and
 * "Modern" (dark cards with a colour accent, a slim bar and the durability as text).
 */
public final class ArmorHud {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private record Cell(ItemStack stack, boolean damageable, float fraction, String text, int color) {}

	private record Place(int x, int y, int w, int h) {}

	private record Layout(List<Cell> cells, List<Place> places, int w, int h, boolean modern, boolean vertical,
						  boolean showBar, boolean showBg, int bgColor, boolean shadow) {}

	private ArmorHud() {}

	/** Sample armor for the HUD editor when you are not wearing any. */
	private static ItemStack[] sample() {
		ItemStack[] out = {
				new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
				new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS)
		};
		int[] damage = {60, 250, 400, 440};
		for (int i = 0; i < out.length; i++) out[i].setDamageValue(damage[i]);
		return out;
	}

	/** Width and height of the panel in GUI pixels (before the Size setting), or {0, 0} if nothing to show. */
	static int[] size(Minecraft mc, boolean preview) {
		Layout l = layout(mc, preview);
		return l == null ? new int[]{0, 0} : new int[]{l.w(), l.h()};
	}

	private static Layout layout(Minecraft mc, boolean preview) {
		Font tr = mc.font;

		boolean modern = Modules.AH_STYLE.index() == 0;
		boolean vertical = Modules.AH_LAYOUT.index() == 0;
		int display = Modules.AH_DISPLAY.index(); // 0 percent, 1 number, 2 both, 3 none
		boolean byDurability = Modules.AH_COLOR_MODE.bool;
		int baseRgb = HudUtil.rgb(Modules.AH_TEXT_COLOR.str, 0xFFFFFF);
		boolean showBar = Modules.AH_BAR.bool;
		boolean showBg = Modules.AH_BG.bool;
		int bgColor = HudUtil.alpha(Modules.AH_BG_OPACITY.num) << 24;
		boolean shadow = Modules.AH_SHADOW.bool;
		boolean hideEmpty = Modules.AH_HIDE_EMPTY.bool;

		// ---- the four pieces (helmet at the top, boots at the bottom) ----
		ItemStack[] stacks = new ItemStack[SLOTS.length];
		boolean anyWorn = false;
		for (int i = 0; i < SLOTS.length; i++) {
			stacks[i] = mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(SLOTS[i]);
			if (!stacks[i].isEmpty()) anyWorn = true;
		}
		if (!anyWorn && preview) stacks = sample();
		else if (mc.player == null) return null;

		List<Cell> cells = new ArrayList<>();
		boolean anyText = false;
		for (ItemStack stack : stacks) {
			if (stack.isEmpty() && hideEmpty) continue;

			boolean damageable = !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 0;
			float fraction = 1f;
			String text = "";
			if (damageable) {
				int max = stack.getMaxDamage();
				int left = Math.max(0, max - stack.getDamageValue());
				fraction = Math.max(0f, Math.min(1f, left / (float) max));
				int pct = Math.round(fraction * 100f);
				text = switch (display) {
					case 0 -> pct + "%";
					case 1 -> String.valueOf(left);
					case 2 -> left + " (" + pct + "%)";
					default -> "";
				};
			}
			int rgb = byDurability && damageable ? durabilityRgb(fraction) : baseRgb;
			if (!text.isEmpty()) anyText = true;
			cells.add(new Cell(stack, damageable, fraction, text, 0xFF000000 | rgb));
		}
		if (cells.isEmpty()) return null;

		// ---- where every card goes (relative to the panel's top-left corner) ----
		List<Place> places = new ArrayList<>();
		int gap = 2;
		int panelW;
		int panelH;
		if (vertical) {
			int maxW = 0;
			int[] widths = new int[cells.size()];
			for (int i = 0; i < cells.size(); i++) {
				int textW = cells.get(i).text().isEmpty() ? 0 : tr.width(cells.get(i).text());
				widths[i] = modern ? 3 + 16 + (textW > 0 ? 4 + textW : 0) + 3 : 16 + (textW > 0 ? 4 + textW : 0);
				maxW = Math.max(maxW, widths[i]);
			}
			int cellH = modern ? (showBar ? 25 : 22) : 16;
			int y = 0;
			for (int i = 0; i < cells.size(); i++) {
				places.add(new Place(0, y, modern ? maxW : widths[i], cellH));
				y += cellH + gap;
			}
			panelW = maxW;
			panelH = y - gap;
		} else {
			int cellH = modern ? 22 + (anyText ? 11 : 0) + (showBar ? 5 : 0) : 16 + (anyText ? 10 : 0);
			int x = 0;
			for (Cell c : cells) {
				int textW = c.text().isEmpty() ? 0 : tr.width(c.text());
				int w = modern ? Math.max(22, textW + 6) : Math.max(16, textW);
				places.add(new Place(x, 0, w, cellH));
				x += w + gap;
			}
			panelW = x - gap;
			panelH = cellH;
		}
		return new Layout(cells, places, panelW, panelH, modern, vertical, showBar, showBg, bgColor, shadow);
	}

	/** Draws the panel with its top-left corner at (0, 0); the movable-HUD system has already moved and scaled. */
	static void draw(GuiGraphics ctx, Minecraft mc, boolean preview) {
		Layout l = layout(mc, preview);
		if (l == null) return;
		Font tr = mc.font;
		for (int i = 0; i < l.cells().size(); i++) {
			Cell c = l.cells().get(i);
			Place p = l.places().get(i);
			if (l.modern()) {
				drawModern(ctx, tr, c, p, l.vertical(), l.showBar(), l.showBg(), l.bgColor(), l.shadow());
			} else {
				drawVanilla(ctx, tr, c, p, l.vertical(), l.shadow());
			}
		}
	}

	private static void drawModern(GuiGraphics ctx, Font tr, Cell c, Place p, boolean vertical,
								   boolean showBar, boolean showBg, int bgColor, boolean shadow) {
		if (showBg) ctx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), bgColor);

		if (vertical) {
			// colour accent on the left edge
			ctx.fill(p.x(), p.y(), p.x() + 2, p.y() + p.h(), c.color());
			ctx.renderItem(c.stack(), p.x() + 3, p.y() + 3);
			if (!c.text().isEmpty()) {
				ctx.drawString(tr, c.text(), p.x() + 3 + 16 + 4, p.y() + 7, c.color(), shadow);
			}
			if (showBar && c.damageable()) {
				drawBar(ctx, p.x() + 3, p.y() + 20, p.w() - 6, c);
			}
		} else {
			ctx.renderItem(c.stack(), p.x() + (p.w() - 16) / 2, p.y() + 3);
			int y = p.y() + 22;
			if (!c.text().isEmpty()) {
				ctx.drawString(tr, c.text(), p.x() + (p.w() - tr.width(c.text())) / 2, y, c.color(), shadow);
			}
			if (showBar && c.damageable()) {
				int barY = p.y() + p.h() - 5;
				drawBar(ctx, p.x() + 3, barY, p.w() - 6, c);
			}
		}
	}

	private static void drawVanilla(GuiGraphics ctx, Font tr, Cell c, Place p, boolean vertical, boolean shadow) {
		if (vertical) {
			ctx.renderItem(c.stack(), p.x(), p.y());
			ctx.renderItemDecorations(tr, c.stack(), p.x(), p.y()); // vanilla durability bar
			if (!c.text().isEmpty()) {
				ctx.drawString(tr, c.text(), p.x() + 16 + 4, p.y() + 4, c.color(), shadow);
			}
		} else {
			int ix = p.x() + (p.w() - 16) / 2;
			ctx.renderItem(c.stack(), ix, p.y());
			ctx.renderItemDecorations(tr, c.stack(), ix, p.y());
			if (!c.text().isEmpty()) {
				ctx.drawString(tr, c.text(), p.x() + (p.w() - tr.width(c.text())) / 2, p.y() + 18, c.color(), shadow);
			}
		}
	}

	private static void drawBar(GuiGraphics ctx, int x, int y, int width, Cell c) {
		ctx.fill(x, y, x + width, y + 2, 0xFF202020);
		int filled = Math.max(c.fraction() > 0 ? 1 : 0, Math.round(width * c.fraction()));
		ctx.fill(x, y, x + filled, y + 2, c.color());
	}

	private static int durabilityRgb(float fraction) {
		if (fraction >= 0.60f) return HudUtil.rgb(Modules.AH_C_HIGH.str, 0x55FF55);
		if (fraction >= 0.30f) return HudUtil.rgb(Modules.AH_C_MID.str, 0xFFFF55);
		if (fraction >= 0.10f) return HudUtil.rgb(Modules.AH_C_LOW.str, 0xFFAA00);
		return HudUtil.rgb(Modules.AH_C_CRIT.str, 0xFF5555);
	}
}
