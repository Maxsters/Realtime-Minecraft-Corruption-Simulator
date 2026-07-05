package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Immutable view of the current corruption state used by mechanics and render hooks.
// All intensity comes from the current level, seed, target mask, and surface tuning.
public final class CorruptionEffectStack {
    private static final float ACTIVE_THRESHOLD = 0.00025F;
    private static final float LOG_RESPONSE_CURVE = 9.0F;

    private final int corruptionLevel;
    private final float effectiveLevel;
    private final int effectiveLevelBucket;
    private final long fixedSeed;
    private final int enabledTargetsMask;
    private final boolean clientDriftEnabled;
    private final int layerCount;
    private final float instability;
    private final float[] intensityCache;
    private final Map<TargetIntensityKey, Float> targetIntensityCache;

    private CorruptionEffectStack(int corruptionLevel, long fixedSeed, int enabledTargetsMask) {
        this(corruptionLevel, fixedSeed, enabledTargetsMask, false);
    }

    private CorruptionEffectStack(int corruptionLevel, long fixedSeed, int enabledTargetsMask, boolean clientDriftEnabled) {
        this.corruptionLevel = clampPercent(corruptionLevel);
        this.effectiveLevel = mappedPercent(this.corruptionLevel);
        this.effectiveLevelBucket = Math.round(this.effectiveLevel * 100.0F);
        this.fixedSeed = fixedSeed;
        this.enabledTargetsMask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        this.clientDriftEnabled = clientDriftEnabled;
        this.layerCount = computeLayerCount(this.effectiveLevel);
        this.instability = computeInstability(this.effectiveLevel);
        this.intensityCache = new float[CorruptionSurface.values().length];
        this.targetIntensityCache = new ConcurrentHashMap<>();
        Arrays.fill(this.intensityCache, Float.NaN);
    }

    public static CorruptionEffectStack from(CorruptionSavedData data) {
        if (data == null) {
            return local(0);
        }
        return new CorruptionEffectStack(
                data.getCorruptionLevel(),
                data.getFixedCorruptionSeed(),
                data.getEnabledTargetsMask(),
                data.isClientDriftEnabled()
        );
    }

    public static CorruptionEffectStack from(CorruptionStateSnapshot snapshot) {
        if (snapshot == null) {
            return local(0);
        }
        return new CorruptionEffectStack(
                snapshot.getCorruptionLevel(),
                snapshot.getEffectiveCorruptionSeed(),
                snapshot.getEnabledTargetsMask(),
                snapshot.isClientDriftEnabled()
        );
    }

    public static CorruptionEffectStack fromGameplay(CorruptionStateSnapshot snapshot) {
        if (snapshot == null) {
            return local(0);
        }
        // Gameplay uses the effective client seed too: with drift off this is the
        // world seed, while drift on intentionally lets local gameplay diverge.
        return from(snapshot);
    }

    public static CorruptionEffectStack local(int corruptionLevel) {
        return local(corruptionLevel, DeterministicCorruption.DEFAULT_SEED, CorruptionTarget.ALL_MASK);
    }

    public static CorruptionEffectStack local(int corruptionLevel, long fixedSeed, int enabledTargetsMask) {
        return new CorruptionEffectStack(corruptionLevel, fixedSeed, enabledTargetsMask);
    }

    public int level() {
        return corruptionLevel;
    }

    public float effectiveLevel() {
        return effectiveLevel;
    }

    public long fixedSeed() {
        return fixedSeed;
    }

    public int enabledTargetsMask() {
        return enabledTargetsMask;
    }

    public boolean clientDriftEnabled() {
        return clientDriftEnabled;
    }

    public boolean targetEnabled(CorruptionTarget target) {
        return CorruptionTarget.enabled(enabledTargetsMask, target);
    }

    public boolean surfaceEnabled(CorruptionSurface surface) {
        return targetEnabled(CorruptionTarget.forSurface(surface));
    }

    public boolean extreme(CorruptionSurface surface) {
        if (corruptionLevel < 92 || !surfaceEnabled(surface)) {
            return false;
        }
        float pressure = intensity(surface) * 0.68F + instability() * 0.22F + layerCount() / 24.0F * 0.10F;
        float threshold = 0.72F - smoothstep(effectiveLevel) * 0.18F;
        return pressure >= threshold;
    }

    public boolean activeOrExtreme(CorruptionSurface surface) {
        return extreme(surface) || active(surface);
    }

    public boolean activeOrExtreme(CorruptionSurface surface, String targetId) {
        return extreme(surface) || active(surface, targetId);
    }

    public float intensityOrExtreme(CorruptionSurface surface) {
        return intensity(surface);
    }

    public int layerCount() {
        return layerCount;
    }

    public float instability() {
        return instability;
    }

