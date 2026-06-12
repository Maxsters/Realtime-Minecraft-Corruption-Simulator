package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
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
}
