package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MenuPanoramaCorruptionHooks {
    private MenuPanoramaCorruptionHooks() {
    }

    public static float pitch(float original) {
        return mutateCameraAngle(original, "title_panorama:pitch", 0x50495443, 72.0F);
    }

    public static float yaw(float original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = cameraIntensity(stack, "title_panorama:yaw");
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.CAMERA_TRANSFORM, "title_panorama:yaw", 0x594157);
        float multiplier = stack.extreme(CorruptionSurface.CAMERA_TRANSFORM)
                ? -3.0F + unit(seed ^ 0x4D554C54L) * 8.0F
                : 1.0F + signed(seed ^ 0x4D554C54L, intensity * 2.2F);
        float offset = signed(seed ^ 0x4F464653L, stack.extreme(CorruptionSurface.CAMERA_TRANSFORM) ? 360.0F : 18.0F + intensity * 150.0F);
        return original * multiplier + offset;
    }

    public static float alpha(float original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = titleIntensity(stack, "title_panorama:alpha");
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.TITLE_RENDER, "title_panorama:alpha", 0x414C5048);
        return Mth.clamp(original * (0.35F + unit(seed) * (1.20F + intensity * 1.8F)), 0.02F, 1.0F);
    }

    public static VertexConsumer vertex(BufferBuilder builder, double x, double y, double z) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = modelIntensity(stack, "title_panorama:cubemap");
        if (builder == null || intensity <= 0.025F) {
            return builder.vertex(x, y, z);
        }

        long seed = stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, "title_panorama:cubemap", 0x43554245);
        double xScale = 1.0D + signed(seed ^ 0x5853434CL, stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 2.2D : intensity * 1.25D);
        double yScale = 1.0D + signed(seed ^ 0x5953434CL, stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 2.2D : intensity * 1.25D);
        double zScale = 1.0D + signed(seed ^ 0x5A53434CL, stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 2.2D : intensity * 1.25D);
        double shearXY = signed(seed ^ 0x53485859L, intensity * 0.95D);
        double shearYZ = signed(seed ^ 0x5348595AL, intensity * 0.95D);
        double shearZX = signed(seed ^ 0x53485A58L, intensity * 0.95D);
        if (unit(seed ^ 0x5155414EL) < 0.12F + intensity * 0.24F) {
            double step = 0.15D + intensity * 0.55D;
            x = quantize(x, step);
            y = quantize(y, step);
            z = quantize(z, step);
        }
        return builder.vertex(
                x * xScale + y * shearXY,
                y * yScale + z * shearYZ,
                z * zScale + x * shearZX
        );
    }

    private static float mutateCameraAngle(float original, String targetId, int salt, float span) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        float intensity = cameraIntensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        long seed = stack.stableLong(CorruptionSurface.CAMERA_TRANSFORM, targetId, salt);
        return original + signed(seed, stack.extreme(CorruptionSurface.CAMERA_TRANSFORM) ? span * 2.4F : 4.0F + intensity * span);
    }

    private static float cameraIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.CAMERA_TRANSFORM) ? 1.0F : stack.intensity(CorruptionSurface.CAMERA_TRANSFORM) * 0.82F,
                stack.targetIntensity(CorruptionSurface.CAMERA_TRANSFORM, targetId)
        ), 0.0F, 1.0F);
    }

    private static float titleIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.TITLE_RENDER)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.TITLE_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.TITLE_RENDER) * 0.78F,
                stack.targetIntensity(CorruptionSurface.TITLE_RENDER, targetId)
        ), 0.0F, 1.0F);
    }

    private static float modelIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 1.0F : stack.intensity(CorruptionSurface.MODEL_GEOMETRY) * 0.78F,
                stack.targetIntensity(CorruptionSurface.MODEL_GEOMETRY, targetId)
        ), 0.0F, 1.0F);
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
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
