package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class BlockRenderCorruptionHooks {
    private BlockRenderCorruptionHooks() {
    }

    public static boolean beginTesselate(BlockState state, BlockPos pos, PoseStack poseStack) {
        if (state == null || pos == null || poseStack == null) {
            return false;
        }

        Vec3 offset = currentRenderSpaceOffset();
        if (!hasRenderSpaceOffset(offset)) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        return true;
    }

    public static Vec3 currentRenderSpaceOffset() {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.BLOCK_COLLISION)) {
            return Vec3.ZERO;
        }

        float intensity = stack.extreme(CorruptionSurface.BLOCK_COLLISION)
                ? 1.0F
                : stack.intensity(CorruptionSurface.BLOCK_COLLISION);
        if (intensity <= 0.01F) {
            return Vec3.ZERO;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String targetId = "visual_collision_world:" + dimension;
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, 0x424C4F43);
        double blockSpan = stack.extreme(CorruptionSurface.BLOCK_COLLISION)
                ? 20.0D
                : 0.35D + Math.pow(intensity, 1.35D) * 19.65D;
        double phase = stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":phase", stack.bucket(CorruptionSurface.BLOCK_COLLISION, targetId, 0x50484153, 96));
        double dynamic = dynamicWave(seed, phase, intensity);
        double x = snappedSigned(seed ^ 0x585348494654L, blockSpan) * dynamic;
        double y = snappedSigned(seed ^ 0x595348494654L, blockSpan * 0.72D) * dynamicWave(seed ^ 0x59444E4DL, phase, intensity);
        double z = snappedSigned(seed ^ 0x5A5348494654L, blockSpan) * dynamicWave(seed ^ 0x5A444E4DL, phase, intensity);
        return new Vec3(x, y, z);
    }

    public static boolean hasRenderSpaceOffset(Vec3 offset) {
        return offset != null && Math.abs(offset.x) + Math.abs(offset.y) + Math.abs(offset.z) >= 0.03125D;
    }

    public static void endTesselate(PoseStack poseStack, boolean applied) {
        if (applied && poseStack != null) {
            poseStack.popPose();
        }
    }

    public static List<BakedQuad> corruptBlockFaces(BlockState state, Direction side, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                && !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)
                && !stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return quads;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String blockId = state == null ? "unknown" : blockTargetId(state);
        String targetId = "missing_block_faces:" + dimension + ":" + blockId;
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                Math.max(
                        (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 1.0F : stack.intensity(CorruptionSurface.MODEL_GEOMETRY)) * 0.76F,
                        (stack.extreme(CorruptionSurface.TEXTURE_MEMORY) ? 1.0F : stack.intensity(CorruptionSurface.TEXTURE_MEMORY)) * 0.48F
                )
        ), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return quads;
        }

        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 0.98F
                : Mth.clamp(0.05F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.92F);
        int bucket = stack.bucket(CorruptionSurface.WORLD_RENDER, targetId, 0x46414345, 96);
        if (stack.unit(CorruptionSurface.WORLD_RENDER, targetId + ":enabled", bucket) > chance) {
            return quads;
        }

        if (side != null) {
            return faceDropped(stack, targetId, side, bucket, intensity) ? Collections.emptyList() : quads;
        }

        List<BakedQuad> filtered = null;
        for (BakedQuad quad : quads) {
            if (faceDropped(stack, targetId, quad.getDirection(), bucket, intensity)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(quads.size());
                }
                continue;
            }
            if (filtered != null) {
                filtered.add(quad);
            }
        }
        return filtered == null ? quads : filtered;
    }

    private static boolean faceDropped(CorruptionEffectStack stack, String targetId, Direction direction, int bucket, float intensity) {
        if (direction == null) {
            return false;
        }
        Direction primary = missingFaceDirection(stack, targetId, bucket, 0);
        Direction secondary = missingFaceDirection(stack, targetId, bucket, 1);
        if (direction == primary) {
            return true;
        }
        float secondaryChance = Mth.clamp(Math.max(0.0F, intensity - 0.32F) * 0.84F + stack.instability() * 0.08F, 0.0F, 0.78F);
        if (direction == secondary && stack.unit(CorruptionSurface.WORLD_RENDER, targetId + ":secondary", bucket ^ 0x534543) < secondaryChance) {
            return true;
        }
        float scatterChance = Mth.clamp(Math.max(0.0F, intensity - 0.58F) * 0.38F, 0.0F, 0.28F);
        return stack.unit(CorruptionSurface.WORLD_RENDER, targetId + ":scatter:" + direction.getName(), bucket ^ direction.ordinal()) < scatterChance;
    }

    private static Direction missingFaceDirection(CorruptionEffectStack stack, String targetId, int bucket, int ordinal) {
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId + ":global_face:" + ordinal, bucket ^ 0x4D495353);
        Direction[] directions = Direction.values();
        return directions[Math.floorMod((int) (seed >>> (ordinal * 11)), directions.length)];
    }

    private static String blockTargetId(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "unknown" : id.toString();
    }

    private static double snappedSigned(long value, double amplitude) {
        double raw = (unit(value) * 2.0D - 1.0D) * amplitude;
        double snap = 0.25D;
        return Mth.clamp(Math.rint(raw / snap) * snap, -20.0D, 20.0D);
    }

    private static double dynamicWave(long seed, double phase, float intensity) {
        int mode = Math.floorMod((int) (seed >>> 33), 6);
        double value = switch (mode) {
            case 0 -> Math.sin(phase * Math.PI * 2.0D);
            case 1 -> phase < 0.50D ? phase * 2.0D : (1.0D - phase) * 2.0D;
            case 2 -> phase < 0.72D ? 0.0D : (phase - 0.72D) / 0.28D;
            case 3 -> Math.sin(phase * Math.PI * 4.0D + unit(seed >>> 17) * Math.PI);
            case 4 -> Math.rint(Math.sin(phase * Math.PI * 2.0D) * (2.0D + intensity * 6.0D)) / (2.0D + intensity * 6.0D);
            default -> 0.35D + unit(seed ^ ((long) (phase * 256.0D) << 24)) * 0.95D;
        };
        return Mth.clamp(value, -1.0D, 1.0D);
    }

    private static double unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }
}
