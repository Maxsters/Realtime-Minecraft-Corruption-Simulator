package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CorruptionValueMutator {
    private CorruptionValueMutator() {
    }

    public static double mutateScalar(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, double value, double span, double min, double max, int salt, long clock) {
        double result = value;
        for (CorruptionMutation mutation : stack.mutations(surface, targetId, 8)) {
            result = applyScalarMutation(mutation, result, span, salt, clock);
        }
        return Mth.clamp(result, min, max);
    }

    public static float mutateScalar(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float value, float span, float min, float max, int salt, long clock) {
        return (float) mutateScalar(stack, surface, targetId, (double) value, (double) span, (double) min, (double) max, salt, clock);
    }

    public static Vec3 mutateVector(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, Vec3 value, double span, double maxComponent, int salt, long clock) {
        String baseTarget = targetId == null ? surface.name() : targetId;
        double x = mutateScalar(stack, surface, baseTarget + ":x", value.x, span, -maxComponent, maxComponent, salt ^ 0x11, clock);
        double y = mutateScalar(stack, surface, baseTarget + ":y", value.y, span, -maxComponent, maxComponent, salt ^ 0x2B, clock);
        double z = mutateScalar(stack, surface, baseTarget + ":z", value.z, span, -maxComponent, maxComponent, salt ^ 0x47, clock);
        return new Vec3(x, y, z);
    }

    public static Vec3 mutateVectorComponents(CorruptionEffectStack stack, CorruptionSurface surface, String xTargetId, String yTargetId, String zTargetId, Vec3 value, double span, double maxComponent, int salt, long clock) {
        double x = mutateScalar(stack, surface, xTargetId, value.x, span, -maxComponent, maxComponent, salt ^ 0x11, clock);
        double y = mutateScalar(stack, surface, yTargetId, value.y, span, -maxComponent, maxComponent, salt ^ 0x2B, clock);
        double z = mutateScalar(stack, surface, zTargetId, value.z, span, -maxComponent, maxComponent, salt ^ 0x47, clock);
        return new Vec3(x, y, z);
    }

    public static int mutateColor(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int color, int salt, long clock) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        red = Math.round(mutateScalar(stack, surface, targetId + ":r", red, 96.0F, 0.0F, 255.0F, salt ^ 0x12, clock));
        green = Math.round(mutateScalar(stack, surface, targetId + ":g", green, 96.0F, 0.0F, 255.0F, salt ^ 0x24, clock));
        blue = Math.round(mutateScalar(stack, surface, targetId + ":b", blue, 96.0F, 0.0F, 255.0F, salt ^ 0x48, clock));
        return red << 16 | green << 8 | blue;
    }

    public static boolean decision(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int salt, float maxChance) {
        float intensity = stack.targetIntensity(surface, targetId);
        if (intensity <= 0.0F) {
            return false;
        }
        float chance = Math.min(1.0F, maxChance * intensity + stack.instability() * 0.18F);
        return stack.unit(surface, targetId, salt) < chance;
    }

    public static int selectIndex(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int salt, int bound) {
        if (bound <= 0) {
            return 0;
        }
        List<CorruptionMutation> mutations = stack.mutations(surface, targetId, 8);
        long signature = stack.stableLong(surface, targetId, salt);
        for (CorruptionMutation mutation : mutations) {
            signature ^= mutation.stableLong(salt ^ mutation.layer());
        }
        return Math.floorMod((int) (signature ^ (signature >>> 32)), bound);
    }

    private static double applyScalarMutation(CorruptionMutation mutation, double value, double span, int salt, long clock) {
        double amount = span * mutation.strength();
        double signed = mutation.signed(salt, (float) amount);
        double phase = mutation.signed((int) clock ^ salt, (float) amount);
        return switch (mutation.operation()) {
            case AMPLIFY -> value + Math.copySign(amount * (0.35D + mutation.unit(salt) * 0.90D), value == 0.0D ? signed : value);
            case DAMPEN -> value * (1.0D - mutation.strength() * (0.18D + mutation.unit(salt) * 0.62D));
            case OFFSET, DRIFT -> value + signed + phase * 0.35D;
            case FOLD -> fold(value + signed, Math.max(0.01D, span * (0.18D + mutation.unit(salt) * 0.82D)));
            case INVERT -> -value * mutation.strength() + signed * 0.40D;
            case REMAP, REPLACE -> signed;
            case SMEAR, ECHO -> value + signed * 0.55D + phase * 0.55D;
            case STUTTER -> quantize(value + signed * 0.35D, Math.max(0.01D, span * (0.08D + mutation.unit(salt) * 0.30D)));
            case DESYNC, NOISE -> value + signed;
        };
    }

    private static double fold(double value, double width) {
        double folded = Math.abs(value % (width * 2.0D));
        return folded > width ? width * 2.0D - folded : folded;
    }

    private static double quantize(double value, double step) {
        return Math.round(value / step) * step;
    }
}
