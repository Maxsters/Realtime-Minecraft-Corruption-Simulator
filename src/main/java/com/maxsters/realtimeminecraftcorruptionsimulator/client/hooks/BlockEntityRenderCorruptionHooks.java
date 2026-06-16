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
        int mode = Math.floorMod((int) (seed >>> 28), 8);
        float phase = (blockEntity.hasLevel() ? blockEntity.getLevel().getGameTime() : 0L) + partialTick + unit(seed ^ 0x50484153L) * 80.0F;
        double orbit = Math.sin(phase * (0.035F + intensity * (0.08F + unit(seed ^ 0x53504544L) * 0.55F)));
        double counterOrbit = Math.cos(phase * (0.025F + intensity * (0.08F + unit(seed ^ 0x43535044L) * 0.38F)));
        double cameraFollow = mode == 0 && unit(seed ^ 0x43414D46L) < 0.08F + intensity * 0.20F
                ? 0.08D + unit(seed ^ 0x464F4C4CL) * intensity * 0.58D
                : 0.0D;
        double x = signed(seed ^ 0x584F4646L, 0.12D + intensity * 2.6D) + (cameraPos.x - pos.getX()) * cameraFollow;
        double y = signed(seed ^ 0x594F4646L, 0.10D + intensity * 2.2D) + (cameraPos.y - pos.getY()) * cameraFollow;
        double z = signed(seed ^ 0x5A4F4646L, 0.12D + intensity * 2.6D) + (cameraPos.z - pos.getZ()) * cameraFollow;
        if (mode == 1 || mode == 5) {
            x += orbit * intensity * (0.75D + unit(seed ^ 0x4F524258L) * 5.2D);
            z += counterOrbit * intensity * (0.75D + unit(seed ^ 0x4F52425AL) * 5.2D);
        } else if (mode == 2) {
            y += orbit * intensity * (0.45D + unit(seed ^ 0x424F554EL) * 4.4D);
        } else if (mode == 3) {
            x = Math.rint(x * (1.0D + intensity * 2.5D)) / Math.max(0.25D, 1.0D + intensity * 2.5D);
            z = Math.rint(z * (1.0D + intensity * 2.5D)) / Math.max(0.25D, 1.0D + intensity * 2.5D);
        } else if (mode == 6) {
            x += signed(seed ^ (long) Math.floor(phase * (0.5F + intensity * 8.0F)), intensity * 2.0D);
            y += signed(seed ^ 0x4A495459L ^ (long) Math.floor(phase * (0.4F + intensity * 6.0F)), intensity * 1.3D);
        }
        poseStack.translate(Mth.clamp(x, -64.0D, 64.0D), Mth.clamp(y, -64.0D, 64.0D), Mth.clamp(z, -64.0D, 64.0D));

        float spin = (float) Math.sin(phase * (0.05F + intensity * (0.14F + unit(seed ^ 0x5350494EL) * 0.72F))) * (8.0F + intensity * 220.0F);
        float xRot = (float) signed(seed ^ 0x58524F54L, 90.0D * intensity);
        float yRot = (float) signed(seed ^ 0x59524F54L, 110.0D * intensity);
        float zRot = (float) signed(seed ^ 0x5A524F54L, 100.0D * intensity);
        switch (mode) {
            case 0 -> poseStack.mulPose(Axis.YP.rotationDegrees(spin + yRot));
            case 1 -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.65F + xRot));
                poseStack.mulPose(Axis.ZP.rotationDegrees((float) (counterOrbit * intensity * 180.0D) + zRot));
            }
            case 2 -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(spin + zRot));
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot * 0.35F));
            }
            case 3 -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(Math.round((spin + xRot) / 15.0F) * 15.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(Math.round(yRot / 30.0F) * 30.0F));
            }
            case 4 -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(spin + xRot));
                poseStack.mulPose(Axis.YP.rotationDegrees(-spin * 0.55F + yRot));
                poseStack.mulPose(Axis.ZP.rotationDegrees(spin * 0.28F + zRot));
            }
            default -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
                poseStack.mulPose(Axis.XP.rotationDegrees((float) (spin * signed(seed ^ 0x5358504EL, 1.0D) + xRot)));
                poseStack.mulPose(Axis.ZP.rotationDegrees((float) signed(seed ^ 0x535A504EL, 180.0D * intensity)));
            }
        }
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
