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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.ForgeRegistries;

public final class WorldgenCorruptionHooks {
    private static final ThreadLocal<Boolean> FEATURE_ORIGIN_REROUTE_ACTIVE = ThreadLocal.withInitial(() -> false);

    private WorldgenCorruptionHooks() {
    }

    public record DensityCoordinates(int x, int y, int z) {
        public boolean matches(int otherX, int otherY, int otherZ) {
            return x == otherX && y == otherY && z == otherZ;
        }
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
        float chance = clamp01((intensity - 0.24F) * 0.32F + stack.instability() * 0.06F);
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
        float chance = clamp01((intensity - 0.30F) * 0.26F + stack.instability() * 0.05F);
        return unit(hash) < chance;
    }

    public static boolean shouldSkipFeature(Feature<?> feature, WorldGenLevel level, BlockPos origin) {
        if (FEATURE_ORIGIN_REROUTE_ACTIVE.get()) {
            return false;
        }
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

        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || feature == null || level == null || origin == null) {
            return origin;
        }

        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        long seed = mix(level.getSeed()
                ^ stack.fixedSeed()
                ^ origin.asLong()
                ^ stableString(target) * 0xD6E8FEB86659FD93L
                ^ 0x4645415455524F52L);
        float chance = featureRerouteChance(target, intensity, stack.instability());
        if (unit(seed ^ 0x5245524FL) >= chance) {
            return origin;
        }

        int horizontalSpan = Math.max(1, Math.round(1.0F + intensity * (2.0F + unit(seed >>> 7) * 10.0F)));
        int verticalSpan = Math.max(1, Math.round(1.0F + intensity * (5.0F + unit(seed >>> 17) * 34.0F)));
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

    public static boolean fakeFeatureSuccess(Feature<?> feature, BlockPos origin) {
        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        long hash = mix(stableString(target) ^ origin.asLong() ^ 0x46414B4553554343L);
        return unit(hash) < 0.38F;
    }

    public static boolean handleFeatureRerouteFailure(Feature<?> feature, BlockPos origin, BlockPos reroutedOrigin, RuntimeException exception) {
        ResourceLocation featureId = ForgeRegistries.FEATURES.getKey(feature);
        String target = featureId == null ? feature.getClass().getName() : featureId.toString();
        long hash = mix(stableString(target)
                ^ origin.asLong()
                ^ reroutedOrigin.asLong() * 0x9E3779B97F4A7C15L
                ^ exception.getClass().getName().hashCode()
                ^ 0x464541545552464CL);
        return unit(hash) < 0.18F;
    }

    public static DensityFunction corruptDensity(String channel, DensityFunction function) {
        if (function == null || function instanceof CorruptedDensityFunction) {
            return function;
        }
        return new CorruptedDensityFunction(channel, function);
    }

    public static boolean shouldUseFastDensityFill() {
        return worldgenIntensity(stack()) <= 0.0F;
    }

