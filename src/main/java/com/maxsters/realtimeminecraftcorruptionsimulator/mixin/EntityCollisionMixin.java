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
@SuppressWarnings("target")
public abstract class EntityCollisionMixin {
    @Unique
    private int rmc$corruptedNoPhysicsTicks;
    @Unique
    private boolean rmc$forcedNoPhysics;
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
        int bypassTicks = CorruptionMechanicsManager.corruptedBlockCollisionBypassTicks(entity, moverType, movement);
        if (bypassTicks > rmc$corruptedNoPhysicsTicks) {
            rmc$corruptedNoPhysicsTicks = bypassTicks;
        }
        if (rmc$corruptedNoPhysicsTicks <= 0) {
            return;
        }
        if (!rmc$forcedNoPhysics) {
            rmc$previousNoPhysics = entity.noPhysics;
        }
        if (!entity.noPhysics) {
            entity.noPhysics = true;
        }
        rmc$forcedNoPhysics = true;
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
    private void rmc$tickCorruptedNoPhysics(CallbackInfo callback) {
        if (rmc$corruptedNoPhysicsTicks > 0) {
            rmc$corruptedNoPhysicsTicks--;
        }
        if (rmc$corruptedNoPhysicsTicks <= 0 && rmc$forcedNoPhysics) {
            ((Entity) (Object) this).noPhysics = rmc$previousNoPhysics;
            rmc$forcedNoPhysics = false;
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

    @Inject(
            method = {
                    "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    "m_20272_(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$bypassCollisionResolution(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (rmc$corruptedNoPhysicsTicks > 0 || CorruptionMechanicsManager.shouldResolveBlockCollisionAsEmpty(entity, movement)) {
            callback.setReturnValue(CorruptionMechanicsManager.corruptCollisionResolution(entity, movement, movement));
        }
    }

    @Inject(
            method = {
                    "isInWall()Z",
                    "m_20070_()Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Entity#isInWall.")
    private void rmc$suppressCorruptedWallCheck(CallbackInfoReturnable<Boolean> callback) {
        if (rmc$corruptedNoPhysicsTicks > 0 || CorruptionMechanicsManager.shouldSuppressCorruptedWallCheck((Entity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
