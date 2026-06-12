package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BlockRenderCorruptionHooks {
    private BlockRenderCorruptionHooks() {
    }

    public static boolean beginTesselate(BlockState state, BlockPos pos, PoseStack poseStack) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (state == null || pos == null || poseStack == null || !stack.activeOrExtreme(CorruptionSurface.BLOCK_COLLISION)) {
            return false;
        }

        float intensity = stack.extreme(CorruptionSurface.BLOCK_COLLISION)
                ? 1.0F
                : stack.intensity(CorruptionSurface.BLOCK_COLLISION);
        if (intensity <= 0.01F) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String targetId = "visual_collision_world:" + dimension;
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, 0x424C4F43);
        double blockSpan = stack.extreme(CorruptionSurface.BLOCK_COLLISION)
                ? 20.0D
                : 0.35D + Math.pow(intensity, 1.35D) * 19.65D;
        double phase = stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":phase", stack.bucket(CorruptionSurface.BLOCK_COLLISION, targetId, 0x50484153, 96));
        double dynamic = dynamicWave(seed, phase, intensity);
        double x = snappedSigned(seed ^ 0x585348494654L, blockSpan) * dynamic;
        double y = snappedSigned(seed ^ 0x595348494654L, blockSpan * 0.72D) * dynamicWave(seed ^ 0x59444E4DL, phase, intensity);
        double z = snappedSigned(seed ^ 0x5A5348494654L, blockSpan) * dynamicWave(seed ^ 0x5A444E4DL, phase, intensity);
        if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 0.03125D) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        return true;
    }

    public static void endTesselate(PoseStack poseStack, boolean applied) {
        if (applied && poseStack != null) {
            poseStack.popPose();
        }
    }

    private static double snappedSigned(long value, double amplitude) {
        double raw = (unit(value) * 2.0D - 1.0D) * amplitude;
        double snap = 0.25D;
        return Mth.clamp(Math.rint(raw / snap) * snap, -20.0D, 20.0D);
    }

    private static double dynamicWave(long seed, double phase, float intensity) {
        int mode = Math.floorMod((int) (seed >>> 33), 6);
        double value = switch (mode) {
            case 0 -> Math.sin(phase * Math.PI * 2.0D);
            case 1 -> phase < 0.50D ? phase * 2.0D : (1.0D - phase) * 2.0D;
            case 2 -> phase < 0.72D ? 0.0D : (phase - 0.72D) / 0.28D;
            case 3 -> Math.sin(phase * Math.PI * 4.0D + unit(seed >>> 17) * Math.PI);
            case 4 -> Math.rint(Math.sin(phase * Math.PI * 2.0D) * (2.0D + intensity * 6.0D)) / (2.0D + intensity * 6.0D);
            default -> 0.35D + unit(seed ^ ((long) (phase * 256.0D) << 24)) * 0.95D;
        };
        return Mth.clamp(value, -1.0D, 1.0D);
    }

    private static double unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }
}
