package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HangingEntity.class)
@SuppressWarnings("target")
public abstract class HangingEntityBoundingBoxMixin {
    @Unique
    private int rmc$lastHangingHitboxMutationSignature;
    @Unique
    private boolean rmc$refreshingHangingHitbox;

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

    @Inject(
            method = {
                    "tick()V",
                    "m_8119_()V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for HangingEntity#tick.")
    private void rmc$refreshCorruptedHangingBoundingBox(CallbackInfo callback) {
        if (rmc$refreshingHangingHitbox) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        int signature = CorruptionMechanicsManager.entityHitboxMutationSignature(entity);
        if (signature == rmc$lastHangingHitboxMutationSignature) {
            return;
        }

        rmc$lastHangingHitboxMutationSignature = signature;
        rmc$refreshingHangingHitbox = true;
        try {
            entity.setPos(entity.getX(), entity.getY(), entity.getZ());
        } finally {
            rmc$refreshingHangingHitbox = false;
        }
    }
}
