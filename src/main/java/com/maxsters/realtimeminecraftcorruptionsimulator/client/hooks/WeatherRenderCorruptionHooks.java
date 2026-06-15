package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WeatherRenderCorruptionHooks {
    private WeatherRenderCorruptionHooks() {
    }

    public static boolean shouldSkipWeatherOverlay(ClientLevel level, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER) && !stack.activeOrExtreme(CorruptionSurface.TICK_SPEED)) {
            return false;
        }

        String targetId = "weather_overlay_skip:" + dimension(level);
        float intensity = weatherIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return false;
        }
        long clock = weatherClock(stack, targetId, level, partialTick);
        if (unit(clock ^ 0x464C4943L) < 0.10F + intensity * 0.46F) {
            return ((clock >>> 3) & 1L) == 0L;
        }
        return unit(clock ^ 0x44524F50L) < intensity * 0.08F;
    }

    public static float mutateRainLevel(ClientLevel level, float original, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER) && !stack.activeOrExtreme(CorruptionSurface.TICK_SPEED)) {
            return original;
        }

        String targetId = "weather_overlay:" + dimension(level);
        float intensity = weatherIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }
        long clock = weatherClock(stack, targetId, level, partialTick);
        float chance = Mth.clamp(0.06F + intensity * 0.74F + stack.instability() * 0.10F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ 0x454E4142L) > chance) {
            return original;
        }

        int mode = Math.floorMod((int) (clock >>> 26), 5);
        return switch (mode) {
            case 0 -> 1.0F;
            case 1 -> (((clock >>> 2) & 1L) == 0L) ? 0.0F : 1.0F;
            case 2 -> Mth.clamp(original + signed(clock ^ 0x5241494EL, intensity * 1.45F), 0.0F, 1.0F);
            case 3 -> Mth.clamp((float) Math.abs(Math.sin((gameTime(level) + partialTick) * (0.35F + intensity * 2.4F))), 0.0F, 1.0F);
            default -> Mth.clamp(unit(clock ^ 0x4E4F4953L) * (0.25F + intensity * 1.45F), 0.0F, 1.0F);
        };
    }

    private static float weatherIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                (stack.extreme(CorruptionSurface.TICK_SPEED) ? 1.0F : stack.intensity(CorruptionSurface.TICK_SPEED)) * 0.68F
        ), 0.0F, 1.0F);
    }

    private static String dimension(ClientLevel level) {
        return level == null ? "no_level" : level.dimension().location().toString();
    }

    private static long weatherClock(CorruptionEffectStack stack, String targetId, ClientLevel level, float partialTick) {
        long time = gameTime(level);
        return stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x57454154) ^ (time << 18) ^ Float.floatToIntBits(partialTick);
    }

    private static long gameTime(ClientLevel level) {
        return level == null ? 0L : level.getGameTime();
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
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
