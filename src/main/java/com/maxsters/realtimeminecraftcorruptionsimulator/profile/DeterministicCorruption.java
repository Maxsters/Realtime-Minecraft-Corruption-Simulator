package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

public final class DeterministicCorruption {
    private DeterministicCorruption() {
    }

    public static long mix(long seed, int corruptionLevel, int salt) {
        long value = seed ^ ((long) corruptionLevel << 32) ^ Integer.toUnsignedLong(salt);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    public static int range(long seed, int corruptionLevel, int salt, int bound) {
        if (bound <= 0) {
            return 0;
        }
        return Math.floorMod(mix(seed, corruptionLevel, salt), bound);
    }

    public static float unit(long seed, int corruptionLevel, int salt) {
        return range(seed, corruptionLevel, salt, 10_000) / 10_000.0F;
    }
}
