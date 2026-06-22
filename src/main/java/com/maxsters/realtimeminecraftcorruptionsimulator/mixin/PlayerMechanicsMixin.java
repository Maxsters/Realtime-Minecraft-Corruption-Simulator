package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
@SuppressWarnings("target")
public abstract class PlayerMechanicsMixin {
    @Unique
    private int rmc$lastPlayerHitboxMutationSignature;
    @Unique
    private boolean rmc$refreshingPlayerHitboxDimensions;

    @ModifyVariable(
            method = {
                    "causeFoodExhaustion(F)V",
                    "m_36399_(F)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#causeFoodExhaustion.")
    private float rmc$corruptFoodExhaustion(float exhaustion) {
        return CorruptionMechanicsManager.corruptFoodExhaustion((Player) (Object) this, exhaustion);
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
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#travel.")
    private Vec3 rmc$corruptTravelVector(Vec3 travel) {
        return CorruptionMechanicsManager.corruptTravelVector((Player) (Object) this, travel);
    }

    @Inject(
            method = {
                    "getAttackStrengthScale(F)F",
                    "m_36403_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#getAttackStrengthScale.")
    private void rmc$corruptAttackStrengthScale(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptAttackStrengthScale((Player) (Object) this, callback.getReturnValue(), partialTick));
    }

    @Inject(
            method = {
                    "getCurrentItemAttackStrengthDelay()F",
                    "m_36333_()F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#getCurrentItemAttackStrengthDelay.")
    private void rmc$corruptAttackStrengthDelay(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptAttackStrengthDelay((Player) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "attack(Lnet/minecraft/world/entity/Entity;)V",
                    "m_5706_(Lnet/minecraft/world/entity/Entity;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#attack.")
    private void rmc$cancelCorruptedAttack(Entity target, CallbackInfo callback) {
        if (CorruptionMechanicsManager.shouldCancelPlayerAttack((Player) (Object) this, target)) {
            callback.cancel();
        }
    }

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
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#getDimensions.")
    private void rmc$corruptPlayerHitboxDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptEntityHitboxDimensions((Player) (Object) this, callback.getReturnValue()));
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
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Player#tick.")
    private void rmc$refreshCorruptedPlayerHitboxDimensions(CallbackInfo callback) {
        if (rmc$refreshingPlayerHitboxDimensions) {
            return;
        }

        Player player = (Player) (Object) this;
        int signature = CorruptionMechanicsManager.entityHitboxMutationSignature(player);
        if (signature == rmc$lastPlayerHitboxMutationSignature) {
            return;
        }

        rmc$lastPlayerHitboxMutationSignature = signature;
        rmc$refreshingPlayerHitboxDimensions = true;
        try {
            player.refreshDimensions();
        } finally {
            rmc$refreshingPlayerHitboxDimensions = false;
        }
    }
}
