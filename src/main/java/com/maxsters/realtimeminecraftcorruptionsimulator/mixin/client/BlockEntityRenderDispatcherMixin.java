package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.BlockEntityRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
@SuppressWarnings("target")
public abstract class BlockEntityRenderDispatcherMixin {
    private static final ThreadLocal<Boolean> RMC$BLOCK_ENTITY_POSE_APPLIED = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
                    "m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockEntityRenderDispatcher#render.")
    private <E extends BlockEntity> void rmc$beginCorruptedBlockEntityRender(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo callback) {
        ModelRenderCorruptionHooks.beginBlockEntityRender(blockEntity, partialTick);
        RMC$BLOCK_ENTITY_POSE_APPLIED.set(BlockEntityRenderCorruptionHooks.beginRender(blockEntity, partialTick, poseStack));
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
                    "m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockEntityRenderDispatcher#render.")
    private <E extends BlockEntity> void rmc$endCorruptedBlockEntityRender(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo callback) {
        BlockEntityRenderCorruptionHooks.endRender(poseStack, RMC$BLOCK_ENTITY_POSE_APPLIED.get());
        ModelRenderCorruptionHooks.endBlockEntityRender();
        RMC$BLOCK_ENTITY_POSE_APPLIED.remove();
    }
}
