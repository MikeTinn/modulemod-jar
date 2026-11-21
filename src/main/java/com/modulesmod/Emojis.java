package com.modulesmod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emoji shortcodes like :heart: turned into symbols that Minecraft's normal font can draw.
 * Real emoji (the colourful ones) are NOT in Minecraft's font, so they would show as boxes for
 * other players. These symbols are plain characters, so every player sees them without any mod.
 */
public final class Emojis {
	private static final Map<String, String> MAP = new HashMap<>();
	private static final Pattern CODE = Pattern.compile(":([a-zA-Z0-9_+\\-]{1,24}):");

	static {
		MAP.put("heart", "\u2764");
		MAP.put("hearts", "\u2665");
		MAP.put("star", "\u2605");
		MAP.put("star2", "\u2606");
		MAP.put("smile", "\u263A");
		MAP.put("smiley", "\u263B");
		MAP.put("sad", "\u2639");
		MAP.put("check", "\u2714");
		MAP.put("yes", "\u2714");
		MAP.put("x", "\u2716");
		MAP.put("no", "\u2716");
		MAP.put("cross", "\u271D");
		MAP.put("sun", "\u2600");
		MAP.put("cloud", "\u2601");
		MAP.put("umbrella", "\u2602");
		MAP.put("snowman", "\u2603");
		MAP.put("snowflake", "\u2744");
		MAP.put("skull", "\u2620");
		MAP.put("peace", "\u262E");
		MAP.put("yinyang", "\u262F");
		MAP.put("music", "\u266A");
		MAP.put("notes", "\u266B");
		MAP.put("spades", "\u2660");
		MAP.put("clubs", "\u2663");
		MAP.put("diamonds", "\u2666");
		MAP.put("flower", "\u2740");
		MAP.put("sparkles", "\u2726");
		MAP.put("crown", "\u265B");
		MAP.put("phone", "\u260E");
		MAP.put("radioactive", "\u2622");
		MAP.put("biohazard", "\u2623");
		MAP.put("warning", "\u26A0");
		MAP.put("zap", "\u26A1");
		MAP.put("bolt", "\u26A1");
		MAP.put("swords", "\u2694");
		MAP.put("anchor", "\u2693");
		MAP.put("coffee", "\u2615");
		MAP.put("scissors", "\u2702");
		MAP.put("plane", "\u2708");
		MAP.put("mail", "\u2709");
		MAP.put("pencil", "\u270F");
		MAP.put("victory", "\u270C");
		MAP.put("gear", "\u2699");
		MAP.put("hourglass", "\u231B");
		MAP.put("arrow_right", "\u2192");
		MAP.put("arrow_left", "\u2190");
		MAP.put("arrow_up", "\u2191");
		MAP.put("arrow_down", "\u2193");
		MAP.put("infinity", "\u221E");
		MAP.put("copyright", "\u00A9");
		MAP.put("tm", "\u2122");
		MAP.put("heart_eyes", "\u2665\u203F\u2665");
		MAP.put("cat", "=^\uFF65\u03C9\uFF65^=");
		MAP.put("shrug", "\u00AF\\_(\u30C4)_/\u00AF");
		MAP.put("tableflip", "(\u256F\u00B0\u25A1\u00B0)\u256F\uFE35 \u253B\u2501\u253B");
		MAP.put("unflip", "\u252C\u2500\u252C \u30CE( \u309C-\u309C\u30CE)");
	}

	private Emojis() {}

	public static Map<String, String> all() {
		return MAP;
	}

	/** Replaces known :shortcodes: (and optionally text faces like :) and <3). */
	public static String replace(String text, boolean faces) {
		if (text == null || text.isEmpty()) return text;
		String out = text;
		if (out.indexOf(':') >= 0) {
			Matcher m = CODE.matcher(out);
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				String rep = MAP.get(m.group(1).toLowerCase(Locale.ROOT));
				m.appendReplacement(sb, Matcher.quoteReplacement(rep != null ? rep : m.group()));
			}
			m.appendTail(sb);
			out = sb.toString();
		}
		if (faces && !out.contains("://")) {
			out = out.replace(":)", "\u263A").replace(":(", "\u2639").replace("<3", "\u2764").replace(":D", "\u263B");
		}
		return out;
	}
}
