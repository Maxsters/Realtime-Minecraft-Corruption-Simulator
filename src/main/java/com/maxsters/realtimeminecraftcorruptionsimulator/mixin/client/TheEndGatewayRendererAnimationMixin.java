package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockEntityAnimationCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.TheEndGatewayRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndGatewayRenderer.class)
@SuppressWarnings("target")
public abstract class TheEndGatewayRendererAnimationMixin {
    private static final ThreadLocal<TheEndGatewayBlockEntity> RMC$CURRENT_GATEWAY = new ThreadLocal<>();

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the end gateway currently computing beam animation time.")
    private void rmc$beginGatewayAnimation(TheEndGatewayBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_GATEWAY.set(blockEntity);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endGatewayAnimation(TheEndGatewayBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_GATEWAY.remove();
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts TheEndGatewayRenderer's vanilla beam scroll time.")
    private long rmc$corruptGatewayBeamTime(Level level) {
        long gameTime = level == null ? 0L : level.getGameTime();
        return BlockEntityAnimationCorruptionHooks.mutateAnimationGameTime(RMC$CURRENT_GATEWAY.get(), gameTime, "end_gateway_beam_time");
    }
}
