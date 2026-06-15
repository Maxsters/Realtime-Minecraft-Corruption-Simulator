package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTargetingMixin {
    @Inject(
            method = {
                    "isPickable()Z",
                    "m_6087_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LivingEntity#isPickable.")
    private void rmc$corruptPickability(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && CorruptionMechanicsManager.shouldDisableEntityTargeting((LivingEntity) (Object) this, "pickable")) {
            callback.setReturnValue(false);
        }
    }
}
