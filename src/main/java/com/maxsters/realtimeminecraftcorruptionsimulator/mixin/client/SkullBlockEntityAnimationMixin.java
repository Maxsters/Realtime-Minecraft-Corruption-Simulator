package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockEntityAnimationCorruptionHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkullBlockEntity.class)
@SuppressWarnings("target")
public abstract class SkullBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "getAnimation(F)F",
                    "m_261082_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SkullBlockEntity#getAnimation.")
    private void rmc$corruptSkullAnimation(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutateSkullAnimation((BlockEntity) (Object) this, partialTick, callback.getReturnValue()));
    }
}
