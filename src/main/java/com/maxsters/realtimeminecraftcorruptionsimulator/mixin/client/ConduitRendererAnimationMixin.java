package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConduitRenderer.class)
@SuppressWarnings("target")
public abstract class ConduitRendererAnimationMixin {
    private static final ThreadLocal<ConduitBlockEntity> RMC$CURRENT_CONDUIT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RMC$CONDUIT_POSE_APPLIED = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the conduit currently computing renderer-local bobbing.")
    private void rmc$beginConduitAnimation(ConduitBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_CONDUIT.set(blockEntity);
        RMC$CONDUIT_POSE_APPLIED.set(BlockEntityAnimationCorruptionHooks.beginConduitRenderPose(blockEntity, partialTick, poseStack));
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endConduitAnimation(ConduitBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        BlockEntityAnimationCorruptionHooks.endConduitRenderPose(poseStack, RMC$CONDUIT_POSE_APPLIED.get());
        RMC$CURRENT_CONDUIT.remove();
        RMC$CONDUIT_POSE_APPLIED.remove();
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts ConduitRenderer's vanilla vertical bobbing sine.")
    private float rmc$corruptConduitBob(float value) {
        return BlockEntityAnimationCorruptionHooks.mutateConduitBob(RMC$CURRENT_CONDUIT.get(), value, Mth.sin(value));
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14031_(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts ConduitRenderer's SRG vanilla vertical bobbing sine.")
    private float rmc$corruptConduitBobSrg(float value) {
        return BlockEntityAnimationCorruptionHooks.mutateConduitBob(RMC$CURRENT_CONDUIT.get(), value, Mth.sin(value));
    }
}
