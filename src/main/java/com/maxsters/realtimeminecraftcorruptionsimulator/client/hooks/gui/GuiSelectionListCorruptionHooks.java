package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GuiSelectionListCorruptionHooks {
    private GuiSelectionListCorruptionHooks() {
    }

    public static int rowLeft(AbstractSelectionList<?> list, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "row_left");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenWidth = screenExtent(true);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x524C4546);
        int offset = Math.round(signed(seed, screenWidth * (stack.extreme(CorruptionSurface.GUI_SURFACE) ? 0.44F : 0.02F + intensity * 0.22F)));
        return Mth.clamp(original + offset, -screenWidth, screenWidth * 2);
    }

    public static int rowWidth(AbstractSelectionList<?> list, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "row_width");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenWidth = screenExtent(true);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x52574944);
        float multiplier = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.18F + unit(seed) * 3.9F
                : Mth.clamp(1.0F + signed(seed, 0.12F + intensity * 1.45F), 0.28F, 2.85F);
        return Math.round(Mth.clamp(original * multiplier, 18.0F, screenWidth * 2.0F));
    }

    public static int rowTop(AbstractSelectionList<?> list, int row, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "row_top");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenHeight = screenExtent(false);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x52544F50);
        int global = Math.round(signed(seed ^ 0x474C4F42L, screenHeight * (stack.extreme(CorruptionSurface.GUI_SURFACE) ? 0.28F : 0.015F + intensity * 0.14F)));
        int perRow = Math.round(signed(seed ^ (row * 0x9E3779B9L), stack.extreme(CorruptionSurface.GUI_SURFACE) ? 64.0F : 3.0F + intensity * 30.0F));
        if (unit(seed ^ 0x5155414EL) < 0.08F + intensity * 0.20F) {
            int grid = 2 + Math.round(unit(seed ^ 0x47524944L) * (4.0F + intensity * 24.0F));
            perRow = Math.round((float) perRow / grid) * grid;
        }
        return original + global + perRow;
    }

    public static int scrollbarPosition(AbstractSelectionList<?> list, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "scrollbar_x");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenWidth = screenExtent(true);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x5343524C);
        int offset = Math.round(signed(seed, screenWidth * (stack.extreme(CorruptionSurface.GUI_SURFACE) ? 0.36F : 0.025F + intensity * 0.20F)));
        return Mth.clamp(original + offset, -16, screenWidth + 16);
    }

    public static int maxScroll(AbstractSelectionList<?> list, int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "max_scroll");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenHeight = screenExtent(false);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x4D415853);
        float multiplier = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.04F + unit(seed) * 4.8F
                : Mth.clamp(1.0F + signed(seed, 0.22F + intensity * 1.85F), 0.08F, 3.45F);
        int extra = Math.round(signed(seed ^ 0x45585452L, screenHeight * (stack.extreme(CorruptionSurface.GUI_SURFACE) ? 2.2F : intensity * 0.85F)));
        return Math.max(0, Math.round(original * multiplier) + extra);
    }

    public static double scrollAmount(AbstractSelectionList<?> list, double original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        String targetId = targetId(list, "scroll_amount");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int screenHeight = screenExtent(false);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x53414D54);
        double multiplier = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? -1.25D + unit(seed) * 4.0D
                : 1.0D + signed(seed, 0.10D + intensity * 0.95D);
        double offset = signed(seed ^ 0x4F464653L, screenHeight * (stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.35D : intensity * 0.42D));
        return original * multiplier + offset;
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return 0.0F;
        }
        return stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.76F
                ) + stack.instability() * 0.07F, 0.0F, 1.0F);
    }

    private static String targetId(AbstractSelectionList<?> list, String part) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft == null || minecraft.screen == null ? "no_screen" : minecraft.screen.getClass().getName();
        String listType = list == null ? "unknown_list" : list.getClass().getName();
        return "gui_selection_list:" + screen + ":" + listType + ":" + part;
    }

    private static int screenExtent(boolean horizontal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return horizontal ? 320 : 180;
        }
        return Math.max(1, horizontal ? minecraft.getWindow().getGuiScaledWidth() : minecraft.getWindow().getGuiScaledHeight());
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
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
