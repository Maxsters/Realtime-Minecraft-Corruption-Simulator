package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.CloudRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BreakingTextureCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.SkyRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
            method = {
                    "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
                    "m_109537_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void rmc$corruptPackedLight(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(LightingCorruptionHooks.mutatePackedLight(level, state, pos, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
                    "m_202423_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$refreshCorruptedStars(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, net.minecraft.client.Camera camera, boolean fog, Runnable setupFog, CallbackInfo callback) {
        SkyRenderCorruptionHooks.onRenderSky((LevelRenderer) (Object) this);
    }

    @Redirect(
            method = {
                    "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                    "m_109599_"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", remap = false),
            remap = false,
            require = 0
    )
    private Object rmc$corruptDestroyTextureStage(List<?> list, int index) {
        return BreakingTextureCorruptionHooks.getDestroyTexture(list, index);
    }

    @Inject(
            method = {
                    "drawStars(Lcom/mojang/blaze3d/vertex/BufferBuilder;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234259_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$beginStarMeshCorruption(BufferBuilder builder, CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) {
        SkyRenderCorruptionHooks.beginBuild();
    }

    @Inject(
            method = {
                    "drawStars(Lcom/mojang/blaze3d/vertex/BufferBuilder;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234259_"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endStarMeshCorruption(BufferBuilder builder, CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) {
        SkyRenderCorruptionHooks.endBuild();
    }

    @Redirect(
            method = {
                    "drawStars(Lcom/mojang/blaze3d/vertex/BufferBuilder;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234259_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptStarVertex(BufferBuilder builder, double x, double y, double z) {
        return SkyRenderCorruptionHooks.vertex(builder, x, y, z);
    }

    @Redirect(
            method = {
                    "drawStars(Lcom/mojang/blaze3d/vertex/BufferBuilder;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234259_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;m_5483_(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptStarVertexSrg(BufferBuilder builder, double x, double y, double z) {
        return SkyRenderCorruptionHooks.vertex(builder, x, y, z);
    }

    @Inject(
            method = {
                    "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FDDD)V",
                    "m_253054_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$refreshCorruptedCloudMesh(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, double cameraX, double cameraY, double cameraZ, CallbackInfo callback) {
        CloudRenderCorruptionHooks.onRenderClouds((LevelRenderer) (Object) this);
    }

    @Inject(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$beginCloudMeshCorruption(BufferBuilder builder, double cloudX, double cloudY, double cloudZ, Vec3 cloudColor, CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) {
        CloudRenderCorruptionHooks.beginBuild(cloudColor);
    }

    @Inject(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endCloudMeshCorruption(BufferBuilder builder, double cloudX, double cloudY, double cloudZ, Vec3 cloudColor, CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) {
        CloudRenderCorruptionHooks.endBuild();
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudVertex(BufferBuilder builder, double x, double y, double z) {
        return CloudRenderCorruptionHooks.vertex(builder, x, y, z);
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;m_5483_(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudVertexSrg(BufferBuilder builder, double x, double y, double z) {
        return CloudRenderCorruptionHooks.vertex(builder, x, y, z);
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudUv(VertexConsumer consumer, float u, float v) {
        return CloudRenderCorruptionHooks.uv(consumer, u, v);
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_7421_(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudUvSrg(VertexConsumer consumer, float u, float v) {
        return CloudRenderCorruptionHooks.uv(consumer, u, v);
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudColor(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        return CloudRenderCorruptionHooks.color(consumer, red, green, blue, alpha);
    }

    @Redirect(
            method = {
                    "buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",
                    "m_234261_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_85950_(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptCloudColorSrg(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        return CloudRenderCorruptionHooks.color(consumer, red, green, blue, alpha);
    }
}
