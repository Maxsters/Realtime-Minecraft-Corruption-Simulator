package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
@SuppressWarnings("target")
public abstract class EntityHitboxMixin {
    @Unique
    private int rmc$lastHitboxMutationSignature;
    @Unique
    private boolean rmc$rebuildingHitbox;

    @Inject(
            method = {
                    "makeBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    "m_142242_()Lnet/minecraft/world/phys/AABB;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets mapped dev names and SRG runtime aliases for Entity#makeBoundingBox.")
    private void rmc$corruptBoundingBox(CallbackInfoReturnable<AABB> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptEntityHitboxBounds(
                (Entity) (Object) this,
                callback.getReturnValue()
        ));
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
    @Dynamic("Targets mapped dev names and SRG runtime aliases for Entity#tick.")
    private void rmc$rebuildHitboxWhenMutationChanges(CallbackInfo callback) {
        if (rmc$rebuildingHitbox) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        int signature = CorruptionMechanicsManager.entityHitboxMutationSignature(entity);
        if (signature == rmc$lastHitboxMutationSignature) {
            return;
        }

        rmc$lastHitboxMutationSignature = signature;
        rmc$rebuildingHitbox = true;
        try {
            // setPos rebuilds from the entity's real box constructor, including subclass overrides.
            entity.setPos(entity.getX(), entity.getY(), entity.getZ());
        } finally {
            rmc$rebuildingHitbox = false;
        }
    }
}
