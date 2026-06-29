package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
@SuppressWarnings("target")
public abstract class EntityFireAndPowderSnowMixin {
    @ModifyVariable(
            method = {
                    "setRemainingFireTicks(I)V",
                    "m_7311_(I)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#setRemainingFireTicks.")
    private int rmc$corruptRemainingFireTicks(int ticks) {
        return CorruptionMechanicsManager.corruptFireTicks((Entity) (Object) this, ticks);
    }

    @Inject(
            method = {
                    "isOnFire()Z",
                    "m_20070_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#isOnFire.")
    private void rmc$corruptOnFireState(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptIsOnFire((Entity) (Object) this, callback.getReturnValue()));
    }

    @ModifyVariable(
            method = {
                    "setTicksFrozen(I)V",
                    "m_146917_(I)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#setTicksFrozen.")
    private int rmc$corruptTicksFrozen(int ticks) {
        return CorruptionMechanicsManager.corruptTicksFrozen((Entity) (Object) this, ticks);
    }

    @ModifyVariable(
            method = {
                    "setIsInPowderSnow(Z)V",
                    "m_146924_(Z)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#setIsInPowderSnow.")
    private boolean rmc$corruptPowderSnowDetection(boolean inPowderSnow) {
        return CorruptionMechanicsManager.corruptPowderSnowDetection((Entity) (Object) this, inPowderSnow);
    }
}
