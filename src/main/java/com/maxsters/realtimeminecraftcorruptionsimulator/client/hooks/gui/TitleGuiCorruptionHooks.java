package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TitleGuiCorruptionHooks {
    private TitleGuiCorruptionHooks() {
    }

    public static float splashX(float original) {
        return original + splashOffset("splash:x", 0, 0x53504C58);
    }

    public static float splashY(float original) {
        return original + splashOffset("splash:y", 1, 0x53504C59);
    }

    public static float splashRotation(float original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_splash:rotation");
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_splash:rotation", 0x53524F54);
        return original + signed(seed, stack.extreme(CorruptionSurface.TITLE_RENDER) ? 180.0F : 8.0F + intensity * 96.0F);
    }

    public static float splashScale(float original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_splash:scale");
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_splash:scale", 0x53534341);
        float multiplier = stack.extreme(CorruptionSurface.TITLE_RENDER)
                ? 0.10F + unit(seed) * 4.8F
                : 1.0F + signed(seed, 0.16F + intensity * 1.55F);
        return Mth.clamp(original * multiplier, 0.02F, 8.0F);
    }

    public static int splashTextX(int original) {
        return original + Math.round(splashOffset("splash:text_x", 0, 0x535458));
    }

    public static int splashTextY(int original) {
        return original + Math.round(splashOffset("splash:text_y", 1, 0x535459));
    }

    public static int splashColor(int original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_splash:color");
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_splash:color", 0x53434F4C);
        if (unit(seed ^ 0x47415445L) > (stack.extreme(CorruptionSurface.TITLE_RENDER) ? 0.96F : 0.18F + intensity * 0.70F)) {
            return original;
        }
        int alpha = original & 0xFF000000;
        int red = (original >>> 16) & 0xFF;
        int green = (original >>> 8) & 0xFF;
        int blue = original & 0xFF;
        float span = stack.extreme(CorruptionSurface.TITLE_RENDER) ? 255.0F : 48.0F + intensity * 168.0F;
        red = Mth.clamp(red + Math.round(signed(seed ^ 0x52L, span)), 0, 255);
        green = Mth.clamp(green + Math.round(signed(seed ^ 0x47L, span)), 0, 255);
        blue = Mth.clamp(blue + Math.round(signed(seed ^ 0x42L, span)), 0, 255);
        return alpha | (red << 16) | (green << 8) | blue;
    }

    public static LogoTransform logoTransform(int screenWidth, int top) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_logo:transform");
        if (intensity <= 0.025F) {
            return LogoTransform.IDENTITY;
        }

        int width = Math.max(1, screenWidth);
        int height = screenExtent(false);
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_logo:transform", 0x4C4F474F);
        float dx = signed(seed ^ 0x584F4646L, width * (stack.extreme(CorruptionSurface.TITLE_RENDER) ? 0.34F : 0.018F + intensity * 0.18F));
        float dy = signed(seed ^ 0x594F4646L, height * (stack.extreme(CorruptionSurface.TITLE_RENDER) ? 0.30F : 0.014F + intensity * 0.15F));
        float scaleX = stack.extreme(CorruptionSurface.TITLE_RENDER)
                ? 0.12F + unit(seed ^ 0x5853434CL) * 3.6F
                : Mth.clamp(1.0F + signed(seed ^ 0x5853434CL, 0.12F + intensity * 1.20F), 0.16F, 2.85F);
        float scaleY = stack.extreme(CorruptionSurface.TITLE_RENDER)
                ? 0.12F + unit(seed ^ 0x5953434CL) * 3.6F
                : Mth.clamp(1.0F + signed(seed ^ 0x5953434CL, 0.12F + intensity * 1.20F), 0.16F, 2.85F);
        float rotation = signed(seed ^ 0x524F5441L, stack.extreme(CorruptionSurface.TITLE_RENDER) ? 180.0F : 5.0F + intensity * 82.0F);
        float pivotX = width * (0.50F + signed(seed ^ 0x505658L, 0.16F + intensity * 0.20F));
        float pivotY = top + 24.0F + signed(seed ^ 0x505659L, 8.0F + intensity * 42.0F);
        return new LogoTransform(dx, dy, scaleX, scaleY, rotation, pivotX, pivotY);
    }

    private static float splashOffset(String target, int axis, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_" + target);
        if (intensity <= 0.025F) {
            return 0.0F;
        }
        int extent = screenExtent(axis == 0);
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_" + target, salt);
        float span = stack.extreme(CorruptionSurface.TITLE_RENDER)
                ? extent * 0.40F
                : extent * (0.015F + intensity * (axis == 0 ? 0.18F : 0.14F));
        return signed(seed, span);
    }

    private static int screenExtent(boolean horizontal) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getWindow() == null
                ? (horizontal ? 320 : 180)
                : Math.max(1, horizontal ? minecraft.getWindow().getGuiScaledWidth() : minecraft.getWindow().getGuiScaledHeight());
    }

    private static float titleIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.TITLE_RENDER)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.TITLE_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.TITLE_RENDER) * 0.86F,
                stack.targetIntensity(CorruptionSurface.TITLE_RENDER, targetId)
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

    public record LogoTransform(float dx, float dy, float scaleX, float scaleY, float rotation, float pivotX, float pivotY) {
        private static final LogoTransform IDENTITY = new LogoTransform(0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);

        public boolean identity() {
            return this == IDENTITY;
        }
    }
}
