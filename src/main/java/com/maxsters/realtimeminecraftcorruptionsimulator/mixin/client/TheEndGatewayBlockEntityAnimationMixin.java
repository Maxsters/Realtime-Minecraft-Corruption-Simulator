package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TheEndGatewayBlockEntity.class)
@SuppressWarnings("target")
public abstract class TheEndGatewayBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "getSpawnPercent(F)F",
                    "m_59933_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for TheEndGatewayBlockEntity#getSpawnPercent.")
    private void rmc$corruptGatewaySpawnPercent(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutateEndGatewayPercent((BlockEntity) (Object) this, partialTick, callback.getReturnValue(), "spawn_percent"));
    }

    @Inject(
            method = {
                    "getCooldownPercent(F)F",
                    "m_59967_(F)F"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for TheEndGatewayBlockEntity#getCooldownPercent.")
    private void rmc$corruptGatewayCooldownPercent(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockEntityAnimationCorruptionHooks.mutateEndGatewayPercent((BlockEntity) (Object) this, partialTick, callback.getReturnValue(), "cooldown_percent"));
    }
}
