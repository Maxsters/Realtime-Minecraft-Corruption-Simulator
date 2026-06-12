package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityFluidStateMixin {
    @Inject(
            method = {
                    "isInWater()Z",
                    "m_20069_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptInWater(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFluidDetection((Entity) (Object) this, "in_water", callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "isInLava()Z",
                    "m_20077_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptInLava(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFluidDetection((Entity) (Object) this, "in_lava", callback.getReturnValue()));
    }

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
    private void rmc$corruptUnderWater(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFluidDetection((Entity) (Object) this, "underwater", callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z",
                    "m_204029_(Lnet/minecraft/tags/TagKey;)Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptEyeInFluid(TagKey<Fluid> fluidTag, CallbackInfoReturnable<Boolean> callback) {
        String tag = fluidTag == null ? "unknown" : fluidTag.location().toString();
        callback.setReturnValue(CorruptionMechanicsManager.corruptFluidDetection((Entity) (Object) this, "eye:" + tag, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "isPushedByFluid()Z",
                    "m_6063_()Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptFluidPush(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFluidPush((Entity) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "updateSwimming()V",
                    "m_5844_()V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptSwimmingState(CallbackInfo callback) {
        CorruptionMechanicsManager.corruptSwimmingState((Entity) (Object) this);
    }
}
