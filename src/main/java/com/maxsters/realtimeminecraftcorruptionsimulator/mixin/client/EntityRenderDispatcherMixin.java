package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
@SuppressWarnings("target")
public abstract class EntityRenderDispatcherMixin {
    private static final ThreadLocal<Boolean> RMC$SHADOW_POSE_APPLIED = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_114384_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            remap = false,
            require = 0
    )
    private <E extends Entity> void rmc$corruptNonLivingEntityAnimation(E entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback) {
        ModelRenderCorruptionHooks.mutateNonLivingEntityTransform(entity, partialTick, poseStack);
    }

    @Inject(
            method = {
                    "renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V",
                    "m_114457_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void rmc$beginCorruptedEntityShadow(PoseStack poseStack, MultiBufferSource bufferSource, Entity entity, float opacity, float partialTick, LevelReader level, float radius, CallbackInfo callback) {
        RMC$SHADOW_POSE_APPLIED.set(ModelRenderCorruptionHooks.beginShadowRender(poseStack, entity, partialTick, radius));
    }

    @Inject(
            method = {
                    "renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V",
                    "m_114457_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void rmc$endCorruptedEntityShadow(PoseStack poseStack, MultiBufferSource bufferSource, Entity entity, float opacity, float partialTick, LevelReader level, float radius, CallbackInfo callback) {
        ModelRenderCorruptionHooks.endShadowRender(poseStack, RMC$SHADOW_POSE_APPLIED.get());
        RMC$SHADOW_POSE_APPLIED.remove();
    }
}
