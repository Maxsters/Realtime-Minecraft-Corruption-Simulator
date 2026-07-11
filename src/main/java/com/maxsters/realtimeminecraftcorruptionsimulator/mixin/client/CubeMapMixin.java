package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.GuiDirectTextureCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.CameraRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.MenuPanoramaCorruptionHooks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CubeMap.class)
@SuppressWarnings("target")
public abstract class CubeMapMixin {
    @Redirect(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts CubeMap's actual panorama projection matrix through the shared camera profile.")
    private Matrix4f rmc$corruptPanoramaProjection(Matrix4f matrix, float fovRadians, float aspect, float nearPlane, float farPlane) {
        return CameraRenderCorruptionHooks.setMenuPerspective(matrix, fovRadians, aspect, nearPlane, farPlane);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CubeMap#render.")
    private void rmc$corruptPanoramaTexture(int slot, ResourceLocation texture) {
        ResourceLocation replacement = GuiDirectTextureCorruptionHooks.bindRawTexture(texture, "gui_raw_direct_texture", texture == null ? 0x43554245 : texture.hashCode());
        RenderSystem.setShaderTexture(slot, replacement);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CubeMap#render.")
    private VertexConsumer rmc$corruptPanoramaCubeVertex(BufferBuilder builder, double x, double y, double z) {
        return MenuPanoramaCorruptionHooks.vertex(builder, x, y, z);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;m_5483_(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of BufferBuilder#vertex from CubeMap#render.")
    private VertexConsumer rmc$corruptPanoramaCubeVertexSrg(BufferBuilder builder, double x, double y, double z) {
        return MenuPanoramaCorruptionHooks.vertex(builder, x, y, z);
    }

    @Redirect(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CubeMap#render.")
    private VertexConsumer rmc$corruptPanoramaUv(VertexConsumer consumer, float u, float v) {
        GuiDirectTextureCorruptionHooks.Uv corrupted = GuiDirectTextureCorruptionHooks.rawUv(u, v);
        return consumer.uv(corrupted.u(), corrupted.v());
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/client/Minecraft;FFF)V",
                    "m_108849_(Lnet/minecraft/client/Minecraft;FFF)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CubeMap#render.")
    private void rmc$clearPanoramaTextureState(CallbackInfo callback) {
        GuiDirectTextureCorruptionHooks.clearRawTexture();
    }
}
