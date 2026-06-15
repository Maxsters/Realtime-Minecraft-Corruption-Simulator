package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityAttackableMixin {
    @Inject(
            method = {
                    "isAttackable()Z",
                    "m_6097_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#isAttackable.")
    private void rmc$corruptAttackability(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && CorruptionMechanicsManager.shouldDisableEntityTargeting((Entity) (Object) this, "attackable")) {
            callback.setReturnValue(false);
        }
    }
}
