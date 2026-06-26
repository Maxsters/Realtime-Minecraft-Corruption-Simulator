package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WorldRenderCorruptionHooks {
    private WorldRenderCorruptionHooks() {
    }

    public static boolean shouldSkipChunkLayer(RenderType renderType) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String layer = renderType == null ? "unknown" : renderType.toString();
        String targetId = "chunk_layer_failure:" + dimension + ":" + layer;
        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return false;
        }

        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x43484E4B);
        boolean globalChunkBlank = stack.level() >= 88
                && unit(hash ^ 0x414C4C4CL) < 0.14F + intensity * 0.48F;
        float chance = globalChunkBlank
                ? 0.98F
                : Mth.clamp(0.02F + intensity * 0.46F + stack.instability() * 0.08F, 0.0F, 0.78F);
        return unit(hash ^ 0x534B4950L) < chance;
    }

    public static double mutateChunkLayerCameraAxis(RenderType renderType, double original, int axis) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String targetId = "chunk_layer_origin:" + dimension;
        float intensity = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : Mth.clamp(stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return original;
        }

        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x4F524947);
        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : Mth.clamp(0.04F + intensity * 0.48F + stack.instability() * 0.10F, 0.0F, 0.86F);
        if (unit(hash ^ 0x454E4142L) > chance) {
            return original;
        }

        double span = axis == 1 ? 1.0D + intensity * 18.0D : 4.0D + intensity * 96.0D;
        double component = signed(hash ^ ((long) axis * 0x9E3779B97F4A7C15L), span);
        double phase = repeatedOffsetPhase(time, hash, intensity);
        return original + repeatedOffset(component, phase, axis);
    }

    private static double repeatedOffsetPhase(long time, long seed, float intensity) {
        double fastPeriod = Mth.lerp(intensity, 180.0D, 32.0D);
        double slowPeriod = Mth.lerp(intensity, 420.0D, 112.0D);
        double basePeriod = fastPeriod + unit(seed ^ 0x50455249L) * Math.max(1.0D, slowPeriod - fastPeriod);
        double speedBoost = 1.0D + intensity * 4.0D;
        long period = Math.max(4L, Math.round(basePeriod / speedBoost));
        long phaseOffset = Math.floorMod((long) Math.floor(unit(seed ^ 0x50484153L) * period), period);
        return Math.floorMod(time + phaseOffset, period) / (double) period;
    }

    private static double repeatedOffset(double component, double phase, int axis) {
        double verticalDamping = axis == 1 ? 0.60D : 1.0D;
        return component * phase * verticalDamping;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }
}
