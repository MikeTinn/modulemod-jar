package com.modulesmod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Draws the outline (and optional fill) of the block you are looking at through vanilla's gizmo
 * renderer, so it is depth tested like the normal outline. Called every frame from DebugRendererMixin.
 */
public final class BlockOutlineRenderer {
	private static volatile long lastCallMs = 0;

	private BlockOutlineRenderer() {}

	/**
	 * True when the vanilla outline should be hidden. Only true while our own drawing hook is actually
	 * running, so if a Minecraft update breaks the hook you still get the normal outline.
	 */
	public static boolean replacesVanilla() {
		return Modules.BLOCK_OUTLINE.isEnabled() && Modules.BO_REPLACE.bool
				&& System.currentTimeMillis() - lastCallMs < 1000;
	}

	public static void emit(float tickProgress) {
		lastCallMs = System.currentTimeMillis();
		if (!Modules.BLOCK_OUTLINE.isEnabled()) return;
		Diagnostics.outlineEmit++;
		try {
			draw();
		} catch (Exception e) {
			Diagnostics.error("block outline", e);
		}
	}

	private static void draw() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.options.hideGui) return;

		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return;

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir()) return;
		VoxelShape shape = state.getShape(mc.level, pos);
		if (shape.isEmpty()) return;

		// ---- settings ----
		int shapeMode = Modules.BO_SHAPE.index();        // 0 real shape, 1 bounding box, 2 full cube
		int colorMode = Modules.BO_COLOR_MODE.index();   // 0 solid, 1 rainbow, 2 fade, 3 pulse
		int base = HudUtil.rgb(Modules.BO_COLOR.str, 0xFFFFFF);
		int second = HudUtil.rgb(Modules.BO_COLOR2.str, 0xFF55FF);
		double speed = Modules.BO_SPEED.num;
		float width = (float) Modules.BO_WIDTH.num;
		double expand = Modules.BO_EXPAND.num;
		boolean glow = Modules.BO_GLOW.bool;
		int fillMode = Modules.BO_FILL.index();          // 0 off, 1 whole block, 2 hit face only

		// ---- colour (animated modes use real time) ----
		double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * speed;
		int rgb = base;
		double alphaMul = 1.0;
		switch (colorMode) {
			case 1 -> rgb = HudUtil.hsvToRgb((float) ((t * 0.2) % 1.0), 1f, 1f);
			case 2 -> rgb = HudUtil.lerpRgb(base, second, 0.5 + 0.5 * Math.sin(t * Math.PI));
			case 3 -> alphaMul = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 2 * Math.PI));
			default -> { }
		}
		int strokeAlpha = (int) Math.round(HudUtil.alpha(Modules.BO_OPACITY.num) * alphaMul);

		// ---- fill first, so the lines sit on top of it ----
		if (fillMode != 0) {
			int fillRgb = Modules.BO_FILL_MATCH.bool ? rgb : HudUtil.rgb(Modules.BO_FILL_COLOR.str, 0xFFFFFF);
			int fillAlpha = (int) Math.round(HudUtil.alpha(Modules.BO_FILL_OPACITY.num) * alphaMul);
			if (fillAlpha > 0) {
				GizmoStyle fill = GizmoStyle.fill((fillAlpha << 24) | fillRgb);
				if (fillMode == 1) {
					for (AABB b : fillBoxes(shape, shapeMode)) {
						Gizmos.cuboid(b.move(pos).inflate(expand), fill);
					}
				} else {
					Gizmos.cuboid(faceSlab(shape, shapeMode, pos, blockHit.getDirection(), expand), fill);
				}
			}
		}

		// ---- outline (with an optional wider, fainter pass underneath as a glow) ----
		if (strokeAlpha > 0) {
			if (glow) {
				drawOutline(shape, shapeMode, pos, expand, ((strokeAlpha / 4) << 24) | rgb, width * 2.4f);
			}
			drawOutline(shape, shapeMode, pos, expand, (strokeAlpha << 24) | rgb, width);
		}
	}

	private static java.util.List<AABB> fillBoxes(VoxelShape shape, int shapeMode) {
		if (shapeMode == 2) return java.util.List.of(new AABB(0, 0, 0, 1, 1, 1));
		if (shapeMode == 1) return java.util.List.of(shape.bounds());
		return shape.toAabbs();
	}

	/** A paper-thin box sitting on the face you are looking at. */
	private static AABB faceSlab(VoxelShape shape, int shapeMode, BlockPos pos, Direction side, double expand) {
		AABB bb = (shapeMode == 2 ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos).inflate(expand);
		double t = 0.002;
		return switch (side) {
			case UP -> new AABB(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY + t, bb.maxZ);
			case DOWN -> new AABB(bb.minX, bb.minY - t, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
			case NORTH -> new AABB(bb.minX, bb.minY, bb.minZ - t, bb.maxX, bb.maxY, bb.minZ);
			case SOUTH -> new AABB(bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ + t);
			case WEST -> new AABB(bb.minX - t, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.maxZ);
			case EAST -> new AABB(bb.maxX, bb.minY, bb.minZ, bb.maxX + t, bb.maxY, bb.maxZ);
		};
	}

	private static void drawOutline(VoxelShape shape, int shapeMode, BlockPos pos, double expand, int argb, float width) {
		if (shapeMode == 0) {
			// every edge of the real shape (stairs, fences, slabs...), pushed outward by "expand"
			shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> Gizmos.line(
					new Vec3(pos.getX() + grow(x1, expand), pos.getY() + grow(y1, expand), pos.getZ() + grow(z1, expand)),
					new Vec3(pos.getX() + grow(x2, expand), pos.getY() + grow(y2, expand), pos.getZ() + grow(z2, expand)),
					argb, width));
		} else {
			AABB box = (shapeMode == 2 ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos).inflate(expand);
			Gizmos.cuboid(box, GizmoStyle.stroke(argb, width));
		}
	}

	/** Moves a coordinate (0-1 inside the block) away from the block's centre by e. */
	private static double grow(double c, double e) {
		return c > 0.5 ? c + e : (c < 0.5 ? c - e : c);
	}
}
