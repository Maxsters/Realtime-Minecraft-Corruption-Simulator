package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.ForgeRegistries;

public final class WorldgenCorruptionHooks {
    private WorldgenCorruptionHooks() {
    }

    public static boolean shouldSkipBiomeDecoration(WorldGenLevel level, ChunkAccess chunk) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || chunk == null) {
            return false;
        }

        ChunkPos pos = chunk.getPos();
        long hash = mix(level.getSeed()
                ^ stack.fixedSeed()
                ^ pos.x * 0x9E3779B97F4A7C15L
                ^ pos.z * 0xBF58476D1CE4E5B9L
                ^ 0x42494F4D45504153L);
        float chance = clamp01((intensity - 0.18F) * 0.78F + stack.instability() * 0.10F);
        return unit(hash) < chance;
    }

    public static boolean shouldSkipStructures(ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess chunk) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || chunk == null) {
            return false;
        }

        ChunkPos pos = chunk.getPos();
        long hash = mix(stack.fixedSeed()
                ^ pos.x * 0xD6E8FEB86659FD93L
                ^ pos.z * 0x94D049BB133111EBL
                ^ System.identityHashCode(state) * 0x632BE59BD9B4E019L
                ^ System.identityHashCode(structureManager)
                ^ 0x5354525543545552L);
        float chance = clamp01((intensity - 0.12F) * 0.86F + stack.instability() * 0.12F);
        return unit(hash) < chance;
    }

    public static boolean shouldSkipFeature(Feature<?> feature, WorldGenLevel level, BlockPos origin) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || level == null || origin == null) {
            return false;
        }

        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        long hash = mix(level.getSeed()
                ^ stack.fixedSeed()
                ^ origin.asLong()
                ^ stableString(target) * 0x94D049BB133111EBL
                ^ 0x4645415455524550L);
        float chance = featureSkipChance(target, intensity, stack.instability());
        return unit(hash) < chance;
    }

    public static boolean fakeFeatureSuccess(Feature<?> feature, BlockPos origin) {
        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        long hash = mix(stableString(target) ^ origin.asLong() ^ 0x46414B4553554343L);
        return unit(hash) < 0.38F;
    }

    public static DensityFunction corruptDensity(String channel, DensityFunction function) {
        if (function == null || function instanceof CorruptedDensityFunction) {
            return function;
        }
        return new CorruptedDensityFunction(channel, function);
    }

    public static double corruptDensitySample(String channel, double value, int x, int y, int z, double minValue, double maxValue) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || !Double.isFinite(value)) {
            return value;
        }

        float channelWeight = densityChannelWeight(channel);
        float strength = clamp01(intensity * channelWeight);
        if (strength <= 0.0F) {
            return value;
        }

        long channelSeed = mix(stack.fixedSeed() ^ stableString(channel));
        int family = Math.floorMod((int) (channelSeed >>> 17), 6);
        double warpScale = 10.0D + strength * 74.0D + unit(channelSeed ^ 0x57415250L) * 46.0D;
        double warpX = smoothNoise2D(x, z, channelSeed ^ 0x5857415250L, 0.006D + unit(channelSeed) * 0.011D) * warpScale;
        double warpZ = smoothNoise2D(x, z, channelSeed ^ 0x5A57415250L, 0.005D + unit(channelSeed >>> 9) * 0.013D) * warpScale;
        double wx = x + warpX;
        double wz = z + warpZ;

        double broad = fractalNoise2D(wx, wz, channelSeed ^ 0x42524F4144L, 0.0018D + unit(channelSeed >>> 21) * 0.0065D, 3);
        double folded = fractalNoise2D(wx + broad * warpScale, wz - broad * warpScale, channelSeed ^ 0x464F4C4445L, 0.006D + unit(channelSeed >>> 33) * 0.027D, 3);
        double fine = smoothNoise3D(wx, y, wz, channelSeed ^ 0x334446494EL, 0.015D + strength * 0.045D + unit(channelSeed >>> 45) * 0.020D);
        double ripple = coordinateRipple(channelSeed, (int) Math.round(wx), y, (int) Math.round(wz), strength);
        double blend = broad * 0.58D + folded * 0.32D + fine * 0.10D;
        double amplitude = densityAmplitude(channel, strength, stack.instability());
        double result;

        if (family == 0) {
            result = value + (blend * 1.35D + ripple * 0.18D) * amplitude;
        } else if (family == 1) {
            result = value * (1.0D + folded * amplitude * 0.62D) + broad * amplitude * 0.78D;
        } else if (family == 2) {
            result = lerp(value, -value + folded * amplitude, 0.18D + strength * 0.62D);
        } else if (family == 3) {
            double bend = Math.sin((value + broad * 1.7D) * (1.2D + strength * 5.8D));
            result = value + (bend * 0.72D + folded * 0.48D) * amplitude;
        } else if (family == 4) {
            double smear = Math.copySign(Math.pow(Math.abs(value + broad * 0.55D) + 0.001D, 0.52D + (1.0D - strength) * 0.36D), value + folded * 0.12D);
            result = lerp(value, smear + blend * amplitude * 0.58D, 0.12D + strength * 0.70D);
        } else {
            double shelves = Math.sin((y + broad * 80.0D) * (0.015D + strength * 0.040D));
            result = value + (broad * 0.65D + folded * 0.55D + shelves * 0.28D) * amplitude;
        }

        if ("final_density".equals(channel) || "initial_density".equals(channel)) {
            result += fine * amplitude * (0.34D + strength * 0.38D);
        }
        if ("continents".equals(channel) || "erosion".equals(channel) || "ridges".equals(channel)) {
            result += broad * folded * amplitude * (0.38D + strength * 0.48D);
        }

        double limit = Math.max(4.0D, Math.max(Math.abs(minValue), Math.abs(maxValue)) + 2.0D + strength * 9.0D);
        return clamp(result, -limit, limit);
    }

    public static double densityMinValue(String channel, double value) {
        float intensity = worldgenIntensity(stack());
        return value - densityAmplitude(channel, intensity, 0.0F) * 1.6D;
    }

    public static double densityMaxValue(String channel, double value) {
        float intensity = worldgenIntensity(stack());
        return value + densityAmplitude(channel, intensity, 0.0F) * 1.6D;
    }

    public static void noteFillFromNoise(ChunkAccess chunk) {
        if (chunk == null) {
            return;
        }
        CorruptionEffectStack stack = stack();
        if (worldgenIntensity(stack) <= 0.0F) {
            return;
        }
        chunk.setUnsaved(true);
    }

    private static float featureSkipChance(String target, float intensity, float instability) {
        String normalized = target.toLowerCase();
        float base;
        if (normalized.contains("tree")
                || normalized.contains("flower")
                || normalized.contains("vegetation")
                || normalized.contains("random_patch")
                || normalized.contains("bamboo")
                || normalized.contains("vines")
                || normalized.contains("mushroom")
                || normalized.contains("root")
                || normalized.contains("grass")) {
            base = 0.18F + intensity * 1.05F;
        } else if (normalized.contains("lake")
                || normalized.contains("spring")
                || normalized.contains("disk")
                || normalized.contains("ice")
                || normalized.contains("magma")
                || normalized.contains("coral")
                || normalized.contains("kelp")
                || normalized.contains("seagrass")) {
            base = 0.12F + intensity * 0.88F;
        } else if (normalized.contains("ore")
                || normalized.contains("geode")
                || normalized.contains("fossil")
                || normalized.contains("dripstone")
                || normalized.contains("monster_room")) {
            base = 0.08F + intensity * 0.72F;
        } else {
            base = 0.06F + intensity * 0.58F;
        }
        return clamp01(base + instability * 0.12F);
    }

    private static CorruptionEffectStack stack() {
        return CorruptionEffectStack.local(
                GlobalCorruptionSettings.activeLevel(),
                GlobalCorruptionSettings.seed(),
                GlobalCorruptionSettings.enabledTargetsMask()
        );
    }

    private static float worldgenIntensity(CorruptionEffectStack stack) {
        if (!stack.activeOrExtreme(CorruptionSurface.WORLDGEN_SURFACE)) {
            return 0.0F;
        }
        return clamp01(Math.max(stack.intensityOrExtreme(CorruptionSurface.WORLDGEN_SURFACE), stack.level() / 100.0F * 0.92F));
    }

    private static float densityChannelWeight(String channel) {
        return switch (channel) {
            case "continents" -> 1.28F;
            case "erosion" -> 1.18F;
            case "ridges" -> 1.22F;
            case "depth" -> 0.98F;
            case "initial_density" -> 0.86F;
            case "final_density" -> 0.72F;
            case "temperature", "vegetation" -> 0.62F;
            default -> 0.54F;
        };
    }

    private static double densityAmplitude(String channel, float strength, float instability) {
        double base = switch (channel) {
            case "continents" -> 1.85D;
            case "erosion" -> 1.55D;
            case "ridges" -> 1.70D;
            case "depth" -> 1.25D;
            case "initial_density" -> 0.95D;
            case "final_density" -> 0.62D;
            case "temperature", "vegetation" -> 0.42D;
            default -> 0.34D;
        };
        return base * (0.20D + strength * 1.45D + instability * 0.28D);
    }

    private static double coordinateRipple(long seed, int x, int y, int z, float strength) {
        double fx = 0.010D + strength * 0.075D + unit(seed ^ 0x46524551L) * 0.040D;
        double fz = 0.012D + strength * 0.070D + unit(seed ^ 0x464F4C44L) * 0.044D;
        double fy = 0.006D + strength * 0.026D;
        return Math.sin((x + signedUnit(seed) * 53.0D) * fx)
                + Math.cos((z + signedUnit(seed >>> 7) * 47.0D) * fz)
                + Math.sin((y + signedUnit(seed >>> 13) * 29.0D) * fy);
    }

    private static double fractalNoise2D(double x, double z, long seed, double scale, int octaves) {
        double value = 0.0D;
        double amplitude = 1.0D;
        double total = 0.0D;
        double frequency = scale;
        for (int octave = 0; octave < octaves; octave++) {
            value += smoothNoise2D(x, z, seed ^ octave * 0x9E3779B97F4A7C15L, frequency) * amplitude;
            total += amplitude;
            amplitude *= 0.52D;
            frequency *= 2.17D + unit(seed ^ octave * 0x46524551L) * 0.42D;
        }
        return total <= 0.0D ? 0.0D : value / total;
    }

    private static double smoothNoise2D(double x, double z, long seed, double scale) {
        double sx = x * scale + signedUnit(seed) * 512.0D;
        double sz = z * scale + signedUnit(seed >>> 11) * 512.0D;
        int x0 = fastFloor(sx);
        int z0 = fastFloor(sz);
        double tx = fade(sx - x0);
        double tz = fade(sz - z0);
        double a = lattice2D(x0, z0, seed);
        double b = lattice2D(x0 + 1, z0, seed);
        double c = lattice2D(x0, z0 + 1, seed);
        double d = lattice2D(x0 + 1, z0 + 1, seed);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static double smoothNoise3D(double x, double y, double z, long seed, double scale) {
        double sx = x * scale + signedUnit(seed) * 256.0D;
        double sy = y * scale + signedUnit(seed >>> 19) * 256.0D;
        double sz = z * scale + signedUnit(seed >>> 37) * 256.0D;
        int x0 = fastFloor(sx);
        int y0 = fastFloor(sy);
        int z0 = fastFloor(sz);
        double tx = fade(sx - x0);
        double ty = fade(sy - y0);
        double tz = fade(sz - z0);
        double x00 = lerp(lattice3D(x0, y0, z0, seed), lattice3D(x0 + 1, y0, z0, seed), tx);
        double x10 = lerp(lattice3D(x0, y0 + 1, z0, seed), lattice3D(x0 + 1, y0 + 1, z0, seed), tx);
        double x01 = lerp(lattice3D(x0, y0, z0 + 1, seed), lattice3D(x0 + 1, y0, z0 + 1, seed), tx);
        double x11 = lerp(lattice3D(x0, y0 + 1, z0 + 1, seed), lattice3D(x0 + 1, y0 + 1, z0 + 1, seed), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private static double lattice2D(int x, int z, long seed) {
        return signedUnit(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xBF58476D1CE4E5B9L);
    }

    private static double lattice3D(int x, int y, int z, long seed) {
        return signedUnit(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0x94D049BB133111EBL ^ z * 0xBF58476D1CE4E5B9L);
    }

    private static int fastFloor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long stableString(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix(hash);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static double signedUnit(long value) {
        return unit(value) * 2.0D - 1.0D;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
