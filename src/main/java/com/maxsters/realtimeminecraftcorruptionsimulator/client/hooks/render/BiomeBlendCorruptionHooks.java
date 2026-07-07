package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BiomeBlendCorruptionHooks {
    private BiomeBlendCorruptionHooks() {
    }

    public static int mutateBiomeColor(String channel, BlockAndTintGetter level, BlockPos pos, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.BIOME_TINT) && !stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        String dimension = currentDimensionId();
        String targetId = "biome_blend_failure:" + channel + ":" + dimension;
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.BIOME_TINT) ? 1.0F : stack.intensity(CorruptionSurface.BIOME_TINT),
                (stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER)) * 0.56F
        ), 0.0F, 1.0F);
        if (intensity <= 0.01F) {
            return original;
        }
        int salt = pos == null ? channel.hashCode() : pos.hashCode() ^ channel.hashCode();
        float chance = Mth.clamp(0.10F + intensity * 0.82F + stack.instability() * 0.08F, 0.0F, 0.96F);
        if (!stack.extreme(CorruptionSurface.BIOME_TINT) && stack.unit(CorruptionSurface.BIOME_TINT, targetId, salt) > chance) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.BIOME_TINT, targetId, salt ^ 0x42494F4D);
        int mode = Math.floorMod((int) (seed >>> 27), 7);
        int mutated = switch (mode) {
            case 0 -> quantizeColor(original, seed, intensity);
            case 1 -> channelSwap(original, seed);
            case 2 -> checkerColor(original, pos, seed, intensity);
            case 3 -> directColor(seed);
            case 4 -> 0x000000;
            case 5 -> 0xFFFFFF;
            default -> CorruptionValueMutator.mutateColor(stack, CorruptionSurface.BIOME_TINT, targetId, original, 0x4D, seed);
        };
        return blend(original, mutated, Mth.clamp(0.32F + intensity * 0.78F, 0.0F, 1.0F));
    }

    private static int quantizeColor(int color, long seed, float intensity) {
        int step = 16 + Math.round(unit(seed ^ 0x53544550L) * (112.0F + intensity * 128.0F));
        int r = quantize((color >> 16) & 0xFF, step);
        int g = quantize((color >> 8) & 0xFF, step);
        int b = quantize(color & 0xFF, step);
        return r << 16 | g << 8 | b;
    }

    private static int channelSwap(int color, long seed) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (seed & 1L) == 0L ? (g << 16 | b << 8 | r) : (b << 16 | r << 8 | g);
    }

    private static int checkerColor(int color, BlockPos pos, long seed, float intensity) {
        if (pos == null) {
            return directColor(seed);
        }
        int scale = Math.max(1, Math.round(1.0F + intensity * 12.0F));
        int checker = (Mth.floor((float) pos.getX() / scale) ^ Mth.floor((float) pos.getZ() / scale)) & 1;
        return checker == 0 ? color : directColor(seed ^ pos.asLong());
    }

    private static int quantize(int channel, int step) {
        return Mth.clamp(Math.round(channel / (float) step) * step, 0, 255);
    }

    private static int directColor(long seed) {
        int r = Math.round(unit(seed ^ 0x524544L) * 255.0F);
        int g = Math.round(unit(seed ^ 0x47524545L) * 255.0F);
        int b = Math.round(unit(seed ^ 0x424C5545L) * 255.0F);
        return r << 16 | g << 8 | b;
    }

    private static int blend(int from, int to, float amount) {
        int r = Math.round(Mth.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(amount, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
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
