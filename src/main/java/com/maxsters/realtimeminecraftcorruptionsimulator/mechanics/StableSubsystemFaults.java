package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.util.Mth;

final class StableSubsystemFaults {
    private StableSubsystemFaults() {
    }

    static boolean broken(
            CorruptionEffectStack stack,
            CorruptionSurface surface,
            String callback,
            String subject,
            int salt,
            float baseChance,
            float intensityScale,
            float extremeChance,
            float minimumIntensity
    ) {
        if (stack == null || !stack.activeOrExtreme(surface)) {
            return false;
        }

        float intensity = stack.extreme(surface) ? 1.0F : stack.intensity(surface);
        if (intensity < minimumIntensity) {
            return false;
        }

        // The fixed seed chooses which subsystem is broken. Raising corruption only
        // raises pressure; it must not reroll a callback on every level or invocation.
        float pressure = (float) Math.sqrt(intensity) * 0.76F + intensity * 0.24F;
        float chance = stack.extreme(surface)
                ? extremeChance
                : Mth.clamp(baseChance + pressure * intensityScale + stack.instability() * 0.06F, 0.0F, extremeChance - 0.02F);
        return unit(seed(stack, surface, callback, subject, salt)) < chance;
    }

    static long seed(CorruptionEffectStack stack, CorruptionSurface surface, String callback, String subject, int salt) {
        long value = stack.fixedSeed();
        value ^= (long) surface.salt() * 0x9E3779B97F4A7C15L;
        value ^= stableString(callback) * 0xBF58476D1CE4E5B9L;
        value ^= stableString(subject) * 0x94D049BB133111EBL;
        value ^= Integer.toUnsignedLong(salt) * 0xD6E8FEB86659FD93L;
        return mix(value);
    }

    static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    static float signedUnit(long value) {
        return unit(value) * 2.0F - 1.0F;
    }

    private static long stableString(String value) {
        long hash = 0xcbf29ce484222325L;
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
            hash ^= hash >>> 32;
        }
        return hash;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
