package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayPanel;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.util.Mth;

public final class ToastPopupCorruptionHooks {
    private static final ThreadLocal<Integer> RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private ToastPopupCorruptionHooks() {
    }

    public static float toastYOffset(float originalY) {
        return originalY + CorruptionOverlayPanel.topRightPinnedToastOffset();
    }

    public static void beginToastRender(Toast toast, GuiGraphics graphics) {
        if (!isAdvancementOrRecipeToast(toast) || graphics == null || protectedScreen()) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(toast, "surface");
        float intensity = surfaceIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.94F
                : Mth.clamp(0.08F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, 0x544F4153) > chance) {
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, toast.getClass().getName().hashCode());
        float dx = signed(seed ^ 0x584F4646L, 8.0F + intensity * 58.0F);
        float dy = signed(seed ^ 0x594F4646L, 5.0F + intensity * 46.0F);
        float scaleX = Mth.clamp(1.0F + signed(seed ^ 0x53584F46L, 0.08F + intensity * 0.58F), 0.38F, 2.10F);
        float scaleY = Mth.clamp(1.0F + signed(seed ^ 0x53594F46L, 0.08F + intensity * 0.58F), 0.38F, 2.10F);
        int mode = Math.floorMod((int) (seed >>> 30), 5);
        if (mode == 1) {
            dx = Math.round(dx / 8.0F) * 8.0F;
            dy = Math.round(dy / 8.0F) * 8.0F;
        } else if (mode == 2) {
            scaleX = Mth.clamp(-scaleX, -2.10F, -0.38F);
        } else if (mode == 3) {
            scaleY = Mth.clamp(scaleY * 0.45F, 0.28F, 1.0F);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(dx, dy, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        RENDER_DEPTH.set(RENDER_DEPTH.get() + 1);
    }

    public static void endToastRender(GuiGraphics graphics) {
        if (graphics == null) {
            return;
        }
        int depth = RENDER_DEPTH.get();
        if (depth <= 0) {
            return;
        }
        graphics.pose().popPose();
        RENDER_DEPTH.set(depth - 1);
    }

    public static long corruptToastTime(Toast toast, long visibleTime) {
        if (!isAdvancementOrRecipeToast(toast) || visibleTime < 0L || protectedScreen()) {
            return visibleTime;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(toast, "time");
        float intensity = functionalityIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return visibleTime;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.94F
                : Mth.clamp(0.08F + intensity * 0.70F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (stack.unit(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x54494D45) > chance) {
            return visibleTime;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x544F4154);
        int mode = Math.floorMod((int) (seed >>> 29), 6);
        double scale = 0.20D + unit(seed ^ 0x5343414CL) * (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 4.80D : 0.90D + intensity * 3.20D);
        double mutated = switch (mode) {
            case 0 -> visibleTime * scale;
            case 1 -> visibleTime + signed(seed ^ 0x4F464653L, 800.0D + intensity * 7200.0D);
            case 2 -> Math.max(0.0D, visibleTime - (600.0D + unit(seed ^ 0x44454C41L) * (2200.0D + intensity * 7600.0D)));
            case 3 -> Math.rint(visibleTime / (180.0D + intensity * 900.0D)) * (180.0D + intensity * 900.0D);
            case 4 -> Math.abs(((visibleTime + Math.round(unit(seed ^ 0x50484153L) * 4200.0D)) % 10000L) - 5000L);
            default -> visibleTime * (unit(seed ^ 0x46415354L) < 0.5F ? 0.35D : 2.75D + intensity * 2.50D);
        };
        return Math.round(Mth.clamp(mutated, 0.0D, 20_000.0D));
    }

    public static Toast.Visibility corruptVisibility(Toast toast, Toast.Visibility visibility, long visibleTime) {
        if (visibility != Toast.Visibility.SHOW || !isAdvancementOrRecipeToast(toast) || visibleTime < 0L || protectedScreen()) {
            return visibility;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(toast, "visibility");
        float intensity = functionalityIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return visibility;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.28F
                : Mth.clamp(0.015F + intensity * 0.17F + stack.instability() * 0.035F, 0.0F, 0.24F);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x48494445);
        return unit(seed ^ (visibleTime / 250L)) < chance ? Toast.Visibility.HIDE : visibility;
    }

    private static boolean isAdvancementOrRecipeToast(Toast toast) {
        return toast instanceof AdvancementToast || toast instanceof RecipeToast;
    }

    private static boolean protectedScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null
                || ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isDeathScreen(minecraft.screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(minecraft.screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(minecraft.screen);
    }

    private static float surfaceIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.0F : stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.76F,
                stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId)
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static float functionalityIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 1.0F : stack.intensity(CorruptionSurface.GUI_FUNCTIONALITY) * 0.78F,
                stack.targetIntensity(CorruptionSurface.GUI_FUNCTIONALITY, targetId)
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String targetId(Toast toast, String phase) {
        return "gui_toast_popup:" + phase + ":" + (toast == null ? "unknown" : toast.getClass().getName());
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
