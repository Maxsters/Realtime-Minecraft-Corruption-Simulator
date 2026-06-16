package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    @Unique
    private boolean rmc$restoreNoPhysicsAfterMove;
    @Unique
    private boolean rmc$previousNoPhysics;

    @Inject(
            method = {
                    "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                    "m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#move.")
    private void rmc$beginCorruptedNoPhysicsMove(MoverType moverType, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        rmc$restoreNoPhysicsAfterMove = false;
        rmc$previousNoPhysics = entity.noPhysics;
        if (!entity.noPhysics && CorruptionMechanicsManager.shouldTemporarilyBypassBlockCollision(entity, moverType, movement)) {
            entity.noPhysics = true;
            rmc$restoreNoPhysicsAfterMove = true;
        }
    }

    @Inject(
            method = {
                    "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                    "m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#move.")
    private void rmc$endCorruptedNoPhysicsMove(MoverType moverType, Vec3 movement, CallbackInfo callback) {
        if (rmc$restoreNoPhysicsAfterMove) {
            ((Entity) (Object) this).noPhysics = rmc$previousNoPhysics;
            rmc$restoreNoPhysicsAfterMove = false;
        }
    }

    @Inject(
            method = {
                    "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    "m_20272_(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$corruptCollisionResolution(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptCollisionResolution((Entity) (Object) this, movement, callback.getReturnValue()));
    }
}
