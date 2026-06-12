package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseRouter.class)
public abstract class NoiseRouterMixin {
    @Inject(method = {"continents()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209386_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptContinents(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("continents", callback.getReturnValue()));
    }

    @Inject(method = {"erosion()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209387_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptErosion(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("erosion", callback.getReturnValue()));
    }

    @Inject(method = {"depth()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209388_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptDepth(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("depth", callback.getReturnValue()));
    }

    @Inject(method = {"ridges()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209389_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptRidges(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("ridges", callback.getReturnValue()));
    }

    @Inject(method = {"initialDensityWithoutJaggedness()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209390_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptInitialDensity(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("initial_density", callback.getReturnValue()));
    }

    @Inject(method = {"finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209391_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptFinalDensity(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("final_density", callback.getReturnValue()));
    }

    @Inject(method = {"temperature()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_209384_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptTemperature(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("temperature", callback.getReturnValue()));
    }

    @Inject(method = {"vegetation()Lnet/minecraft/world/level/levelgen/DensityFunction;", "f_224392_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rmc$corruptVegetation(CallbackInfoReturnable<DensityFunction> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptDensity("vegetation", callback.getReturnValue()));
    }
}
