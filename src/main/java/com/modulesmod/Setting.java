package com.modulesmod;

public class Setting {
	public enum Type { BOOL, INT, SLIDER, CHOICE, TEXT, HEX, PRESET, ACTION }

	public final String id;
	public final String label;
	public final Type type;
	public final double min;
	public final double max;
	public final int decimals;
	public final String[] options;
	/** PRESET rows: the slider setting that the preset button changes. */
	public final Setting linked;
	/** ACTION rows: button text and what happens when it is pressed. */
	public final String buttonText;
	public final Runnable action;
	private final boolean defBool;
	private final double defNum;
	private final String defStr;

	public boolean bool;
	public double num;
	public String str;

	private Setting(String id, String label, Type type, double min, double max, int decimals,
					String[] options, boolean defBool, double defNum, String defStr,
					Setting linked, String buttonText, Runnable action) {
		this.id = id;
		this.label = label;
		this.type = type;
		this.min = min;
		this.max = max;
		this.decimals = decimals;
		this.options = options;
		this.linked = linked;
		this.buttonText = buttonText;
		this.action = action;
		this.defBool = defBool;
		this.defNum = defNum;
		this.defStr = defStr;
		this.bool = defBool;
		this.num = defNum;
		this.str = defStr;
	}

	private static final String[] NONE = new String[0];

	public static Setting bool(String id, String label, boolean def) {
		return new Setting(id, label, Type.BOOL, 0, 1, 0, NONE, def, 0, "", null, null, null);
	}

	public static Setting integer(String id, String label, int def) {
		return new Setting(id, label, Type.INT, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, NONE, false, def, "", null, null, null);
	}

	public static Setting slider(String id, String label, double min, double max, double def) {
		return slider(id, label, min, max, def, 2);
	}

	public static Setting slider(String id, String label, double min, double max, double def, int decimals) {
		return new Setting(id, label, Type.SLIDER, min, max, decimals, NONE, false, def, "", null, null, null);
	}

	public static Setting choice(String id, String label, int defIndex, String... options) {
		return new Setting(id, label, Type.CHOICE, 0, options.length - 1, 0, options, false, defIndex, "", null, null, null);
	}

	public static Setting text(String id, String label, String def) {
		return new Setting(id, label, Type.TEXT, 0, 0, 0, NONE, false, 0, def, null, null, null);
	}

	/** 6-digit hex colour like FFFFFF. */
	public static Setting hex(String id, String label, String def) {
		return new Setting(id, label, Type.HEX, 0, 0, 0, NONE, false, 0, def, null, null, null);
	}

	/** A button that cycles through presets and sets the linked slider. */
	public static Setting preset(String id, String label, Setting linked) {
		return new Setting(id, label, Type.PRESET, 0, 0, 0, NONE, false, 0, "", linked, null, null);
	}

	/** A button that just runs something when pressed. */
	public static Setting action(String id, String label, String buttonText, Runnable action) {
		return new Setting(id, label, Type.ACTION, 0, 0, 0, NONE, false, 0, "", null, buttonText, action);
	}

	public int index() {
		return Math.max(0, Math.min(options.length - 1, (int) num));
	}

	public String choice() {
		return options[index()];
	}

	public void reset() {
		bool = defBool;
		num = defNum;
		str = defStr;
	}

	public Object snapshot() {
		return switch (type) {
			case BOOL -> (Object) bool;
			case TEXT, HEX -> (Object) str;
			default -> (Object) num;
		};
	}

	public void restore(Object o) {
		if (o instanceof Boolean b) bool = b;
		else if (o instanceof String s) str = s;
		else if (o instanceof Number n) num = n.doubleValue();
	}
}
