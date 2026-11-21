package com.modulesmod.mixin;

import com.modulesmod.WeatherTweaks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Weather and time are read through these World methods. WeatherTweaks only changes the answer on the
 * client world (never on the integrated server), and every injection uses require = 0.
 */
@Mixin(Level.class)
public abstract class WorldMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$rain(float delta, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(WeatherTweaks.rain((Level) (Object) this, cir.getReturnValue()));
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(WeatherTweaks.thunder((Level) (Object) this, cir.getReturnValue()));
	}

	@Inject(method = {"getDayTime", "getLunarTime"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void modulesmod$time(CallbackInfoReturnable<Long> cir) {
		cir.setReturnValue(WeatherTweaks.timeOfDay((Level) (Object) this, cir.getReturnValue()));
	}
}
