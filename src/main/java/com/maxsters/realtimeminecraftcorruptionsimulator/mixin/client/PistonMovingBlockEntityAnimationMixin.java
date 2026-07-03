package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockEntityAnimationCorruptionHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonMovingBlockEntity.class)
@SuppressWarnings("target")
public abstract class PistonMovingBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "getProgress(F)F",
                    "m_60350_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for PistonMovingBlockEntity#getProgress.")
    private void rmc$corruptPistonProgress(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutatePistonProgress((BlockEntity) (Object) this, partialTick, callback.getReturnValue()));
    }
}
