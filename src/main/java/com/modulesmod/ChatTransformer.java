package com.modulesmod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Everything the Chat module does to a message before it is added to the chat window:
 * heads, timestamps, emoji, name highlight, font, shadow, colour. The vanilla chat window
 * (scrolling, clicking, hovering) is left alone, so all of that keeps working.
 */
public final class ChatTransformer {
	// index matches Modules.CH_FONT options; null = vanilla default font
	private static final Identifier[] FONTS = {
			null,
			Identifier.fromNamespaceAndPath("minecraft", "uniform"),
			Identifier.fromNamespaceAndPath("minecraft", "alt"),
			Identifier.fromNamespaceAndPath("minecraft", "illageralt")
	};

	private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{1,16}");

	private ChatTransformer() {}

	/** Called by ChatHudMixin for every message that is added. Never throws. */
	public static Component transform(Component original) {
		if (!Modules.CHAT.isEnabled()) return original;
		try {
			return doTransform(original);
		} catch (Exception e) {
			ModulesMod.LOGGER.warn("Chat transform failed", e);
			return original;
		}
	}

	public static int maxLines(int vanilla) {
		if (!Modules.CHAT.isEnabled()) return vanilla;
		return Math.max(100, Math.min(100000, (int) Modules.CH_HISTORY.num));
	}

	public static double sizeMultiplier() {
		return Modules.CHAT.isEnabled() ? Modules.CH_SIZE.num : 1.0;
	}

