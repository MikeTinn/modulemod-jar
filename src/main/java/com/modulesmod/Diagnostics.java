package com.modulesmod;

/** Counters that show which hooks actually run in game. Print them with /modulesmod status. */
public final class Diagnostics {
	public static volatile int chatTransform = 0;
	public static volatile int chatStackHook = 0;
	public static volatile int chatRenderHook = 0;
	public static volatile int projectionHook = 0;
	public static volatile int hitboxEmit = 0;
	public static volatile int hitboxVanillaCancelled = 0;
	public static volatile int outlineEmit = 0;
	public static volatile int fogHook = 0;
	public static volatile int weatherHook = 0;
	public static volatile int zoomHook = 0;
	public static volatile int zoomScrolls = 0;
	public static volatile int timeHook = 0;
	public static volatile String motionBlur = "off";
	public static volatile int outlineVanillaCancelled = 0;
	public static volatile String lyrics = "off";
	public static volatile String upscaler = "off";
	public static volatile String lastError = "none";

	private Diagnostics() {}

	public static void error(String where, Throwable t) {
		lastError = where + ": " + t;
		ModulesMod.LOGGER.warn("[modulesmod] {} failed", where, t);
	}
}
