package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConduitBlockEntity.class)
@SuppressWarnings("target")
public abstract class ConduitBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "getActiveRotation(F)F",
                    "m_59197_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ConduitBlockEntity#getActiveRotation.")
    private void rmc$corruptConduitRotation(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutateConduitRotation((BlockEntity) (Object) this, partialTick, callback.getReturnValue()));
    }
}
