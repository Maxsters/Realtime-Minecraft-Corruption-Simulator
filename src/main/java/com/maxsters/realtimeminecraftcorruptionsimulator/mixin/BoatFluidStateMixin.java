package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Boat.class)
public abstract class BoatFluidStateMixin {
    @Inject(
            method = {
                    "isUnderWater()Z",
                    "m_5842_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptBoatUnderWater(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptBoatUnderWater((Boat) (Object) this, callback.getReturnValue()));
    }
}
