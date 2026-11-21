package com.modulesmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Modules {
	// ---- Scoreboard settings ----
	public static final Setting SB_OPACITY = Setting.slider("opacity", "Background Opacity", 0, 100, 30, 0);
	public static final Setting SB_SCALE = Setting.slider("scale", "Scale", 0.5, 2.0, 1.0, 2);
	public static final Setting SB_HIDE_TITLE = Setting.bool("hideTitle", "Hide Title", false);
	public static final Setting SB_HIDE_NUMBERS = Setting.bool("hideNumbers", "Hide Scores (red numbers)", false);
	public static final Setting SB_HIDDEN_ROWS = Setting.text("hiddenRows", "Hide Rows (e.g. 1,3,5-7)", "");
	public static final Setting SB_HIDDEN_TEXT = Setting.text("hiddenText", "Hide Rows Containing", "");
	public static final Setting SB_FONT = Setting.choice("font", "Font", 0,
			"Default", "Uniform", "Alt (Enchanting)", "Illager");
	public static final Setting SB_COLOR = Setting.hex("textColor", "Text Color (hex)", "FFFFFF");
	public static final Setting SB_SHADOW = Setting.bool("shadow", "Text Shadow", false);
	public static final Setting SB_BOLD = Setting.bool("bold", "Bold", false);
	public static final Setting SB_ITALIC = Setting.bool("italic", "Italic", false);

	public static final Module SCOREBOARD = new Module("scoreboard", "Scoreboard",
			"Customize the sidebar scoreboard.",
			SB_OPACITY, SB_SCALE, SB_HIDE_TITLE, SB_HIDE_NUMBERS, SB_HIDDEN_ROWS, SB_HIDDEN_TEXT,
			SB_FONT, SB_COLOR, SB_SHADOW, SB_BOLD, SB_ITALIC);

	// ---- Tab settings ----
	public static final Setting T_SCALE = Setting.slider("scale", "Size", 0.5, 2.0, 1.0, 2);
	public static final Setting T_TEXT_COLOR = Setting.hex("textColor", "Text Color (hex)", "FFFFFF");
	public static final Setting T_BG_COLOR = Setting.hex("bgColor", "Background Color (hex)", "000000");
	public static final Setting T_OPACITY = Setting.slider("opacity", "Background Opacity", 0, 100, 50, 0);
	public static final Setting T_ROW_BG = Setting.bool("rowBg", "Row Backgrounds", true);
	public static final Setting T_HIDE_HEADER = Setting.bool("hideHeader", "Hide Header", false);
	public static final Setting T_HIDE_FOOTER = Setting.bool("hideFooter", "Hide Footer", false);
	public static final Setting T_MAX = Setting.integer("maxPlayers", "Max Players Shown", 80);
	public static final Setting T_HEADS = Setting.bool("heads", "Show Heads", true);
	public static final Setting T_SHADOW = Setting.bool("shadow", "Text Shadow", true);
	public static final Setting T_PING = Setting.choice("pingStyle", "Ping Style", 2, "Off", "Number", "Bars");
	public static final Setting T_PING_1 = Setting.hex("ping1", "Ping Color 0-50 ms", "55FF55");
	public static final Setting T_PING_2 = Setting.hex("ping2", "Ping Color 51-100 ms", "AAFF55");
	public static final Setting T_PING_3 = Setting.hex("ping3", "Ping Color 101-150 ms", "FFFF55");
	public static final Setting T_PING_4 = Setting.hex("ping4", "Ping Color 151-250 ms", "FFAA00");
	public static final Setting T_PING_5 = Setting.hex("ping5", "Ping Color 251+ ms", "FF5555");
	public static final Setting T_OWN = Setting.bool("ownHighlight", "Highlight Own Name", true);
	public static final Setting T_OWN_COLOR = Setting.hex("ownColor", "Own Name Color (hex)", "FFFF55");
	public static final Setting T_OWN_BOLD = Setting.bool("ownBold", "Own Name Bold", true);
	public static final Setting T_OWN_UL = Setting.bool("ownUnderline", "Own Name Underline", false);
	public static final Setting T_OWN_BG = Setting.bool("ownRowBg", "Own Row Highlight", true);

	public static final Module TAB = new Module("tab", "Tab",
			"Customize the player list shown when holding Tab.",
			T_SCALE, T_TEXT_COLOR, T_BG_COLOR, T_OPACITY, T_ROW_BG, T_HIDE_HEADER, T_HIDE_FOOTER,
			T_MAX, T_HEADS, T_SHADOW, T_PING, T_PING_1, T_PING_2, T_PING_3, T_PING_4, T_PING_5,
			T_OWN, T_OWN_COLOR, T_OWN_BOLD, T_OWN_UL, T_OWN_BG);

	// ---- Aspect Ratio settings ----
	public static final Setting AR_RATIO = Setting.slider("ratio", "Aspect Ratio", 0.5, 3.0, 1.78, 2);
	public static final Setting AR_PRESET = Setting.preset("preset", "Preset", AR_RATIO);
	public static final Setting AR_NAME = Setting.text("presetName", "New Preset Name", "");
	public static final Setting AR_SAVE = Setting.action("savePreset", "Save Preset", "Save Current Ratio", () -> {
		AspectPresets.saveUser(AR_NAME.str, AR_RATIO.num);
		AR_NAME.str = "";
	});
	public static final Setting AR_DELETE = Setting.action("deletePreset", "Delete Preset", "Delete Selected Preset",
			() -> AspectPresets.deleteMatching(AR_RATIO.num));

	public static final Module ASPECT_RATIO = new Module("aspect_ratio", "Aspect Ratio",
			"Make everything look wider or slimmer, with saveable presets.",
			AR_PRESET, AR_RATIO, AR_NAME, AR_SAVE, AR_DELETE);

	// ---- Chat settings ----
	public static final Setting CH_SMOOTH = Setting.bool("smooth", "Smooth Chat", true);
	public static final Setting CH_SMOOTH_MS = Setting.slider("smoothMs", "Smooth Speed (ms)", 50, 600, 250, 0);
	public static final Setting CH_SIZE = Setting.slider("size", "Size", 0.5, 2.0, 1.0, 2);
	public static final Setting CH_HISTORY = Setting.integer("historyLines", "Chat History Lines", 1000);
	public static final Setting CH_BG_ON = Setting.bool("customBg", "Custom Background", false);
	public static final Setting CH_BG = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 50, 0);
	public static final Setting CH_FONT = Setting.choice("font", "Font", 0,
			"Default", "Uniform", "Alt (Enchanting)", "Illager");
	public static final Setting CH_SHADOW = Setting.bool("shadow", "Text Shadow", true);
	public static final Setting CH_COLOR_ON = Setting.bool("overrideColor", "Override Text Color", false);
	public static final Setting CH_COLOR = Setting.hex("textColor", "Text Color (hex)", "FFFFFF");
	public static final Setting CH_HEADS = Setting.bool("heads", "Chat Heads", true);
	public static final Setting CH_HEAD_HAT = Setting.bool("headHat", "Heads Show Hat Layer", true);
	public static final Setting CH_TIME = Setting.bool("timestamps", "Timestamps", false);
	public static final Setting CH_TIME_FMT = Setting.choice("timeFormat", "Time Format", 0, "24 hour", "12 hour");
	public static final Setting CH_TIME_COLOR = Setting.hex("timeColor", "Timestamp Color (hex)", "AAAAAA");
	public static final Setting CH_EMOJI = Setting.bool("emoji", "Emoji Shortcodes (send)", true);
	public static final Setting CH_EMOJI_IN = Setting.bool("emojiIncoming", "Convert Incoming Emojis", true);
	public static final Setting CH_FACES = Setting.bool("faces", "Text Faces (:) <3)", false);
	public static final Setting CH_MENTION = Setting.bool("mention", "Highlight My Name", true);
	public static final Setting CH_MENTION_COLOR = Setting.hex("mentionColor", "Mention Color (hex)", "FFFF55");
	public static final Setting CH_MENTION_BOLD = Setting.bool("mentionBold", "Mention Bold", true);
	public static final Setting CH_STACK = Setting.bool("stack", "Stack Duplicate Messages", true);
	public static final Setting CH_STACK_COLOR = Setting.hex("stackColor", "Stack Counter Color (hex)", "AAAAAA");
	public static final Setting CH_SEARCH = Setting.action("search", "Chat Search", "Open Search", () -> {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		mc.gui.setScreen(new ChatSearchScreen(mc.gui.screen(), ""));
	});
	public static final Setting CH_CLEAR_LOG = Setting.action("clearLog", "Search Log", "Clear Search Log",
			ChatState.LOG::clear);

	public static final Module CHAT = new Module("chat", "Chat",
			"Smooth chat, heads, search, emojis, longer history, font and more.",
			CH_SMOOTH, CH_SMOOTH_MS, CH_SIZE, CH_HISTORY, CH_BG_ON, CH_BG,
			CH_FONT, CH_SHADOW, CH_COLOR_ON, CH_COLOR,
			CH_HEADS, CH_HEAD_HAT, CH_TIME, CH_TIME_FMT, CH_TIME_COLOR,
			CH_STACK, CH_STACK_COLOR,
			CH_EMOJI, CH_EMOJI_IN, CH_FACES,
			CH_MENTION, CH_MENTION_COLOR, CH_MENTION_BOLD,
			CH_SEARCH, CH_CLEAR_LOG);

	// ---- Hitbox settings ----
	public static final Setting HB_COLOR = Setting.hex("color", "Hitbox Color", "FFFFFF");
	public static final Setting HB_AIM_COLOR = Setting.hex("aimColor", "Aim Color", "FF5555");
	public static final Setting HB_WIDTH = Setting.slider("width", "Line Width", 1.0, 8.0, 2.0, 1);
	public static final Setting HB_FILL = Setting.bool("fill", "Fill", false);
	public static final Setting HB_FILL_OPACITY = Setting.slider("fillOpacity", "Fill Opacity", 0, 100, 25, 0);
	public static final Setting HB_HIDE_EYE = Setting.bool("hideEyeHeight", "Hide Eye Height Line", false);
	public static final Setting HB_HIDE_LOOK = Setting.bool("hideLookDirection", "Hide Look Direction Line", false);
	public static final Setting HB_TARGET = Setting.choice("targets", "Show Hitboxes For", 0,
			"Everything", "Players only", "Everything but players");

	public static final Module HITBOX = new Module("hitbox", "Hitbox",
			"Custom hitbox colors, aim color, width and fill. Never shows through walls.",
			HB_COLOR, HB_AIM_COLOR, HB_WIDTH, HB_FILL, HB_FILL_OPACITY, HB_HIDE_EYE, HB_HIDE_LOOK, HB_TARGET);

	// ---- Zoom settings ----
	public static final Setting ZM_MODE = Setting.choice("mode", "Mode", 0, "Hold", "Toggle");
	public static final Setting ZM_FOV = Setting.slider("zoomFov", "Zoom FOV (degrees)", 5, 70, 30, 0);
	public static final Setting ZM_SPEED = Setting.slider("speed", "Zoom Speed", 2, 30, 12, 0);
	public static final Setting ZM_SCROLL = Setting.bool("scroll", "Scroll To Zoom More/Less", true);
	public static final Setting ZM_STEP = Setting.slider("scrollStep", "Scroll Step (%)", 5, 50, 15, 0);
	public static final Setting ZM_INVERT = Setting.bool("invertScroll", "Invert Scroll", false);
	public static final Setting ZM_MIN_FOV = Setting.slider("minFov", "Strongest Zoom (FOV)", 1, 20, 3, 0);

	public static final Module ZOOM = new Module("zoom", "Zoom",
			"Smooth zoom: hold C, scroll to adjust",
			ZM_MODE, ZM_FOV, ZM_SPEED, ZM_SCROLL, ZM_STEP, ZM_INVERT, ZM_MIN_FOV);

	// ---- On/off only for now (no settings and no effect yet) ----
	// ---- Motion Blur settings ----
	public static final Setting MB_STRENGTH = Setting.choice("strength", "Strength", 1,
			"Light", "Medium", "Strong", "Extreme");

	public static final Module MOTION_BLUR = new Module("motion_blur", "Motion Blur",
			"Smooth blur between frames", MB_STRENGTH);
	// ---- Armor Status settings ----
	public static final Setting AH_STYLE = Setting.choice("style", "Style", 0, "Modern", "Vanilla");
	public static final Setting AH_LAYOUT = Setting.choice("layout", "Layout", 0, "Vertical", "Horizontal");
	public static final Setting AH_DISPLAY = Setting.choice("display", "Durability Text", 0,
			"Percent", "Number", "Number + Percent", "None");
	public static final Setting AH_COLOR_MODE = Setting.bool("colorByDurability", "Color By Durability", true);
	public static final Setting AH_TEXT_COLOR = Setting.hex("textColor", "Text Color", "FFFFFF");
	public static final Setting AH_C_HIGH = Setting.hex("colorHigh", "Durability High (60%+)", "55FF55");
	public static final Setting AH_C_MID = Setting.hex("colorMid", "Durability Medium (30-60%)", "FFFF55");
	public static final Setting AH_C_LOW = Setting.hex("colorLow", "Durability Low (10-30%)", "FFAA00");
	public static final Setting AH_C_CRIT = Setting.hex("colorCrit", "Durability Critical (<10%)", "FF5555");
	public static final Setting AH_BAR = Setting.bool("bar", "Durability Bar (Modern)", true);
	public static final Setting AH_BG = Setting.bool("background", "Background (Modern)", true);
	public static final Setting AH_BG_OPACITY = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 45, 0);
	public static final Setting AH_SHADOW = Setting.bool("shadow", "Text Shadow", true);
	public static final Setting AH_HIDE_EMPTY = Setting.bool("hideEmpty", "Hide Empty Slots", true);
	public static final Setting AH_X = Setting.slider("posX", "Position X (%)", 0, 100, 30.0, 1);
	public static final Setting AH_Y = Setting.slider("posY", "Position Y (%)", 0, 100, 80.0, 1);
	public static final Setting AH_SCALE = Setting.slider("scale", "Size", 0.5, 2.0, 1.0, 2);

	public static final Module ARMOR_STATUS = new Module("armor_status", "Armor Status",
			"Armor and durability HUD: modern or vanilla style. Move it with Move HUDs.",
			AH_STYLE, AH_LAYOUT, AH_DISPLAY, AH_COLOR_MODE, AH_TEXT_COLOR,
			AH_C_HIGH, AH_C_MID, AH_C_LOW, AH_C_CRIT,
			AH_BAR, AH_BG, AH_BG_OPACITY, AH_SHADOW, AH_HIDE_EMPTY,
			AH_X, AH_Y, AH_SCALE);
	// ---- Block Outline settings ----
	public static final Setting BO_REPLACE = Setting.bool("replaceVanilla", "Replace Vanilla Outline", true);
	public static final Setting BO_SHAPE = Setting.choice("shape", "Outline Shape", 0,
			"Real Shape", "Bounding Box", "Full Cube");
	public static final Setting BO_COLOR_MODE = Setting.choice("colorMode", "Color Mode", 0,
			"Solid", "Rainbow", "Fade (2 colors)", "Pulse");
	public static final Setting BO_COLOR = Setting.hex("color", "Outline Color", "FFFFFF");
	public static final Setting BO_COLOR2 = Setting.hex("color2", "Second Color (Fade)", "FF55FF");
	public static final Setting BO_SPEED = Setting.slider("speed", "Animation Speed", 0.1, 5.0, 1.0, 1);
	public static final Setting BO_OPACITY = Setting.slider("opacity", "Outline Opacity", 0, 100, 100, 0);
	public static final Setting BO_WIDTH = Setting.slider("width", "Line Width", 1.0, 10.0, 3.0, 1);
	public static final Setting BO_EXPAND = Setting.slider("expand", "Expand", 0.0, 0.05, 0.002, 3);
	public static final Setting BO_GLOW = Setting.bool("glow", "Soft Glow", false);
	public static final Setting BO_FILL = Setting.choice("fill", "Fill", 0, "Off", "Whole Block", "Hit Face Only");
	public static final Setting BO_FILL_MATCH = Setting.bool("fillMatch", "Fill Matches Outline Color", true);
	public static final Setting BO_FILL_COLOR = Setting.hex("fillColor", "Fill Color", "FFFFFF");
	public static final Setting BO_FILL_OPACITY = Setting.slider("fillOpacity", "Fill Opacity", 0, 100, 20, 0);

	public static final Module BLOCK_OUTLINE = new Module("block_outline", "Block Outline",
			"Colors, animation, width, fill and more",
			BO_REPLACE, BO_SHAPE, BO_COLOR_MODE, BO_COLOR, BO_COLOR2, BO_SPEED, BO_OPACITY, BO_WIDTH,
			BO_EXPAND, BO_GLOW, BO_FILL, BO_FILL_MATCH, BO_FILL_COLOR, BO_FILL_OPACITY);
	// ---- Fog Customizer settings ----
	public static final Setting FG_REMOVE_ALL = Setting.bool("removeAll", "Remove All Fog", false);
	public static final Setting FG_REMOVE_DISTANCE = Setting.bool("removeDistance", "Remove Distance Fog", false);
	public static final Setting FG_REMOVE_ENV = Setting.bool("removeAtmospheric", "Remove Atmospheric Fog", false);
	public static final Setting FG_DIST_START = Setting.slider("distanceStart", "Distance Fog Start (%)", 0, 300, 100, 0);
	public static final Setting FG_DIST_END = Setting.slider("distanceEnd", "Distance Fog End (%)", 20, 400, 100, 0);
	public static final Setting FG_ENV_SCALE = Setting.slider("atmosphericScale", "Atmospheric Fog Distance (%)", 20, 500, 100, 0);
	public static final Setting FG_COLOR_ON = Setting.bool("customColor", "Custom Fog Color", false);
	public static final Setting FG_COLOR_MODE = Setting.choice("colorMode", "Fog Color Mode", 0, "Solid", "Rainbow");
	public static final Setting FG_COLOR = Setting.hex("color", "Fog Color", "C0D8FF");
	public static final Setting FG_SPEED = Setting.slider("speed", "Rainbow Speed", 0.1, 5.0, 1.0, 1);

	public static final Module FOG_CUSTOMIZER = new Module("fog_customizer", "Fog Customizer",
			"Remove fog completely, or change its distance and color",
			FG_REMOVE_ALL, FG_REMOVE_DISTANCE, FG_REMOVE_ENV, FG_DIST_START, FG_DIST_END, FG_ENV_SCALE,
			FG_COLOR_ON, FG_COLOR_MODE, FG_COLOR, FG_SPEED);
	// ---- Weather Changer settings (only changes what YOU see, never the server) ----
	public static final Setting WC_WEATHER = Setting.choice("weather", "Weather", 0,
			"Server Weather", "Clear", "Rain", "Thunderstorm");
	public static final Setting WC_INTENSITY = Setting.slider("intensity", "Weather Intensity", 0, 100, 100, 0);
	public static final Setting WC_TIME = Setting.choice("time", "Time of Day", 0,
			"Server Time", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom", "Cycle (fast)");
	public static final Setting WC_HOUR = Setting.slider("customHour", "Custom Time (hour)", 0, 24, 12, 1);
	public static final Setting WC_CYCLE = Setting.slider("cycleSeconds", "Cycle: Seconds Per Day", 5, 300, 30, 0);

	public static final Module WEATHER_CHANGER = new Module("weather_changer", "Weather Changer",
			"Change the weather and time of day you see",
			WC_WEATHER, WC_INTENSITY, WC_TIME, WC_HOUR, WC_CYCLE);

	// ---- HUDs (on/off only for now) ----
	// ---- FPS Display settings ----
	public static final Setting FH_X = Setting.slider("posX", "Position X (%)", 0, 100, 1.0, 1);
	public static final Setting FH_Y = Setting.slider("posY", "Position Y (%)", 0, 100, 1.0, 1);
	public static final Setting FH_SCALE = Setting.slider("scale", "Size", 0.5, 3.0, 1.0, 2);
	public static final Setting FH_FORMAT = Setting.choice("format", "Format", 0, "123 FPS", "FPS: 123", "123");
	public static final Setting FH_COLOR_BY = Setting.bool("colorByFps", "Color By FPS", true);
	public static final Setting FH_COLOR = Setting.hex("textColor", "Text Color", "FFFFFF");
	public static final Setting FH_C_GOOD = Setting.hex("colorGood", "Good FPS (60+)", "55FF55");
	public static final Setting FH_C_MID = Setting.hex("colorMid", "Medium FPS (30-59)", "FFFF55");
	public static final Setting FH_C_LOW = Setting.hex("colorLow", "Low FPS (under 30)", "FF5555");
	public static final Setting FH_BG = Setting.bool("background", "Background", true);
	public static final Setting FH_BG_OPACITY = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 40, 0);
	public static final Setting FH_SHADOW = Setting.bool("shadow", "Text Shadow", true);

	public static final Module FPS_DISPLAY = new Module("fps_display", "FPS Display",
			"Shows your FPS on screen",
			FH_X, FH_Y, FH_SCALE, FH_FORMAT, FH_COLOR_BY, FH_COLOR, FH_C_GOOD, FH_C_MID, FH_C_LOW,
			FH_BG, FH_BG_OPACITY, FH_SHADOW);
	// ---- Reach Display settings ----
	public static final Setting RH_X = Setting.slider("posX", "Position X (%)", 0, 100, 1.0, 1);
	public static final Setting RH_Y = Setting.slider("posY", "Position Y (%)", 0, 100, 9.0, 1);
	public static final Setting RH_SCALE = Setting.slider("scale", "Size", 0.5, 3.0, 1.0, 2);
	public static final Setting RH_FORMAT = Setting.choice("format", "Format", 0, "3.00 blocks", "Reach: 3.00", "3.00");
	public static final Setting RH_DECIMALS = Setting.choice("decimals", "Decimals", 1, "1", "2", "3");
	public static final Setting RH_TIMEOUT = Setting.slider("timeout", "Clear After (seconds, 0 = never)", 0, 30, 0, 0);
	public static final Setting RH_COLOR = Setting.hex("textColor", "Text Color", "FFFFFF");
	public static final Setting RH_BG = Setting.bool("background", "Background", true);
	public static final Setting RH_BG_OPACITY = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 40, 0);
	public static final Setting RH_SHADOW = Setting.bool("shadow", "Text Shadow", true);

	public static final Module REACH_DISPLAY = new Module("reach_display", "Reach Display",
			"Shows the distance of your last hit",
			RH_X, RH_Y, RH_SCALE, RH_FORMAT, RH_DECIMALS, RH_TIMEOUT, RH_COLOR, RH_BG, RH_BG_OPACITY, RH_SHADOW);
	// ---- Ping Display settings ----
	public static final Setting PH_X = Setting.slider("posX", "Position X (%)", 0, 100, 1.0, 1);
	public static final Setting PH_Y = Setting.slider("posY", "Position Y (%)", 0, 100, 5.0, 1);
	public static final Setting PH_SCALE = Setting.slider("scale", "Size", 0.5, 3.0, 1.0, 2);
	public static final Setting PH_FORMAT = Setting.choice("format", "Format", 0, "123 ms", "Ping: 123", "123");
	public static final Setting PH_COLOR_BY = Setting.bool("colorByPing", "Color By Ping", true);
	public static final Setting PH_COLOR = Setting.hex("textColor", "Text Color", "FFFFFF");
	public static final Setting PH_C_GOOD = Setting.hex("colorGood", "Good Ping (up to 60)", "55FF55");
	public static final Setting PH_C_MID = Setting.hex("colorMid", "Medium Ping (61-150)", "FFFF55");
	public static final Setting PH_C_BAD = Setting.hex("colorBad", "Bad Ping (over 150)", "FF5555");
	public static final Setting PH_BG = Setting.bool("background", "Background", true);
	public static final Setting PH_BG_OPACITY = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 40, 0);
	public static final Setting PH_SHADOW = Setting.bool("shadow", "Text Shadow", true);

	public static final Module PING_DISPLAY = new Module("ping_display", "Ping Display",
			"Shows your ping on screen",
			PH_X, PH_Y, PH_SCALE, PH_FORMAT, PH_COLOR_BY, PH_COLOR, PH_C_GOOD, PH_C_MID, PH_C_BAD,
			PH_BG, PH_BG_OPACITY, PH_SHADOW);
	// ---- Inventory HUD settings ----
	public static final Setting IH_X = Setting.slider("posX", "Position X (%)", 0, 100, 1.0, 1);
	public static final Setting IH_Y = Setting.slider("posY", "Position Y (%)", 0, 100, 17.0, 1);
	public static final Setting IH_SCALE = Setting.slider("scale", "Size", 0.5, 3.0, 1.0, 2);
	public static final Setting IH_HOTBAR = Setting.bool("showHotbar", "Show Hotbar Row", false);
	public static final Setting IH_COUNTS = Setting.bool("showCounts", "Item Counts and Durability", true);
	public static final Setting IH_OUTLINE = Setting.bool("slotOutlines", "Slot Outlines", true);
	public static final Setting IH_OUTLINE_COLOR = Setting.hex("outlineColor", "Outline Color", "FFFFFF");
	public static final Setting IH_BG = Setting.bool("background", "Background", true);
	public static final Setting IH_BG_OPACITY = Setting.slider("bgOpacity", "Background Opacity", 0, 100, 40, 0);

	public static final Module INVENTORY_HUD = new Module("inventory_hud", "Inventory HUD",
			"Shows your inventory on screen",
			IH_X, IH_Y, IH_SCALE, IH_HOTBAR, IH_COUNTS, IH_OUTLINE, IH_OUTLINE_COLOR, IH_BG, IH_BG_OPACITY);

	// ---- Lyrics settings ----
	public static final Setting LY_DISPLAY = Setting.choice("display", "Display", 0, "In World (3D)", "GUI (movable)");
	public static final Setting LY_X = Setting.slider("posX", "GUI Position X (%)", 0, 100, 30.0, 1);
	public static final Setting LY_Y = Setting.slider("posY", "GUI Position Y (%)", 0, 100, 66.0, 1);
	public static final Setting LY_SCALE = Setting.slider("scale", "GUI Size", 0.5, 3.0, 1.0, 2);
	public static final Setting LY_WIDTH = Setting.slider("guiWidth", "GUI Width", 120, 500, 260, 0);
	public static final Setting LY_NEXT = Setting.bool("showNext", "GUI Show Next Line", true);
	public static final Setting LY_HIGHLIGHT = Setting.choice("highlight", "GUI Highlight", 0, "By Word", "By Letter");
	public static final Setting LY_TRANSITION = Setting.slider("transition", "GUI Line Slide (ms)", 0, 600, 280, 0);
	public static final Setting LY_BG = Setting.bool("background", "GUI Background", true);
	public static final Setting LY_BG_OPACITY = Setting.slider("bgOpacity", "GUI Background Opacity", 0, 100, 40, 0);
	public static final Setting LY_SHADOW = Setting.bool("shadow", "GUI Text Shadow", true);
	public static final Setting LY_SHOW = Setting.choice("show", "World: Show", 0, "Words", "Lines");
	public static final Setting LY_LAYOUT = Setting.choice("layout", "World: Word Layout", 0, "Scatter", "Center");
	public static final Setting LY_DISTANCE = Setting.slider("distance", "World: Distance (blocks)", 2, 12, 5, 1);
	public static final Setting LY_SPREAD = Setting.slider("spread", "World: Spread (blocks)", 0.5, 5, 2.2, 1);
	public static final Setting LY_HEIGHT = Setting.slider("minHeight", "World: Min Height Above Ground (blocks)", 0, 12, 3, 1);
	public static final Setting LY_FONT = Setting.choice("font", "Font", 0, LyricsFonts.NAMES);
	public static final Setting LY_SIZE = Setting.slider("size", "Text Size", 0.3, 4, 1.5, 2);
	public static final Setting LY_LIFETIME = Setting.slider("lifetime", "World: Word Lifetime (ms)", 800, 8000, 3000, 0);
	public static final Setting LY_RAINBOW = Setting.bool("rainbow", "Rainbow Colors", false);
	public static final Setting LY_COLOR = Setting.hex("textColor", "Text Color (hex)", "FFFFFF");
	public static final Setting LY_OFFSET = Setting.slider("offset", "Sync Offset (ms)", -1500, 1500, 0, 0);
	public static final Setting LY_STATUS = Setting.action("status", "Detected Song", "Show In Chat",
			LyricsController::printStatus);

	public static final Module LYRICS = new Module("lyrics", "Lyrics",
			"Synced lyrics of the song you are playing, as 3D text around you. Off by default: when on, the song title and artist are sent to lrclib.net. Needs Windows.",
			false,
			LY_DISPLAY, LY_X, LY_Y, LY_SCALE, LY_WIDTH, LY_NEXT, LY_HIGHLIGHT, LY_TRANSITION, LY_BG, LY_BG_OPACITY, LY_SHADOW,
			LY_SHOW, LY_LAYOUT, LY_DISTANCE, LY_SPREAD, LY_HEIGHT, LY_SIZE, LY_FONT, LY_LIFETIME, LY_RAINBOW, LY_COLOR, LY_OFFSET, LY_STATUS);

	// ---- Copy /give Command settings ----
	public static final Setting GC_TARGET = Setting.choice("target", "Who To Give", 0, "@s (you)", "@p (nearest player)", "Your Name");
	public static final Setting GC_COUNT = Setting.bool("includeCount", "Include Item Count", true);
	public static final Setting GC_MESSAGE = Setting.bool("message", "Show Message When Copied", true);

	public static final Module COPY_GIVE = new Module("copy_give", "Copy /give Command",
			"Press the key set in Controls (Modules Mod > Copy /give Command, default G) to copy a /give command for the item in your main hand, with its enchantments, name and other data.",
			GC_TARGET, GC_COUNT, GC_MESSAGE);

	// ---- Upscaler settings ----
	public static final Setting UP_SCALE = Setting.slider("scale", "Render Scale (%)", 33, 100, 67, 0);
	public static final Setting UP_MENUS = Setting.bool("menusFull", "Full Resolution In Menus", true);

	public static final Module UPSCALER = new Module("upscaler", "Upscaler (FPS)",
			"Draws the game at a lower resolution and stretches it to your screen for more FPS. Everything, HUD included, gets blurrier. 67% is like FSR Quality, 50% like Performance. Stretching uses the game's own filter. Experimental: if the screen looks wrong, turn it off.",
			false,
			UP_SCALE, UP_MENUS);

	// ---- Cosmetics ----
	public static final Setting CO_OPEN = Setting.action("open", "Cosmetics", "Open Cosmetics Screen", () -> {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		mc.gui.setScreen(new CosmeticsScreen(mc.gui.screen()));
	});

	public static final Setting CO_HIDE_CAPE = Setting.bool("hideOwnCape", "Hide My Own Cape", true);
	public static final Setting CO_PET_SIDE = Setting.choice("petSide", "Pet Shoulder", 0, "Right", "Left");

	public static final Module COSMETICS = new Module("cosmetics", "Cosmetics",
			"Capes, wings, hats, jackets and pets that only you can see. Pick them on the Cosmetics screen: the button on the Modules screen, or the one here. Tool skins and your own cape pictures (png, jpg, gif) are there too.",
			CO_OPEN, CO_HIDE_CAPE, CO_PET_SIDE);

	public static final List<Module> ALL = List.of(SCOREBOARD, TAB, ASPECT_RATIO, CHAT, HITBOX, ZOOM,
			MOTION_BLUR, ARMOR_STATUS, BLOCK_OUTLINE, FOG_CUSTOMIZER, WEATHER_CHANGER,
			FPS_DISPLAY, REACH_DISPLAY, PING_DISPLAY, INVENTORY_HUD, LYRICS, COPY_GIVE, UPSCALER, COSMETICS);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Modules() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("modulesmod.json");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f)) {
			Map<String, Object> map = GSON.fromJson(r, new TypeToken<Map<String, Object>>() {}.getType());
			if (map == null) return;
			for (Module m : ALL) {
				for (Setting s : m.settings) {
					Object v = map.get(m.id + "." + s.id);
					if (v == null) continue;
					switch (s.type) {
						case BOOL -> { if (v instanceof Boolean b) s.bool = b; }
						case TEXT, HEX -> { if (v instanceof String str) s.str = str; }
						default -> { if (v instanceof Number n) s.num = n.doubleValue(); }
					}
				}
			}
		} catch (Exception e) {
			ModulesMod.LOGGER.warn("Could not load config", e);
		}
	}

	public static void save() {
		Map<String, Object> map = new LinkedHashMap<>();
		for (Module m : ALL) {
			for (Setting s : m.settings) {
				if (s.type == Setting.Type.PRESET || s.type == Setting.Type.ACTION) continue;
				map.put(m.id + "." + s.id, s.snapshot());
			}
		}
		try (Writer w = Files.newBufferedWriter(file())) {
			GSON.toJson(map, w);
		} catch (Exception e) {
			ModulesMod.LOGGER.warn("Could not save config", e);
		}
	}
}
