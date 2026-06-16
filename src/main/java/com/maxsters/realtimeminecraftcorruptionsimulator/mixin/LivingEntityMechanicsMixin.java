package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMechanicsMixin {
    @Inject(
            method = {
                    "getJumpPower()F",
                    "m_6118_()F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LivingEntity#getJumpPower.")
    private void rmc$corruptJumpPower(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptJumpPower((LivingEntity) (Object) this, callback.getReturnValue()));
    }

    @ModifyVariable(
            method = {
                    "travel(Lnet/minecraft/world/phys/Vec3;)V",
                    "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LivingEntity#travel.")
    private Vec3 rmc$corruptTravelVector(Vec3 travel) {
        return CorruptionMechanicsManager.corruptTravelVector((LivingEntity) (Object) this, travel);
    }
}
