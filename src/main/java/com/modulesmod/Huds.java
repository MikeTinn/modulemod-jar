package com.modulesmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Locale;

/**
 * The movable HUDs (FPS, Reach, Ping, Inventory, Armor, Lyrics). Each one stores its position as a percentage of the
 * screen, so it stays put when you change the GUI scale or window size. The HUD editor changes those
 * percentages by dragging.
 */
public final class Huds {
	private Huds() {}

	// =====================================================================================
	// base classes
	// =====================================================================================
	public abstract static class Widget {
		public final String name;
		public final Module module;
		final Setting x;
		final Setting y;
		final Setting scale;

		Widget(String name, Module module, Setting x, Setting y, Setting scale) {
			this.name = name;
			this.module = module;
			this.x = x;
			this.y = y;
			this.scale = scale;
		}

		/** Whether this HUD is drawn in the game right now. */
		public boolean active() {
			return module.isEnabled();
		}

		abstract int width(Minecraft mc, boolean preview);

		abstract int height(Minecraft mc, boolean preview);

		/** Draws at (0, 0); the caller has already moved and scaled the drawing space. */
		abstract void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview);

		/** {x, y, width, height} on screen, after scaling and keeping it fully on screen. */
		public int[] bounds(int screenW, int screenH, Minecraft mc, boolean preview) {
			float s = (float) scale.num;
			int w = Math.round(width(mc, preview) * s);
			int h = Math.round(height(mc, preview) * s);
			int px = (int) Math.round(screenW * x.num / 100.0);
			int py = (int) Math.round(screenH * y.num / 100.0);
			px = Math.max(0, Math.min(Math.max(0, screenW - w), px));
			py = Math.max(0, Math.min(Math.max(0, screenH - h), py));
			return new int[]{px, py, w, h};
		}

		public void render(GuiGraphicsExtractor ctx, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			int[] b = bounds(ctx.guiWidth(), ctx.guiHeight(), mc, preview);
			float s = (float) scale.num;
			ctx.pose().pushMatrix();
			ctx.pose().translate((float) b[0], (float) b[1]);
			ctx.pose().scale(s, s);
			draw(ctx, mc, preview);
			ctx.pose().popMatrix();
		}

		/** Moves the HUD so its top-left corner is at the given screen position. */
		public void moveTo(int px, int py, int screenW, int screenH) {
			x.num = Math.round(Math.max(0, Math.min(100, px * 100.0 / screenW)) * 10.0) / 10.0;
			y.num = Math.round(Math.max(0, Math.min(100, py * 100.0 / screenH)) * 10.0) / 10.0;
		}