    public static DensityCoordinates corruptDensityCoordinates(String channel, int x, int y, int z) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F) {
            return new DensityCoordinates(x, y, z);
        }

        float strength = clamp01(intensity * densityChannelWeight(channel));
        if (strength <= 0.0F) {
            return new DensityCoordinates(x, y, z);
        }

        long domainSeed = mix(stack.fixedSeed() ^ 0x574F524C444F4D4CL);
        int mode = Math.floorMod((int) (domainSeed >>> 48), 6);
        int period = Math.max(16, Math.round(24.0F + (1.0F - strength) * 144.0F + unit(domainSeed >>> 6) * 384.0F));
        int precision = Math.max(1, Math.round(1.0F + strength * (3.0F + unit(domainSeed >>> 18) * 17.0F)));
        int lane = Math.max(8, Math.round(10.0F + (1.0F - strength) * 48.0F + unit(domainSeed >>> 30) * 96.0F));
        int thickness = Math.max(1, Math.round(1.0F + strength * (1.0F + unit(domainSeed >>> 42) * 5.0F)));
        int farX = signedFarOffset(domainSeed ^ 0x58464152L, strength);
        int farZ = signedFarOffset(domainSeed ^ 0x5A464152L, strength);

        int sx = safeCoordinate((long) x + Math.round(farX * strength));
        int sz = safeCoordinate((long) z + Math.round(farZ * strength));
        int sy = y;
        if (mode == 0) {
            sx = precisionCoordinate(sx, precision);
            sz = foldCoordinate(sz, period, farZ);
            if (laneHit(x, lane, thickness, domainSeed ^ 0x4C414E45L)) {
                sx = repeatCoordinate(sx, Math.max(16, period / 2), farX);
            }
        } else if (mode == 1) {
            sx = repeatCoordinate(precisionCoordinate(sx, precision), period, farX);
            sz = repeatCoordinate(precisionCoordinate(sz, precision), period, farZ);
        } else if (mode == 2) {
            int shear = signedRange(domainSeed >>> 12, Math.max(1, Math.round(1.0F + strength * 5.0F)));
            sx = foldCoordinate(safeCoordinate((long) sx + (long) floorToCell(z, lane) * shear), period, farX);
            sz = precisionCoordinate(sz, precision);
        } else if (mode == 3) {
            sx = aliasCoordinate(sx, period, precision, domainSeed ^ 0x58414C49L);
            sz = aliasCoordinate(sz, Math.max(16, period * 2), precision, domainSeed ^ 0x5A414C49L);
        } else if (mode == 4) {
            sx = laneHit(x, lane, thickness, domainSeed ^ 0x58434F52L)
                    ? foldCoordinate(sx, period, farX)
                    : precisionCoordinate(sx, precision);
            sz = laneHit(z, lane, thickness, domainSeed ^ 0x5A434F52L)
                    ? foldCoordinate(sz, period, farZ)
                    : precisionCoordinate(sz, precision);
        } else {
            int localX = Math.floorMod(sx + Math.floorMod((int) domainSeed, lane), lane);
            int localZ = Math.floorMod(sz + Math.floorMod((int) (domainSeed >>> 16), lane), lane);
            sx = floorToCell(sx, lane) + Math.floorMod(localX, Math.max(1, precision));
            sz = foldCoordinate(floorToCell(sz, lane) + Math.floorMod(localZ, Math.max(1, precision)), period, farZ);
        }

        float blend = clamp01(0.18F + strength * 0.82F);
        return new DensityCoordinates(
                safeCoordinate(Math.round(lerp(x, sx, blend))),
                sy,
                safeCoordinate(Math.round(lerp(z, sz, blend)))
        );
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
        int gateCell = Math.max(8, Math.round(18.0F + (1.0F - strength) * 42.0F + unit(channelSeed ^ 0x47415445L) * 26.0F));
        int gateCellY = Math.max(8, gateCell / 2);
        long gateSeed = mix(channelSeed
                ^ floorToCell(x, gateCell) * 0x9E3779B97F4A7C15L
                ^ floorToCell(y, gateCellY) * 0x94D049BB133111EBL
                ^ floorToCell(z, gateCell) * 0xBF58476D1CE4E5B9L
                ^ 0x44454E5347415445L);
        boolean terrainShape = isTerrainShapeChannel(channel);
        float activeChance = clamp01((terrainShape ? 0.025F : 0.045F) + strength * (terrainShape ? 0.16F : 0.28F) + stack.instability() * 0.035F);
        if (unit(gateSeed ^ 0x414354495645L) >= activeChance) {
            return value;
        }

        int family = Math.floorMod((int) (channelSeed >>> 17), 6);
        double amplitude = densityAmplitude(channel, strength, stack.instability()) * (terrainShape ? 0.28D : 0.70D);
        double coarse = smoothNoise2D(x, z, channelSeed ^ 0x434F41525345L, 0.002D + unit(channelSeed >>> 11) * 0.006D);
        double folded = smoothNoise2D(foldCoordinate(x, 64 + gateCell, (int) channelSeed), foldCoordinate(z, 64 + gateCell, (int) (channelSeed >>> 32)), channelSeed ^ 0x464F4C444544L, 0.010D + strength * 0.030D);
        double lane = densityScanlinePulse(channelSeed ^ 0x4C414E455354L, x, y, z, strength);
        double result = value;

        if (family == 0) {
            result = value + (coarse * 0.42D + folded * 0.18D) * amplitude;
        } else if (family == 1) {
            double step = 0.03125D + unit(channelSeed >>> 21) * (0.10D + strength * 0.45D);
            result = quantize(value + coarse * amplitude * 0.22D, step);
        } else if (family == 2) {
            double scale = 0.70D + unit(channelSeed >>> 27) * 0.46D;
            result = lerp(value, value * scale + folded * amplitude * 0.30D, 0.08D + strength * 0.22D);
        } else if (family == 3) {
            result = value + lane * amplitude * (0.16D + strength * 0.28D);
        } else if (family == 4) {
            double foldedValue = fold(value + coarse * amplitude * 0.35D, 0.35D + unit(channelSeed >>> 33) * (0.8D + strength * 1.6D));
            result = lerp(value, foldedValue, 0.04D + strength * 0.18D);
        } else {
            double shelves = Math.sin((x + z + floorToCell(y, Math.max(2, gateCellY))) * (0.010D + strength * 0.025D));
            result = value + (coarse * 0.30D + folded * 0.22D + shelves * 0.12D) * amplitude;
        }

        if ("continents".equals(channel) || "erosion".equals(channel) || "ridges".equals(channel)) {
            result += coarse * folded * amplitude * (0.18D + strength * 0.22D);
        }

        result = applyDensityAddressFaults(channel, stack, result, value, x, y, z, strength, amplitude, minValue, maxValue, channelSeed);
        if (terrainShape && y > 124 && result > value) {
            result = lerp(result, value, clamp01((y - 124) / 96.0F));
        }
        double limit = Math.max(3.0D, Math.max(Math.abs(minValue), Math.abs(maxValue)) + 1.0D + strength * 3.5D);
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

    public static Climate.TargetPoint corruptClimateSample(Climate.TargetPoint point, int quartX, int quartY, int quartZ) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (point == null || intensity <= 0.0F) {
            return point;
        }

        int cell = Math.max(4, Math.round(16.0F + (1.0F - intensity) * 42.0F + unit(stack.fixedSeed() ^ 0x434C494D4543454CL) * 28.0F));
        int cellY = Math.max(2, cell / 2);
        long seed = mix(stack.fixedSeed()
                ^ floorToCell(quartX, cell) * 0x9E3779B97F4A7C15L
                ^ floorToCell(quartY, cellY) * 0x94D049BB133111EBL
                ^ floorToCell(quartZ, cell) * 0xBF58476D1CE4E5B9L
                ^ 0x434C494D415445L);
        float chance = clamp01(0.018F + intensity * 0.24F + stack.instability() * 0.035F);
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
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || original == null) {
            return original;
        }
        int cell = Math.max(2, Math.round(4.0F + (1.0F - intensity) * 10.0F + unit(stack.fixedSeed() ^ 0x5355524643454CL) * 8.0F));
        long seed = mix(stack.fixedSeed()
                ^ floorToCell(x, cell) * 0x9E3779B97F4A7C15L
                ^ floorToCell(y, 4) * 0x94D049BB133111EBL
                ^ floorToCell(z, cell) * 0xBF58476D1CE4E5B9L
                ^ 0x535552464D4154L);
        float chance = clamp01(Math.max(0.0F, intensity - 0.18F) * 0.24F + stack.instability() * 0.03F);
        if (unit(seed ^ 0x53555246L) >= chance) {
            return original;
        }
        if (unit(seed ^ 0x4E554C4CL) < Math.max(0.0F, intensity - 0.82F) * 0.035F) {
            return null;
        }
        return surfaceRuleReplacement(original, seed);
    }

    public static boolean corruptCarverStart(Object carver, boolean original, RandomSource random) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || random == null) {
            return original;
        }

        long seed = mix(stack.fixedSeed()
                ^ System.identityHashCode(carver) * 0x9E3779B97F4A7C15L
                ^ random.nextLong()
                ^ 0x4341525645524741L);
        float flipChance = original
                ? clamp01(0.012F + intensity * 0.12F + stack.instability() * 0.025F)
                : clamp01(0.006F + intensity * 0.07F + stack.instability() * 0.02F);
        if (unit(seed ^ 0x5354415254L) < flipChance) {
            return !original;
        }
        return original;
    }

    public static boolean afterCarver(Object carver, ChunkAccess chunk, ChunkPos carvingOrigin, boolean original) {
        CorruptionEffectStack stack = stack();
        float intensity = worldgenIntensity(stack);
        if (intensity <= 0.0F || chunk == null || carvingOrigin == null) {
            return original;
        }
        long seed = mix(stack.fixedSeed()
                ^ chunk.getPos().toLong()
                ^ carvingOrigin.toLong() * 0xD6E8FEB86659FD93L
                ^ System.identityHashCode(carver)
                ^ 0x434152564552504FL);
        if (unit(seed ^ 0x52544E53L) < 0.025F + intensity * 0.08F) {
            return !original;
        }
        return original;
    }

    private static BlockState surfaceRuleReplacement(BlockState original, long seed) {
        if (original.hasBlockEntity() || !original.getFluidState().isEmpty()) {
            return original;
        }
        if (isOneOf(original, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD)) {
            return pickSurface(seed, original, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD);
        }
        if (isOneOf(original, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD)) {
            return pickSurface(seed, original, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD);
        }
        if (isOneOf(original, Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.DRIPSTONE_BLOCK)) {
            return pickSurface(seed, original, Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.DRIPSTONE_BLOCK);
        }
        if (isOneOf(original, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE)) {
            return pickSurface(seed, original, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE);
        }
        if (isOneOf(original, Blocks.NETHERRACK, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM)) {
            return pickSurface(seed, original, Blocks.NETHERRACK, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM);
        }
        if (isOneOf(original, Blocks.END_STONE, Blocks.OBSIDIAN)) {
            return pickSurface(seed, original, Blocks.END_STONE, Blocks.OBSIDIAN);
        }
        return original;
    }

    private static boolean isOneOf(BlockState state, Block... blocks) {
        for (Block block : blocks) {
            if (state.is(block)) {
                return true;
            }
        }
        return false;
    }

    private static BlockState pickSurface(long seed, BlockState original, Block... blocks) {
        if (blocks.length == 0) {
            return original;
        }
        BlockState replacement = blocks[Math.floorMod((int) (seed >>> 24), blocks.length)].defaultBlockState();
        return replacement.equals(original) ? original : replacement;
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
        return clamp01(base + instability * 0.035F);
    }

    private static float featureRerouteChance(String target, float intensity, float instability) {
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
        return clamp01(base + instability * 0.045F);
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

    private static boolean isTerrainShapeChannel(String channel) {
        return "final_density".equals(channel) || "initial_density".equals(channel) || "depth".equals(channel);
    }

    private static int signedFarOffset(long seed, float strength) {
        int distance = Math.round(250_000.0F + strength * (4_500_000.0F + unit(seed >>> 9) * 9_000_000.0F));
        return signedUnit(seed) >= 0.0D ? distance : -distance;
    }

    private static int precisionCoordinate(int value, int precision) {
        int step = Math.max(1, precision);
        return floorToCell(value, step);
    }

    private static int repeatCoordinate(int value, int period, int anchor) {
        int size = Math.max(1, period);
        return safeCoordinate((long) anchor + Math.floorMod(value - anchor, size) - size / 2L);
    }

    private static int foldCoordinate(int value, int period, int anchor) {
        int size = Math.max(1, period);
        int wrapped = Math.floorMod(value - anchor, size * 2);
        int folded = wrapped > size ? size * 2 - wrapped : wrapped;
        return safeCoordinate((long) anchor + folded - size / 2L);
    }

    private static int aliasCoordinate(int value, int period, int precision, long seed) {
        int repeated = repeatCoordinate(value, period, Math.floorMod((int) seed, Math.max(1, period)));
        int scale = Math.max(1, precision + Math.floorMod((int) (seed >>> 24), Math.max(1, precision + 1)));
        return precisionCoordinate(safeCoordinate((long) repeated * scale), Math.max(1, precision));
    }

    private static boolean laneHit(int value, int lane, int thickness, long seed) {
        int width = Math.max(1, lane);
        int offset = Math.floorMod((int) seed, width);
        return Math.floorMod(value + offset, width) < Math.max(1, thickness);
    }

    private static int safeCoordinate(long value) {
        return (int) Math.max(-30_000_000L, Math.min(30_000_000L, value));
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
            case "continents" -> 1.35D;
            case "erosion" -> 1.18D;
            case "ridges" -> 1.24D;
            case "depth" -> 0.92D;
            case "initial_density" -> 0.78D;
            case "final_density" -> 0.54D;
            case "temperature", "vegetation" -> 0.34D;
            default -> 0.28D;
        };
        return base * (0.12D + strength * 1.08D + instability * 0.22D);
    }

    private static double applyDensityAddressFaults(String channel, CorruptionEffectStack stack, double result, double original, int x, int y, int z, float strength, double amplitude, double minValue, double maxValue, long channelSeed) {
        double out = result;
        long addressSeed = mix(channelSeed ^ 0x41444452455353L);
        int cell = 2 << Math.floorMod((int) (addressSeed >>> 6), 6);
        int qx = floorToCell(x + signedRange(addressSeed >>> 12, cell), cell);
        int qy = floorToCell(y + signedRange(addressSeed >>> 24, Math.max(2, cell / 2)), Math.max(2, cell / 2));
        int qz = floorToCell(z + signedRange(addressSeed >>> 36, cell), cell);
        long cellHash = mix(addressSeed
                ^ qx * 0x9E3779B97F4A7C15L
                ^ qy * 0x94D049BB133111EBL
                ^ qz * 0xBF58476D1CE4E5B9L);

        float chance = clamp01(0.012F + strength * 0.16F + stack.instability() * 0.035F);
        boolean channelIsShape = "final_density".equals(channel) || "initial_density".equals(channel) || "depth".equals(channel);
        if (channelIsShape) {
            chance = clamp01(chance * 0.55F);
        }
        if (unit(cellHash ^ 0x43454C4CL) < chance) {
            int mode = Math.floorMod((int) (cellHash >>> 43), 9);
            double faultAmplitude = amplitude * (0.35D + strength * (channelIsShape ? 0.75D : 1.60D));
            if (mode == 0) {
                out += signedUnit(cellHash >>> 3) * faultAmplitude;
            } else if (mode == 1) {
                out = lerp(out, original + signedUnit(cellHash >>> 5) * faultAmplitude, 0.24D + strength * 0.34D);
            } else if (mode == 2) {
                double step = 0.0625D + unit(cellHash >>> 17) * (0.18D + strength * 0.72D);
                out = quantize(out + signedUnit(cellHash >>> 23) * faultAmplitude * 0.28D, step);
            } else if (mode == 3) {
                double stuck = original + densityScanlinePulse(addressSeed, x, y, z, strength) * faultAmplitude;
                out = lerp(out, stuck, 0.20D + strength * 0.28D);
            } else if (mode == 4) {
                out = lerp(out, original, 0.42D + strength * 0.32D) + signedUnit(cellHash >>> 31) * faultAmplitude * 0.22D;
            } else if (mode == 5) {
                out *= 0.45D + unit(cellHash >>> 37) * 0.42D;
            } else if (mode == 6) {
                out += densityScanlinePulse(addressSeed, x, y, z, strength) * faultAmplitude * 0.65D;
            } else if (mode == 7) {
                out = lerp(out, fold(out + signedUnit(cellHash >>> 41) * faultAmplitude, 0.35D + unit(cellHash >>> 49) * (0.9D + strength * 1.8D)), 0.18D + strength * 0.22D);
            } else {
                out += smoothNoise2D(x, z, cellHash ^ 0x424F554E44L, 0.018D + strength * 0.038D) * faultAmplitude;
            }
        }

        double lane = densityScanlinePulse(addressSeed ^ 0x4C414E45L, x, y, z, strength);
        if (Math.abs(lane) > 0.0D && unit(cellHash ^ 0x4C414E4550L) < 0.05F + strength * 0.12F) {
            out += lane * amplitude * (channelIsShape ? 0.28D : 0.70D);
        }

        if (!channelIsShape && unit(cellHash ^ 0x424954464C4950L) < strength * 0.025F) {
            long bits = Double.doubleToRawLongBits(out);
            int bit = 42 + Math.floorMod((int) (cellHash >>> 7), 12);
            bits ^= 1L << bit;
            double flipped = Double.longBitsToDouble(bits);
            if (Double.isFinite(flipped) && Math.abs(flipped) < 1.0E6D) {
                out = lerp(out, flipped, 0.22D + strength * 0.42D);
            }
        }

        return Double.isFinite(out) ? out : result;
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

    private static int floorToCell(int value, int cell) {
        int size = Math.max(1, cell);
        return Math.floorDiv(value, size) * size;
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

    private static int signedRange(long value, int range) {
        int span = Math.max(1, range);
        return Math.floorMod((int) mix(value), span * 2 + 1) - span;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
