package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
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
    private static final ThreadLocal<Boolean> RMC$BEACON_BEAM_POSE_APPLIED = ThreadLocal.withInitial(() -> false);

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

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
                    "m_6922_(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_46467_()J"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts BeaconRenderer's SRG vanilla beam scroll time.")
    private long rmc$corruptBeaconBeamTimeSrg(Level level) {
        long gameTime = level == null ? 0L : level.getGameTime();
        return BlockEntityAnimationCorruptionHooks.mutateAnimationGameTime(RMC$CURRENT_BEACON.get(), gameTime, "beacon_beam_time");
    }

    @Inject(
            method = {
                    "renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V",
                    "m_112184_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Wraps beacon beam rendering in a corrupted pose scope.")
    private static void rmc$beginBeaconBeamGeometry(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float partialTick, float textureScale, long gameTime, int yOffset, int height, float[] color, float radius, float glowRadius, CallbackInfo callback) {
        if (poseStack == null) {
            RMC$BEACON_BEAM_POSE_APPLIED.set(false);
            return;
        }
        poseStack.pushPose();
        ModelRenderCorruptionHooks.mutateBeaconBeamGeometry(poseStack, texture, gameTime, yOffset, height, radius, glowRadius);
        RMC$BEACON_BEAM_POSE_APPLIED.set(true);
    }

    @Inject(
            method = {
                    "renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V",
                    "m_112184_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void rmc$endBeaconBeamGeometry(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float partialTick, float textureScale, long gameTime, int yOffset, int height, float[] color, float radius, float glowRadius, CallbackInfo callback) {
        if (RMC$BEACON_BEAM_POSE_APPLIED.get() && poseStack != null) {
            poseStack.popPose();
        }
        RMC$BEACON_BEAM_POSE_APPLIED.remove();
    }

    @Inject(
            method = {
                    "renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V",
                    "m_112184_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", shift = At.Shift.AFTER),
            remap = false,
            require = 0
    )
    @Dynamic("Beacon beams draw raw vertices, so model-part geometry hooks cannot reach them.")
    private static void rmc$corruptBeaconBeamGeometry(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float partialTick, float textureScale, long gameTime, int yOffset, int height, float[] color, float radius, float glowRadius, CallbackInfo callback) {
        ModelRenderCorruptionHooks.mutateBeaconBeamGeometry(poseStack, texture, gameTime, yOffset, height, radius, glowRadius);
    }

    @Inject(
            method = {
                    "renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V",
                    "m_112184_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJII[FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V", shift = At.Shift.AFTER),
            remap = false,
            require = 0
    )
    @Dynamic("Beacon beams draw raw vertices, so model-part geometry hooks cannot reach their SRG path.")
    private static void rmc$corruptBeaconBeamGeometrySrg(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float partialTick, float textureScale, long gameTime, int yOffset, int height, float[] color, float radius, float glowRadius, CallbackInfo callback) {
        ModelRenderCorruptionHooks.mutateBeaconBeamGeometry(poseStack, texture, gameTime, yOffset, height, radius, glowRadius);
    }
}
