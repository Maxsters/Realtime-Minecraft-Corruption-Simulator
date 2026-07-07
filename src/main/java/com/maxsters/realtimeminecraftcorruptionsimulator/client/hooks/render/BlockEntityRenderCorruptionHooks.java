package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class BlockEntityRenderCorruptionHooks {
    private BlockEntityRenderCorruptionHooks() {
    }

    public static boolean beginRender(BlockEntity blockEntity, float partialTick, PoseStack poseStack) {
        if (blockEntity == null || poseStack == null) {
            return false;
        }

        Vec3 offset = WorldRenderCorruptionHooks.cameraRenderOffset("block_entity:" + blockEntityId(blockEntity), 0x42455244);
        if (!BlockRenderCorruptionHooks.hasRenderSpaceOffset(offset)) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        return true;
    }

    public static void endRender(PoseStack poseStack, boolean applied) {
        if (applied && poseStack != null) {
            poseStack.popPose();
        }
    }

    private static String blockEntityId(BlockEntity blockEntity) {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return id == null ? blockEntity.getType().toString() : id.toString();
    }
}
