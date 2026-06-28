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

        Vec3 offset = currentRenderSpaceOffset(pos);
        if (!hasRenderSpaceOffset(offset)) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        return true;
    }

    public static Vec3 currentRenderSpaceOffset() {
        return currentRenderSpaceOffset(null);
    }

    public static Vec3 currentRenderSpaceOffset(BlockPos pos) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return Vec3.ZERO;
        }

        float intensity = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : stack.intensity(CorruptionSurface.WORLD_RENDER);
        if (intensity <= 0.01F) {
            return Vec3.ZERO;
        }

        String targetId = "world_render_space:" + currentDimensionId() + ":" + renderRegionKey(pos);
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x424C4F43);
        double blockSpan = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 20.0D
                : 0.35D + Math.pow(intensity, 1.35D) * 19.65D;
        double phase = stack.unit(CorruptionSurface.WORLD_RENDER, targetId + ":phase", stack.bucket(CorruptionSurface.WORLD_RENDER, targetId, 0x50484153, 96));
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

    public static List<BakedQuad> corruptBlockFaces(BlockState state, BlockPos pos, Direction side, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }
        if (state == null || pos == null) {
            return quads;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return quads;
        }

        String targetId = "missing_block_faces:" + currentDimensionId() + ":" + blockTargetId(state);
        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return quads;
        }

        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 0.98F
                : Mth.clamp(0.05F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.92F);
        long faceSeed = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x46414345) ^ positionSeed(pos);
        if (unit(faceSeed ^ 0x454E41424C45L) > chance) {
            return quads;
        }

        if (side != null) {
            return faceDropped(stack, faceSeed, side, intensity) ? Collections.emptyList() : quads;
        }

        List<BakedQuad> filtered = null;
        for (BakedQuad quad : quads) {
            if (faceDropped(stack, faceSeed, quad.getDirection(), intensity)) {
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

    private static boolean faceDropped(CorruptionEffectStack stack, long faceSeed, Direction direction, float intensity) {
        if (direction == null) {
            return false;
        }
        Direction primary = missingFaceDirection(faceSeed, 0);
        Direction secondary = missingFaceDirection(faceSeed, 1);
        if (direction == primary) {
            return true;
        }
        float secondaryChance = Mth.clamp(Math.max(0.0F, intensity - 0.32F) * 0.84F + stack.instability() * 0.08F, 0.0F, 0.78F);
        if (direction == secondary && unit(faceSeed ^ 0x5345434F4E444152L) < secondaryChance) {
            return true;
        }
        float scatterChance = Mth.clamp(Math.max(0.0F, intensity - 0.58F) * 0.38F, 0.0F, 0.28F);
        return unit(faceSeed ^ ((long) direction.ordinal() + 1L) * 0x9E3779B97F4A7C15L) < scatterChance;
    }

    private static Direction missingFaceDirection(long faceSeed, int ordinal) {
        long seed = mixLong(faceSeed ^ (long) ordinal * 0xD1B54A32D192ED03L ^ 0x4D495353L);
        Direction[] directions = Direction.values();
        return directions[Math.floorMod((int) (seed >>> (ordinal * 11)), directions.length)];
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
    }

    private static String renderRegionKey(BlockPos pos) {
        if (pos == null) {
            return "global";
        }
        // Render-space offsets corrupt chunk sections. Keying them to the section
        // makes the tear deterministic for every client while still producing real
        // geometry displacement instead of a camera-only illusion.
        return (pos.getX() >> 4) + ":" + (pos.getY() >> 4) + ":" + (pos.getZ() >> 4);
    }

    private static long positionSeed(BlockPos pos) {
        return mixLong(pos.asLong() ^ (long) pos.getX() * 0x9E3779B97F4A7C15L ^ (long) pos.getZ() * 0x632BE59BD9B4E019L);
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
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }

    private static long mixLong(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
