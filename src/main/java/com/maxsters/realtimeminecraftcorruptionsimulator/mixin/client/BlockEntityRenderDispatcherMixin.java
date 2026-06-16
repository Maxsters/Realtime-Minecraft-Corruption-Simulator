package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BlockEntityRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(BlockEntityRenderDispatcher.class)
@SuppressWarnings("target")
public abstract class BlockEntityRenderDispatcherMixin {
    private static final ThreadLocal<Boolean> RMC$BLOCK_ENTITY_POSE_APPLIED = ThreadLocal.withInitial(() -> false);

    @Unique
    private static Field rmc$cameraField;
    @Unique
    private static boolean rmc$cameraFieldChecked;

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
                    "m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockEntityRenderDispatcher#render.")
    private <E extends BlockEntity> void rmc$beginCorruptedBlockEntityRender(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo callback) {
        RMC$BLOCK_ENTITY_POSE_APPLIED.set(BlockEntityRenderCorruptionHooks.beginRender(blockEntity, partialTick, poseStack, rmc$camera((BlockEntityRenderDispatcher) (Object) this)));
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
                    "m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockEntityRenderDispatcher#render.")
    private <E extends BlockEntity> void rmc$endCorruptedBlockEntityRender(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo callback) {
        BlockEntityRenderCorruptionHooks.endRender(poseStack, RMC$BLOCK_ENTITY_POSE_APPLIED.get());
        RMC$BLOCK_ENTITY_POSE_APPLIED.remove();
    }

    @Unique
    private static Camera rmc$camera(BlockEntityRenderDispatcher dispatcher) {
        Field field = rmc$cameraField();
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(dispatcher);
            return value instanceof Camera camera ? camera : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static Field rmc$cameraField() {
        if (!rmc$cameraFieldChecked) {
            rmc$cameraFieldChecked = true;
            for (String name : new String[]{"camera", "f_112249_"}) {
                try {
                    Field field = BlockEntityRenderDispatcher.class.getDeclaredField(name);
                    field.setAccessible(true);
                    rmc$cameraField = field;
                    break;
                } catch (NoSuchFieldException | RuntimeException ignored) {
                }
            }
        }
        return rmc$cameraField;
    }
}
