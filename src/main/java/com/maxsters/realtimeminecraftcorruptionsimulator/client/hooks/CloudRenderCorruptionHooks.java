package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

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
        String desiredSignature = cloudMutationActive(stack) ? cloudSignature(stack) : "";
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
        BUILD_CONTEXT.set(new BuildContext(stack, cloudSignature(stack), cloudColor == null ? Vec3.ZERO : cloudColor));
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

        return builder.vertex(x, y, z);
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
        float intensity = cloudIntensity(stack, face);
        if (intensity <= 0.0F) {
            return consumer.uv(u, v);
        }

        long clock = stack.stableLong(CorruptionSurface.MODEL_UV, context.signature(), face ^ 0x5556);
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
        float intensity = cloudIntensity(stack, face);
        if (intensity <= 0.0F) {
            return consumer.color(red, green, blue, alpha);
        }

        long clock = stack.stableLong(CorruptionSurface.LIGHT_FIELD, context.signature(), face ^ 0x434F4C);
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
        if (stack.extreme(CorruptionSurface.WORLD_RENDER) || stack.extreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return 1.0F;
        }
        String targetId = "cloud:face:" + face;
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.64F);
        float texture = Math.max(stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId), stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.42F);
        float uv = Math.max(stack.targetIntensity(CorruptionSurface.MODEL_UV, targetId), stack.intensity(CorruptionSurface.MODEL_UV) * 0.46F);
        return Mth.clamp(Math.max(world, Math.max(texture, uv)), 0.0F, 1.0F);
    }

    private static String cloudSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.previousLevel()
                + ":" + stack.delta()
                + ":" + stack.fixedSeed()
                + ":" + stack.enabledTargetsMask()
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x434C4F44, 96)
                + ":" + stack.bucket(CorruptionSurface.MODEL_UV, 0x5556434C, 96)
                + ":" + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x54455843, 96)
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x4C494748, 96);
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
        private final String signature;
        private int vertexOrdinal = -1;

        private BuildContext(CorruptionEffectStack stack, String signature, Vec3 cloudColor) {
            this.stack = stack;
            this.signature = signature + ":" + Math.round(cloudColor.x * 255.0D) + ":" + Math.round(cloudColor.y * 255.0D) + ":" + Math.round(cloudColor.z * 255.0D);
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private String signature() {
            return signature;
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
