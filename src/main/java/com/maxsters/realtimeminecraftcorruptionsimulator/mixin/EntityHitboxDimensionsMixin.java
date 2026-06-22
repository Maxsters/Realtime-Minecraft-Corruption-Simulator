package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
@SuppressWarnings("target")
public abstract class EntityHitboxDimensionsMixin {
    @Unique
    private int rmc$lastHitboxMutationSignature;
    @Unique
    private boolean rmc$refreshingHitboxDimensions;

    @Inject(
            method = {
                    "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
                    "m_6972_(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#getDimensions.")
    private void rmc$corruptHitboxDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptEntityHitboxDimensions((Entity) (Object) this, callback.getReturnValue()));
    }

    @Redirect(
            method = "refreshDimensions()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"
            ),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the mapped Entity#refreshDimensions lookup of the final virtual dimensions.")
    private EntityDimensions rmc$corruptRefreshHitboxDimensions(Entity entity, Pose pose) {
        return rmc$corruptRefreshDimensionLookup(entity, pose);
    }

    @Redirect(
            method = "m_6210_()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_6972_(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"
            ),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG Entity#refreshDimensions lookup of the final virtual dimensions.")
    private EntityDimensions rmc$corruptRefreshHitboxDimensionsSrg(Entity entity, Pose pose) {
        return rmc$corruptRefreshDimensionLookup(entity, pose);
    }

    @Unique
    private EntityDimensions rmc$corruptRefreshDimensionLookup(Entity entity, Pose pose) {
        CorruptionMechanicsManager.beginEntityHitboxDimensionBypass();
        EntityDimensions dimensions;
        try {
            dimensions = entity.getDimensions(pose);
        } finally {
            CorruptionMechanicsManager.endEntityHitboxDimensionBypass();
        }
        return CorruptionMechanicsManager.corruptEntityHitboxDimensions(entity, dimensions);
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
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#tick.")
    private void rmc$refreshCorruptedHitboxDimensions(CallbackInfo callback) {
        if (rmc$refreshingHitboxDimensions) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        int signature = CorruptionMechanicsManager.entityHitboxMutationSignature(entity);
        if (signature == rmc$lastHitboxMutationSignature) {
            return;
        }

        rmc$lastHitboxMutationSignature = signature;
        rmc$refreshingHitboxDimensions = true;
        try {
            entity.refreshDimensions();
        } finally {
            rmc$refreshingHitboxDimensions = false;
        }
    }
}
