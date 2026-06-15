package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class BlockEntityRenderCorruptionHooks {
    private BlockEntityRenderCorruptionHooks() {
    }

    public static boolean beginRender(BlockEntity blockEntity, float partialTick, PoseStack poseStack, Camera camera) {
        if (blockEntity == null || poseStack == null) {
            return false;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                && !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)
                && !stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return false;
        }

        String targetId = "block_entity_render:" + blockEntityId(blockEntity);
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                Math.max(
                        (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 1.0F : stack.intensity(CorruptionSurface.MODEL_GEOMETRY)) * 0.86F,
                        (stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING)) * 0.54F
                )
        ), 0.0F, 1.0F);
        if (intensity <= 0.012F) {
            return false;
        }
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, blockEntity.getBlockPos().hashCode() ^ 0x42455244);
        float chance = Mth.clamp(0.06F + intensity * 0.78F + stack.instability() * 0.10F, 0.0F, 0.95F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(seed ^ 0x454E4142L) > chance) {
            return false;
        }

        poseStack.pushPose();
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPos = camera == null ? Vec3.ZERO : camera.getPosition();
        double cameraFollow = unit(seed ^ 0x43414D46L) < 0.20F + intensity * 0.46F
                ? 0.35D + unit(seed ^ 0x464F4C4CL) * intensity * 1.45D
                : 0.0D;
        double x = signed(seed ^ 0x584F4646L, 0.18D + intensity * 3.2D) + (cameraPos.x - pos.getX()) * cameraFollow;
        double y = signed(seed ^ 0x594F4646L, 0.16D + intensity * 2.6D) + (cameraPos.y - pos.getY()) * cameraFollow;
        double z = signed(seed ^ 0x5A4F4646L, 0.18D + intensity * 3.2D) + (cameraPos.z - pos.getZ()) * cameraFollow;
        poseStack.translate(Mth.clamp(x, -64.0D, 64.0D), Mth.clamp(y, -64.0D, 64.0D), Mth.clamp(z, -64.0D, 64.0D));

        float phase = (blockEntity.hasLevel() ? blockEntity.getLevel().getGameTime() : 0L) + partialTick + unit(seed ^ 0x50484153L) * 80.0F;
        float spin = (float) Math.sin(phase * (0.06F + intensity * 0.42F)) * (10.0F + intensity * 150.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees((float) (spin + signed(seed ^ 0x59524F54L, 80.0D * intensity))));
        poseStack.mulPose(Axis.XP.rotationDegrees((float) signed(seed ^ 0x58524F54L, 70.0D * intensity)));
        if (unit(seed ^ 0x5343414CL) < 0.14F + intensity * 0.42F) {
            float sx = scale(seed ^ 0x5853434CL, intensity, stack.extreme(CorruptionSurface.MODEL_GEOMETRY));
            float sy = scale(seed ^ 0x5953434CL, intensity, stack.extreme(CorruptionSurface.MODEL_GEOMETRY));
            float sz = scale(seed ^ 0x5A53434CL, intensity, stack.extreme(CorruptionSurface.MODEL_GEOMETRY));
            poseStack.scale(sx, sy, sz);
        }
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

    private static float scale(long seed, float intensity, boolean extreme) {
        float span = extreme ? 7.5F : 2.25F + intensity * 3.0F;
        float value = 1.0F + (unit(seed) * 2.0F - 1.0F) * span;
        if (Math.abs(value) < 0.05F) {
            value = Math.copySign(0.05F, value == 0.0F ? 1.0F : value);
        }
        return Mth.clamp(value, -8.0F, 9.0F);
    }

    private static double signed(long seed, double amplitude) {
        return (unit(seed) * 2.0D - 1.0D) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }
}
