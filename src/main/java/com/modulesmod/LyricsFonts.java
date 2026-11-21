package com.modulesmod;

/**
 * Restyles lyric text by swapping letters for look-alike Unicode characters (bold, script, fraktur...).
 * The in-world text gizmo can only draw plain strings with the default font, so this is how we get
 * different "fonts" there. Characters Minecraft's font has no glyph for may show as a box.
 */
final class LyricsFonts {
	static final String[] NAMES = {
			"Default", "Bold", "Script", "Fraktur", "Double-Struck", "Monospace",
			"Small Caps", "Fullwidth", "Bubble", "UPPERCASE", "lowercase"
	};

	private static final int[] SMALL_CAPS = {
			0x1D00, 0x0299, 0x1D04, 0x1D05, 0x1D07, 0xA730, 0x0262, 0x029C, 0x026A, 0x1D0A, 0x1D0B, 0x029F, 0x1D0D,
			0x0274, 0x1D0F, 0x1D18, 0x01EB, 0x0280, 0xA731, 0x1D1B, 0x1D1C, 0x1D20, 0x1D21, 0x0078, 0x028F, 0x1D22
	};

	private LyricsFonts() {}

	static String apply(String text, int style) {
		if (style <= 0 || style >= NAMES.length) return text;
		if (style == 9) return text.toUpperCase();
		if (style == 10) return text.toLowerCase();
		StringBuilder sb = new StringBuilder(text.length() * 2);
		for (int i = 0; i < text.length(); i++) {
			sb.appendCodePoint(map(text.charAt(i), style));
		}
		return sb.toString();
	}

	private static int map(char c, int style) {
		boolean upper = c >= 'A' && c <= 'Z';
		boolean lower = c >= 'a' && c <= 'z';
		boolean digit = c >= '0' && c <= '9';
		switch (style) {
			case 1: // bold sans
				if (upper) return 0x1D5D4 + (c - 'A');
				if (lower) return 0x1D5EE + (c - 'a');
				if (digit) return 0x1D7EC + (c - '0');
				break;
			case 2: // bold script
				if (upper) return 0x1D4D0 + (c - 'A');
				if (lower) return 0x1D4EA + (c - 'a');
				break;
			case 3: // bold fraktur
				if (upper) return 0x1D56C + (c - 'A');
				if (lower) return 0x1D586 + (c - 'a');
				break;
			case 4: // double-struck (a few capitals live elsewhere in Unicode)
				if (upper) {
					return switch (c) {
						case 'C' -> 0x2102;
						case 'H' -> 0x210D;
						case 'N' -> 0x2115;
						case 'P' -> 0x2119;
						case 'Q' -> 0x211A;
						case 'R' -> 0x211D;
						case 'Z' -> 0x2124;
						default -> 0x1D538 + (c - 'A');
					};
				}
				if (lower) return 0x1D552 + (c - 'a');
				if (digit) return 0x1D7D8 + (c - '0');
				break;
			case 5: // monospace
				if (upper) return 0x1D670 + (c - 'A');
				if (lower) return 0x1D68A + (c - 'a');
				if (digit) return 0x1D7F6 + (c - '0');
				break;
			case 6: // small caps
				if (upper) return SMALL_CAPS[c - 'A'];
				if (lower) return SMALL_CAPS[c - 'a'];
				break;
			case 7: // fullwidth
				if (c >= 0x21 && c <= 0x7E) return c + 0xFEE0;
				break;
			case 8: // bubble
				if (upper) return 0x24B6 + (c - 'A');
				if (lower) return 0x24D0 + (c - 'a');
				if (c == '0') return 0x24EA;
				if (c >= '1' && c <= '9') return 0x2460 + (c - '1');
				break;
			default:
				break;
		}
		return c;
	}
}
