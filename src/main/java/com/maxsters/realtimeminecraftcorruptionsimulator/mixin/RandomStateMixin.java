package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the mapped development name for NoiseRouter#mapAll.")
    private NoiseRouter rmc$installRuntimeDensityCorruption(NoiseRouter router, DensityFunction.Visitor visitor) {
        return WorldgenCorruptionHooks.corruptRuntimeNoiseRouter(router.mapAll(visitor));
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;m_224412_(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG runtime name for NoiseRouter#mapAll.")
    private NoiseRouter rmc$installRuntimeDensityCorruptionSrg(NoiseRouter router, DensityFunction.Visitor visitor) {
        return rmc$installRuntimeDensityCorruption(router, visitor);
    }
}
