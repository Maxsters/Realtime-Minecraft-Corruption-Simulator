package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HangingEntity.class)
@SuppressWarnings("target")
public abstract class HangingEntityBoundingBoxMixin {
    @Inject(
            method = {
                    "recalculateBoundingBox()V",
                    "m_7087_()V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for HangingEntity#recalculateBoundingBox.")
    private void rmc$corruptHangingBoundingBox(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        entity.setBoundingBox(CorruptionMechanicsManager.corruptEntityHitboxBounds(entity, entity.getBoundingBox()));
    }
}