		public void resetPosition() {
			x.reset();
			y.reset();
		}
	}

	/** A HUD that is one line of text on a little card. */
	abstract static class TextWidget extends Widget {
		private final Setting bg;
		private final Setting bgOpacity;
		private final Setting shadow;

		TextWidget(String name, Module module, Setting x, Setting y, Setting scale,
				   Setting bg, Setting bgOpacity, Setting shadow) {
			super(name, module, x, y, scale);
			this.bg = bg;
			this.bgOpacity = bgOpacity;
			this.shadow = shadow;
		}

		abstract String text(Minecraft mc, boolean preview);

		abstract int color(Minecraft mc, boolean preview);

		@Override
		int width(Minecraft mc, boolean preview) {
			return mc.font.width(text(mc, preview)) + 8;
		}

		@Override
		int height(Minecraft mc, boolean preview) {
			return 15;
		}

		@Override
		void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
			String text = text(mc, preview);
			int w = mc.font.width(text) + 8;
			if (bg.bool) ctx.fill(0, 0, w, 15, HudUtil.alpha(bgOpacity.num) << 24);
			ctx.text(mc.font, text, 4, 3, 0xFF000000 | color(mc, preview), shadow.bool);
		}
	}

	// =====================================================================================
	// the four HUDs
	// =====================================================================================
	static final class Fps extends TextWidget {
		Fps() {
			super("FPS Display", Modules.FPS_DISPLAY, Modules.FH_X, Modules.FH_Y, Modules.FH_SCALE,
					Modules.FH_BG, Modules.FH_BG_OPACITY, Modules.FH_SHADOW);
		}

		@Override
		String text(Minecraft mc, boolean preview) {
			int fps = mc.getFps();
			return switch (Modules.FH_FORMAT.index()) {
				case 1 -> "FPS: " + fps;
				case 2 -> String.valueOf(fps);
				default -> fps + " FPS";
			};
		}

		@Override
		int color(Minecraft mc, boolean preview) {
			if (!Modules.FH_COLOR_BY.bool) return HudUtil.rgb(Modules.FH_COLOR.str, 0xFFFFFF);
			int fps = mc.getFps();
			if (fps >= 60) return HudUtil.rgb(Modules.FH_C_GOOD.str, 0x55FF55);
			if (fps >= 30) return HudUtil.rgb(Modules.FH_C_MID.str, 0xFFFF55);
			return HudUtil.rgb(Modules.FH_C_LOW.str, 0xFF5555);
		}
	}

	static final class Ping extends TextWidget {
		Ping() {
			super("Ping Display", Modules.PING_DISPLAY, Modules.PH_X, Modules.PH_Y, Modules.PH_SCALE,
					Modules.PH_BG, Modules.PH_BG_OPACITY, Modules.PH_SHADOW);
		}

		private static int latency(Minecraft mc) {
			if (mc.player == null || mc.getConnection() == null) return 0;
			PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			return entry == null ? 0 : Math.max(0, entry.getLatency());
		}

		@Override
		String text(Minecraft mc, boolean preview) {
			int ms = latency(mc);
			return switch (Modules.PH_FORMAT.index()) {
				case 1 -> "Ping: " + ms;
				case 2 -> String.valueOf(ms);
				default -> ms + " ms";
			};
		}

		@Override
		int color(Minecraft mc, boolean preview) {
			if (!Modules.PH_COLOR_BY.bool) return HudUtil.rgb(Modules.PH_COLOR.str, 0xFFFFFF);
			int ms = latency(mc);
			if (ms <= 60) return HudUtil.rgb(Modules.PH_C_GOOD.str, 0x55FF55);
			if (ms <= 150) return HudUtil.rgb(Modules.PH_C_MID.str, 0xFFFF55);
			return HudUtil.rgb(Modules.PH_C_BAD.str, 0xFF5555);
		}
	}

	static final class Reach extends TextWidget {
		Reach() {
			super("Reach Display", Modules.REACH_DISPLAY, Modules.RH_X, Modules.RH_Y, Modules.RH_SCALE,
					Modules.RH_BG, Modules.RH_BG_OPACITY, Modules.RH_SHADOW);
		}

		@Override
		String text(Minecraft mc, boolean preview) {
			String number;
			boolean has = lastReach >= 0;
			double timeout = Modules.RH_TIMEOUT.num;
			if (has && timeout > 0 && System.currentTimeMillis() - lastHitMs > timeout * 1000.0) has = false;
			if (preview && !has) {
				number = String.format(Locale.ROOT, "%." + (Modules.RH_DECIMALS.index() + 1) + "f", 3.0);
			} else if (!has) {
				number = "--";
			} else {
				number = String.format(Locale.ROOT, "%." + (Modules.RH_DECIMALS.index() + 1) + "f", lastReach);
			}
			return switch (Modules.RH_FORMAT.index()) {
				case 1 -> "Reach: " + number;
				case 2 -> number;
				default -> number + (number.equals("--") ? "" : " blocks");
			};
		}

		@Override
		int color(Minecraft mc, boolean preview) {
			return HudUtil.rgb(Modules.RH_COLOR.str, 0xFFFFFF);
		}
	}

	static final class Inventory extends Widget {
		Inventory() {
			super("Inventory HUD", Modules.INVENTORY_HUD, Modules.IH_X, Modules.IH_Y, Modules.IH_SCALE);
		}

		@Override
		int width(Minecraft mc, boolean preview) {
			return 9 * 18 + 4;
		}

		@Override
		int height(Minecraft mc, boolean preview) {
			return 3 * 18 + 4 + (Modules.IH_HOTBAR.bool ? 18 + 4 : 0);
		}

		@Override
		void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
			int w = width(mc, preview);
			int h = height(mc, preview);
			if (Modules.IH_BG.bool) ctx.fill(0, 0, w, h, HudUtil.alpha(Modules.IH_BG_OPACITY.num) << 24);
			if (mc.player == null) return;

			Font tr = mc.font;
			int outline = 0x66000000 | HudUtil.rgb(Modules.IH_OUTLINE_COLOR.str, 0xFFFFFF);
			boolean showOutline = Modules.IH_OUTLINE.bool;
			boolean counts = Modules.IH_COUNTS.bool;

			for (int i = 0; i < 27; i++) {
				drawSlot(ctx, tr, mc.player.getInventory().getItem(9 + i), 2 + (i % 9) * 18, 2 + (i / 9) * 18,
						showOutline, outline, counts);
			}
			if (Modules.IH_HOTBAR.bool) {
				int rowY = 2 + 3 * 18 + 4;
				for (int i = 0; i < 9; i++) {
					drawSlot(ctx, tr, mc.player.getInventory().getItem(i), 2 + i * 18, rowY, showOutline, outline, counts);
				}
			}
		}

		private static void drawSlot(GuiGraphicsExtractor ctx, Font tr, ItemStack stack, int x, int y,
									 boolean showOutline, int outline, boolean counts) {
			if (showOutline) {
				ctx.fill(x, y, x + 18, y + 1, outline);
				ctx.fill(x, y + 17, x + 18, y + 18, outline);
				ctx.fill(x, y, x + 1, y + 18, outline);
				ctx.fill(x + 17, y, x + 18, y + 18, outline);
			}
			if (stack.isEmpty()) return;
			ctx.item(stack, x + 1, y + 1);
			if (counts) ctx.itemDecorations(tr, stack, x + 1, y + 1);
		}
	}

	/** Armor pieces with their durability; the drawing lives in ArmorHud. */
	static final class Armor extends Widget {
		Armor() {
			super("Armor Status", Modules.ARMOR_STATUS, Modules.AH_X, Modules.AH_Y, Modules.AH_SCALE);
		}

		@Override
		int width(Minecraft mc, boolean preview) {
			return ArmorHud.size(mc, preview)[0];
		}

		@Override
		int height(Minecraft mc, boolean preview) {
			return ArmorHud.size(mc, preview)[1];
		}

		@Override
		void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
			ArmorHud.draw(ctx, mc, preview);
		}
	}

	/**
	 * Lyrics as a movable card: the line being sung (already sung words or letters are bright) and the next
	 * line. When a line ends the rows slide up: the old line fades out upward, the next line grows into the
	 * main row and a new "next" line fades in underneath.
	 */
	static final class LyricsHud extends Widget {
		LyricsHud() {
			super("Lyrics HUD", Modules.LYRICS, Modules.LY_X, Modules.LY_Y, Modules.LY_SCALE);
		}

		@Override
		public boolean active() {
			return module.isEnabled() && Modules.LY_DISPLAY.index() == 1;
		}

		@Override
		int width(Minecraft mc, boolean preview) {
			return (int) Math.round(Modules.LY_WIDTH.num);
		}

		@Override
		int height(Minecraft mc, boolean preview) {
			return 15 + (Modules.LY_NEXT.bool ? 12 : 0);
		}

		private static float lerp(float a, float b, float t) {
			return a + (b - a) * t;
		}

		private static int argb(float alpha, int rgb) {
			int a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
			return (a << 24) | rgb;
		}

		@Override
		void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean preview) {
			LyricsController.HudLine line = LyricsController.hudLine();
			if (line == null) {
				if (!preview) return;
				line = new LyricsController.HudLine("Lyrics will", "", 0f, " show up here",
						"the next line shows here", "", 1f);
			}
			int w = width(mc, preview);
			int h = height(mc, preview);
			Font tr = mc.font;
			if (Modules.LY_BG.bool) ctx.fill(0, 0, w, h, HudUtil.alpha(Modules.LY_BG_OPACITY.num) << 24);

			int font = Modules.LY_FONT.index();
			int rgb = HudUtil.rgb(Modules.LY_COLOR.str, 0xFFFFFF);
			boolean shadow = Modules.LY_SHADOW.bool;
			boolean showNext = Modules.LY_NEXT.bool;

			float t = line.anim();
			float e = 1f - (1f - t) * (1f - t) * (1f - t); // ease out

			// the line that just ended slides up and fades
			if (t < 1f && !line.prev().isEmpty()) {
				drawRow(ctx, tr, LyricsFonts.apply(line.prev(), font), w, 3f - 9f * e, 1f, 1f - e, rgb, shadow);
			}

			// the main row: grows out of the "next" row
			float cy = showNext ? lerp(16f, 3f, e) : lerp(9f, 3f, e);
			float cs = showNext ? lerp(0.8f, 1f, e) : 1f;
			float ca = showNext ? lerp(0.4f, 1f, e) : e;
			drawCurrent(ctx, tr, line, font, w, cy, cs, ca, rgb, shadow);

			// the new "next" row fades in from below
			if (showNext && !line.next().isEmpty()) {
				drawRow(ctx, tr, LyricsFonts.apply(line.next(), font), w, lerp(23f, 16f, e), 0.8f,
						lerp(0f, 0.4f, e), rgb, shadow);
			}
		}

		private static void drawRow(GuiGraphicsExtractor ctx, Font tr, String text, int w, float y, float scale,
									float alpha, int rgb, boolean shadow) {
			if (text.isEmpty() || alpha * 255f < 8f) return;
			int tw = Math.max(1, tr.width(text));
			float f = Math.min(scale, (w - 8f) / tw);
			ctx.pose().pushMatrix();
			ctx.pose().translate((w - tw * f) / 2f, y);
			ctx.pose().scale(f, f);
			ctx.text(tr, text, 0, 0, argb(alpha, rgb), shadow);
			ctx.pose().popMatrix();
		}

		private static void drawCurrent(GuiGraphicsExtractor ctx, Font tr, LyricsController.HudLine line, int font,
										int w, float y, float scale, float alpha, int rgb, boolean shadow) {
			if (alpha * 255f < 8f) return;
			String sung = LyricsFonts.apply(line.sung(), font);
			String part = LyricsFonts.apply(line.partial(), font);
			String rest = LyricsFonts.apply(line.rest(), font);
			int sungW = tr.width(sung);
			int partW = tr.width(part);
			int fullW = Math.max(1, sungW + partW + tr.width(rest));
			float f = Math.min(scale, (w - 8f) / fullW);
			ctx.pose().pushMatrix();
			ctx.pose().translate((w - fullW * f) / 2f, y);
			ctx.pose().scale(f, f);
			if (!sung.isEmpty()) ctx.text(tr, sung, 0, 0, argb(alpha, rgb), shadow);
			if (!part.isEmpty()) {
				ctx.text(tr, part, sungW, 0, argb(alpha * (0.5f + 0.5f * line.partialFrac()), rgb), shadow);
			}
			if (!rest.isEmpty()) ctx.text(tr, rest, sungW + partW, 0, argb(alpha * 0.5f, rgb), shadow);
			ctx.pose().popMatrix();
		}
	}

	// =====================================================================================
	// registry + reach tracking
	// =====================================================================================
	public static final List<Widget> ALL = List.of(new Fps(), new Ping(), new Reach(), new Inventory(), new Armor(), new LyricsHud());

	private static volatile double lastReach = -1;
	private static volatile long lastHitMs = 0;

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ModulesMod.MOD_ID, "huds"), (ctx, tickCounter) -> {
					for (Widget w : ALL) {
						if (!w.active()) continue;
						try {
							w.render(ctx, false);
						} catch (Exception e) {
							Diagnostics.error("hud " + w.name, e);
						}
					}
				});

		// remember how far away the last thing you hit was
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()) {
				try {
					recordHit(player, entity);
				} catch (Exception e) {
					Diagnostics.error("reach", e);
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static void recordHit(Player player, Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player) return;

		Vec3 eye = player.getEyePosition();
		double distance;
		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target) {
			distance = eye.distanceTo(entityHit.getLocation());
		} else {
			// fall back to the closest point of the target's hitbox
			AABB b = target.getBoundingBox();
			double cx = Math.max(b.minX, Math.min(b.maxX, eye.x));
			double cy = Math.max(b.minY, Math.min(b.maxY, eye.y));
			double cz = Math.max(b.minZ, Math.min(b.maxZ, eye.z));
			distance = eye.distanceTo(new Vec3(cx, cy, cz));
		}
		lastReach = distance;
		lastHitMs = System.currentTimeMillis();
	}
}
