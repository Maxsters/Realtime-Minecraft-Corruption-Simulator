package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WeatherRenderCorruptionHooks {
    private static final ThreadLocal<WeatherContext> WEATHER_CONTEXT = new ThreadLocal<>();

    private WeatherRenderCorruptionHooks() {
    }

    public static void beginWeather(ClientLevel level, float partialTick, double cameraX, double cameraY, double cameraZ) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            WEATHER_CONTEXT.remove();
            return;
        }

        String targetId = "weather_curtains:" + dimension(level);
        float intensity = weatherIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            WEATHER_CONTEXT.remove();
            return;
        }

        long clock = weatherClock(stack, targetId, level, partialTick);
        WEATHER_CONTEXT.set(new WeatherContext(
                stack,
                targetId,
                intensity,
                clock,
                Math.floorMod((int) (clock >>> 28), 8),
                gameTime(level),
                partialTick,
                cameraX,
                cameraY,
                cameraZ
        ));
    }

    public static void endWeather() {
        WEATHER_CONTEXT.remove();
    }

    public static boolean shouldSkipWeatherOverlay(ClientLevel level, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
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
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
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

        int mode = Math.floorMod((int) (clock >>> 26), 7);
        float time = gameTime(level) + partialTick;
        return switch (mode) {
            case 0 -> 1.0F;
            case 1 -> (((clock >>> 2) & 1L) == 0L) ? 0.0F : 1.0F;
            case 2 -> Mth.clamp(original + signed(clock ^ 0x5241494EL, intensity * 1.45F), 0.0F, 1.0F);
            case 3 -> Mth.clamp((float) Math.abs(Math.sin(time * (0.35F + intensity * 2.4F))), 0.0F, 1.0F);
            case 4 -> Mth.clamp(unit(clock ^ ((long) (time / Math.max(1.0F, 5.0F - intensity * 4.0F)) << 12)) * (0.35F + intensity * 1.65F), 0.0F, 1.0F);
            case 5 -> Mth.clamp(((time * (0.05F + intensity * 0.60F)) % 1.0F) * (0.65F + intensity * 0.75F), 0.0F, 1.0F);
            default -> Mth.clamp(unit(clock ^ 0x4E4F4953L) * (0.25F + intensity * 1.45F), 0.0F, 1.0F);
        };
    }

    public static double mutateWeatherCameraX(double original, float partialTick) {
        return mutateWeatherCameraAxis(original, partialTick, 0);
    }

    public static double mutateWeatherCameraY(double original, float partialTick) {
        return mutateWeatherCameraAxis(original, partialTick, 1);
    }

    public static double mutateWeatherCameraZ(double original, float partialTick) {
        return mutateWeatherCameraAxis(original, partialTick, 2);
    }

    public static VertexConsumer vertex(BufferBuilder builder, double x, double y, double z) {
        WeatherContext context = WEATHER_CONTEXT.get();
        if (context == null || context.intensity <= 0.01F) {
            return builder.vertex(x, y, z);
        }

        long cell = Mth.floor(x * 1.75D) * 73428767L ^ Mth.floor(y * 1.15D) * 912931L ^ Mth.floor(z * 1.75D) * 42317861L;
        long hash = mix(context.clock ^ cell);
        double time = context.gameTime + context.partialTick;
        double speed = switch (context.mode) {
            case 0 -> 0.08D + context.intensity * 0.22D;
            case 1 -> -(0.04D + context.intensity * 0.18D);
            case 2 -> 0.40D + context.intensity * 2.60D;
            case 3 -> 0.0D;
            default -> 0.14D + context.intensity * (0.45D + unit(hash ^ 0x535044L) * 1.60D);
        };
        double waveA = Math.sin(time * speed + x * (0.20D + context.intensity * 0.75D) + z * 0.17D + unit(hash) * Mth.TWO_PI);
        double waveB = Math.cos(time * (speed * 1.37D + 0.03D) + z * (0.16D + context.intensity * 0.62D) - x * 0.11D + unit(hash >>> 9) * Mth.TWO_PI);
        double curtainHeight = y - context.cameraY;
        double warp = 0.025D + context.intensity * (0.70D + unit(hash >>> 19) * 1.40D);
        double dx = waveA * warp + signed(hash ^ 0x584D4553L, context.intensity * 0.34D);
        double dz = waveB * warp + signed(hash ^ 0x5A4D4553L, context.intensity * 0.34D);
        double dy = Math.sin(time * (0.05D + context.intensity * 0.32D) + curtainHeight * 0.38D + unit(hash >>> 31) * Mth.TWO_PI) * context.intensity * 0.24D;

        switch (context.mode) {
            case 1 -> {
                dx += curtainHeight * signed(context.clock ^ 0x53484558L, context.intensity * 0.055D);
                dz += curtainHeight * signed(context.clock ^ 0x5348455AL, context.intensity * 0.055D);
            }
            case 2 -> {
                double snap = 0.25D + context.intensity * (0.75D + unit(context.clock >>> 13) * 2.50D);
                x = quantize(x + dx, snap);
                z = quantize(z + dz, snap);
                dx = 0.0D;
                dz = 0.0D;
            }
            case 3 -> {
                dx += signed(context.clock ^ 0x4C414758L, 2.0D + context.intensity * 10.0D);
                dz += signed(context.clock ^ 0x4C41475AL, 2.0D + context.intensity * 10.0D);
            }
            case 4 -> {
                double folded = Math.sin((x + z) * (0.12D + context.intensity * 0.24D) + time * speed) * context.intensity * 2.25D;
                dx += folded;
                dz -= folded;
            }
            case 5 -> {
                dx *= unit(hash ^ 0x44524F58L) < 0.35F + context.intensity * 0.42F ? -1.8D : 0.18D;
                dz *= unit(hash ^ 0x44524F5AL) < 0.35F + context.intensity * 0.42F ? -1.8D : 0.18D;
            }
            default -> {
                dx += Math.sin(y * 0.52D + time * speed) * context.intensity * 0.72D;
                dz += Math.cos(y * 0.47D - time * speed) * context.intensity * 0.72D;
            }
        }

        return builder.vertex(x + dx, y + dy, z + dz);
    }

    public static VertexConsumer uv(VertexConsumer consumer, float u, float v) {
        WeatherContext context = WEATHER_CONTEXT.get();
        if (context == null || context.intensity <= 0.01F) {
            return consumer.uv(u, v);
        }

        float time = context.gameTime + context.partialTick;
        float speed = switch (context.mode) {
            case 0 -> 0.02F + context.intensity * 0.18F;
            case 1 -> -(0.02F + context.intensity * 0.14F);
            case 2 -> 0.35F + context.intensity * 1.85F;
            case 3 -> 0.0F;
            default -> 0.05F + context.intensity * (0.32F + unit(context.clock >>> 25) * 1.20F);
        };
        float warpedU = u + (float) Math.sin(time * speed + v * (2.0F + context.intensity * 9.0F)) * context.intensity * 0.22F;
        float warpedV = v + time * speed;
        if (context.mode == 3 || unit(context.clock ^ 0x53545556L) < context.intensity * 0.24F) {
            float step = 0.03125F + context.intensity * 0.22F;
            warpedU = (float) quantize(warpedU, step);
            warpedV = (float) quantize(warpedV, step);
        }
        return consumer.uv(warpedU, warpedV);
    }

    private static double mutateWeatherCameraAxis(double original, float partialTick, int axis) {
        ClientLevel level = Minecraft.getInstance().level;
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        String targetId = "weather_follow_failure:" + dimension(level);
        float intensity = weatherIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }
        long clock = weatherClock(stack, targetId, level, partialTick);
        float chance = Mth.clamp(0.12F + intensity * 0.68F + stack.instability() * 0.10F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ (0x464F4C4CL + axis)) > chance) {
            return original;
        }

        double time = gameTime(level) + partialTick;
        int mode = Math.floorMod((int) (clock >>> 32), 6);
        double span = axis == 1 ? 0.35D + intensity * 7.5D : 4.0D + intensity * 52.0D;
        double offset = signed(clock ^ (axis * 0x9E3779B97F4A7C15L), span);
        if (mode == 1) {
            double cadence = Math.max(2.0D, 14.0D - intensity * 11.0D);
            offset = signed(clock ^ ((long) Math.floor(time / cadence) << 17) ^ axis, span);
        } else if (mode == 2) {
            offset += Math.sin(time * (0.035D + intensity * 0.24D) + axis * 2.1D) * span * 0.65D;
        } else if (mode == 3) {
            offset = quantize(offset, 1.0D + intensity * 7.0D);
        } else if (mode == 4 && axis != 1) {
            offset += Math.sin(time * (0.45D + intensity * 2.2D)) * (2.0D + intensity * 14.0D);
        }
        return original + offset;
    }

    private static float weatherIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId)
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

    private static double signed(long seed, double amplitude) {
        return (unit(seed) * 2.0D - 1.0D) * amplitude;
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record WeatherContext(
            CorruptionEffectStack stack,
            String targetId,
            float intensity,
            long clock,
            int mode,
            long gameTime,
            float partialTick,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
    }
}
