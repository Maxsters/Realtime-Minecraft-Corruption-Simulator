package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.AnimationSpeedCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemEntityRenderer.class)
@SuppressWarnings("target")
public abstract class ItemEntityRendererMixin {
    @ModifyVariable(
            method = {
                    "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ItemEntityRenderer#render.")
    private float rmc$corruptDroppedItemAnimationPartial(float partialTick, ItemEntity entity, float entityYaw, float methodPartialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        return AnimationSpeedCorruptionHooks.mutateDroppedItemPartial(entity, partialTick);
    }
}
