package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

final class RedstoneCorruptionMechanics {
    private RedstoneCorruptionMechanics() {
    }

    static int corruptSignal(
            BlockState state,
            BlockPos pos,
            Direction direction,
            int original,
            String channel,
            boolean analog,
            CorruptionEffectStack stack,
            long gameTime,
            float minimumIntensity
    ) {
        if (state == null || pos == null || !isRelevantState(state, original, analog)) {
            return original;
        }

        String directionId = direction == null ? "none" : direction.getName();
        String targetId = "redstone:" + channel + ":" + blockTargetId(state) + ":" + directionId;
        if (!active(stack, targetId, minimumIntensity)) {
            return original;
        }

        float intensity = intensity(stack, targetId);
        long cadence = Math.max(1L, Math.round(Mth.lerp(intensity, 8.0F, 1.0F)));
        long bucket = Math.floorDiv(gameTime, cadence);
        long seed = stack.stableLong(CorruptionSurface.REDSTONE_MECHANICS, targetId, pos.hashCode() ^ channel.hashCode() ^ 0x525344)
                ^ mixLong(pos.asLong() + bucket * 0x9E3779B97F4A7C15L)
                ^ (long) original * 0xBF58476D1CE4E5B9L;
        float chance = stack.extreme(CorruptionSurface.REDSTONE_MECHANICS)
                ? 0.98F
                : Mth.clamp(0.06F + intensity * 0.72F + stack.instability() * 0.08F, 0.0F, 0.92F);
        if (unitHash(seed ^ 0x5349474EL) > chance) {
            return original;
        }

        int mode = Math.floorMod((int) (seed >>> 28), 7);
        int mutated = switch (mode) {
            case 0 -> 0;
            case 1 -> 15;
            case 2 -> 15 - original;
            case 3 -> Math.round(unitHash(seed ^ 0x52414E44L) * 15.0F);
            case 4 -> unitHash(seed ^ 0x464C4950L) < 0.5F ? 0 : 15;
            case 5 -> clampInt(original + Math.round(signedUnit(seed ^ 0x44524946L) * (2.0F + intensity * 18.0F)), 0, 15);
            default -> Math.round(CorruptionValueMutator.mutateScalar(
                    stack,
                    CorruptionSurface.REDSTONE_MECHANICS,
                    targetId,
                    original,
                    2.0F + intensity * 18.0F,
                    0.0F,
                    15.0F,
                    0x5253,
                    seed
            ));
        };
        return clampInt(mutated, 0, 15);
    }

    static boolean active(CorruptionEffectStack stack, String targetId, float minimumIntensity) {
        return surfaceActive(stack, minimumIntensity) || targetActive(stack, targetId, minimumIntensity);
    }

    static float intensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(weightedSurfaceIntensity(stack, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static boolean isRelevantState(BlockState state, int original, boolean analog) {
        return original > 0 || state.isSignalSource() || (analog && state.hasAnalogOutputSignal());
    }

    private static boolean surfaceActive(CorruptionEffectStack stack, float minimumIntensity) {
        return stack != null
                && stack.activeOrExtreme(CorruptionSurface.REDSTONE_MECHANICS)
                && (stack.extreme(CorruptionSurface.REDSTONE_MECHANICS)
                || stack.intensity(CorruptionSurface.REDSTONE_MECHANICS) >= minimumIntensity);
    }

    private static boolean targetActive(CorruptionEffectStack stack, String targetId, float minimumIntensity) {
        return stack != null
                && stack.activeOrExtreme(CorruptionSurface.REDSTONE_MECHANICS, targetId)
                && (stack.extreme(CorruptionSurface.REDSTONE_MECHANICS)
                || stack.targetIntensity(CorruptionSurface.REDSTONE_MECHANICS, targetId) >= minimumIntensity);
    }

    private static float weightedSurfaceIntensity(CorruptionEffectStack stack, String targetId, float weight) {
        if (stack == null) {
            return 0.0F;
        }
        return Math.max(stack.targetIntensity(CorruptionSurface.REDSTONE_MECHANICS, targetId), stack.intensity(CorruptionSurface.REDSTONE_MECHANICS) * weight);
    }

    private static String blockTargetId(BlockState state) {
        ResourceLocation location = state == null ? null : ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return location == null ? "unknown" : location.toString();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long mixLong(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float unitHash(long value) {
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static float signedUnit(long value) {
        return unitHash(value) * 2.0F - 1.0F;
    }
}
