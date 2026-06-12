package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public final class SkyRenderCorruptionHooks {
    private static final ThreadLocal<BuildContext> BUILD_CONTEXT = new ThreadLocal<>();
    private static Method createStarsMethod;
    private static boolean createStarsMethodChecked;
    private static String appliedSignature = "";

    private SkyRenderCorruptionHooks() {
    }

    public static void onRenderSky(LevelRenderer renderer) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String signature = starMutationActive(stack) ? starSignature(stack) : "";
        if (signature.equals(appliedSignature)) {
            return;
        }
        appliedSignature = signature;
        Method method = createStarsMethod();
        if (method == null || renderer == null) {
            return;
        }
        try {
            method.invoke(renderer);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static void beginBuild() {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!starMutationActive(stack)) {
            BUILD_CONTEXT.remove();
            return;
        }
        BUILD_CONTEXT.set(new BuildContext(stack, starSignature(stack)));
    }

    public static void endBuild() {
        BUILD_CONTEXT.remove();
    }

    public static VertexConsumer vertex(BufferBuilder builder, double x, double y, double z) {
        BuildContext context = BUILD_CONTEXT.get();
        if (context == null) {
            return builder.vertex(x, y, z);
        }

        int ordinal = context.nextVertexOrdinal();
        int star = ordinal >> 2;
        int corner = ordinal & 3;
        CorruptionEffectStack stack = context.stack();
        float intensity = starIntensity(stack, star);
        if (intensity <= 0.0F) {
            return builder.vertex(x, y, z);
        }

        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 0.000001D) {
            return builder.vertex(x, y, z);
        }

        String targetId = "sky_star:" + star;
        long hash = stack.stableLong(CorruptionSurface.LIGHT_FIELD, targetId, corner ^ 0x53544152);
        double centerScale = 100.0D / length;
        double centerX = x * centerScale;
        double centerY = y * centerScale;
        double centerZ = z * centerScale;
        double offsetX = x - centerX;
        double offsetY = y - centerY;
        double offsetZ = z - centerZ;
        double scale = 1.0D + intensity * (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER) ? 34.0D : 14.0D);
        double skewX = 1.0D + signed(hash ^ 0x58415354L, intensity * 8.0D);
        double skewY = 1.0D + signed(hash ^ 0x59415354L, intensity * 8.0D);
        double skewZ = 1.0D + signed(hash ^ 0x5A415354L, intensity * 8.0D);
        double drift = stack.extreme(CorruptionSurface.WORLD_RENDER) ? signed(hash ^ 0x44524946L, intensity * 14.0D) : 0.0D;
        return builder.vertex(
                centerX + offsetX * scale * skewX + signed(hash ^ 0x4A585354L, intensity * 0.55D) + drift * 0.20D,
                centerY + offsetY * scale * skewY + signed(hash ^ 0x4A595354L, intensity * 0.55D),
                centerZ + offsetZ * scale * skewZ + signed(hash ^ 0x4A5A5354L, intensity * 0.55D) - drift * 0.20D
        );
    }

    private static boolean starMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.LIGHT_FIELD)
                || stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                || stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY);
    }

    private static float starIntensity(CorruptionEffectStack stack, int star) {
        if (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }
        String targetId = "sky_star:" + star;
        float light = Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.86F);
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.72F);
        float texture = stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.36F;
        return Mth.clamp(Math.max(light, Math.max(world, texture)), 0.0F, 1.0F);
    }

    private static String starSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.enabledTargetsMask()
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x53544152, 64)
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x574F524C, 64)
                + ":" + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x544558, 64);
    }

    private static Method createStarsMethod() {
        if (createStarsMethodChecked) {
            return createStarsMethod;
        }
        createStarsMethodChecked = true;
        for (String name : new String[]{"createStars", "m_109835_"}) {
            try {
                Method method = LevelRenderer.class.getDeclaredMethod(name);
                method.setAccessible(true);
                createStarsMethod = method;
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }

    private static double unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }

    private static final class BuildContext {
        private final CorruptionEffectStack stack;
        private int vertexOrdinal = -1;

        private BuildContext(CorruptionEffectStack stack, String signature) {
            this.stack = stack;
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private int nextVertexOrdinal() {
            vertexOrdinal++;
            return vertexOrdinal;
        }
    }
}
