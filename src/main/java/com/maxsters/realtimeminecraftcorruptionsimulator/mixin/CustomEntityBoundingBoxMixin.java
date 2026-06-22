package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Interaction.class, Shulker.class})
@SuppressWarnings("target")
public abstract class CustomEntityBoundingBoxMixin {
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
    @Dynamic("Targets mapped dev names and SRG runtime aliases for vanilla makeBoundingBox overrides.")
    private void rmc$corruptCustomEntityBoundingBox(CallbackInfoReturnable<AABB> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptEntityHitboxBounds((Entity) (Object) this, callback.getReturnValue()));
    }
}
