package com.modulesmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Replaces the vanilla player list (Tab) with our own drawing code so every Tab
 * setting can be applied. When the module is off, vanilla draws as usual.
 */
public final class TabHud {
	/** Filled by PlayerListHudMixin when the server sends a header / footer. */
	public static volatile Component header;
	public static volatile Component footer;

	private record Cell(PlayerInfo entry, Component name, boolean own) {}

	private TabHud() {}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, vanilla -> (ctx, tickCounter) -> {
			if (!Modules.TAB.isEnabled()) {
				vanilla.extractRenderState(ctx, tickCounter);
				return;
			}
			render(ctx);
		});
	}

	private static void render(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		ClientPacketListener nh = mc.getConnection();
		if (nh == null) return;
		if (!mc.options.keyPlayerList.isDown()) return;

		// same "when to show" rule as vanilla
		Collection<PlayerInfo> listed = nh.getListedOnlinePlayers();
		Objective listObjective = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST);
		if (mc.isLocalServer() && listed.size() <= 1 && listObjective == null) return;

		Font tr = mc.font;
		PlayerTabOverlay vanillaHud = mc.gui.getTabList();

		// ---- settings ----
		int textColor = 0xFF000000 | HudUtil.rgb(Modules.T_TEXT_COLOR.str, 0xFFFFFF);
		int bgRgb = HudUtil.rgb(Modules.T_BG_COLOR.str, 0x000000);
		int bg = (HudUtil.alpha(Modules.T_OPACITY.num) << 24) | bgRgb;
		boolean rowBg = Modules.T_ROW_BG.bool;
		boolean shadow = Modules.T_SHADOW.bool;
		boolean heads = Modules.T_HEADS.bool;
		int pingStyle = Modules.T_PING.index(); // 0 off, 1 number, 2 bars
		int maxPlayers = Math.max(1, (int) Modules.T_MAX.num);
		float userScale = (float) Modules.T_SCALE.num;

		boolean ownOn = Modules.T_OWN.bool;
		int ownRgb = HudUtil.rgb(Modules.T_OWN_COLOR.str, 0xFFFF55);
		boolean ownBold = Modules.T_OWN_BOLD.bool;
		boolean ownUnderline = Modules.T_OWN_UL.bool;
		boolean ownRowBg = Modules.T_OWN_BG.bool;

		int[] pingColors = {
				0xFF000000 | HudUtil.rgb(Modules.T_PING_1.str, 0x55FF55),
				0xFF000000 | HudUtil.rgb(Modules.T_PING_2.str, 0xAAFF55),
				0xFF000000 | HudUtil.rgb(Modules.T_PING_3.str, 0xFFFF55),
				0xFF000000 | HudUtil.rgb(Modules.T_PING_4.str, 0xFFAA00),
				0xFF000000 | HudUtil.rgb(Modules.T_PING_5.str, 0xFF5555)
		};

		// ---- players: spectators last, then team, then name (like vanilla) ----
		List<PlayerInfo> entries = new ArrayList<>(listed);
		entries.sort((a, b) -> {
			int c = Integer.compare(a.getGameMode() == GameType.SPECTATOR ? 1 : 0,
					b.getGameMode() == GameType.SPECTATOR ? 1 : 0);
			if (c != 0) return c;
			PlayerTeam ta = a.getTeam();
			PlayerTeam tb = b.getTeam();
			c = (ta == null ? "" : ta.getName()).compareTo(tb == null ? "" : tb.getName());
			if (c != 0) return c;
			return String.CASE_INSENSITIVE_ORDER.compare(
					vanillaHud.getNameForDisplay(a).getString(), vanillaHud.getNameForDisplay(b).getString());
		});
		if (entries.size() > maxPlayers) entries = new ArrayList<>(entries.subList(0, maxPlayers));

		PlayerInfo ownEntry = nh.getPlayerInfo(mc.player.getUUID());

		List<Cell> cells = new ArrayList<>();
		int maxNameW = 0;
		int pingW = pingStyle == 2 ? 14 : 0;
		for (PlayerInfo e : entries) {
			boolean own = ownOn && e == ownEntry;
			Component name = vanillaHud.getNameForDisplay(e);
			if (own) {
				Style st = Style.EMPTY.withColor(ownRgb);
				if (ownBold) st = st.withBold(true);
				if (ownUnderline) st = st.withUnderlined(true);
				name = Component.literal(name.getString()).setStyle(st);
			}
			cells.add(new Cell(e, name, own));
			maxNameW = Math.max(maxNameW, tr.width(name));
			if (pingStyle == 1) pingW = Math.max(pingW, tr.width(pingText(e.getLatency())));
		}

		// ---- header / footer ----
		int screenW = ctx.guiWidth();
		Component h = header;
		Component f = footer;
		List<FormattedCharSequence> headerLines = (Modules.T_HIDE_HEADER.bool || h == null)
				? List.of() : tr.split(h, screenW - 50);
		List<FormattedCharSequence> footerLines = (Modules.T_HIDE_FOOTER.bool || f == null)
				? List.of() : tr.split(f, screenW - 50);
		int headerW = 0;
		for (FormattedCharSequence l : headerLines) headerW = Math.max(headerW, tr.width(l));
		int footerW = 0;
		for (FormattedCharSequence l : footerLines) footerW = Math.max(footerW, tr.width(l));

		if (cells.isEmpty() && headerLines.isEmpty() && footerLines.isEmpty()) return;

		// ---- grid: up to 20 rows per column, like vanilla ----
		int n = cells.size();
		int rows = Math.max(n, 1);
		int cols = 1;
		while (rows > 20) {
			cols++;
			rows = (n + cols - 1) / cols;
		}
		int headW = heads ? 9 : 0;
		int cellW = headW + maxNameW + (pingStyle == 0 ? 1 : 4 + pingW) + 1;
		int listW = n == 0 ? 0 : cols * cellW + (cols - 1) * 5;

		int blockW = Math.max(listW, Math.max(headerW, footerW)) + 2;
		float scale = Math.min(userScale, (screenW - 10) / (float) blockW); // never wider than the screen
		int cx = screenW / 2;
		int top = 10;

		ctx.pose().pushMatrix();
		ctx.pose().translate((float) cx, (float) top);
		ctx.pose().scale(scale, scale);
		ctx.pose().translate((float) -cx, (float) -top);

		int y = top;

		// header
		if (!headerLines.isEmpty()) {
			int hh = headerLines.size() * 9;
			ctx.fill(cx - headerW / 2 - 1, y - 1, cx + headerW / 2 + 1, y + hh, bg);
			for (int i = 0; i < headerLines.size(); i++) {
				FormattedCharSequence l = headerLines.get(i);
				ctx.drawString(tr, l, cx - tr.width(l) / 2, y + i * 9, textColor, shadow);
			}
			y += hh + 2;
		}

		// player grid
		if (n > 0) {
			int x0 = cx - listW / 2;
			ctx.fill(x0 - 1, y - 1, x0 + listW + 1, y + rows * 9, bg);

			for (int i = 0; i < n; i++) {
				Cell cell = cells.get(i);
				int col = i / rows;
				int row = i % rows;
				int cellX = x0 + col * (cellW + 5);
				int cellY = y + row * 9;

				if (cell.own() && ownRowBg) {
					ctx.fill(cellX, cellY, cellX + cellW, cellY + 8, 0x50000000 | ownRgb);
				} else if (rowBg) {
					ctx.fill(cellX, cellY, cellX + cellW, cellY + 8, 0x20FFFFFF);
				}

				int nameX = cellX;
				if (heads) {
					PlayerFaceRenderer.draw(ctx, cell.entry().getSkin(), cellX, cellY, 8);
					nameX += 9;
				}
				ctx.drawString(tr, cell.name(), nameX, cellY, textColor, shadow);

				if (pingStyle != 0) {
					int ping = cell.entry().getLatency();
					int pingX = cellX + cellW - 1 - pingW;
					if (pingStyle == 2) {
						drawBars(ctx, pingX, cellY, ping, pingColors);
					} else {
						String txt = pingText(ping);
						int color = ping < 0 ? 0xFF808080 : pingColor(ping, pingColors);
						ctx.drawString(tr, txt, pingX + pingW - tr.width(txt), cellY, color, shadow);
					}
				}
			}
			y += rows * 9 + 2;
		}

		// footer
		if (!footerLines.isEmpty()) {
			int fh = footerLines.size() * 9;
			ctx.fill(cx - footerW / 2 - 1, y - 1, cx + footerW / 2 + 1, y + fh, bg);
			for (int i = 0; i < footerLines.size(); i++) {
				FormattedCharSequence l = footerLines.get(i);
				ctx.drawString(tr, l, cx - tr.width(l) / 2, y + i * 9, textColor, shadow);
			}
		}

		ctx.pose().popMatrix();
	}

	private static String pingText(int ping) {
		return ping < 0 ? "?" : ping + "ms";
	}

	private static int pingColor(int ping, int[] c) {
		if (ping <= 50) return c[0];
		if (ping <= 100) return c[1];
		if (ping <= 150) return c[2];
		if (ping <= 250) return c[3];
		return c[4];
	}

	private static void drawBars(GuiGraphicsExtractor ctx, int x, int y, int ping, int[] colors) {
		int filled = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
		int color = ping < 0 ? 0xFF808080 : pingColor(ping, colors);
		int[] heights = {2, 3, 5, 6, 8};
		for (int i = 0; i < 5; i++) {
			int bx = x + i * 3;
			ctx.fill(bx, y + 8 - heights[i], bx + 2, y + 8, i < filled ? color : 0xFF303030);
		}
	}
}
