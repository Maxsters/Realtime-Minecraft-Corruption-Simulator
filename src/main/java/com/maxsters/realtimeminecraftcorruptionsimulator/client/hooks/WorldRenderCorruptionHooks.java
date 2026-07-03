package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class WorldRenderCorruptionHooks {
    private WorldRenderCorruptionHooks() {
    }

    public static boolean shouldSkipChunkLayer(RenderType renderType) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String layer = stableChunkLayerId(renderType);
        String targetId = "chunk_layer_failure:" + dimension + ":" + layer;
        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return false;
        }

        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x43484E4B);
        boolean globalChunkBlank = stack.level() >= 88
                && unit(hash ^ 0x414C4C4CL) < 0.14F + intensity * 0.48F;
        float chance = globalChunkBlank
                ? 0.98F
                : Mth.clamp(0.02F + intensity * 0.46F + stack.instability() * 0.08F, 0.0F, 0.78F);
        return unit(hash ^ 0x534B4950L) < chance;
    }

    public static double mutateChunkLayerCameraAxis(RenderType renderType, double original, int axis) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String targetId = "chunk_layer_origin:" + dimension;
        float intensity = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : Mth.clamp(stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return original;
        }

        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x4F524947);
        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : Mth.clamp(0.04F + intensity * 0.48F + stack.instability() * 0.10F, 0.0F, 0.86F);
        if (unit(hash ^ 0x454E4142L) > chance) {
            return original;
        }

        double span = axis == 1 ? 1.0D + intensity * 18.0D : 4.0D + intensity * 96.0D;
        double component = signed(hash ^ ((long) axis * 0x9E3779B97F4A7C15L), span);
        double phase = repeatedOffsetPhase(time, hash, intensity);
        return original + repeatedOffset(component, phase, axis);
    }

    public static Vec3 cameraRenderOffset(String targetId, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        CorruptionSurface surface = CorruptionSurface.MODEL_GEOMETRY;
        if (!stack.activeOrExtreme(surface)) {
            return Vec3.ZERO;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return Vec3.ZERO;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();

        String address = "camera_render_offset:" + currentDimensionId(minecraft) + ":" + targetId;
        float intensity = stack.extreme(surface)
                ? 1.0F
                : Mth.clamp(stack.intensity(surface) + stack.instability() * 0.10F, 0.0F, 1.0F);
        if (intensity <= 0.012F) {
            return Vec3.ZERO;
        }

        long seed = stack.stableLong(surface, address, salt);
        long seedProfile = stack.stableLong(surface, "camera_render_offset_seed_profile:" + targetId, salt ^ 0x53454544);
        Vec3 cameraPosition = seededCameraPosition(camera.getPosition(), seedProfile, intensity);
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double seedMagnitude = 0.70D + unit(seedProfile ^ 0x4D41474EL) * 0.60D;
        double span = stack.extreme(surface)
                ? (1.20D + unit(seed ^ 0x5350414EL) * 4.93D) * seedMagnitude
                : (0.067D + Math.pow(intensity, 1.06D) * (0.80D + unit(seed ^ 0x5350414EL) * 3.20D)) * seedMagnitude;
        double x = cameraDrivenAxis(cameraPosition, look, up, left, seed ^ Long.rotateLeft(seedProfile, 11) ^ 0x58504F53L, span, intensity);
        double y = cameraDrivenAxis(cameraPosition, look, up, left, seed ^ Long.rotateLeft(seedProfile, 29) ^ 0x59504F53L, span * 0.72D, intensity);
        double z = cameraDrivenAxis(cameraPosition, look, up, left, seed ^ Long.rotateLeft(seedProfile, 47) ^ 0x5A504F53L, span, intensity);
        return new Vec3(
                Mth.clamp(x, -5.50D, 5.50D),
                Mth.clamp(y, -4.00D, 4.00D),
                Mth.clamp(z, -5.50D, 5.50D)
        );
    }

    private static double repeatedOffsetPhase(long time, long seed, float intensity) {
        double fastPeriod = Mth.lerp(intensity, 180.0D, 32.0D);
        double slowPeriod = Mth.lerp(intensity, 420.0D, 112.0D);
        double basePeriod = fastPeriod + unit(seed ^ 0x50455249L) * Math.max(1.0D, slowPeriod - fastPeriod);
        double speedBoost = 1.0D + intensity * 4.0D;
        long period = Math.max(4L, Math.round(basePeriod / speedBoost));
        long phaseOffset = Math.floorMod((long) Math.floor(unit(seed ^ 0x50484153L) * period), period);
        return Math.floorMod(time + phaseOffset, period) / (double) period;
    }

    private static double repeatedOffset(double component, double phase, int axis) {
        double verticalDamping = axis == 1 ? 0.60D : 1.0D;
        return component * phase * verticalDamping;
    }

    private static Vec3 seededCameraPosition(Vec3 cameraPosition, long seedProfile, float intensity) {
        double range = 48.0D + intensity * 192.0D;
        return cameraPosition.add(
                signed(seedProfile ^ 0x5853454544L, range),
                signed(seedProfile ^ 0x5953454544L, range * 0.62D),
                signed(seedProfile ^ 0x5A53454544L, range)
        );
    }

    private static double cameraDrivenAxis(Vec3 cameraPosition, Vector3f look, Vector3f up, Vector3f left, long seed, double span, float intensity) {
        double lookProjection = cameraPosition.x * look.x() + cameraPosition.y * look.y() + cameraPosition.z * look.z();
        double upProjection = cameraPosition.x * up.x() + cameraPosition.y * up.y() + cameraPosition.z * up.z();
        double leftProjection = cameraPosition.x * left.x() + cameraPosition.y * left.y() + cameraPosition.z * left.z();
        double orientationSignal = look.x() * 11.0D + look.y() * 7.0D + look.z() * 13.0D
                + up.x() * 5.0D + up.y() * 17.0D + left.z() * 3.0D;
        double source = switch (Math.floorMod((int) (seed >>> 35), 8)) {
            case 0 -> lookProjection + orientationSignal;
            case 1 -> upProjection - orientationSignal * 0.55D;
            case 2 -> leftProjection + orientationSignal * 0.75D;
            case 3 -> cameraPosition.x * look.z() - cameraPosition.z * look.x() + orientationSignal;
            case 4 -> cameraPosition.y * up.y() + leftProjection * 0.50D;
            case 5 -> lookProjection + leftProjection * 0.40D - upProjection * 0.20D;
            case 6 -> cameraPosition.x + cameraPosition.y * look.y() - cameraPosition.z * left.x();
            default -> cameraPosition.x * look.x() + cameraPosition.y * up.y() + cameraPosition.z * left.z() + orientationSignal;
        };
        double period = 1.0D + unit(seed ^ 0x50455249L) * (4.0D + intensity * 24.0D);
        double folded = source / period;
        double saw = (folded - Math.floor(folded)) * 2.0D - 1.0D;
        double waveFrequency = 0.075D + unit(seed ^ 0x46524551L) * (0.25D + intensity * 0.55D);
        double wave = Math.sin(source * waveFrequency + orientationSignal * 0.15D);
        double snap = 0.125D + unit(seed ^ 0x534E4150L) * (0.25D + intensity * 2.75D);
        double value = (saw * 0.80D + wave * 0.55D) * span;
        return Math.rint(value / snap) * snap;
    }

    private static String currentDimensionId(Minecraft minecraft) {
        return minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
    }

    private static String stableChunkLayerId(RenderType renderType) {
        if (renderType == null) {
            return "unknown";
        }

        // RenderType#toString includes CompositeState details. Those are useful
        // for debugging but are not a stable corruption address across clients.
        // Forge gives chunk buffer layers fixed IDs in vanilla render order.
        return switch (renderType.getChunkLayerId()) {
            case 0 -> "solid";
            case 1 -> "cutout_mipped";
            case 2 -> "cutout";
            case 3 -> "translucent";
            case 4 -> "tripwire";
            default -> "non_chunk";
        };
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }
}
