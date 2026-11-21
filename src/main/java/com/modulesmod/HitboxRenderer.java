package com.modulesmod;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws entity hitboxes through vanilla's own gizmo renderer (the same one F3+B uses), so they are
 * depth tested like vanilla: nothing shows through walls, and invisible entities stay hidden.
 * Called every frame from DebugRendererMixin.
 */
public final class HitboxRenderer {
	private HitboxRenderer() {}

	public static void emit(float tickProgress) {
		if (!Modules.HITBOX.isEnabled()) return;
		Diagnostics.hitboxEmit++;
		try {
			draw(tickProgress);
		} catch (Exception e) {
			Diagnostics.error("hitbox", e);
		}
	}

	private static void draw(float tickProgress) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		// same rule as vanilla F3+B: servers can turn debug info off, and then no hitboxes
		if (mc.player.isReducedDebugInfo()) return;

		int base = HudUtil.rgb(Modules.HB_COLOR.str, 0xFFFFFF);
		int aim = HudUtil.rgb(Modules.HB_AIM_COLOR.str, 0xFF5555);
		float width = (float) Modules.HB_WIDTH.num;
		boolean fill = Modules.HB_FILL.bool;
		int fillAlpha = HudUtil.alpha(Modules.HB_FILL_OPACITY.num);
		boolean hideEye = Modules.HB_HIDE_EYE.bool;
		boolean hideLook = Modules.HB_HIDE_LOOK.bool;
		int target = Modules.HB_TARGET.index(); // 0 everything, 1 players, 2 everything but players

		Entity camera = mc.getCameraEntity();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.isInvisible()) continue;
			if (e == camera && firstPerson) continue;
			if (mc.player.distanceToSqr(e) > 96.0 * 96.0) continue;

			boolean isPlayer = e instanceof Player;
			if (target == 1 && !isPlayer) continue;
			if (target == 2 && isPlayer) continue;

			Vec3 lerped = e.getPosition(tickProgress);
			Vec3 off = new Vec3(lerped.x - e.getX(), lerped.y - e.getY(), lerped.z - e.getZ());
			AABB box = e.getBoundingBox().move(off);

			int rgb = (mc.crosshairPickEntity == e) ? aim : base;
			int stroke = 0xFF000000 | rgb;
			GizmoStyle style = fill
					? GizmoStyle.strokeAndFill(stroke, width, (fillAlpha << 24) | rgb)
					: GizmoStyle.stroke(stroke, width);
			Gizmos.cuboid(box, style);

			if (e instanceof LivingEntity) {
				double eyeY = e.getEyeY() + off.y;
				if (!hideEye) {
					AABB eyeBox = new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ);
					Gizmos.cuboid(eyeBox, GizmoStyle.stroke(stroke, width));
				}
				if (!hideLook) {
					Vec3 eye = new Vec3(lerped.x, eyeY, lerped.z);
					Vec3 dir = e.getViewVector(tickProgress);
					Gizmos.line(eye, eye.add(dir.scale(2.0)), stroke, width);
				}
			}
		}
	}
}