	private static Component doTransform(Component original) {
		Diagnostics.chatTransform++;
		Minecraft mc = Minecraft.getInstance();
		String plain = original.getString();
		ChatState.record(original, plain);

		// ---- stacking: the same message again in a row becomes "message (x2)" ----
		int stackCount = 1;
		if (Modules.CH_STACK.bool && !plain.isBlank() && plain.equals(ChatState.lastPlain)) {
			stackCount = ChatState.lastCount + 1;
			ChatState.stackRemove = ChatState.lastVisibleLines;
		} else {
			ChatState.stackRemove = 0;
		}
		ChatState.lastPlain = plain;
		ChatState.lastCount = stackCount;

		// ---- who sent it (for heads and name highlight) ----
		PlayerInfo sender = null;
		PlayerInfo own = null;
		try {
			ClientPacketListener nh = mc.getConnection();
			if (Modules.CH_HEADS.bool || Modules.CH_MENTION.bool) sender = findSender(nh, plain);
			if (nh != null && mc.player != null) own = nh.getPlayerInfo(mc.player.getUUID());
		} catch (Exception e) {
			Diagnostics.error("chat sender", e);
		}
		boolean fromMe = sender != null && sender == own;

		// ---- emoji + name highlight (rebuilds the text but keeps every click / hover / colour) ----
		Component body = original;
		try {
			String me = mc.player == null ? null : mc.player.getName().getString();
			boolean emoji = Modules.CH_EMOJI_IN.bool;
			boolean mention = Modules.CH_MENTION.bool && me != null && !fromMe
					&& plain.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT));
			if (emoji || mention) {
				final int mentionRgb = HudUtil.rgb(Modules.CH_MENTION_COLOR.str, 0xFFFF55);
				final boolean mentionBold = Modules.CH_MENTION_BOLD.bool;
				final boolean faces = Modules.CH_FACES.bool;
				final String key = mention ? me : null;
				MutableComponent out = Component.empty();
				original.<Object>visit((style, str) -> {
					String s = emoji ? Emojis.replace(str, faces) : str;
					if (key == null) {
						out.append(Component.literal(s).setStyle(style));
					} else {
						appendHighlighted(out, style, s, key, mentionRgb, mentionBold);
					}
					return Optional.empty();
				}, Style.EMPTY);
				body = out;
			}
		} catch (Exception e) {
			Diagnostics.error("chat rebuild", e);
			body = original;
		}

		// ---- assemble: [time] [head] message (xN) ----
		MutableComponent result = Component.empty();
		try {
			if (Modules.CH_TIME.bool) result.append(timestamp());
		} catch (Exception e) {
			Diagnostics.error("chat timestamp", e);
		}
		try {
			if (Modules.CH_HEADS.bool && sender != null) {
				Component head = head(sender);
				result.append(head);
				result.append(Component.literal(" "));
			}
		} catch (Exception e) {
			Diagnostics.error("chat head", e);
		}
		result.append(body);
		if (stackCount > 1) {
			result.append(Component.literal(" (x" + stackCount + ")")
					.setStyle(Style.EMPTY.withColor(HudUtil.rgb(Modules.CH_STACK_COLOR.str, 0xAAAAAA))));
		}

		// ---- font / shadow / colour for the whole message ----
		try {
			final int fontIdx = Modules.CH_FONT.index();
			final boolean shadowOff = !Modules.CH_SHADOW.bool;
			final boolean colorOn = Modules.CH_COLOR_ON.bool;
			final int colorRgb = HudUtil.rgb(Modules.CH_COLOR.str, 0xFFFFFF);
			if (fontIdx > 0 || shadowOff || colorOn) {
				result.withStyle(s -> {
					if (fontIdx > 0 && fontIdx < FONTS.length) s = s.withFont(new FontDescription.Resource(FONTS[fontIdx]));
					if (shadowOff) s = s.withShadowColor(0x00000000);
					if (colorOn) s = s.withColor(colorRgb);
					return s;
				});
			}
		} catch (Exception e) {
			Diagnostics.error("chat style", e);
		}

		// rough line count so Smooth Chat knows how far to slide (measured on plain text, which is always safe)
		try {
			int width = mc.font.width(plain);
			double size = Modules.CH_SIZE.num <= 0 ? 1.0 : Modules.CH_SIZE.num;
			ChatState.added((int) Math.ceil(width * size / 320.0));
		} catch (Exception e) {
			ChatState.added(1);
		}

		return result;
	}

	private static Component timestamp() {
		String pattern = Modules.CH_TIME_FMT.index() == 1 ? "hh:mm a" : "HH:mm";
		String t = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
		return Component.literal("[" + t + "] ")
				.setStyle(Style.EMPTY.withColor(HudUtil.rgb(Modules.CH_TIME_COLOR.str, 0xAAAAAA)));
	}

	private static Component head(PlayerInfo sender) {
		return MutableComponent.create(new ObjectContents(
				new PlayerSprite(ResolvableProfile.createResolved(sender.getProfile()), Modules.CH_HEAD_HAT.bool)));
	}

	/**
	 * Finds who sent a message: the last player name that appears before the first ':' or '>'
	 * ("<Steve> hi", "[VIP] Steve: hi"). Messages with no such separator (join / leave / system) get no head.
	 */
	private static PlayerInfo findSender(ClientPacketListener nh, String plain) {
		if (nh == null) return null;
		int delim = -1;
		for (int i = 0; i < Math.min(plain.length(), 45); i++) {
			char c = plain.charAt(i);
			if (c == ':' || c == '>' || c == '\u00BB') {
				delim = i;
				break;
			}
		}
		if (delim <= 0) return null;
		Matcher m = NAME_TOKEN.matcher(plain.substring(0, delim));
		PlayerInfo found = null;
		while (m.find()) {
			PlayerInfo e = nh.getPlayerInfo(m.group());
			if (e != null) found = e;
		}
		return found;
	}

	/** Appends s to out, colouring every (case-insensitive) occurrence of key. */
	private static void appendHighlighted(MutableComponent out, Style style, String s, String key, int rgb, boolean bold) {
		String lower = s.toLowerCase(Locale.ROOT);
		String k = key.toLowerCase(Locale.ROOT);
		int from = 0;
		int idx;
		while (!k.isEmpty() && (idx = lower.indexOf(k, from)) >= 0) {
			if (idx > from) out.append(Component.literal(s.substring(from, idx)).setStyle(style));
			Style hs = style.withColor(rgb);
			if (bold) hs = hs.withBold(true);
			out.append(Component.literal(s.substring(idx, idx + k.length())).setStyle(hs));
			from = idx + k.length();
		}
		if (from < s.length()) out.append(Component.literal(s.substring(from)).setStyle(style));
	}

	/** Used by the search screen: yellow + bold on every match. */
	public static Component highlight(Component input, String query) {
		MutableComponent out = Component.empty();
		input.<Object>visit((style, str) -> {
			appendHighlighted(out, style, str, query, 0xFFFF55, true);
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}
