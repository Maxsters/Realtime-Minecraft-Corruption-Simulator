package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuiSliderCorruptionHooks {
    private static final ThreadLocal<Boolean> APPLYING_IMPOSSIBLE_VALUE = ThreadLocal.withInitial(() -> false);
    private static final Set<AbstractSliderButton> CORRUPTED_SLIDERS = Collections.newSetFromMap(new WeakHashMap<>());

    private GuiSliderCorruptionHooks() {
    }

    public static boolean shouldCorruptSlider(AbstractSliderButton button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(minecraft.screen)) {
            return false;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return false;
        }

        String targetId = targetId(button);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.72F);
        return intensity > 0.04F
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, System.identityHashCode(button) ^ 0x534C4944)
                < Mth.clamp(0.10F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.96F);
    }

    public static double corruptSliderValue(AbstractSliderButton button, double clampedValue) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(button);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.72F);
        long hash = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, System.identityHashCode(button) ^ 0x534C4952);
        int mode = Math.floorMod((int) (hash >>> 30), 5);
        double span = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 5.0D : 1.3D + intensity * 3.0D;
        double corrupted = switch (mode) {
            case 0 -> -span * (0.15D + unit(hash ^ 0x4E4547L) * 0.85D);
            case 1 -> 1.0D + span * (0.10D + unit(hash ^ 0x504F53L) * 0.90D);
            case 2 -> clampedValue + (unit(hash ^ 0x4F4646L) * 2.0D - 1.0D) * span;
            case 3 -> Math.rint((clampedValue - span * 0.5D) * (2.0D + intensity * 10.0D)) / (2.0D + intensity * 10.0D);
            default -> unit(hash ^ 0x455854L) < 0.5D ? -1.0D - span : 2.0D + span;
        };
        return Mth.clamp(corrupted, -3.0D, 4.0D);
    }

    public static void markSliderCorrupted(AbstractSliderButton button) {
        if (button != null) {
            CORRUPTED_SLIDERS.add(button);
        }
    }

    public static boolean consumeSliderRepair(AbstractSliderButton button, double currentValue) {
        if (button == null) {
            return false;
        }
        boolean wasCorrupted = CORRUPTED_SLIDERS.remove(button);
        return wasCorrupted || currentValue < 0.0D || currentValue > 1.0D || Double.isNaN(currentValue);
    }

    public static void beginImpossibleSliderValue() {
        APPLYING_IMPOSSIBLE_VALUE.set(true);
    }

    public static void endImpossibleSliderValue() {
        APPLYING_IMPOSSIBLE_VALUE.set(false);
    }

    public static boolean isApplyingImpossibleSliderValue() {
        return APPLYING_IMPOSSIBLE_VALUE.get();
    }

    private static String targetId(AbstractSliderButton button) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft == null || minecraft.screen == null ? "no_screen" : minecraft.screen.getClass().getName();
        return "gui_slider:" + screen + ":" + (button == null ? "unknown" : button.getClass().getName());
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
