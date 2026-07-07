package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LoadingScreenCorruptionHooks {
    private LoadingScreenCorruptionHooks() {
    }

    public static int chunkCenterX(int original) {
        return mutatePosition(original, 0, "level_loading_chunks:center_x", 0x434858);
    }

    public static int chunkCenterY(int original) {
        return mutatePosition(original, 1, "level_loading_chunks:center_y", 0x434859);
    }

    public static int chunkCellSize(int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = guiIntensity(stack, "level_loading_chunks:cell");
        if (intensity <= 0.025F) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, "level_loading_chunks:cell", 0x43454C4C);
        float multiplier = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.18F + unit(seed) * 8.8F
                : 1.0F + signed(seed, 0.20F + intensity * 2.35F);
        if (unit(seed ^ 0x534E4150L) < 0.08F + intensity * 0.28F) {
            multiplier = Math.round(multiplier * 2.0F) / 2.0F;
        }
        return Math.max(1, Math.round(Mth.clamp(original * multiplier, 1.0F, 18.0F)));
    }

    public static int chunkPadding(int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = guiIntensity(stack, "level_loading_chunks:padding");
        if (intensity <= 0.025F) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, "level_loading_chunks:padding", 0x50414444);
        int span = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 12 : Math.round(1.0F + intensity * 7.0F);
        return Mth.clamp(original + Math.round(signed(seed, span)), 0, 14);
    }

    public static int progressTextX(int original) {
        return mutatePosition(original, 0, "level_loading_progress:x", 0x505258);
    }

    public static int progressTextY(int original) {
        return mutatePosition(original, 1, "level_loading_progress:y", 0x505259);
    }

    public static int progressTextColor(int original) {
        return mutateColor(original, "level_loading_progress:color", 0x5052434C);
    }

    public static int chunkColor(int original, Object status) {
        String statusName = status instanceof ChunkStatus chunkStatus ? chunkStatus.toString() : String.valueOf(status);
        return mutateColor(original, "level_loading_chunks:color:" + statusName, 0x43434F4C);
    }

    private static int mutatePosition(int original, int axis, String targetId, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = guiIntensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int extent = minecraft == null || minecraft.getWindow() == null
                ? (axis == 0 ? 320 : 180)
                : Math.max(1, axis == 0 ? minecraft.getWindow().getGuiScaledWidth() : minecraft.getWindow().getGuiScaledHeight());
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, salt);
        float span = extent * (0.025F + intensity * (axis == 0 ? 0.20F : 0.17F));
        int offset = Math.round(signed(seed, stack.extreme(CorruptionSurface.GUI_SURFACE) ? extent * 0.48F : span));
        if (unit(seed ^ 0x5155414EL) < 0.08F + intensity * 0.22F) {
            int grid = 2 + Math.round(unit(seed ^ 0x47524944L) * (6.0F + intensity * 22.0F));
            offset = Math.round((float) offset / grid) * grid;
        }
        return Mth.clamp(original + offset, -extent, extent * 2);
    }

    private static int mutateColor(int original, String targetId, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = guiIntensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, salt);
        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.96F
                : Mth.clamp(0.12F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.90F);
        if (unit(seed ^ 0x47415445L) > chance) {
            return original;
        }

        int alpha = original & 0xFF000000;
        int red = (original >>> 16) & 0xFF;
        int green = (original >>> 8) & 0xFF;
        int blue = original & 0xFF;
        float span = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 255.0F : 36.0F + intensity * 164.0F;
        red = Mth.clamp(red + Math.round(signed(seed ^ 0x52L, span)), 0, 255);
        green = Mth.clamp(green + Math.round(signed(seed ^ 0x47L, span)), 0, 255);
        blue = Mth.clamp(blue + Math.round(signed(seed ^ 0x42L, span)), 0, 255);
        if (unit(seed ^ 0x53574150L) < 0.12F + intensity * 0.30F) {
            int mode = Math.floorMod((int) (seed >>> 28), 3);
            if (mode == 0) {
                int temp = red;
                red = green;
                green = blue;
                blue = temp;
            } else if (mode == 1) {
                red = 255 - red;
                green = 255 - green;
                blue = 255 - blue;
            } else {
                red = Math.round(unit(seed ^ 0x52414E44L) * 255.0F);
                green = Math.round(unit(seed ^ 0x47414E44L) * 255.0F);
                blue = Math.round(unit(seed ^ 0x42414E44L) * 255.0F);
            }
        }
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static float guiIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.0F : stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.82F,
                stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId)
        ) + stack.instability() * 0.05F, 0.0F, 1.0F);
    }

    private static float signed(long seed, float span) {
        return (unit(seed) * 2.0F - 1.0F) * span;
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