    public boolean active(CorruptionSurface surface) {
        return intensity(surface) > ACTIVE_THRESHOLD;
    }

    public boolean active(CorruptionSurface surface, String targetId) {
        return targetIntensity(surface, targetId) > ACTIVE_THRESHOLD;
    }

    public float intensity(CorruptionSurface surface) {
        if (corruptionLevel <= 0 || !surfaceEnabled(surface)) {
            return 0.0F;
        }
        int index = surface.ordinal();
        float cached = intensityCache[index];
        if (!Float.isNaN(cached)) {
            return cached;
        }

        float level = effectiveLevel;
        float levelPressure = (float) Math.pow(level, 1.18F);
        float layerPressure = layerPressure(surface);
        // Surface personality adjusts how quickly each real subsystem falls apart at the same percent.
        float raw = levelPressure * (0.64F + layerPressure * 0.46F);
        raw += instability * surface.instabilityBias() * (0.18F + level * 0.54F);
        raw += smoothstep(level) * surface.entropyBias() * (0.10F + level * 0.42F);
        float intensity = clamp01(raw);
        intensityCache[index] = intensity;
        return intensity;
    }

    public float targetIntensity(CorruptionSurface surface, String targetId) {
        float global = intensity(surface);
        if (global <= ACTIVE_THRESHOLD) {
            return 0.0F;
        }
        if (targetId == null || targetId.isBlank()) {
            return global;
        }
        return targetIntensityCache.computeIfAbsent(new TargetIntensityKey(surface, targetId), key -> computeTargetIntensity(key.surface(), key.targetId(), global));
    }

    private float computeTargetIntensity(CorruptionSurface surface, String targetId, float global) {
        long hash = mix(surfaceSeed(surface, targetId, 0));
        float gate = clamp01(0.08F + global * (0.46F + surface.targetBias()) + instability * 0.24F + Math.min(0.18F, layerCount * 0.006F));
        if (unit(hash) > gate) {
            return 0.0F;
        }
        return clamp01(global * (0.62F + unit(hash >>> 17) * 0.62F) + instability * 0.12F);
    }

    public List<CorruptionMutation> mutations(CorruptionSurface surface, String targetId, int limit) {
        float targetStrength = targetIntensity(surface, targetId);
        if (targetStrength <= ACTIVE_THRESHOLD) {
            return Collections.emptyList();
        }

        int layers = Math.max(1, layerCount());
        int max = Math.max(1, Math.min(Math.max(1, limit), layers));
        List<CorruptionMutation> result = new ArrayList<>(max);
        for (int layer = 0; layer < layers && result.size() < max; layer++) {
            float progress = layerProgress(layer);
            if (progress <= 0.0F) {
                continue;
            }

            long seed = mutationSeed(surface, targetId, layer, result.size());
            float gate = clamp01(surface.affinity() * 0.72F + targetStrength * 0.34F + instability * 0.20F);
            if (unit(seed ^ 0x4D55544154494F4EL) > gate) {
                continue;
            }

            float strength = clamp01(targetStrength * (0.42F + progress * 0.48F + unit(seed >>> 11) * 0.22F) + instability * 0.10F);
            if (strength > ACTIVE_THRESHOLD) {
                result.add(new CorruptionMutation(surface, surface.operationFor(seed), layer, strength, seed));
            }
        }

        if (result.isEmpty()) {
            int layer = Math.max(0, layers - 1);
            long seed = mutationSeed(surface, targetId, layer, 0);
            result.add(new CorruptionMutation(surface, surface.operationFor(seed), layer, targetStrength, seed));
        }
        return result;
    }

    public int mutationCount(CorruptionSurface surface, String targetId, int max) {
        float targetStrength = targetIntensity(surface, targetId);
        if (targetStrength <= ACTIVE_THRESHOLD) {
            return 0;
        }
        int upperBound = Math.max(1, max);
        return Math.max(1, Math.min(upperBound, 1 + Math.round(targetStrength * Math.min(upperBound - 1, layerCount() / 2.0F))));
    }

    public float scaled(CorruptionSurface surface, float min, float max) {
        return min + (max - min) * intensity(surface);
    }

    public double scaled(CorruptionSurface surface, double min, double max) {
        return min + (max - min) * intensity(surface);
    }

    public float unit(CorruptionSurface surface, int salt) {
        return unit(mix(surfaceSeed(surface, null, salt)));
    }

    public float unit(CorruptionSurface surface, String targetId, int salt) {
        return unit(mix(surfaceSeed(surface, targetId, salt)));
    }

    public float signed(CorruptionSurface surface, int salt, float amplitude) {
        return (unit(surface, salt) * 2.0F - 1.0F) * amplitude * intensity(surface);
    }

    public float signed(CorruptionSurface surface, String targetId, int salt, float amplitude) {
        return (unit(surface, targetId, salt) * 2.0F - 1.0F) * amplitude * targetIntensity(surface, targetId);
    }

