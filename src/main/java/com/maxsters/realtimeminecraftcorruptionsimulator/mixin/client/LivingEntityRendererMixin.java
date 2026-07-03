package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$beginCorruptedEntityModelRender(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback) {
        ModelRenderCorruptionHooks.beginEntityRender(entity, partialTick);
        ModelRenderCorruptionHooks.mutateEntityRenderPositionFallback(entity, partialTick, poseStack);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endCorruptedEntityModelRender(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback) {
        ModelRenderCorruptionHooks.endEntityRender();
        ModelRenderCorruptionHooks.clearEntityRenderPositionMarker(entity);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;prepareMobModel(Lnet/minecraft/world/entity/Entity;FFF)V", remap = false),
            remap = false,
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rmc$corruptPrepareMobModelArgs(EntityModel model, Entity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        float[] mutated = ModelRenderCorruptionHooks.mutatePrepareArgs(entity, limbSwing, limbSwingAmount, partialTick);
        model.prepareMobModel(entity, mutated[0], mutated[1], mutated[2]);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;m_6839_(Lnet/minecraft/world/entity/Entity;FFF)V", remap = false),
            remap = false,
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rmc$corruptPrepareMobModelArgsSrg(EntityModel model, Entity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        float[] mutated = ModelRenderCorruptionHooks.mutatePrepareArgs(entity, limbSwing, limbSwingAmount, partialTick);
        model.prepareMobModel(entity, mutated[0], mutated[1], mutated[2]);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", remap = false),
            remap = false,
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rmc$corruptSetupAnimArgs(EntityModel model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float[] mutated = ModelRenderCorruptionHooks.mutateSetupAnimArgs(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.setupAnim(entity, mutated[0], mutated[1], mutated[2], mutated[3], mutated[4]);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;m_6973_(Lnet/minecraft/world/entity/Entity;FFFFF)V", remap = false),
            remap = false,
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rmc$corruptSetupAnimArgsSrg(EntityModel model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float[] mutated = ModelRenderCorruptionHooks.mutateSetupAnimArgs(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.setupAnim(entity, mutated[0], mutated[1], mutated[2], mutated[3], mutated[4]);
    }
}
