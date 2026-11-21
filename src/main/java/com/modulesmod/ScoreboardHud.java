package com.modulesmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Replaces the vanilla sidebar scoreboard with our own drawing code so every
 * Scoreboard setting can be applied. When the module is off, vanilla draws as usual.
 */
public final class ScoreboardHud {
	// index matches Modules.SB_FONT options; null = vanilla default font
	private static final Identifier[] FONTS = {
			null,
			Identifier.fromNamespaceAndPath("minecraft", "uniform"),
			Identifier.fromNamespaceAndPath("minecraft", "alt"),
			Identifier.fromNamespaceAndPath("minecraft", "illageralt")
	};

	private record Line(Component name, Component score) {}

	private ScoreboardHud() {}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (ctx, tickCounter) -> {
			if (!Modules.SCOREBOARD.isEnabled()) {
				vanilla.render(ctx, tickCounter);
				return;
			}
			render(ctx);
		});
	}

	private static void render(GuiGraphics ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		Scoreboard sb = mc.level.getScoreboard();

		// same objective choice as vanilla: team colour slot first, then the sidebar
		Objective obj = null;
		PlayerTeam playerTeam = sb.getPlayersTeam(mc.player.getScoreboardName());
		if (playerTeam != null) {
			try {
				DisplaySlot slot = DisplaySlot.valueOf(
						"TEAM_" + playerTeam.getColor().getName().toUpperCase(Locale.ROOT));
				obj = sb.getDisplayObjective(slot);
			} catch (IllegalArgumentException ignored) {
				// team has no colour slot (e.g. RESET) -> fall back to the sidebar
			}
		}
		if (obj == null) obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return;

		// ---- settings ----
		int fontIdx = Modules.SB_FONT.index();
		boolean bold = Modules.SB_BOLD.bool;
		boolean italic = Modules.SB_ITALIC.bool;
		boolean shadow = Modules.SB_SHADOW.bool;
		boolean hideTitle = Modules.SB_HIDE_TITLE.bool;
		boolean hideNumbers = Modules.SB_HIDE_NUMBERS.bool;
		float scale = (float) Modules.SB_SCALE.num;
		int textColor = parseColor(Modules.SB_COLOR.str);
		Set<Integer> hiddenRows = parseRows(Modules.SB_HIDDEN_ROWS.str);
		List<String> hiddenWords = parseWords(Modules.SB_HIDDEN_TEXT.str);

		int alpha = (int) Math.round(Math.max(0, Math.min(100, Modules.SB_OPACITY.num)) / 100.0 * 255.0);
		int titleAlpha = Math.min(255, (int) Math.round(alpha * 1.34));
		int rowBg = alpha << 24;
		int titleBg = titleAlpha << 24;

		// ---- collect rows (vanilla order, max 15) ----
		List<PlayerScoreEntry> all = new ArrayList<>();
		for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
			if (!e.isHidden()) all.add(e);
		}
		all.sort((a, b) -> {
			int c = Integer.compare(b.value(), a.value());
			return c != 0 ? c : String.CASE_INSENSITIVE_ORDER.compare(a.owner(), b.owner());
		});
		if (all.size() > 15) all = new ArrayList<>(all.subList(0, 15));

		NumberFormat numberFormat = obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			if (hiddenRows.contains(i + 1)) continue;
			PlayerScoreEntry e = all.get(i);
			Component name = PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName());
			String plain = name.getString().toLowerCase(Locale.ROOT);
			boolean skip = false;
			for (String w : hiddenWords) {
				if (plain.contains(w)) {
					skip = true;
					break;
				}
			}
			if (skip) continue;
			Component score = hideNumbers ? Component.empty() : e.formatValue(numberFormat);
			lines.add(new Line(style(name, fontIdx, bold, italic), style(score, fontIdx, bold, italic)));
		}

		if (lines.isEmpty() && hideTitle) return;

		Font tr = mc.font;
		Component title = style(obj.getDisplayName(), fontIdx, bold, italic);
		int spaceW = tr.width(style(Component.literal(": "), fontIdx, bold, italic));

		int maxW = hideTitle ? 0 : tr.width(title);
		for (Line l : lines) {
			int w = tr.width(l.name());
			int sw = tr.width(l.score());
			if (sw > 0) w += spaceW + sw;
			maxW = Math.max(maxW, w);
		}

		// ---- layout (vanilla placement: right edge, vertically centered) ----
		final int lineH = 9;
		int rowsH = lines.size() * lineH;
		int screenW = ctx.guiWidth();
		int screenH = ctx.guiHeight();
		int bottom = screenH / 2 + rowsH / 3;
		int rowsTop = bottom - rowsH;
		int right = screenW - 1;
		int left = right - maxW - 4;
		int boardTop = hideTitle ? rowsTop - 1 : rowsTop - 10;
		int centerY = (boardTop + bottom) / 2;

		ctx.pose().pushMatrix();
		ctx.pose().translate((float) right, (float) centerY);
		ctx.pose().scale(scale, scale);
		ctx.pose().translate((float) -right, (float) -centerY);

		if (!hideTitle) {
			ctx.fill(left, rowsTop - 10, right, rowsTop - 1, titleBg);
		}
		ctx.fill(left, rowsTop - 1, right, bottom, rowBg);

		if (!hideTitle) {
			int tx = left + 2 + (maxW - tr.width(title)) / 2;
			ctx.drawString(tr, title, tx, rowsTop - 9, textColor, shadow);
		}

		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			int y = rowsTop + i * lineH;
			ctx.drawString(tr, l.name(), left + 2, y, textColor, shadow);
			int sw = tr.width(l.score());
			if (sw > 0) {
				ctx.drawString(tr, l.score(), right - 2 - sw, y, textColor, shadow);
			}
		}

		ctx.pose().popMatrix();
	}

	/** Applies font / bold / italic on top of whatever styling the server already set. */
	private static Component style(Component t, int fontIdx, boolean bold, boolean italic) {
		if (fontIdx <= 0 && !bold && !italic) return t;
		return t.copy().withStyle(s -> {
			if (fontIdx > 0 && fontIdx < FONTS.length) s = s.withFont(new FontDescription.Resource(FONTS[fontIdx]));
			if (bold) s = s.withBold(true);
			if (italic) s = s.withItalic(true);
			return s;
		});
	}

	private static int parseColor(String hex) {
		try {
			if (hex != null && hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
		} catch (NumberFormatException ignored) {
		}
		return 0xFFFFFFFF;
	}

	/** "1,3,5-7" -> {1,3,5,6,7} */
	private static Set<Integer> parseRows(String csv) {
		Set<Integer> out = new HashSet<>();
		if (csv == null) return out;
		for (String part : csv.split("[,\\s]+")) {
			if (part.isEmpty()) continue;
			try {
				int dash = part.indexOf('-', 1);
				if (dash > 0) {
					int a = Integer.parseInt(part.substring(0, dash));
					int b = Integer.parseInt(part.substring(dash + 1));
					for (int i = a; i <= b && i - a < 50; i++) out.add(i);
				} else {
					out.add(Integer.parseInt(part));
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return out;
	}

	private static List<String> parseWords(String csv) {
		List<String> out = new ArrayList<>();
		if (csv == null) return out;
		for (String part : csv.split(",")) {
			String w = part.trim().toLowerCase(Locale.ROOT);
			if (!w.isEmpty()) out.add(w);
		}
		return out;
	}
}
