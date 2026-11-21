package com.modulesmod;

/** Shared between the Cosmetics screen and the mixin that turns the preview player. */
public final class PreviewSpin {
	public static volatile boolean active = false;
	public static volatile float radians = 0f;

	private PreviewSpin() {}
}
