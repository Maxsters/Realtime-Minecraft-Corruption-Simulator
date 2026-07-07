package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlockEntity.class)
@SuppressWarnings("target")
public abstract class ChestBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "getOpenNess(F)F",
                    "m_6683_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ChestBlockEntity#getOpenNess.")
    private void rmc$corruptChestOpenNess(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutateLidOpenNess((BlockEntity) (Object) this, partialTick, callback.getReturnValue(), "chest"));
    }
}
