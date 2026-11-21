package com.modulesmod.mixin;

import com.modulesmod.Diagnostics;
import com.modulesmod.FogTweaks;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;

/**
 * Vanilla writes the fog numbers for each frame in one private method. We adjust them on the way in.
 * The six float arguments are, in order: environmentalStart, environmentalEnd, renderDistanceStart,
 * renderDistanceEnd, skyEnd, cloudEnd. All injections use require = 0, so a rename can't crash the game.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	private static final String WRITE = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

	@Inject(method = WRITE, at = @At("HEAD"), require = 0)
	private void modulesmod$fogColor(ByteBuffer buffer, int position, Vector4f fogColor,
									 float environmentalStart, float environmentalEnd, float renderDistanceStart,
									 float renderDistanceEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
		Diagnostics.fogHook++;
		FogTweaks.tint(fogColor);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private float modulesmod$envStart(float v) {
		return FogTweaks.environmentalStart(v);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private float modulesmod$envEnd(float v) {
		return FogTweaks.environmentalEnd(v);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private float modulesmod$distStart(float v) {
		return FogTweaks.distanceStart(v);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
	private float modulesmod$distEnd(float v) {
		return FogTweaks.distanceEnd(v);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
	private float modulesmod$skyEnd(float v) {
		return FogTweaks.skyOrCloudEnd(v);
	}

	@ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 5, require = 0)
	private float modulesmod$cloudEnd(float v) {
		return FogTweaks.skyOrCloudEnd(v);
	}
}
