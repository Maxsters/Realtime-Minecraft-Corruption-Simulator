package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Climate.Sampler.class)
public abstract class ClimateSamplerMixin {
    @Inject(
            method = {
                    "sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
                    "m_183445_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptClimateSample(int quartX, int quartY, int quartZ, CallbackInfoReturnable<Climate.TargetPoint> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptClimateSample(callback.getReturnValue(), quartX, quartY, quartZ));
    }
}
