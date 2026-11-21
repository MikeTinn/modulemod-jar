package com.modulesmod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Pose;

/** Draws the equipped cosmetics on the player layer. Only draws for the local player. */
public final class CosmeticsFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticsFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
		super(context);
	}

	@Override
	public void render(PoseStack matrices, SubmitNodeCollector queue, int light,
					   AvatarRenderState state, float headYaw, float headPitch) {
		if (!Modules.COSMETICS.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || state.id != mc.player.getId()) return;

		boolean sneaking = state.hasPose(Pose.CROUCHING);
		for (Cosmetics.Def def : Cosmetics.equipped()) {
			try {
				// cubes that keep their own colours, then the cubes that take the colour you picked
				CosmeticModel fixed = Cosmetics.model(def, false);
				if (fixed != null) {
					fixed.setPose(headYaw, headPitch, sneaking, state.ageInTicks);
					renderColoredCutoutModel(fixed, def.textureId(), matrices, queue, light, state, -1, 1);
				}
				if (def.tint() != 0) {
					CosmeticModel tinted = Cosmetics.model(def, true);
					if (tinted != null) {
						tinted.setPose(headYaw, headPitch, sneaking, state.ageInTicks);
						renderColoredCutoutModel(tinted, def.textureId(), matrices, queue, light, state, Cosmetics.tintColor(def), 1);
					}
				}
			} catch (Exception e) {
				Diagnostics.error("cosmetic " + def.id(), e);
			}
		}
	}
}