    public boolean chance(CorruptionSurface surface, int salt, float maxChance) {
        float chance = clamp01(maxChance * intensity(surface) + instability * 0.08F);
        return unit(surface, salt) < chance;
    }

    public int range(CorruptionSurface surface, int salt, int bound) {
        if (bound <= 0) {
            return 0;
        }
        return Math.floorMod((int) mix(surfaceSeed(surface, null, salt)), bound);
    }

    public int range(CorruptionSurface surface, String targetId, int salt, int bound) {
        if (bound <= 0) {
            return 0;
        }
        return Math.floorMod((int) mix(surfaceSeed(surface, targetId, salt)), bound);
    }

    public int stableInt(CorruptionSurface surface, int salt) {
        return (int) mix(surfaceSeed(surface, null, salt));
    }

    public long stableLong(CorruptionSurface surface, int salt) {
        return mix(surfaceSeed(surface, null, salt));
    }

    public long stableLong(CorruptionSurface surface, String targetId, int salt) {
        return mix(surfaceSeed(surface, targetId, salt));
    }

    public int bucket(CorruptionSurface surface, int salt, int intensitySteps) {
        int steps = Math.max(1, intensitySteps);
        int intensityBucket = Math.round(intensity(surface) * steps);
        return layerCount() * 31 + intensityBucket * 7 + range(surface, salt, 7);
    }

    public int bucket(CorruptionSurface surface, String targetId, int salt, int intensitySteps) {
        int steps = Math.max(1, intensitySteps);
        int intensityBucket = Math.round(targetIntensity(surface, targetId) * steps);
        return layerCount() * 31 + intensityBucket * 7 + range(surface, targetId, salt, 7);
    }

    private float layerPressure(CorruptionSurface surface) {
        if (effectiveLevel <= 0.0F) {
            return 0.0F;
        }

        float level = effectiveLevel;
        long hash = mix(fixedSeed ^ surface.salt() * 0x9E37_79B9_7F4A_7C15L);
        float seedGain = 0.58F + unit(hash) * 0.84F;
        float affinityGain = 0.28F + surface.affinity() * 0.72F;
        return clamp01(level * affinityGain * seedGain + smoothstep(level) * 0.22F);
    }

    private float layerProgress(int layer) {
        int layers = Math.max(1, layerCount());
        float layerPosition = (layer + 1.0F) / layers;
        float level = effectiveLevel;
        return clamp01((0.38F + layerPosition * 0.62F) * (0.25F + level * 0.75F));
    }

    private long mutationSeed(CorruptionSurface surface, String targetId, int layer, int salt) {
        long value = surfaceSeed(surface, targetId, salt);
        value ^= (long) layer * 0x632B_E59B_D9B4_E019L;
        value ^= (long) layerCount() * 0x8515_7AF5L;
        return mix(value);
    }

    private long surfaceSeed(CorruptionSurface surface, String targetId, int salt) {
        // Level is mixed into the address space so changing percent can move
        // mutations without depending on old "previous level" bookkeeping.
        long value = fixedSeed;
        value ^= surface.salt() * 0x9E37_79B9_7F4A_7C15L;
        value ^= Integer.toUnsignedLong(salt) * 0xBF58_476D_1CE4_E5B9L;
        if (targetId != null && !targetId.isBlank()) {
            value ^= stableString(targetId) * 0x94D0_49BB_1331_11EBL;
        }
        value ^= (long) effectiveLevelBucket << 32;
        return value;
    }

    private static long stableString(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
            hash ^= hash >>> 32;
        }
        return hash;
    }

    private static float smoothstep(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int computeLayerCount(float effectiveLevel) {
        if (effectiveLevel <= 0.0F) {
            return 0;
        }
        float level = clamp01(effectiveLevel);
        return Math.max(1, Math.min(24, Math.round(1.0F + (float) Math.pow(level, 0.82F) * 23.0F)));
    }

    private static float mappedPercent(int visiblePercent) {
        // The GUI stays linear while the backend preserves fine control at low corruption.
        if (visiblePercent <= 0) {
            return 0.0F;
        }
        if (visiblePercent >= 100) {
            return 1.0F;
        }
        float visible = visiblePercent / 100.0F;
        double denominator = Math.log1p(LOG_RESPONSE_CURVE);
        double mapped = 1.0D - Math.log1p(LOG_RESPONSE_CURVE * (1.0D - visible)) / denominator;
        return clamp01((float) mapped);
    }

    private static float computeInstability(float effectiveLevel) {
        // Instability is now level-derived pressure, not a penalty for changing settings.
        return smoothstep(effectiveLevel);
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

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record TargetIntensityKey(CorruptionSurface surface, String targetId) {
    }
}
