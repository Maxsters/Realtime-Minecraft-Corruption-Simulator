package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

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

        long profileSeed = weatherProfileSeed(stack, targetId, level);
        long clock = weatherClock(stack, targetId, level, partialTick, profileSeed);
        WEATHER_CONTEXT.set(new WeatherContext(
                stack,
                targetId,
                intensity,
                profileSeed,
                clock,
                weatherMode(profileSeed ^ 0x4355525441494E53L, 9),
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
        long profileSeed = weatherProfileSeed(stack, targetId, level);
        long clock = weatherClock(stack, targetId, level, partialTick, profileSeed);
        int mode = weatherMode(profileSeed ^ 0x534B49504F564552L, 7);
        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 0.94F
                : Mth.clamp(0.04F + intensity * (0.16F + unit(profileSeed ^ 0x42494153L) * 0.54F) + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (unit(profileSeed ^ 0x47415445L) > chance) {
            return false;
        }

        long time = gameTime(level);
        return switch (mode) {
            case 0 -> false;
            case 1 -> seededPulse(profileSeed ^ 0x464C49434B4552L, time, partialTick, intensity);
            case 2 -> !seededPulse(profileSeed ^ 0x5745414B44555459L, time, partialTick, intensity * 0.65F);
            case 3 -> unit(clock ^ 0x44524F504F5554L) < intensity * (0.06F + unit(profileSeed ^ 0x44555459L) * 0.24F);
            case 4 -> unit(profileSeed ^ (time / seededCadence(profileSeed ^ 0x434144454E4345L, intensity)) ^ 0x4255434B4554L) < 0.18F + intensity * 0.54F;
            case 5 -> stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(profileSeed ^ 0x535455434B4F4646L) < 0.36F;
            default -> unit(clock ^ 0x44524F50L) < intensity * (0.03F + unit(profileSeed ^ 0x56415249L) * 0.18F);
        };
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
        long profileSeed = weatherProfileSeed(stack, targetId, level);
        long clock = weatherClock(stack, targetId, level, partialTick, profileSeed);
        float chance = Mth.clamp(0.06F + intensity * 0.74F + stack.instability() * 0.10F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ 0x454E4142L) > chance) {
            return original;
        }

        int mode = weatherMode(profileSeed ^ 0x5241494E4C455645L, 8);
        float time = gameTime(level) + partialTick;
        float phase = unit(profileSeed ^ 0x5048415345L) * Mth.TWO_PI;
        float cadence = (float) seededCadence(profileSeed ^ 0x52434144454E4345L, intensity);
        return switch (mode) {
            case 0 -> 1.0F;
            case 1 -> seededPulse(profileSeed ^ 0x52414E44504C53L, gameTime(level), partialTick, intensity) ? 1.0F : 0.0F;
            case 2 -> Mth.clamp(original + signed(clock ^ 0x5241494EL, intensity * 2.25F), 0.0F, 1.0F);
            case 3 -> Mth.clamp((float) Math.abs(Math.sin(time * (0.10F + unit(profileSeed ^ 0x5350454544L) * (0.55F + intensity * 2.75F)) + phase)), 0.0F, 1.0F);
            case 4 -> Mth.clamp(unit(profileSeed ^ ((long) Math.floor((time + phase) / Math.max(1.0F, cadence)) << 12) ^ 0x4E4F495345L) * (0.35F + intensity * 2.45F), 0.0F, 1.0F);
            case 5 -> Mth.clamp(cycle(time + phase, cadence) * (0.65F + intensity * 1.35F), 0.0F, 1.0F);
            case 6 -> Mth.clamp(original * (0.04F + unit(profileSeed ^ 0x5343414C45L) * (0.50F + intensity * 6.50F)), 0.0F, 1.0F);
            default -> Mth.clamp(unit(clock ^ profileSeed ^ 0x4E4F4953L) * (0.25F + intensity * 2.20F), 0.0F, 1.0F);
        };
    }

    public static float mutateRainParticleLevel(ClientLevel level, float original, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        String targetId = "weather_particle_density:" + dimension(level);
        float intensity = weatherIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }

        long profileSeed = weatherProfileSeed(stack, targetId, level);
        long clock = weatherClock(stack, targetId, level, partialTick, profileSeed);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ 0x50415254L) > 0.08F + intensity * 0.56F) {
            return original;
        }
        int mode = weatherMode(profileSeed ^ 0x5041525444434C4FL, 6);
        return switch (mode) {
            case 0 -> 1.0F;
            case 1 -> 0.0F;
            case 2 -> Mth.clamp(original + signed(clock ^ 0x44454E53L, intensity * 1.65F), 0.0F, 1.0F);
            case 3 -> Mth.clamp(unit(profileSeed ^ (gameTime(level) / seededCadence(profileSeed ^ 0x50434144454E4345L, intensity)) ^ 0x4E4F4953L) * (0.45F + intensity * 2.0F), 0.0F, 1.0F);
            case 4 -> seededPulse(profileSeed ^ 0x5044555459L, gameTime(level), partialTick, intensity) ? Mth.clamp(0.55F + intensity * 1.45F, 0.0F, 1.0F) : 0.0F;
            default -> Mth.clamp(original * (0.04F + unit(clock ^ 0x5343414CL) * (4.0F + intensity * 6.0F)), 0.0F, 1.0F);
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
        double warp = 0.025D + context.intensity * (1.35D + unit(hash >>> 19) * 3.40D);
        double dx = waveA * warp + signed(hash ^ 0x584D4553L, context.intensity * 0.34D);
        double dz = waveB * warp + signed(hash ^ 0x5A4D4553L, context.intensity * 0.34D);
        double dy = Math.sin(time * (0.05D + context.intensity * 0.32D) + curtainHeight * 0.38D + unit(hash >>> 31) * Mth.TWO_PI) * context.intensity * 0.72D;

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
                dx += signed(context.clock ^ 0x4C414758L, 2.0D + context.intensity * 26.0D);
                dz += signed(context.clock ^ 0x4C41475AL, 2.0D + context.intensity * 26.0D);
                dy += signed(context.clock ^ 0x4C414759L, context.intensity * 10.0D);
            }
            case 4 -> {
                double folded = Math.sin((x + z) * (0.12D + context.intensity * 0.24D) + time * speed) * context.intensity * 7.25D;
                dx += folded;
                dz -= folded;
            }
            case 5 -> {
                dx *= unit(hash ^ 0x44524F58L) < 0.35F + context.intensity * 0.42F ? -1.8D : 0.18D;
                dz *= unit(hash ^ 0x44524F5AL) < 0.35F + context.intensity * 0.42F ? -1.8D : 0.18D;
            }
            default -> {
                dx += Math.sin(y * 0.52D + time * speed) * context.intensity * 2.40D;
                dz += Math.cos(y * 0.47D - time * speed) * context.intensity * 2.40D;
            }
        }

        if (context.stack.extreme(CorruptionSurface.WORLD_RENDER) || unit(hash ^ 0x464C4154L) < context.intensity * 0.18F) {
            y = (y - context.cameraY) * (0.01D + unit(hash ^ 0x504C414EL) * 0.08D) + context.cameraY;
        }
        if (unit(hash ^ 0x534E4150L) < context.intensity * 0.22F) {
            double snap = 0.125D + unit(hash ^ 0x534E4151L) * (0.5D + context.intensity * 4.0D);
            x = quantize(x, snap);
            z = quantize(z, snap);
        }

        return builder.vertex(x + dx, y + dy, z + dz);
    }

    public static VertexConsumer uv(VertexConsumer consumer, float u, float v) {
        DirectTextureCorruptionHooks.Uv directUv = DirectTextureCorruptionHooks.rawUv(u, v);
        u = directUv.u();
        v = directUv.v();

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

    public static VertexConsumer color(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        WeatherContext context = WEATHER_CONTEXT.get();
        if (context == null || context.intensity <= 0.01F) {
            return consumer.color(red, green, blue, alpha);
        }

        long hash = mix(context.clock ^ 0x434F4C4FL ^ context.targetId.hashCode());
        float colorSpan = 0.16F + context.intensity * 1.18F;
        float mutatedRed = Mth.clamp(red + signed(hash ^ 0x524544L, colorSpan), 0.0F, 1.85F);
        float mutatedGreen = Mth.clamp(green + signed(hash ^ 0x475245454EL, colorSpan), 0.0F, 1.85F);
        float mutatedBlue = Mth.clamp(blue + signed(hash ^ 0x424C5545L, colorSpan), 0.0F, 1.85F);
        float mutatedAlpha = Mth.clamp(alpha * (0.05F + unit(hash ^ 0x414C5048L) * (1.4F + context.intensity * 2.3F)), 0.0F, 1.0F);
        if (context.stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(hash ^ 0x57524954L) < 0.38F) {
            mutatedRed = unit(hash ^ 0x455852L) * 1.85F;
            mutatedGreen = unit(hash ^ 0x455847L) * 1.85F;
            mutatedBlue = unit(hash ^ 0x455842L) * 1.85F;
        }
        return consumer.color(mutatedRed, mutatedGreen, mutatedBlue, mutatedAlpha);
    }

    public static VertexConsumer uv2(VertexConsumer consumer, int packedLight) {
        WeatherContext context = WEATHER_CONTEXT.get();
        if (context == null || context.intensity <= 0.01F) {
            return consumer.uv2(packedLight);
        }

        int block = packedLight & 0xFFFF;
        int sky = packedLight >>> 16 & 0xFFFF;
        long hash = mix(context.clock ^ 0x4C494748L ^ packedLight);
        int mutatedBlock = mutateLightComponent(block, hash ^ 0x424C4B, context);
        int mutatedSky = mutateLightComponent(sky, hash ^ 0x534B59, context);
        return consumer.uv2((mutatedSky << 16) | mutatedBlock);
    }

    public static VertexConsumer uv2(VertexConsumer consumer, int blockLight, int skyLight) {
        WeatherContext context = WEATHER_CONTEXT.get();
        if (context == null || context.intensity <= 0.01F) {
            return consumer.uv2(blockLight, skyLight);
        }

        long hash = mix(context.clock ^ 0x4C494748L ^ (blockLight << 8) ^ skyLight);
        return consumer.uv2(
                mutateLightComponent(blockLight, hash ^ 0x424C4B, context),
                mutateLightComponent(skyLight, hash ^ 0x534B59, context)
        );
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
        long profileSeed = weatherProfileSeed(stack, targetId, level);
        long clock = weatherClock(stack, targetId, level, partialTick, profileSeed);
        float chance = Mth.clamp(0.12F + intensity * 0.68F + stack.instability() * 0.10F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ (0x464F4C4CL + axis)) > chance) {
            return original;
        }

        double time = gameTime(level) + partialTick;
        int mode = weatherMode(profileSeed ^ (axis * 0x41584953L) ^ 0x464F4C4C4F57L, 7);
        double span = axis == 1 ? 0.35D + intensity * 7.5D : 4.0D + intensity * 52.0D;
        double offset = signed(clock ^ (axis * 0x9E3779B97F4A7C15L), span);
        if (mode == 1) {
            double cadence = seededCadence(profileSeed ^ 0x43414D434144L ^ axis, intensity);
            offset = signed(profileSeed ^ ((long) Math.floor((time + unit(profileSeed ^ axis) * cadence) / cadence) << 17) ^ axis, span);
        } else if (mode == 2) {
            offset += Math.sin(time * (0.015D + unit(profileSeed ^ 0x5350454544L ^ axis) * (0.08D + intensity * 0.34D)) + axis * 2.1D + unit(profileSeed ^ 0x5048415345L) * Mth.TWO_PI) * span * 0.65D;
        } else if (mode == 3) {
            offset = quantize(offset, 0.5D + unit(profileSeed ^ 0x534E4150L ^ axis) * (1.0D + intensity * 9.0D));
        } else if (mode == 4 && axis != 1) {
            offset += Math.sin(time * (0.16D + unit(profileSeed ^ 0x4A495454L) * (0.55D + intensity * 2.6D))) * (2.0D + intensity * 14.0D);
        } else if (mode == 5) {
            offset *= seededPulse(profileSeed ^ 0x43414D50554C53L ^ axis, gameTime(level), partialTick, intensity) ? 1.0D : 0.0D;
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

    private static long weatherProfileSeed(CorruptionEffectStack stack, String targetId, ClientLevel level) {
        return stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId + ":profile", dimension(level).hashCode() ^ 0x57454154);
    }

    private static long weatherClock(CorruptionEffectStack stack, String targetId, ClientLevel level, float partialTick, long profileSeed) {
        long time = gameTime(level);
        int shift = 14 + Math.floorMod((int) (profileSeed >>> 9), 9);
        return profileSeed
                ^ stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x57454154)
                ^ (time << shift)
                ^ ((long) Float.floatToIntBits(partialTick) << 1);
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

    private static int weatherMode(long seed, int modes) {
        return Math.floorMod((int) (mix(seed) ^ (mix(seed) >>> 32)), Math.max(1, modes));
    }

    private static long seededCadence(long seed, float intensity) {
        double max = Math.max(2.0D, 34.0D - intensity * 24.0D);
        return Math.max(2L, Math.round(2.0D + unit(seed) * max));
    }

    private static boolean seededPulse(long seed, long time, float partialTick, float intensity) {
        double cadence = seededCadence(seed, intensity);
        double position = cycle(time + partialTick + unit(seed ^ 0x5048415345L) * cadence, cadence);
        double duty = Mth.clamp(0.08D + unit(seed ^ 0x44555459L) * (0.78D - intensity * 0.18D), 0.04D, 0.92D);
        return position < duty;
    }

    private static float cycle(double value, double period) {
        if (period <= 0.0D) {
            return 0.0F;
        }
        double divided = value / period;
        return (float) (divided - Math.floor(divided));
    }

    private static int mutateLightComponent(int original, long seed, WeatherContext context) {
        int mode = Math.floorMod((int) (seed >>> 29), 6);
        int value = switch (mode) {
            case 0 -> 240;
            case 1 -> 0;
            case 2 -> Math.round(original * (0.05F + unit(seed ^ 0x5343414CL) * (2.8F + context.intensity * 4.0F)));
            case 3 -> Math.round(unit(seed ^ 0x4E4F4953L) * 240.0F);
            case 4 -> Math.round(240.0F - original * (0.25F + context.intensity * 0.75F));
            default -> Math.round(original + signed(seed ^ 0x4F464653L, 80.0F + context.intensity * 240.0F));
        };
        return Mth.clamp(value, 0, 240);
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
            long profileSeed,
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
