package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.LiquidRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @Inject(
            method = {
                    "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
                    "m_234369_"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$beginCorruptedLiquidMesh(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState blockState, FluidState fluidState, CallbackInfo callback) {
        if (LiquidRenderCorruptionHooks.shouldDropLiquidMesh(pos, fluidState)) {
            callback.cancel();
        }
    }

    @ModifyVariable(
            method = {
                    "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
                    "m_234369_"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$wrapCorruptedLiquidConsumer(VertexConsumer consumer,
                                                          BlockAndTintGetter level,
                                                          BlockPos pos,
                                                          VertexConsumer originalConsumer,
                                                          BlockState blockState,
                                                          FluidState fluidState) {
        return LiquidRenderCorruptionHooks.wrapConsumer(consumer, pos, fluidState);
    }
}
