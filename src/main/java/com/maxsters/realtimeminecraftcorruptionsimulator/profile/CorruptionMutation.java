package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

public record CorruptionMutation(CorruptionSurface surface, CorruptionOperation operation, int layer, float strength, long seed) {
    public CorruptionMutation {
        strength = clamp01(strength);
    }

    public float unit(int salt) {
        return unit(mix(seed ^ Integer.toUnsignedLong(salt) * 0x9E37_79B9_7F4A_7C15L));
    }

    public float signed(int salt, float amplitude) {
        return (unit(salt) * 2.0F - 1.0F) * amplitude * strength;
    }

    public int range(int salt, int bound) {
        if (bound <= 0) {
            return 0;
        }
        return Math.floorMod((int) mix(seed ^ Integer.toUnsignedLong(salt) * 0xBF58_476D_1CE4_E5B9L), bound);
    }

    public long stableLong(int salt) {
        return mix(seed ^ Integer.toUnsignedLong(salt) * 0xD6E8_FEB8_6659_FD93L);
    }

    private static float unit(long value) {
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
