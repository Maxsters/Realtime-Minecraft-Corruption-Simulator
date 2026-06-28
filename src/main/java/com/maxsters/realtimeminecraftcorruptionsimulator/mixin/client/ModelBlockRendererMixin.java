package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
@SuppressWarnings("target")
public abstract class ModelBlockRendererMixin {
    @Unique
    private final ThreadLocal<Boolean> rmc$renderSpaceOffsetApplied = ThreadLocal.withInitial(() -> false);

    @Redirect(
            method = {
                    "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
                    "m_234379_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useAmbientOcclusion()Z", remap = false),
            remap = false,
            require = 0
    )
    private boolean rmc$corruptAmbientOcclusionSwitch() {
        return LightingCorruptionHooks.mutateAmbientOcclusionSwitch(Minecraft.useAmbientOcclusion());
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$beginRenderSpaceOffset(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random, long seed, int packedOverlay, ModelData modelData, RenderType renderType, CallbackInfo callback) {
        rmc$renderSpaceOffsetApplied.set(BlockRenderCorruptionHooks.beginTesselate(state, pos, poseStack));
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endRenderSpaceOffset(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random, long seed, int packedOverlay, ModelData modelData, RenderType renderType, CallbackInfo callback) {
        BlockRenderCorruptionHooks.endTesselate(poseStack, rmc$renderSpaceOffsetApplied.get());
        rmc$renderSpaceOffsetApplied.remove();
    }

    @Redirect(
            method = {
                    "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
                    "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Applies world-render face loss where the renderer still has BlockPos; model-level getQuads hooks do not.")
    private List<BakedQuad> rmc$getPositionedBlockQuads(BakedModel model,
                                                        BlockState state,
                                                        Direction side,
                                                        RandomSource random,
                                                        ModelData modelData,
                                                        RenderType renderType,
                                                        BlockAndTintGetter level,
                                                        BakedModel originalModel,
                                                        BlockState originalState,
                                                        BlockPos pos,
                                                        PoseStack poseStack,
                                                        VertexConsumer consumer,
                                                        boolean checkSides,
                                                        RandomSource originalRandom,
                                                        long seed,
                                                        int packedOverlay,
                                                        ModelData originalModelData,
                                                        RenderType originalRenderType) {
        List<BakedQuad> quads = model.getQuads(state, side, random, modelData, renderType);
        return BlockRenderCorruptionHooks.corruptBlockFaces(state, pos, side, quads);
    }

    @Redirect(
            method = {
                    "renderModelFaceFlat(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;IIZLcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Ljava/util/List;Ljava/util/BitSet;)V",
                    "m_111001_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;getShade(Lnet/minecraft/core/Direction;Z)F", remap = false),
            remap = false,
            require = 0
    )
    private float rmc$corruptFlatShade(BlockAndTintGetter level, Direction direction, boolean shade) {
        return LightingCorruptionHooks.mutateShade(level, direction, shade, level.getShade(direction, shade));
    }

    @ModifyVariable(
            method = {
                    "putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V",
                    "m_111023_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ModelBlockRenderer#putQuadData.")
    private BakedQuad rmc$corruptEmittedBlockQuad(BakedQuad quad,
                                                  BlockAndTintGetter level,
                                                  BlockState state,
                                                  BlockPos pos,
                                                  VertexConsumer consumer,
                                                  PoseStack.Pose pose,
                                                  BakedQuad originalQuad,
                                                  float shade0,
                                                  float shade1,
                                                  float shade2,
                                                  float shade3,
                                                  int light0,
                                                  int light1,
                                                  int light2,
                                                  int light3,
                                                  int packedOverlay) {
        return ItemTextureCorruptionManager.corruptRenderedBlockQuad(state, quad);
    }
}
