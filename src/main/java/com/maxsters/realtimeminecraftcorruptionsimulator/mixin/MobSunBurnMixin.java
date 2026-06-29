package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
@SuppressWarnings("target")
public abstract class MobSunBurnMixin {
    @Inject(
            method = {
                    "isSunBurnTick()Z",
                    "m_21527_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Mob#isSunBurnTick.")
    private void rmc$corruptSunBurnTick(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && CorruptionMechanicsManager.shouldDisableSunBurn((Mob) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
