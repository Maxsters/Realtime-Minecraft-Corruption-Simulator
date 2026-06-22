package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemFrame.class, LeashFenceKnotEntity.class})
@SuppressWarnings("target")
public abstract class HangingEntitySubclassBoundingBoxMixin {
    @Inject(
            method = {
                    "recalculateBoundingBox()V",
                    "m_7087_()V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for hanging-entity subclass bounding-box recalculation.")
    private void rmc$corruptHangingSubclassBoundingBox(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        entity.setBoundingBox(CorruptionMechanicsManager.corruptEntityHitboxBounds(entity, entity.getBoundingBox()));
    }
}
