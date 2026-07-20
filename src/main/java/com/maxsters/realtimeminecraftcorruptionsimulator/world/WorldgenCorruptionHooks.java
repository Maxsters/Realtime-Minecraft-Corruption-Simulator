package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldgenCorruptionHooks {
    private static final ThreadLocal<Boolean> FEATURE_ORIGIN_REROUTE_ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final float FEATURE_SKIP_MIN_INTENSITY = 0.14F;
    private static final float FEATURE_REROUTE_MIN_INTENSITY = 0.18F;
    private static final float CLIMATE_SAMPLE_MIN_INTENSITY = 0.12F;
    private static final float DENSITY_SAMPLE_MIN_STRENGTH = 0.12F;
    private static final int DENSITY_ROLE_GENERIC = 0;
    private static final int DENSITY_ROLE_CONTINENTS = 1;
    private static final int DENSITY_ROLE_EROSION = 2;
    private static final int DENSITY_ROLE_RIDGES = 3;
    private static final int DENSITY_ROLE_DEPTH = 4;
    private static final int DENSITY_ROLE_INITIAL = 5;
    private static final int DENSITY_ROLE_FINAL = 6;
    private static final int DENSITY_ROLE_TEMPERATURE = 7;
    private static final int DENSITY_ROLE_VEGETATION = 8;
    private static final DensityChannelPlan CONTINENTS_DENSITY = new DensityChannelPlan(stableString("continents"), 1.36F, 1.74D, false, true, DENSITY_ROLE_CONTINENTS);
    private static final DensityChannelPlan EROSION_DENSITY = new DensityChannelPlan(stableString("erosion"), 1.24F, 1.44D, false, true, DENSITY_ROLE_EROSION);
    private static final DensityChannelPlan RIDGES_DENSITY = new DensityChannelPlan(stableString("ridges"), 1.28F, 1.54D, false, true, DENSITY_ROLE_RIDGES);
    private static final DensityChannelPlan DEPTH_DENSITY = new DensityChannelPlan(stableString("depth"), 1.06F, 1.28D, true, false, DENSITY_ROLE_DEPTH);
    private static final DensityChannelPlan INITIAL_DENSITY = new DensityChannelPlan(stableString("initial_density"), 0.94F, 1.04D, true, false, DENSITY_ROLE_INITIAL);
    private static final DensityChannelPlan FINAL_DENSITY = new DensityChannelPlan(stableString("final_density"), 0.82F, 0.92D, true, false, DENSITY_ROLE_FINAL);
    private static final DensityChannelPlan TEMPERATURE_DENSITY = new DensityChannelPlan(stableString("temperature"), 0.72F, 0.52D, false, false, DENSITY_ROLE_TEMPERATURE);
    private static final DensityChannelPlan VEGETATION_DENSITY = new DensityChannelPlan(stableString("vegetation"), 0.72F, 0.52D, false, false, DENSITY_ROLE_VEGETATION);
    private static final ConcurrentMap<Feature<?>, FeatureInfo> FEATURE_INFO_CACHE = new ConcurrentHashMap<>();
    private static final Object DENSITY_RUNTIME_CACHE_LOCK = new Object();
    private static final ConcurrentMap<String, DensityRuntime> DENSITY_RUNTIME_CACHE = new ConcurrentHashMap<>();
    private static volatile WorldgenState cachedWorldgenState;
    private static volatile long cachedDensityRuntimeVersion = Long.MIN_VALUE;

    private WorldgenCorruptionHooks() {
    }

    private record WorldgenState(long version, float intensity, long fixedSeed, float instability, WorldgenProfile profile) {
        private static WorldgenState from(GlobalCorruptionSettings.CorruptionRuntimeSnapshot snapshot) {
            CorruptionEffectStack stack = CorruptionEffectStack.local(snapshot.activeLevel(), snapshot.seed(), snapshot.enabledTargetsMask());
            long fixedSeed = stack.fixedSeed();
            return new WorldgenState(
                    snapshot.version(),
                    worldgenIntensity(stack),
                    fixedSeed,
                    stack.instability(),
                    WorldgenProfile.fromSeed(fixedSeed)
            );
        }
    }

    private record DensityChannelPlan(long hash, float weight, double amplitudeBase, boolean terrainShape, boolean continentalBand, int role) {
    }

    private record FeatureInfo(String normalizedTarget, long targetHash) {
    }

    public record DensityRuntime(
            long version,
            float intensity,
            float instability,
            float strength,
            boolean sampleActive,
            boolean terrainShape,
            boolean continentalBand,
            int role,
            long channelSeed,
            double amplitude,
            double boundsAmplitude,
            float extremePressure,
            int gateCell,
            int gateCellY,
            float activeChance,
            float baselineStrength,
            int baselineMode,
            double baselineBias,
            double baselineScale,
            double baselineStep,
            int family,
            int densityStyle,
            int gateStyle,
            long macroSeed,
            int macroCell,
            int macroCellY
    ) {
    }

    public static boolean shouldSkipBiomeDecoration(WorldGenLevel level, ChunkAccess chunk) {
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity <= 0.0F || level == null || chunk == null) {
            return false;
        }

        float extreme = extremePressure(intensity);
        float chance = clamp01((intensity - 0.24F) * 0.32F + state.instability() * 0.06F + extreme * 0.18F * state.profile().decorationScale());
        if (chance <= 0.0F) {
            return false;
        }

        ChunkPos pos = chunk.getPos();
        long hash = mix(level.getSeed()
                ^ state.fixedSeed()
                ^ pos.x * 0x9E3779B97F4A7C15L
                ^ pos.z * 0xBF58476D1CE4E5B9L
                ^ 0x42494F4D45504153L);
        return unit(hash) < chance;
    }

    public static boolean shouldSkipStructures(ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess chunk) {
        WorldgenState worldgenState = worldgenState();
        float intensity = worldgenState.intensity();
        if (intensity <= 0.0F || chunk == null) {
            return false;
        }

        float extreme = extremePressure(intensity);
        float chance = clamp01((intensity - 0.30F) * 0.26F + worldgenState.instability() * 0.05F + extreme * 0.12F * worldgenState.profile().structureScale());
        if (chance <= 0.0F) {
            return false;
        }

        ChunkPos pos = chunk.getPos();
        long hash = mix(worldgenState.fixedSeed()
                ^ pos.x * 0xD6E8FEB86659FD93L
                ^ pos.z * 0x94D049BB133111EBL
                ^ stableTypeHash(state) * 0x632BE59BD9B4E019L
                ^ stableTypeHash(structureManager)
                ^ 0x5354525543545552L);
        return unit(hash) < chance;
    }

    public static boolean shouldSkipFeature(Feature<?> feature, WorldGenLevel level, BlockPos origin) {
        if (FEATURE_ORIGIN_REROUTE_ACTIVE.get()) {
            return false;
        }
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity < FEATURE_SKIP_MIN_INTENSITY || feature == null || level == null || origin == null) {
            return false;
        }

        FeatureInfo featureInfo = featureInfo(feature);
        long hash = mix(level.getSeed()
                ^ state.fixedSeed()
                ^ origin.asLong()
                ^ featureInfo.targetHash() * 0x94D049BB133111EBL
                ^ 0x4645415455524550L);
        float chance = featureSkipChance(featureInfo, intensity, state.instability(), state.profile());
        return unit(hash) < chance;
    }

    public static boolean isFeatureOriginRerouteActive() {
        return FEATURE_ORIGIN_REROUTE_ACTIVE.get();
    }

    public static void beginFeatureOriginReroute() {
        FEATURE_ORIGIN_REROUTE_ACTIVE.set(true);
    }

    public static void endFeatureOriginReroute() {
        FEATURE_ORIGIN_REROUTE_ACTIVE.set(false);
    }

    public static BlockPos corruptFeatureOrigin(Feature<?> feature, WorldGenLevel level, BlockPos origin) {
        if (FEATURE_ORIGIN_REROUTE_ACTIVE.get()) {
            return origin;
        }

        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity < FEATURE_REROUTE_MIN_INTENSITY || feature == null || level == null || origin == null) {
            return origin;
        }

        FeatureInfo featureInfo = featureInfo(feature);
        long seed = mix(level.getSeed()
                ^ state.fixedSeed()
                ^ origin.asLong()
                ^ featureInfo.targetHash() * 0xD6E8FEB86659FD93L
                ^ 0x4645415455524F52L);
        float chance = featureRerouteChance(featureInfo, intensity, state.instability(), state.profile());
        if (unit(seed ^ 0x5245524FL) >= chance) {
            return origin;
        }

        float extreme = extremePressure(intensity);
        WorldgenProfile profile = state.profile();
        float featureScale = profile.featureScale() * featureRerouteActivation(featureInfo, profile);
        float verticalScale = profile.verticalScale() * featureVerticalActivation(featureInfo, profile);
        int horizontalSpan = Math.max(1, Math.round(1.0F + intensity * (2.0F + unit(seed >>> 7) * 10.0F) + extreme * featureScale * (5.0F + unit(seed >>> 9) * 22.0F)));
        int verticalSpan = Math.max(1, Math.round(1.0F
                + intensity * verticalScale * (2.0F + unit(seed >>> 17) * 11.0F)
                + extreme * verticalScale * (6.0F + unit(seed >>> 21) * 44.0F)));
        int mode = Math.floorMod((int) (seed >>> 41), 7);
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        if (mode == 0) {
            x += signedRange(seed >>> 5, horizontalSpan);
            z += signedRange(seed >>> 19, horizontalSpan);
            y += signedRange(seed >>> 31, verticalSpan);
        } else if (mode == 1) {
            int cell = 4 << Math.floorMod((int) (seed >>> 9), 4);
            x = floorToCell(x + signedRange(seed >>> 13, cell), cell) + Math.floorMod((int) (seed >>> 23), cell);
            z = floorToCell(z + signedRange(seed >>> 29, cell), cell) + Math.floorMod((int) (seed >>> 39), cell);
            y += signedRange(seed >>> 47, verticalSpan);
        } else if (mode == 2) {
            int mask = 1 << (1 + Math.floorMod((int) (seed >>> 11), 5));
            x = (x & ~mask) | ((int) (seed >>> 27) & mask);
            z = (z & ~mask) | ((int) (seed >>> 35) & mask);
            y += signedRange(seed >>> 43, verticalSpan);
        } else if (mode == 3) {
            int lane = Math.max(2, Math.round(3.0F + intensity * 19.0F));
            if (unit(seed >>> 3) < 0.5F) {
                x = floorToCell(x, lane) + Math.floorMod((int) (seed >>> 14), lane);
                z += signedRange(seed >>> 25, horizontalSpan);
            } else {
                z = floorToCell(z, lane) + Math.floorMod((int) (seed >>> 14), lane);
                x += signedRange(seed >>> 25, horizontalSpan);
            }
            y += signedRange(seed >>> 37, verticalSpan);
        } else if (mode == 4) {
            x += signedRange(seed >>> 3, horizontalSpan) * 2;
            z += signedRange(seed >>> 15, horizontalSpan) * 2;
            y = floorToCell(y + signedRange(seed >>> 27, verticalSpan), Math.max(2, Math.round(2.0F + intensity * 14.0F)));
        } else if (mode == 5) {
            int dx = signedRange(seed >>> 5, horizontalSpan);
            int dz = signedRange(seed >>> 17, horizontalSpan);
            x += dz;
            z += dx;
            y += signedRange(seed >>> 29, verticalSpan);
        } else {
            x += signedRange(seed >>> 7, horizontalSpan);
            z += signedRange(seed >>> 23, horizontalSpan);
            y = origin.getY() + signedRange(seed >>> 39, verticalSpan);
            if (unit(seed >>> 49) < 0.42F + intensity * 0.40F) {
                y = floorToCell(y, Math.max(2, Math.round(3.0F + intensity * 21.0F)));
            }
        }

        int chunkMinX = origin.getX() & ~15;
        int chunkMinZ = origin.getZ() & ~15;
        x = clampInt(x, chunkMinX, chunkMinX + 15);
        z = clampInt(z, chunkMinZ, chunkMinZ + 15);
        y = clampInt(y, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2);
        BlockPos rerouted = new BlockPos(x, y, z);
        return rerouted.equals(origin) ? origin : rerouted;
    }

    public static DensityFunction corruptDensity(String channel, DensityFunction function) {
        if (function == null || function instanceof CorruptedDensityFunction) {
            return function;
        }
        return new CorruptedDensityFunction(channel, function);
    }

    public static NoiseRouter corruptRuntimeNoiseRouter(NoiseRouter router) {
        if (router == null) {
            return null;
        }
        return new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                corruptDensity("temperature", router.temperature()),
                corruptDensity("vegetation", router.vegetation()),
                corruptDensity("continents", router.continents()),
                corruptDensity("erosion", router.erosion()),
                corruptDensity("depth", router.depth()),
                corruptDensity("ridges", router.ridges()),
                corruptDensity("initial_density", router.initialDensityWithoutJaggedness()),
                corruptDensity("final_density", router.finalDensity()),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap()
        );
    }

    public static boolean shouldCorruptDensity(String channel) {
        return densityRuntime(channel).sampleActive();
    }

    public static double corruptDensitySample(String channel, double value, int x, int y, int z, double minValue, double maxValue) {
        return corruptDensitySample(densityRuntime(channel), value, x, y, z, minValue, maxValue);
    }

    public static double corruptDensitySample(DensityRuntime runtime, double value, int x, int y, int z, double minValue, double maxValue) {
        if (runtime == null || !runtime.sampleActive() || !Double.isFinite(value)) {
            return value;
        }

        float strength = runtime.strength();
        long channelSeed = runtime.channelSeed();
        long gateSeed = densityGateSeed(runtime, x, y, z);
        boolean terrainShape = runtime.terrainShape();
        int role = runtime.role();
        double amplitude = runtime.amplitude();
        double result = corruptDensityBaseline(runtime, role, value, x, y, z, amplitude);
        if (unit(gateSeed ^ 0x414354495645L) < runtime.activeChance()) {
            result = corruptDensityValue(runtime, role, result, gateSeed, amplitude, strength);
            result = applyExtremeDensityFaults(runtime, role, result, value, x, y, z, strength, amplitude, channelSeed);
        }
        if (terrainShape && y > 124 && result > value) {
            result = lerp(result, value, clamp01((y - 124) / 96.0F));
        }
        double limit = Math.max(3.0D, Math.max(Math.abs(minValue), Math.abs(maxValue)) + 1.0D + strength * 3.5D + runtime.extremePressure() * 4.5D);
        return clamp(result, -limit, limit);
    }

    private static long densityGateSeed(DensityRuntime runtime, int x, int y, int z) {
        int cell = runtime.gateCell();
        int cellY = runtime.gateCellY();
        long seed = runtime.channelSeed() ^ 0x44454E5347415445L;
        return switch (runtime.gateStyle()) {
            case 0 -> mix(seed
                    ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                    ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L);
            case 1 -> mix(seed
                    ^ floorToCell(y, cellY) * 0x94D049BB133111EBL);
            case 2 -> mix(seed
                    ^ floorToCell(x + z, cell) * 0x632BE59BD9B4E019L
                    ^ floorToCell(y, Math.max(cellY, cell)) * 0x94D049BB133111EBL);
            case 3 -> mix(seed
                    ^ floorToCell(x - z, cell) * 0x85157AF5L);
            case 4 -> mix(seed);
            default -> mix(seed
                    ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                    ^ floorToCell(y, Math.max(cellY, cell * 2)) * 0x94D049BB133111EBL
                    ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L);
        };
    }

    private static double corruptDensityBaseline(DensityRuntime runtime, int role, double value, int x, int y, int z, double amplitude) {
        float amount = runtime.baselineStrength();
        if (amount <= 0.0F) {
            return value;
        }

        long hash = mix(runtime.channelSeed()
                ^ floorToCell(x + z, Math.max(16, runtime.gateCell() * 2)) * 0x9E3779B97F4A7C15L
                ^ 0x4241534544454E53L);
        double spatialSign = signedUnit(hash) >= 0.0D ? 1.0D : -1.0D;
        double bias = runtime.baselineBias();
        double scale = runtime.baselineScale();
        double step = runtime.baselineStep();
        double out;
        switch (runtime.baselineMode()) {
            case 0 -> out = value + bias;
            case 1 -> out = -value + bias * 0.45D;
            case 2 -> out = value * scale + bias * 0.30D;
            case 3 -> out = quantize(value + bias * 0.18D, step);
            case 4 -> {
                double period = Math.max(0.18D, step * (2.0D + runtime.baselineScale()));
                out = fold(value + bias * 0.28D, period) - period * 0.50D;
            }
            case 5 -> {
                double stuck = spatialSign * amplitude * (0.42D + runtime.baselineScale() * 0.36D);
                out = lerp(value, stuck + bias * 0.20D, 0.42D + amount * 0.40D);
            }
            case 6 -> {
                double threshold = bias * 0.14D;
                out = value >= threshold ? value + Math.abs(bias) : value - Math.abs(bias);
            }
            default -> {
                double vertical = ((double) y - 64.0D) / 128.0D;
                double roleSign = role == DENSITY_ROLE_DEPTH ? -1.0D : 1.0D;
                out = value + vertical * amplitude * roleSign * runtime.baselineScale() + bias * 0.25D;
            }
        }
        return Double.isFinite(out) ? lerp(value, out, amount) : value;
    }

    private static double corruptDensityValue(DensityRuntime runtime, int role, double value, long cellHash, double amplitude, float strength) {
        int mode = densityValueMode(runtime, role, cellHash);
        double sign = signedUnit(cellHash >>> 5);
        double offset = sign * amplitude * densityRolePressure(role, runtime.continentalBand(), runtime.terrainShape());
        double mix = 0.18D + strength * (runtime.terrainShape() ? 0.34D : 0.48D);
        double out;
        if (mode == 0) {
            out = value + offset;
        } else if (mode == 1) {
            double scale = 0.22D + unit(cellHash >>> 11) * (1.95D + strength * 1.65D);
            out = value * scale + offset * 0.30D;
        } else if (mode == 2) {
            out = -value + offset * 0.45D;
        } else if (mode == 3) {
            double step = 0.03125D + unit(cellHash >>> 17) * (0.10D + strength * 0.62D);
            out = quantize(value + offset * 0.22D, step);
        } else if (mode == 4) {
            double period = 0.28D + unit(cellHash >>> 23) * (0.72D + strength * 2.10D);
            out = fold(value + offset * 0.42D, period) - period * 0.50D;
        } else if (mode == 5) {
            double stuck = sign * amplitude * (0.24D + unit(cellHash >>> 29) * (0.90D + strength));
            out = lerp(value, stuck, 0.30D + strength * 0.44D);
        } else if (mode == 6) {
            double threshold = sign * (0.05D + unit(cellHash >>> 31) * amplitude);
            out = value >= threshold ? value + Math.abs(offset) : value - Math.abs(offset);
        } else {
            double clamped = clamp(value, -Math.abs(offset), Math.abs(offset));
            out = lerp(value, clamped, mix);
        }
        return Double.isFinite(out) ? out : value;
    }

    private static int densityValueMode(DensityRuntime runtime, int role, long cellHash) {
        int variant = Math.floorMod((int) (cellHash >>> 43), 3);
        return switch (runtime.densityStyle()) {
            case 0 -> role == DENSITY_ROLE_CONTINENTS || role == DENSITY_ROLE_EROSION || role == DENSITY_ROLE_RIDGES
                    ? switch (variant) {
                        case 0 -> 0;
                        case 1 -> 1;
                        default -> 6;
                    }
                    : switch (variant) {
                        case 0 -> 5;
                        case 1 -> 7;
                        default -> 0;
                    };
            case 1 -> role == DENSITY_ROLE_DEPTH || role == DENSITY_ROLE_INITIAL || role == DENSITY_ROLE_FINAL
                    ? switch (variant) {
                        case 0 -> 2;
                        case 1 -> 4;
                        default -> 5;
                    }
                    : switch (variant) {
                        case 0 -> 0;
                        case 1 -> 1;
                        default -> 7;
                    };
            case 2 -> switch (variant) {
                case 0 -> 3;
                case 1 -> 5;
                default -> 7;
            };
            case 3 -> role == DENSITY_ROLE_TEMPERATURE || role == DENSITY_ROLE_VEGETATION
                    ? switch (variant) {
                        case 0 -> 2;
                        case 1 -> 3;
                        default -> 5;
                    }
                    : switch (variant) {
                        case 0 -> 7;
                        case 1 -> 0;
                        default -> 1;
                    };
            case 4 -> switch (variant) {
                case 0 -> 4;
                case 1 -> 6;
                default -> 2;
            };
            case 5 -> switch (variant) {
                case 0 -> 1;
                case 1 -> 0;
                default -> 6;
            };
            default -> Math.floorMod(runtime.family() + variant * 3, 8);
        };
    }

    private static double densityRolePressure(int role, boolean continentalBand, boolean terrainShape) {
        double pressure = terrainShape ? 0.58D : 0.82D;
        if (role == DENSITY_ROLE_CONTINENTS || role == DENSITY_ROLE_EROSION || role == DENSITY_ROLE_RIDGES) {
            pressure += 0.26D;
        } else if (role == DENSITY_ROLE_TEMPERATURE || role == DENSITY_ROLE_VEGETATION) {
            pressure += 0.18D;
        }
        return continentalBand ? pressure + 0.18D : pressure;
    }

    public static double densityMinValue(String channel, double value) {
        DensityRuntime runtime = densityRuntime(channel);
        return !runtime.sampleActive() ? value : value - runtime.boundsAmplitude() * 1.6D;
    }

    public static double densityMaxValue(String channel, double value) {
        DensityRuntime runtime = densityRuntime(channel);
        return !runtime.sampleActive() ? value : value + runtime.boundsAmplitude() * 1.6D;
    }

    public static Climate.TargetPoint corruptClimateSample(Climate.TargetPoint point, int quartX, int quartY, int quartZ) {
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (point == null || intensity < CLIMATE_SAMPLE_MIN_INTENSITY) {
            return point;
        }

        float extreme = extremePressure(intensity);
        WorldgenProfile profile = state.profile();
        float climateScale = profile.climateScale();
        float climatePressure = clamp01(extreme * (0.36F + climateScale * 0.64F));
        int cell = Math.max(4, Math.round((16.0F + (1.0F - intensity) * 42.0F + unit(state.fixedSeed() ^ 0x434C494D4543454CL) * 28.0F - climatePressure * 8.0F) * profile.macroScale()));
        int cellY = Math.max(2, cell / 2);
        long seed = mix(state.fixedSeed()
                ^ floorToCell(quartX, cell) * 0x9E3779B97F4A7C15L
                ^ floorToCell(quartY, cellY) * 0x94D049BB133111EBL
                ^ floorToCell(quartZ, cell) * 0xBF58476D1CE4E5B9L
                ^ 0x434C494D415445L);
        float chance = clamp01(0.018F + intensity * 0.24F + state.instability() * 0.035F + climatePressure * 0.42F);
        if (unit(seed ^ 0x53414D504CL) >= chance) {
            return point;
        }

        long temperature = corruptClimateComponent(point.temperature(), seed ^ 0x54454D50L, intensity);
        long humidity = corruptClimateComponent(point.humidity(), seed ^ 0x48554D49L, intensity);
        long continentalness = corruptClimateComponent(point.continentalness(), seed ^ 0x434F4E54L, intensity);
        long erosion = corruptClimateComponent(point.erosion(), seed ^ 0x45524F53L, intensity);
        long depth = corruptClimateComponent(point.depth(), seed ^ 0x44455054L, intensity);
        long weirdness = corruptClimateComponent(point.weirdness(), seed ^ 0x57454952L, intensity);

        int mode = Math.floorMod((int) (seed >>> 42), 8);
        if (mode == 1) {
            long tmp = temperature;
            temperature = humidity;
            humidity = tmp;
        } else if (mode == 2) {
            long tmp = erosion;
            erosion = weirdness;
            weirdness = tmp;
        } else if (mode == 3) {
            continentalness = -continentalness;
        } else if (mode == 4) {
            depth = quantizeLong(depth, Math.max(250L, Math.round(850.0F + intensity * 6400.0F)));
            erosion = quantizeLong(erosion, Math.max(250L, Math.round(700.0F + intensity * 5200.0F)));
        } else if (mode == 5) {
            temperature = foldLong(temperature, Math.max(1200L, Math.round(1800.0F + intensity * 14000.0F)));
            humidity = foldLong(humidity, Math.max(1200L, Math.round(1800.0F + intensity * 14000.0F)));
        } else if (mode == 6) {
            long lane = signedRange(seed >>> 18, Math.max(1, Math.round(2.0F + intensity * 11.0F))) * 1000L;
            temperature += lane;
            weirdness -= lane;
        } else if (mode == 7) {
            continentalness = point.weirdness();
            weirdness = point.continentalness();
        }

        if (extreme > 0.0F) {
            int macroCell = Math.max(2, Math.round((4.0F + (1.0F - extreme) * 18.0F + unit(seed ^ 0x4D434C494D4CL) * 16.0F) * profile.macroScale()));
            int macroCellY = Math.max(1, macroCell / 2);
            long macroSeed = mix(state.fixedSeed()
                    ^ floorToCell(quartX, macroCell) * 0x632BE59BD9B4E019L
                    ^ floorToCell(quartY, macroCellY) * 0x85157AF5L
                    ^ floorToCell(quartZ, macroCell) * 0x94D049BB133111EBL
                    ^ 0x434C494D45585452L);
            if (unit(macroSeed ^ 0x4D4143524FL) < 0.25F + climatePressure * 0.65F) {
                long span = Math.round(18000.0F + climatePressure * 42000.0F);
                long polarity = signedUnit(macroSeed >>> 9) >= 0.0D ? 1L : -1L;
                int macroMode = Math.floorMod((int) (macroSeed >>> 41), 7);
                if (macroMode == 0) {
                    temperature = polarity * span;
                    humidity = -polarity * span;
                    weirdness = foldLong(weirdness + polarity * span / 2L, Math.max(2500L, span / 3L));
                } else if (macroMode == 1) {
                    continentalness = polarity * span;
                    erosion = -polarity * span / 2L;
                    depth = quantizeLong(depth + polarity * span / 4L, Math.max(800L, span / 16L));
                } else if (macroMode == 2) {
                    temperature = point.weirdness();
                    humidity = point.continentalness();
                    continentalness = -point.temperature();
                    weirdness = -point.humidity();
                } else if (macroMode == 3) {
                    temperature = quantizeLong(temperature, Math.max(900L, span / 20L));
                    humidity = quantizeLong(humidity, Math.max(900L, span / 20L));
                    erosion = quantizeLong(erosion, Math.max(700L, span / 24L));
                    weirdness = quantizeLong(weirdness, Math.max(700L, span / 24L));
                } else if (macroMode == 4) {
                    long stuck = signedRange(macroSeed >>> 17, Math.max(2, Math.round(8.0F + extreme * 34.0F))) * 1200L;
                    temperature = stuck;
                    humidity = -stuck;
                    erosion = stuck / 2L;
                    weirdness = -stuck / 2L;
                } else if (macroMode == 5) {
                    continentalness = foldLong(continentalness + polarity * span, Math.max(3200L, span / 2L));
                    erosion = foldLong(erosion - polarity * span / 2L, Math.max(2400L, span / 3L));
                    depth = -depth;
                } else {
                    long tmp = temperature;
                    temperature = -humidity;
                    humidity = -tmp;
                    continentalness = quantizeLong(-continentalness, Math.max(1000L, span / 18L));
                    weirdness = foldLong(weirdness + polarity * span, Math.max(2200L, span / 4L));
                }
            }
        }

        return new Climate.TargetPoint(
                clampLong(temperature, -60000L, 60000L),
                clampLong(humidity, -60000L, 60000L),
                clampLong(continentalness, -60000L, 60000L),
                clampLong(erosion, -60000L, 60000L),
                clampLong(depth, -60000L, 60000L),
                clampLong(weirdness, -60000L, 60000L)
        );
    }

    public static BlockState corruptSurfaceMaterial(BlockState original, int x, int y, int z) {
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity <= 0.0F || original == null) {
            return original;
        }
        float extreme = extremePressure(intensity);
        WorldgenProfile profile = state.profile();
        float surfacePressure = clamp01(extreme * (0.35F + profile.surfaceScale() * 0.65F));
        float chance = clamp01(Math.max(0.0F, intensity - 0.18F) * 0.26F + state.instability() * 0.035F + surfacePressure * 0.34F);
        if (chance <= 0.0F) {
            return original;
        }
        int cell = Math.max(3, Math.round((10.0F + (1.0F - intensity) * 18.0F + unit(state.fixedSeed() ^ 0x5355524643454CL) * 38.0F - surfacePressure * 4.0F) * profile.macroScale()));
        long seed = mix(state.fixedSeed()
                ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                ^ floorToCell(y, Math.max(4, cell / 4)) * 0x94D049BB133111EBL
                ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L
                ^ 0x535552464D4154L);
        if (unit(seed ^ 0x53555246L) >= chance) {
            return original;
        }
        if (original.hasBlockEntity() || !original.getFluidState().isEmpty()) {
            return original;
        }
        float dropoutChance = clamp01(0.035F + intensity * 0.10F + surfacePressure * 0.30F);
        if (unit(seed ^ 0x4E554C4CL) < dropoutChance) {
            return null;
        }
        return original;
    }

    public static boolean corruptCarverStart(Object carver, boolean original, RandomSource random) {
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity <= 0.0F || random == null) {
            return original;
        }

        long seed = mix(state.fixedSeed()
                ^ stableTypeHash(carver) * 0x9E3779B97F4A7C15L
                ^ random.nextLong()
                ^ 0x4341525645524741L);
        float extreme = extremePressure(intensity);
        float flipChance = original
                ? clamp01(0.012F + intensity * 0.12F + state.instability() * 0.025F + extreme * 0.18F * state.profile().carverScale())
                : clamp01(0.006F + intensity * 0.07F + state.instability() * 0.02F + extreme * 0.12F * state.profile().carverScale());
        if (unit(seed ^ 0x5354415254L) < flipChance) {
            return !original;
        }
        return original;
    }

    public static boolean afterCarver(Object carver, ChunkAccess chunk, ChunkPos carvingOrigin, boolean original) {
        WorldgenState state = worldgenState();
        float intensity = state.intensity();
        if (intensity <= 0.0F || chunk == null || carvingOrigin == null) {
            return original;
        }
        long seed = mix(state.fixedSeed()
                ^ chunk.getPos().toLong()
                ^ carvingOrigin.toLong() * 0xD6E8FEB86659FD93L
                ^ stableTypeHash(carver)
                ^ 0x434152564552504FL);
        float extreme = extremePressure(intensity);
        if (unit(seed ^ 0x52544E53L) < 0.025F + intensity * 0.08F + extreme * 0.18F * state.profile().carverScale()) {
            return !original;
        }
        return original;
    }

    private static FeatureInfo featureInfo(Feature<?> feature) {
        return FEATURE_INFO_CACHE.computeIfAbsent(feature, WorldgenCorruptionHooks::createFeatureInfo);
    }

    private static FeatureInfo createFeatureInfo(Feature<?> feature) {
        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        return new FeatureInfo(target.toLowerCase(Locale.ROOT), stableString(target));
    }

    private static long stableTypeHash(Object value) {
        return value == null ? 0L : stableString(value.getClass().getName());
    }

    private static long corruptClimateComponent(long original, long seed, float intensity) {
        int mode = Math.floorMod((int) (seed >>> 49), 7);
        long span = Math.max(180L, Math.round(700.0F + intensity * (2500.0F + unit(seed >>> 8) * 13000.0F)));
        long value = original;
        if (mode == 0) {
            value += signedRange(seed >>> 13, (int) Math.min(Integer.MAX_VALUE / 2L, span));
        } else if (mode == 1) {
            value = -value + signedRange(seed >>> 17, (int) Math.min(Integer.MAX_VALUE / 2L, span / 2L));
        } else if (mode == 2) {
            value = quantizeLong(value, Math.max(120L, span / 8L));
        } else if (mode == 3) {
            value = foldLong(value + signedRange(seed >>> 21, (int) Math.min(Integer.MAX_VALUE / 2L, span)), Math.max(500L, span / 3L));
        } else if (mode == 4) {
            value = Math.round(Math.copySign(Math.max(Math.abs(value), span), signedUnit(seed >>> 25)));
        } else if (mode == 5) {
            value = Math.round(value * (0.10D + unit(seed >>> 29) * (0.55D + intensity * 2.20D)));
        } else {
            value += densityScanlinePulse(seed, (int) (value >> 6), (int) (span >> 4), (int) (seed >> 32), intensity) * span;
        }
        return value;
    }

    private static long quantizeLong(long value, long step) {
        long safeStep = Math.max(1L, step);
        return Math.round(value / (double) safeStep) * safeStep;
    }

    private static long foldLong(long value, long period) {
        long safePeriod = Math.max(1L, period);
        long wrapped = Math.floorMod(value, safePeriod * 2L);
        return wrapped > safePeriod ? safePeriod * 2L - wrapped : wrapped;
    }

    private static float featureSkipChance(FeatureInfo featureInfo, float intensity, float instability, WorldgenProfile profile) {
        String normalized = featureInfo.normalizedTarget();
        float extreme = extremePressure(intensity);
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
            base = 0.025F + intensity * 0.20F;
        } else if (normalized.contains("lake")
                || normalized.contains("spring")
                || normalized.contains("disk")
                || normalized.contains("ice")
                || normalized.contains("magma")
                || normalized.contains("coral")
                || normalized.contains("kelp")
                || normalized.contains("seagrass")) {
            base = 0.022F + intensity * 0.18F;
        } else if (normalized.contains("ore")
                || normalized.contains("geode")
                || normalized.contains("fossil")
                || normalized.contains("dripstone")
                || normalized.contains("monster_room")) {
            base = 0.018F + intensity * 0.14F;
        } else {
            base = 0.014F + intensity * 0.12F;
        }
        return clamp01((base + instability * 0.035F + extreme * 0.24F * profile.featureScale())
                * featureSkipActivation(featureInfo, profile));
    }

    private static float featureRerouteChance(FeatureInfo featureInfo, float intensity, float instability, WorldgenProfile profile) {
        String normalized = featureInfo.normalizedTarget();
        float extreme = extremePressure(intensity);
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
            base = 0.025F + intensity * 0.24F;
        } else if (normalized.contains("lake")
                || normalized.contains("spring")
                || normalized.contains("disk")
                || normalized.contains("ice")
                || normalized.contains("magma")
                || normalized.contains("coral")
                || normalized.contains("kelp")
                || normalized.contains("seagrass")) {
            base = 0.020F + intensity * 0.20F;
        } else if (normalized.contains("ore")
                || normalized.contains("geode")
                || normalized.contains("fossil")
                || normalized.contains("dripstone")
                || normalized.contains("monster_room")) {
            base = 0.016F + intensity * 0.16F;
        } else {
            base = 0.012F + intensity * 0.13F;
        }
        return clamp01((base + instability * 0.045F + extreme * 0.30F * profile.featureScale())
                * featureRerouteActivation(featureInfo, profile));
    }

    private static float featureSkipActivation(FeatureInfo featureInfo, WorldgenProfile profile) {
        float activation = profile.featureActivation();
        if (isVegetationFeature(featureInfo)) {
            activation *= switch (profile.featureStyle()) {
                case 1, 4 -> 1.10F;
                case 3 -> 0.34F;
                default -> 0.58F;
            };
        } else if (isUndergroundFeature(featureInfo)) {
            activation *= profile.featureStyle() == 5 ? 1.18F : 0.82F;
        }
        return clampFloat(activation, 0.08F, 1.65F);
    }

    private static float featureRerouteActivation(FeatureInfo featureInfo, WorldgenProfile profile) {
        float activation = profile.featureActivation();
        if (isVegetationFeature(featureInfo)) {
            activation *= switch (profile.featureStyle()) {
                case 1 -> 1.22F;
                case 4 -> 0.84F;
                case 3 -> 0.12F;
                default -> 0.28F;
            };
        } else if (isUndergroundFeature(featureInfo)) {
            activation *= profile.featureStyle() == 5 ? 1.28F : 0.72F;
        }
        return clampFloat(activation, 0.04F, 1.72F);
    }

    private static float featureVerticalActivation(FeatureInfo featureInfo, WorldgenProfile profile) {
        float activation = profile.featureVerticalActivation();
        if (isVegetationFeature(featureInfo)) {
            activation *= switch (profile.featureStyle()) {
                case 1 -> 1.0F;
                case 4 -> 0.48F;
                case 3 -> 0.05F;
                default -> 0.16F;
            };
        } else if (isUndergroundFeature(featureInfo)) {
            activation *= profile.featureStyle() == 5 ? 0.92F : 0.38F;
        }
        return clampFloat(activation, 0.02F, 1.35F);
    }

    private static boolean isVegetationFeature(FeatureInfo featureInfo) {
        String normalized = featureInfo.normalizedTarget();
        return normalized.contains("tree")
                || normalized.contains("flower")
                || normalized.contains("vegetation")
                || normalized.contains("random_patch")
                || normalized.contains("bamboo")
                || normalized.contains("vines")
                || normalized.contains("mushroom")
                || normalized.contains("root")
                || normalized.contains("grass");
    }

    private static boolean isUndergroundFeature(FeatureInfo featureInfo) {
        String normalized = featureInfo.normalizedTarget();
        return normalized.contains("ore")
                || normalized.contains("geode")
                || normalized.contains("fossil")
                || normalized.contains("dripstone")
                || normalized.contains("monster_room");
    }

    public static DensityRuntime densityRuntime(String channel) {
        WorldgenState state = worldgenState();
        long version = state.version();
        if (cachedDensityRuntimeVersion != version) {
            synchronized (DENSITY_RUNTIME_CACHE_LOCK) {
                if (cachedDensityRuntimeVersion != version) {
                    DENSITY_RUNTIME_CACHE.clear();
                    cachedDensityRuntimeVersion = version;
                }
            }
        }

        String key = channel == null ? "" : channel;
        DensityRuntime cached = DENSITY_RUNTIME_CACHE.get(key);
        if (cached != null && cached.version() == version) {
            return cached;
        }

        DensityRuntime runtime = createDensityRuntime(state, key);
        DENSITY_RUNTIME_CACHE.put(key, runtime);
        return runtime;
    }

    private static DensityRuntime createDensityRuntime(WorldgenState state, String channel) {
        DensityChannelPlan plan = densityPlan(channel);
        float intensity = state.intensity();
        WorldgenProfile profile = state.profile();
        float roleScale = densityRoleScale(profile, plan.role());
        float strength = clamp01(densityStrength(plan, intensity) * roleScale);
        boolean sampleActive = intensity > 0.0F && strength >= DENSITY_SAMPLE_MIN_STRENGTH;
        long channelSeed = mix(state.fixedSeed() ^ plan.hash());
        float roleActivation = densityRoleActivation(profile, plan.role());
        float extremePressure = clamp01(extremePressure(intensity)
                * (0.20F + roleScale * 0.38F)
                * (0.22F + roleActivation * 0.58F)
                * profile.extremeActivation());
        boolean terrainShape = plan.terrainShape();
        double baseAmplitude = densityAmplitude(plan, strength, state.instability());
        double amplitude = baseAmplitude * (terrainShape
                ? 0.44D + roleActivation * 0.16D + extremePressure * (1.05D + roleActivation * 0.34D)
                : 0.78D + extremePressure * 0.62D);
        double boundsAmplitude = densityAmplitude(plan, strength, 0.0F) * (terrainShape
                ? 0.84D + roleActivation * 0.18D + extremePressure * 1.34D
                : 1.0D + extremePressure * 0.85D);
        int gateStyle = channelGateStyle(profile, channelSeed, plan.terrainShape());
        float gateScale = switch (gateStyle) {
            case 1 -> 0.72F;
            case 4 -> 2.20F;
            case 5 -> 1.65F;
            default -> 1.0F + unit(channelSeed ^ 0x474154535343L) * 0.74F;
        };
        int gateCell = Math.max(8, Math.round((18.0F + (1.0F - strength) * 42.0F + unit(channelSeed ^ 0x47415445L) * 26.0F) * gateScale));
        int gateCellY = switch (gateStyle) {
            case 1 -> Math.max(4, Math.round(gateCell * (0.55F + unit(channelSeed ^ 0x5947415445L) * 0.90F)));
            case 2, 5 -> Math.max(16, Math.round(gateCell * (1.35F + unit(channelSeed ^ 0x5947415445L) * 1.80F)));
            default -> Math.max(24, Math.round(gateCell * (2.0F + unit(channelSeed ^ 0x5947415445L) * 3.0F)));
        };
        float activeChance = clamp01((terrainShape ? 0.035F : 0.045F)
                + strength * (terrainShape ? 0.24F : 0.28F)
                + state.instability() * 0.035F
                + extremePressure * (terrainShape ? 0.36F : 0.28F));
        activeChance = clamp01(activeChance * (0.48F + roleActivation * 0.62F));
        float highPressure = clamp01((intensity - 0.62F) / 0.38F);
        float baselineStrength = densityBaselineStrength(plan, terrainShape, roleActivation, highPressure, state.instability());
        int baselineMode = Math.floorMod(profile.densityStyle() + plan.role() + Math.floorMod((int) (channelSeed >>> 39), 5), 8);
        double baselineBias = signedUnit(channelSeed ^ 0x4241534542494153L)
                * baseAmplitude
                * (terrainShape ? 0.44D + roleActivation * 0.28D + highPressure * 1.10D : 0.26D + roleActivation * 0.18D + highPressure * 0.52D);
        double baselineScale = 0.28D + unit(channelSeed ^ 0x424153455343414CL) * (terrainShape ? 3.45D : 2.10D);
        double baselineStep = 0.035D + unit(channelSeed ^ 0x4241534553544550L) * (terrainShape ? 0.84D : 0.46D);
        int family = Math.floorMod((int) (channelSeed >>> 17), 6);
        int densityStyle = Math.floorMod(profile.densityStyle() + Math.floorMod((int) (channelSeed >>> 53), 2), 7);
        long macroSeed = channelSeed ^ 0x4D4143524F44454EL;
        int macroCell = Math.max(8, Math.round((12.0F + (1.0F - strength) * 80.0F + unit(macroSeed ^ 0x5843454CL) * 72.0F - extremePressure * 8.0F) * profile.macroScale()));
        int macroCellY = Math.max(8, Math.round((10.0F + (1.0F - strength) * 40.0F + unit(macroSeed ^ 0x5943454CL) * 54.0F - extremePressure * 4.0F) * profile.macroScale()));

        return new DensityRuntime(
                state.version(),
                intensity,
                state.instability(),
                strength,
                sampleActive,
                terrainShape,
                plan.continentalBand(),
                plan.role(),
                channelSeed,
                amplitude,
                boundsAmplitude,
                extremePressure,
                gateCell,
                gateCellY,
                activeChance,
                baselineStrength,
                baselineMode,
                baselineBias,
                baselineScale,
                baselineStep,
                family,
                densityStyle,
                gateStyle,
                macroSeed,
                macroCell,
                macroCellY
        );
    }

    public static long worldgenVersion() {
        return worldgenState().version();
    }

    private static WorldgenState worldgenState() {
        GlobalCorruptionSettings.CorruptionRuntimeSnapshot snapshot = GlobalCorruptionSettings.runtimeSnapshot();
        WorldgenState cached = cachedWorldgenState;
        if (cached != null && cached.version() == snapshot.version()) {
            return cached;
        }
        WorldgenState state = WorldgenState.from(snapshot);
        cachedWorldgenState = state;
        return state;
    }

    private static float worldgenIntensity(CorruptionEffectStack stack) {
        if (!stack.activeOrExtreme(CorruptionSurface.WORLDGEN_SURFACE)) {
            return 0.0F;
        }
        return clamp01(Math.max(stack.intensityOrExtreme(CorruptionSurface.WORLDGEN_SURFACE), stack.effectiveLevel() * 0.92F));
    }

    private static DensityChannelPlan densityPlan(String channel) {
        if (channel == null) {
            return new DensityChannelPlan(stableString(""), 0.54F, 0.28D, false, false, DENSITY_ROLE_GENERIC);
        }
        return switch (channel) {
            case "continents" -> CONTINENTS_DENSITY;
            case "erosion" -> EROSION_DENSITY;
            case "ridges" -> RIDGES_DENSITY;
            case "depth" -> DEPTH_DENSITY;
            case "initial_density" -> INITIAL_DENSITY;
            case "final_density" -> FINAL_DENSITY;
            case "temperature" -> TEMPERATURE_DENSITY;
            case "vegetation" -> VEGETATION_DENSITY;
            default -> new DensityChannelPlan(stableString(channel), 0.54F, 0.28D, false, false, DENSITY_ROLE_GENERIC);
        };
    }

    private static float densityStrength(DensityChannelPlan plan, float intensity) {
        return clamp01(intensity * plan.weight());
    }

    private static float densityRoleScale(WorldgenProfile profile, int role) {
        float base = switch (role) {
            case DENSITY_ROLE_CONTINENTS, DENSITY_ROLE_EROSION, DENSITY_ROLE_RIDGES -> profile.continentalDensityScale();
            case DENSITY_ROLE_DEPTH, DENSITY_ROLE_INITIAL, DENSITY_ROLE_FINAL -> profile.shapeDensityScale();
            case DENSITY_ROLE_TEMPERATURE, DENSITY_ROLE_VEGETATION -> profile.climateDensityScale();
            default -> (profile.continentalDensityScale() + profile.shapeDensityScale() + profile.climateDensityScale()) / 3.0F;
        };
        return clampFloat(base * densityRoleActivation(profile, role), 0.10F, 1.85F);
    }

    private static float densityRoleActivation(WorldgenProfile profile, int role) {
        return switch (role) {
            case DENSITY_ROLE_CONTINENTS, DENSITY_ROLE_EROSION, DENSITY_ROLE_RIDGES -> profile.continentalActivation();
            case DENSITY_ROLE_DEPTH, DENSITY_ROLE_INITIAL, DENSITY_ROLE_FINAL -> profile.shapeActivation();
            case DENSITY_ROLE_TEMPERATURE, DENSITY_ROLE_VEGETATION -> profile.climateActivation();
            default -> (profile.continentalActivation() + profile.shapeActivation() + profile.climateActivation()) / 3.0F;
        };
    }

    private static float densityBaselineStrength(DensityChannelPlan plan, boolean terrainShape, float roleActivation, float highPressure, float instability) {
        if (terrainShape) {
            return clamp01(highPressure * (0.62F + roleActivation * 0.34F) + instability * 0.06F);
        }
        if (plan.continentalBand()) {
            return clamp01(highPressure * (0.24F + roleActivation * 0.28F) + instability * 0.035F);
        }
        return 0.0F;
    }

    private static int channelGateStyle(WorldgenProfile profile, long channelSeed, boolean terrainShape) {
        int style = profile.gateStyle();
        if (style == 1) {
            return 1;
        }
        float shiftChance = terrainShape ? 0.16F : 0.28F;
        if (unit(channelSeed ^ 0x434847415445L) >= shiftChance) {
            return style;
        }
        int shifted = switch (style) {
            case 0 -> unit(channelSeed ^ 0x4348304CL) < 0.5F ? 2 : 5;
            case 2 -> unit(channelSeed ^ 0x4348324CL) < 0.5F ? 0 : 3;
            case 3 -> unit(channelSeed ^ 0x4348334CL) < 0.5F ? 0 : 2;
            case 4 -> unit(channelSeed ^ 0x4348344CL) < 0.5F ? 0 : 5;
            case 5 -> unit(channelSeed ^ 0x4348354CL) < 0.5F ? 0 : 4;
            default -> 0;
        };
        return shifted == 1 ? style : shifted;
    }

    private static float extremePressure(float intensity) {
        float value = clamp01((intensity - 0.72F) / 0.28F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static double densityAmplitude(DensityChannelPlan plan, float strength, float instability) {
        return plan.amplitudeBase() * (0.12D + strength * 1.08D + instability * 0.22D);
    }

    private static double applyExtremeDensityFaults(DensityRuntime runtime, int role, double result, double original, int x, int y, int z, float strength, double amplitude, long channelSeed) {
        float extreme = runtime.extremePressure();
        if (extreme <= 0.0F) {
            return result;
        }

        int macroCell = runtime.macroCell();
        int macroCellY = runtime.macroCellY();
        long macroHash = densityMacroSeed(runtime, x, y, z);
        if (unit(macroHash ^ 0x5847415445L) > 0.22F + extreme * 0.72F) {
            return result;
        }

        double faultAmplitude = amplitude * (runtime.terrainShape() ? 0.75D + extreme * 1.55D : 0.62D + extreme * 1.10D);
        double sign = signedUnit(macroHash >>> 5);
        int mode = Math.floorMod((int) (macroHash >>> 43), 8);
        int shelf = Math.floorDiv(y + signedRange(macroHash >>> 11, macroCellY), macroCellY);
        double shelfSign = Math.floorMod(shelf + (int) (macroHash >>> 19), 4) < 2 ? 1.0D : -1.0D;
        double phaseSign = Math.floorMod((x / macroCell) + (z / macroCell) + (int) (macroHash >>> 27), 2) == 0 ? sign : -sign;
        double out = result;

        if (role == DENSITY_ROLE_CONTINENTS) {
            out += sign * faultAmplitude * (0.55D + extreme * 0.75D);
            if (mode == 1 || mode == 5) {
                out = -out + sign * faultAmplitude * 0.38D;
            } else if (mode == 2) {
                out = quantize(out, 0.12D + extreme * 0.44D);
            }
        } else if (role == DENSITY_ROLE_EROSION) {
            out = fold(out + phaseSign * faultAmplitude * (0.65D + extreme), 0.32D + extreme * 1.35D);
            if (mode == 3) {
                out -= sign * faultAmplitude * 0.52D;
            }
        } else if (role == DENSITY_ROLE_RIDGES) {
            out += phaseSign * faultAmplitude * (0.70D + extreme * 1.20D);
            if (mode == 4) {
                out = quantize(out, 0.08D + extreme * 0.28D);
            }
        } else if (role == DENSITY_ROLE_DEPTH) {
            out += shelfSign * faultAmplitude * (0.72D + extreme * 1.05D);
            if (mode == 6) {
                out = lerp(out, -original + shelfSign * faultAmplitude * 0.40D, 0.28D + extreme * 0.42D);
            }
        } else if (role == DENSITY_ROLE_INITIAL) {
            out += (shelfSign * 0.70D + phaseSign * 0.55D) * faultAmplitude * (0.55D + extreme * 0.85D);
            if (mode == 0 || mode == 7) {
                out = quantize(out, 0.10D + extreme * 0.36D);
            }
        } else if (role == DENSITY_ROLE_FINAL) {
            double broken = out + (shelfSign * 0.90D + phaseSign * 0.70D) * faultAmplitude;
            if (mode == 2 || mode == 6) {
                broken = -broken * (0.32D + extreme * 0.82D);
            } else if (mode == 3) {
                broken = quantize(broken, 0.08D + extreme * 0.30D);
            }
            out = lerp(out, broken, 0.34D + extreme * 0.52D);
        } else if (role == DENSITY_ROLE_TEMPERATURE || role == DENSITY_ROLE_VEGETATION) {
            out += sign * faultAmplitude * (0.80D + extreme * 1.10D);
            if (mode == 1 || mode == 4) {
                out = -out;
            } else if (mode == 2) {
                out = quantize(out, 0.16D + extreme * 0.52D);
            }
        } else {
            out += sign * faultAmplitude * 0.55D;
        }

        if (runtime.continentalBand() && unit(channelSeed ^ macroHash ^ 0x42414E44464CL) < 0.20F + extreme * 0.52F) {
            out += shelfSign * faultAmplitude * (0.28D + extreme * 0.46D);
        }
        return Double.isFinite(out) ? out : result;
    }

    private static long densityMacroSeed(DensityRuntime runtime, int x, int y, int z) {
        int cell = runtime.macroCell();
        int cellY = runtime.macroCellY();
        long seed = runtime.macroSeed() ^ 0x4558545244454E53L;
        return switch (runtime.gateStyle()) {
            case 0 -> mix(seed
                    ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                    ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L);
            case 1 -> mix(seed
                    ^ floorToCell(y, cellY) * 0x94D049BB133111EBL);
            case 2 -> mix(seed
                    ^ floorToCell(x + z, cell) * 0x632BE59BD9B4E019L
                    ^ floorToCell(y, Math.max(cellY, cell)) * 0x94D049BB133111EBL);
            case 3 -> mix(seed
                    ^ floorToCell(x - z, cell) * 0x85157AF5L);
            case 4 -> mix(seed);
            default -> mix(seed
                    ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                    ^ floorToCell(y, Math.max(cellY, cell * 2)) * 0x94D049BB133111EBL
                    ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L);
        };
    }

    private static double densityScanlinePulse(long seed, int x, int y, int z, float strength) {
        int axis = Math.floorMod((int) (seed >>> 9), 3);
        int stride = Math.max(2, Math.round(4.0F + unit(seed >>> 17) * (18.0F + strength * 46.0F)));
        int thickness = Math.max(1, Math.round(1.0F + strength * (1.0F + unit(seed >>> 25) * 5.0F)));
        int coordinate = axis == 0 ? x : axis == 1 ? y : z;
        int lane = Math.floorMod(coordinate + signedRange(seed >>> 31, stride), stride);
        if (lane >= thickness) {
            return 0.0D;
        }
        double sign = signedUnit(seed ^ coordinate * 0x9E3779B97F4A7C15L);
        return sign >= 0.0D ? 1.0D : -1.0D;
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static double fold(double value, double period) {
        if (period <= 0.0D) {
            return value;
        }
        double wrapped = value % (period * 2.0D);
        if (wrapped < 0.0D) {
            wrapped += period * 2.0D;
        }
        return wrapped > period ? period * 2.0D - wrapped : wrapped;
    }

    private static int floorToCell(int value, int cell) {
        int size = Math.max(1, cell);
        return Math.floorDiv(value, size) * size;
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

    private static int signedRange(long value, int range) {
        int span = Math.max(1, range);
        return Math.floorMod((int) mix(value), span * 2 + 1) - span;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
