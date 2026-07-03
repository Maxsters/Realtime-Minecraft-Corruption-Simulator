package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Material.class)
@SuppressWarnings("target")
public abstract class MaterialMixin {
    @Inject(
            method = {
                    "sprite()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
                    "m_119204_()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Material#sprite.")
    private void rmc$corruptMaterialSprite(CallbackInfoReturnable<TextureAtlasSprite> callback) {
        Material material = (Material) (Object) this;
        callback.setReturnValue(ItemTextureCorruptionManager.corruptMaterialSprite(callback.getReturnValue(), material.atlasLocation(), material.texture()));
    }

    @Inject(
            method = {
                    "buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    "m_119194_(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Material#buffer.")
    private void rmc$corruptMaterialBuffer(MultiBufferSource bufferSource, Function<ResourceLocation, RenderType> renderTypeFactory, CallbackInfoReturnable<VertexConsumer> callback) {
        Material material = (Material) (Object) this;
        callback.setReturnValue(ItemTextureCorruptionManager.wrapMaterialVertexConsumer(callback.getReturnValue(), material.atlasLocation(), material.texture()));
    }

    @Inject(
            method = {
                    "buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    "m_119197_(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for foil Material#buffer.")
    private void rmc$corruptFoilMaterialBuffer(MultiBufferSource bufferSource, Function<ResourceLocation, RenderType> renderTypeFactory, boolean foil, CallbackInfoReturnable<VertexConsumer> callback) {
        Material material = (Material) (Object) this;
        callback.setReturnValue(ItemTextureCorruptionManager.wrapMaterialVertexConsumer(callback.getReturnValue(), material.atlasLocation(), material.texture()));
    }
}
