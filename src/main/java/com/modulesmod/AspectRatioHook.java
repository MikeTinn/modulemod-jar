package com.modulesmod;

import org.joml.Matrix4f;

/**
 * Called from GameRendererMixin around the 3D projection matrix.
 * A perspective matrix has m00 = f / aspect and m11 = f, so setting m00 = m11 / ratio
 * makes the world render with exactly that aspect ratio (smaller ratio = wider look).
 */
public final class AspectRatioHook {
	private static int depth = 0;

	private AspectRatioHook() {}

	public static void enter() {
		Diagnostics.projectionHook++;
		depth++;
	}

	/** Returns a replacement matrix, or null to keep the original. Only the outermost call changes anything. */
	public static Matrix4f exit(Matrix4f original) {
		depth = Math.max(0, depth - 1);
		if (depth != 0 || original == null) return null;
		if (!Modules.ASPECT_RATIO.isEnabled()) return null;

		float ratio = (float) Modules.AR_RATIO.num;
		if (ratio < 0.1f) return null;
		if (original.m33() != 0f) return null; // not a perspective matrix
		float m11 = original.m11();
		if (m11 == 0f) return null;

		Matrix4f out = new Matrix4f(original);
		out.m00(m11 / ratio);
		return out;
	}
}
