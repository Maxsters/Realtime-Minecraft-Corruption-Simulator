package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class GuiEntityPreviewCorruptionHooks {
    private static final int FAMILY_LAYOUT = 0;
    private static final int FAMILY_SCALE = 1;
    private static final int FAMILY_MOUSE_TRACKING = 2;
    private static final int FAMILY_ROTATION = 3;
    private static final int FAMILY_RENDER_STATE = 4;
    private static final int FAMILY_TIMING = 5;
    private static final int FAMILY_VISIBILITY = 6;
    private static final int FAMILY_MIXED = 7;

    private GuiEntityPreviewCorruptionHooks() {
    }

    public static boolean shouldSkip(LivingEntity entity, int x, int y, int scale, float mouseX, float mouseY) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return false;
        }
        String targetId = targetId(entity);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return false;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsVisibilityMutation(family, stack)) {
            return false;
        }
        int salt = previewSalt(entity, x, y, scale) ^ 0x48494445;
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.26F
                : Mth.clamp(0.015F + intensity * 0.22F + stack.instability() * 0.06F, 0.0F, 0.24F);
        return stack.unit(CorruptionSurface.GUI_FUNCTIONALITY, targetId + ":hidden", salt) < chance;
    }

    public static int x(LivingEntity entity, int value, int x, int y, int scale, float mouseX, float mouseY) {
        if (!allowsPositionMutation(entity, x, y, scale)) {
            return value;
        }
        return Math.round(value + layoutOffset(entity, x, y, scale, 0, 24.0F, 118.0F));
    }

    public static int y(LivingEntity entity, int value, int x, int y, int scale, float mouseX, float mouseY) {
        if (!allowsPositionMutation(entity, x, y, scale)) {
            return value;
        }
        return Math.round(value + layoutOffset(entity, x, y, scale, 1, 18.0F, 92.0F));
    }

    public static int scale(LivingEntity entity, int value, int x, int y, int scale, float mouseX, float mouseY) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return value;
        }
        String targetId = targetId(entity);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsScaleMutation(family, stack)) {
            return value;
        }
        long seed = previewSeed(stack, entity, x, y, scale, ":scale", 0x5343414C);
        float multiplier = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.08F + unit(seed ^ 0x45585452L) * 3.75F
                : Mth.clamp(1.0F + signed(seed, 0.22F + intensity * 1.35F), 0.16F, 2.85F);
        if (unit(seed ^ 0x464C4950L) < 0.05F + intensity * 0.20F) {
            multiplier *= unit(seed ^ 0x534D414CL) < 0.55F ? 0.12F + intensity * 0.24F : 1.7F + intensity * 2.3F;
        }
        return Math.max(1, Math.round(value * multiplier));
    }

    public static float mouseX(LivingEntity entity, float value, int x, int y, int scale, float mouseX, float mouseY) {
        if (!allowsMouseMutation(entity, x, y, scale)) {
            return value;
        }
        return corruptMouseCoordinate(entity, value, x, y, scale, mouseX, mouseY, 0);
    }

    public static float mouseY(LivingEntity entity, float value, int x, int y, int scale, float mouseX, float mouseY) {
        if (!allowsMouseMutation(entity, x, y, scale)) {
            return value;
        }
        return corruptMouseCoordinate(entity, value, x, y, scale, mouseX, mouseY, 1);
    }

    public static float angleX(LivingEntity entity, float value, float angleX, float angleY, int x, int y, int scale) {
        return angle(entity, value, angleX, angleY, x, y, scale, 0);
    }

    public static float angleY(LivingEntity entity, float value, float angleX, float angleY, int x, int y, int scale) {
        return angle(entity, value, angleY, angleX, x, y, scale, 1);
    }

    public static float renderYaw(LivingEntity entity, float value, int x, int y, int scale) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity) + ":render_yaw";
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsRotationMutation(family, stack)) {
            return value;
        }
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, previewSalt(entity, x, y, scale) ^ 0x52594157);
        return value + signed(seed, stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 360.0F : 12.0F + intensity * 180.0F);
    }

    public static float partialTick(LivingEntity entity, float value, int x, int y, int scale) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity) + ":partial_tick";
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsTimingMutation(family, stack)) {
            return value;
        }
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, previewSalt(entity, x, y, scale) ^ 0x50544943);
        if (unit(seed ^ 0x47415445L) > (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 0.88F : 0.14F + intensity * 0.48F)) {
            return value;
        }
        return stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? -2.0F + unit(seed) * 6.0F
                : Mth.clamp(value + signed(seed, 0.18F + intensity * 1.65F), -0.75F, 2.5F);
    }

    public static int packedLight(LivingEntity entity, int value, int x, int y, int scale) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity) + ":packed_light";
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsRenderStateMutation(family, stack)) {
            return value;
        }
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, previewSalt(entity, x, y, scale) ^ 0x4C494748);
        if (unit(seed ^ 0x47415445L) > (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 0.92F : 0.16F + intensity * 0.62F)) {
            return value;
        }
        int block = Mth.clamp(Math.round(unit(seed ^ 0x424C4F43L) * (15.0F + intensity * 16.0F)), 0, 15);
        int sky = Mth.clamp(Math.round(unit(seed ^ 0x534B59L) * (15.0F + intensity * 16.0F)), 0, 15);
        if (unit(seed ^ 0x4441524BL) < 0.18F + intensity * 0.26F) {
            block = 0;
            sky = 0;
        }
        return (sky << 20) | (block << 4);
    }

    public static boolean renderShadow(LivingEntity entity, boolean original, int x, int y, int scale) {
        if (original) {
            return true;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity) + ":shadow";
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return original;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsRenderStateMutation(family, stack)) {
            return original;
        }
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.52F
                : Mth.clamp(0.04F + intensity * 0.36F + stack.instability() * 0.05F, 0.0F, 0.44F);
        return stack.unit(CorruptionSurface.GUI_FUNCTIONALITY, targetId, previewSalt(entity, x, y, scale) ^ 0x53484457) < chance;
    }

    private static float angle(LivingEntity entity, float value, float primary, float secondary, int x, int y, int scale, int axis) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity) + ":angle_" + axis;
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }
        int family = behaviorFamily(stack, entity, x, y, scale);
        if (!allowsAngleMutation(family, stack)) {
            return value;
        }
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, previewSalt(entity, x, y, scale) ^ (0x414E474C + axis));
        int mode = Math.floorMod((int) (seed >>> 57), 6);
        float offset = signed(seed ^ 0x4F464653L, stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 2.8F : 0.08F + intensity * 1.6F);
        if (mode == 0) {
            return signed(seed ^ 0x46524545L, stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 3.2F : 0.35F + intensity * 1.7F);
        }
        if (mode == 1) {
            return -primary * (1.0F + intensity * 3.2F) + offset;
        }
        if (mode == 2) {
            return secondary * (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? -4.0F + unit(seed ^ 0x4D4958L) * 8.0F : -1.4F + unit(seed ^ 0x4D4958L) * (2.8F + intensity * 3.8F)) + offset;
        }
        if (mode == 3) {
            float step = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 0.08F + unit(seed ^ 0x53544550L) * 1.6F : 0.06F + intensity * 0.55F;
            return Math.round(primary / step) * step + offset;
        }
        if (mode == 4) {
            return primary * (0.02F + unit(seed ^ 0x4C415443L) * (0.12F + intensity * 0.38F)) + offset;
        }
        return primary * (1.4F + intensity * (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 8.5F : 4.5F)) + offset;
    }

    private static float layoutOffset(LivingEntity entity, int x, int y, int scale, int axis, float normalSpan, float extremeSpan) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return 0.0F;
        }
        String targetId = targetId(entity);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return 0.0F;
        }
        long seed = previewSeed(stack, entity, x, y, scale, ":layout", 0x504F5358 + axis * 0x9E37);
        float span = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? extremeSpan
                : normalSpan * (0.20F + intensity * 1.35F);
        float drift = signed(seed, span);
        if (unit(seed ^ 0x534E4150L) < 0.06F + intensity * 0.24F) {
            float grid = 8.0F + unit(seed ^ 0x47524944L) * (28.0F + intensity * 58.0F);
            drift = Math.round(drift / grid) * grid;
        }
        return drift;
    }

    private static float corruptMouseCoordinate(LivingEntity entity, float value, int x, int y, int scale, float mouseX, float mouseY, int axis) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(entity);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return value;
        }

        long seed = previewSeed(stack, entity, x, y, scale, ":mouse_tracking", 0x4D4F5553 + axis * 0x9E37);
        int mode = Math.floorMod((int) (seed >>> 57), 7);
        float center = axis == 0 ? x : y;
        float secondary = axis == 0 ? mouseY : mouseX;
        float span = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 180.0F
                : 26.0F * (0.35F + intensity * 3.8F);
        float offset = signed(seed ^ 0x4F464653L, span);
        return switch (mode) {
            case 0 -> center + offset;
            case 1 -> secondary + offset * 0.55F;
            case 2 -> center - (value - center) * (0.65F + intensity * 2.6F) + offset * 0.35F;
            case 3 -> {
                float step = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                        ? 4.0F + unit(seed ^ 0x53544550L) * 94.0F
                        : 5.0F + unit(seed ^ 0x53544550L) * (12.0F + intensity * 62.0F);
                yield center + Math.round((value - center) / step) * step + offset * 0.20F;
            }
            case 4 -> {
                float factor = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                        ? -4.0F + unit(seed ^ 0x5343414CL) * 8.0F
                        : -1.15F + unit(seed ^ 0x5343414CL) * (2.4F + intensity * 4.6F);
                yield center + (value - center) * factor + offset * 0.25F;
            }
            case 5 -> center + signed(seed ^ 0x4652455AL, stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 260.0F : 42.0F + intensity * 135.0F);
            default -> value + offset;
        };
    }

    private static boolean allowsPositionMutation(LivingEntity entity, int x, int y, int scale) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return false;
        }
        return allowsPositionMutation(behaviorFamily(stack, entity, x, y, scale), stack);
    }

    private static boolean allowsMouseMutation(LivingEntity entity, int x, int y, int scale) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return false;
        }
        return allowsMouseMutation(behaviorFamily(stack, entity, x, y, scale), stack);
    }

    private static int behaviorFamily(CorruptionEffectStack stack, LivingEntity entity, int x, int y, int scale) {
        if (stack == null) {
            return FAMILY_MIXED;
        }
        String targetId = targetId(entity);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId + ":behavior_family", behaviorSalt(entity) ^ 0x42484156);
        return Math.floorMod((int) (seed ^ (seed >>> 32)), 8);
    }

    private static boolean allowsPositionMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_LAYOUT || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_VISIBILITY);
    }

    private static boolean allowsScaleMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_SCALE || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_LAYOUT);
    }

    private static boolean allowsMouseMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_MOUSE_TRACKING || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_ROTATION);
    }

    private static boolean allowsAngleMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_MOUSE_TRACKING || family == FAMILY_ROTATION || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_TIMING);
    }

    private static boolean allowsRotationMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_ROTATION || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_MOUSE_TRACKING);
    }

    private static boolean allowsTimingMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_TIMING || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_RENDER_STATE);
    }

    private static boolean allowsRenderStateMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_RENDER_STATE || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_VISIBILITY);
    }

    private static boolean allowsVisibilityMutation(int family, CorruptionEffectStack stack) {
        return family == FAMILY_VISIBILITY || family == FAMILY_MIXED
                || (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) && family == FAMILY_RENDER_STATE);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_FUNCTIONALITY, targetId),
                        stack.intensity(CorruptionSurface.GUI_FUNCTIONALITY) * 0.78F
                ) + stack.instability() * 0.06F, 0.0F, 1.0F);
    }

    private static long previewSeed(CorruptionEffectStack stack, LivingEntity entity, int x, int y, int scale, String channel, int salt) {
        return stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId(entity) + channel, previewSalt(entity, x, y, scale) ^ salt);
    }

    private static int previewSalt(LivingEntity entity, int x, int y, int scale) {
        int salt = 0x50525657;
        salt = salt * 31 + x;
        salt = salt * 31 + y;
        salt = salt * 31 + scale;
        salt = salt * 31 + screenSalt();
        if (entity != null) {
            salt = salt * 31 + entity.getType().hashCode();
        }
        return salt;
    }

    private static int behaviorSalt(LivingEntity entity) {
        int salt = 0x50524641;
        salt = salt * 31 + screenSalt();
        if (entity != null) {
            salt = salt * 31 + entity.getType().hashCode();
        }
        return salt;
    }

    private static int screenSalt() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.screen == null ? 0 : minecraft.screen.getClass().getName().hashCode();
    }

    private static String targetId(LivingEntity entity) {
        ResourceLocation typeId = entity == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return "gui_entity_preview:" + (typeId == null ? "unknown" : typeId);
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
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
