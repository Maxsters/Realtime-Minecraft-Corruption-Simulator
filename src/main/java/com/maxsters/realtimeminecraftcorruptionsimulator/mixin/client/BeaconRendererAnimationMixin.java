package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockEntityAnimationCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
@SuppressWarnings("target")
public abstract class BeaconRendererAnimationMixin {
    private static final ThreadLocal<BeaconBlockEntity> RMC$CURRENT_BEACON = new ThreadLocal<>();

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the beacon currently computing beam animation time.")
    private void rmc$beginBeaconAnimation(BeaconBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_BEACON.set(blockEntity);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endBeaconAnimation(BeaconBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo callback) {
        RMC$CURRENT_BEACON.remove();
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts BeaconRenderer's vanilla beam scroll time.")
    private long rmc$corruptBeaconBeamTime(Level level) {
        long gameTime = level == null ? 0L : level.getGameTime();
        return BlockEntityAnimationCorruptionHooks.mutateAnimationGameTime(RMC$CURRENT_BEACON.get(), gameTime, "beacon_beam_time");
    }
}
