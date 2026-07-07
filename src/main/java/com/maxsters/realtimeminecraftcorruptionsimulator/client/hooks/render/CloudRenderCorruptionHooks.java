package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public final class CloudRenderCorruptionHooks {
    private static final ThreadLocal<BuildContext> BUILD_CONTEXT = new ThreadLocal<>();
    private static Field generateCloudsField;
    private static boolean generateCloudsFieldChecked;
    private static String appliedCloudSignature = "";

    private CloudRenderCorruptionHooks() {
    }

    public static void onRenderClouds(LevelRenderer renderer) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String desiredSignature = cloudMutationActive(stack) ? cloudRefreshSignature(stack) : "";
        if (!desiredSignature.equals(appliedCloudSignature)) {
            appliedCloudSignature = desiredSignature;
            flagCloudsDirty(renderer);
        }
    }

    public static void beginBuild(Vec3 cloudColor) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!cloudMutationActive(stack)) {
            BUILD_CONTEXT.remove();
            return;
        }
        BUILD_CONTEXT.set(new BuildContext(stack, cloudRenderSignature(stack), cloudTextureSignature(stack), cloudColor == null ? Vec3.ZERO : cloudColor));
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
        int face = ordinal >> 2;
        CorruptionEffectStack stack = context.stack();
        float intensity = cloudIntensity(stack, face);
        if (intensity <= 0.0F) {
            return builder.vertex(x, y, z);
        }

        int corner = ordinal & 3;
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.renderSignature(), face ^ 0x56455254);
        int mode = Math.floorMod((int) (seed >>> 27), 8);
        double wave = Math.sin((x + z) * (0.018D + intensity * 0.075D) + unit(seed ^ 0x57415645L) * Mth.TWO_PI);
        double dx = signed(seed ^ (corner * 0x9E3779B97F4A7C15L), intensity * 2.8D);
        double dy = signed(seed ^ 0x59434C44L ^ corner, intensity * 1.5D);
        double dz = signed(seed ^ 0x5A434C44L ^ corner, intensity * 2.8D);
        switch (mode) {
            case 0 -> {
                dx += wave * intensity * 7.5D;
                dz -= wave * intensity * 4.5D;
            }
            case 1 -> {
                dy += Math.sin(x * 0.08D + z * 0.11D + unit(seed) * Mth.TWO_PI) * intensity * 10.0D;
            }
            case 2 -> {
                double step = 0.5D + intensity * (3.5D + unit(seed ^ 0x5155414EL) * 9.0D);
                x = quantize(x + dx, step);
                y = quantize(y + dy, Math.max(0.125D, step * 0.5D));
                z = quantize(z + dz, step);
                dx = 0.0D;
                dy = 0.0D;
                dz = 0.0D;
            }
            case 3 -> {
                y *= 0.02D + unit(seed ^ 0x464C4154L) * (0.08D + intensity * 0.18D);
                dx += signed(seed ^ 0x464C5858L, intensity * 15.0D);
                dz += signed(seed ^ 0x464C585AL, intensity * 15.0D);
            }
            case 4 -> {
                x = -x + signed(seed ^ 0x4D495252L, intensity * 12.0D);
                z += wave * intensity * 8.0D;
            }
            case 5 -> {
                double fold = Math.sin((x - z) * (0.05D + intensity * 0.16D)) * intensity * 18.0D;
                dx += fold;
                dz += fold * 0.35D;
                dy -= Math.abs(fold) * 0.18D;
            }
            case 6 -> {
                dx += signed(seed ^ 0x4C414758L, 16.0D + intensity * 80.0D);
                dz += signed(seed ^ 0x4C41475AL, 16.0D + intensity * 80.0D);
                dy += signed(seed ^ 0x4C414759L, 4.0D + intensity * 24.0D);
            }
            default -> {
                dx *= unit(seed ^ 0x44524F58L) < 0.35F + intensity * 0.38F ? -2.4D : 0.22D;
                dy *= unit(seed ^ 0x44524F59L) < 0.35F + intensity * 0.38F ? -2.0D : 0.18D;
                dz *= unit(seed ^ 0x44524F5AL) < 0.35F + intensity * 0.38F ? -2.4D : 0.22D;
            }
        }

        if (stack.extreme(CorruptionSurface.WORLD_RENDER) || unit(seed ^ 0x45585452L) < intensity * 0.20F) {
            double flatten = 0.004D + unit(seed ^ 0x504C414EL) * 0.08D;
            y *= flatten;
        }
        return builder.vertex(x + dx, y + dy, z + dz);
    }

    public static VertexConsumer uv(VertexConsumer consumer, float u, float v) {
        BuildContext context = BUILD_CONTEXT.get();
        if (context == null) {
            return consumer.uv(u, v);
        }

        CorruptionEffectStack stack = context.stack();
        int ordinal = Math.max(0, context.currentVertexOrdinal());
        int face = ordinal >> 2;
        String target = "cloud:uv:" + face + ":" + (ordinal & 3);
        float intensity = cloudUvIntensity(stack, face);
        if (intensity <= 0.0F) {
            return consumer.uv(u, v);
        }

        long clock = stack.stableLong(CorruptionSurface.MODEL_UV, context.textureSignature(), face ^ 0x5556);
        float span = 0.006F + intensity * 0.11F;
        float mutatedU = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.MODEL_UV, target + ":u", u, span, -2.0F, 2.0F, 0x55, clock);
        float mutatedV = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.MODEL_UV, target + ":v", v, span, -2.0F, 2.0F, 0x76, clock);
        return consumer.uv(mutatedU, mutatedV);
    }

    public static VertexConsumer color(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        BuildContext context = BUILD_CONTEXT.get();
        if (context == null) {
            return consumer.color(red, green, blue, alpha);
        }

        CorruptionEffectStack stack = context.stack();
        int ordinal = Math.max(0, context.currentVertexOrdinal());
        int face = ordinal >> 2;
        String target = "cloud:color:" + face;
        float intensity = cloudColorIntensity(stack, face);
        if (intensity <= 0.0F) {
            return consumer.color(red, green, blue, alpha);
        }

        long clock = stack.stableLong(CorruptionSurface.LIGHT_FIELD, context.renderSignature(), face ^ 0x434F4C);
        float span = 0.10F + intensity * 0.82F;
        float mutatedRed = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, target + ":r", red, span, 0.0F, 1.65F, 0x12, clock);
        float mutatedGreen = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, target + ":g", green, span, 0.0F, 1.65F, 0x24, clock);
        float mutatedBlue = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, target + ":b", blue, span, 0.0F, 1.65F, 0x48, clock);
        float mutatedAlpha = (float) Mth.clamp(alpha + signed(stack.stableLong(CorruptionSurface.WORLD_RENDER, target + ":a", 0x61), 0.22D * intensity), 0.05D, 1.0D);
        return consumer.color(mutatedRed, mutatedGreen, mutatedBlue, mutatedAlpha);
    }

    private static boolean cloudMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                || stack.activeOrExtreme(CorruptionSurface.MODEL_UV)
                || stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                || stack.activeOrExtreme(CorruptionSurface.LIGHT_FIELD);
    }

    private static float cloudIntensity(CorruptionEffectStack stack, int face) {
        if (stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }
        String targetId = "cloud:face:" + face;
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.64F);
        return Mth.clamp(world, 0.0F, 1.0F);
    }

    private static float cloudUvIntensity(CorruptionEffectStack stack, int face) {
        if (stack.extreme(CorruptionSurface.MODEL_UV) || stack.extreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return 1.0F;
        }
        String targetId = "cloud:face:" + face;
        float texture = Math.max(stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId), stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.42F);
        float uv = Math.max(stack.targetIntensity(CorruptionSurface.MODEL_UV, targetId), stack.intensity(CorruptionSurface.MODEL_UV) * 0.46F);
        return Mth.clamp(Math.max(texture, uv), 0.0F, 1.0F);
    }

    private static float cloudColorIntensity(CorruptionEffectStack stack, int face) {
        if (stack.extreme(CorruptionSurface.WORLD_RENDER) || stack.extreme(CorruptionSurface.LIGHT_FIELD)) {
            return 1.0F;
        }
        String targetId = "cloud:face:" + face;
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.36F);
        float light = Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.58F);
        return Mth.clamp(Math.max(world, light), 0.0F, 1.0F);
    }

    private static String cloudRefreshSignature(CorruptionEffectStack stack) {
        return cloudRenderSignature(stack)
                + ":" + cloudTextureSignature(stack)
                + ":" + stack.enabledTargetsMask();
    }

    private static String cloudRenderSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x434C4F44, 96)
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x4C494748, 96);
    }

    private static String cloudTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.MODEL_UV, 0x5556434C, 96)
                + ":" + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x54455843, 96);
    }

    private static void flagCloudsDirty(LevelRenderer renderer) {
        if (renderer == null) {
            return;
        }
        Field field = generateCloudsField();
        if (field == null) {
            return;
        }
        try {
            field.setBoolean(renderer, true);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static Field generateCloudsField() {
        if (generateCloudsFieldChecked) {
            return generateCloudsField;
        }
        generateCloudsFieldChecked = true;
        for (String name : new String[]{"generateClouds", "f_109474_"}) {
            try {
                Field field = LevelRenderer.class.getDeclaredField(name);
                field.setAccessible(true);
                generateCloudsField = field;
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
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
        private final String renderSignature;
        private final String textureSignature;
        private int vertexOrdinal = -1;

        private BuildContext(CorruptionEffectStack stack, String renderSignature, String textureSignature, Vec3 cloudColor) {
            this.stack = stack;
            String colorKey = ":" + Math.round(cloudColor.x * 255.0D) + ":" + Math.round(cloudColor.y * 255.0D) + ":" + Math.round(cloudColor.z * 255.0D);
            this.renderSignature = renderSignature + colorKey;
            this.textureSignature = textureSignature;
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private String renderSignature() {
            return renderSignature;
        }

        private String textureSignature() {
            return textureSignature;
        }

        private int nextVertexOrdinal() {
            vertexOrdinal++;
            return vertexOrdinal;
        }

        private int currentVertexOrdinal() {
            return vertexOrdinal;
        }
    }
}
