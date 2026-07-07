package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BellRenderer.class)
@SuppressWarnings("target")
public abstract class BellRendererAnimationMixin {
    private static final ThreadLocal<BellBlockEntity> RMC$CURRENT_BELL = new ThreadLocal<>();

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the bell currently computing its vanilla swing animation.")
    private void rmc$beginBellAnimation(BellBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_BELL.set(blockEntity);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endBellAnimation(BellBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_BELL.remove();
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BellBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts BellRenderer's vanilla sine-driven swing.")
    private float rmc$corruptBellSwing(float value) {
        return BlockEntityAnimationCorruptionHooks.mutateBellWave(RMC$CURRENT_BELL.get(), value, Mth.sin(value));
    }
}
