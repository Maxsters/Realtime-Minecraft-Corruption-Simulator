package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CameraRenderCorruptionHooks {
    private CameraRenderCorruptionHooks() {
    }

    public static void mutateViewBob(PoseStack poseStack, float partialTick, boolean hurtBob) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (poseStack == null || !stack.activeOrExtreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!cameraReady(minecraft)) {
            return;
        }
        String targetId = cameraTargetId(minecraft, hurtBob ? "hurt_bob" : "view_bob");
        float intensity = cameraIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return;
        }

        LocalPlayer player = minecraft.player;
        double motion = cameraMotionSignal(player);
        long time = player == null ? 0L : player.level().getGameTime();
        long seed = stack.stableLong(CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x424F4256) ^ (time << 12) ^ Float.floatToIntBits(partialTick);
        float chance = stack.extreme(CorruptionSurface.CAMERA_TRANSFORM)
                ? 1.0F
                : Mth.clamp(0.07F + intensity * 0.52F + (float) motion * 0.26F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (unit(seed ^ 0x454E4142L) > chance) {
            return;
        }

        double span = (hurtBob ? 0.04D : 0.025D) + intensity * (hurtBob ? 0.58D : 0.34D) + motion * intensity * 0.22D;
        double x = signed(seed ^ 0x584F4646L, span);
        double y = signed(seed ^ 0x594F4646L, span * 0.86D);
        double z = signed(seed ^ 0x5A4F4646L, span * 1.25D);
        if (unit(seed ^ 0x5155414EL) < 0.14F + intensity * 0.30F) {
            double step = 0.025D + intensity * 0.28D;
            x = quantize(x, step);
            y = quantize(y, step);
            z = quantize(z, step);
        }

        poseStack.translate(x, y, z);
        float roll = signed(seed ^ 0x524F4C4CL, (hurtBob ? 16.0F : 7.0F) + intensity * (hurtBob ? 150.0F : 88.0F));
        float pitch = signed(seed ^ 0x50495443L, 3.0F + intensity * 46.0F);
        float yaw = signed(seed ^ 0x59415721L, 3.0F + intensity * 58.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

        if (unit(seed ^ 0x5343414CL) < 0.10F + intensity * 0.24F) {
            float xScale = Mth.clamp(1.0F + signed(seed ^ 0x5853434CL, intensity * 1.6F), 0.05F, 3.4F);
            float yScale = Mth.clamp(1.0F + signed(seed ^ 0x5953434CL, intensity * 1.6F), 0.05F, 3.4F);
            float zScale = Mth.clamp(1.0F + signed(seed ^ 0x5A53434CL, intensity * 1.1F), 0.05F, 2.6F);
            poseStack.scale(xScale, yScale, zScale);
        }
    }

    public static boolean cameraReady(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.player.tickCount > 40;
    }

    private static float cameraIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.CAMERA_TRANSFORM) ? 1.0F : stack.intensity(CorruptionSurface.CAMERA_TRANSFORM),
                stack.targetIntensity(CorruptionSurface.CAMERA_TRANSFORM, targetId)
        ), 0.0F, 1.0F);
    }

    private static String cameraTargetId(Minecraft minecraft, String feature) {
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String camera = minecraft.options == null ? "unknown" : minecraft.options.getCameraType().name();
        LocalPlayer player = minecraft.player;
        String state = player == null
                ? "no_player"
                : player.isFallFlying() ? "fall_flying"
                : player.isSwimming() ? "swimming"
                : player.isInWater() ? "water"
                : player.onGround() ? "ground"
                : "air";
        return "camera:" + feature + ":" + dimension + ":" + camera + ":" + state;
    }

    private static double cameraMotionSignal(LocalPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        double horizontal = player.getDeltaMovement().horizontalDistance();
        double vertical = Math.abs(player.getDeltaMovement().y) * 0.45D;
        double sprint = player.isSprinting() ? 0.22D : 0.0D;
        double fall = player.onGround() ? 0.0D : 0.18D;
        return Mth.clamp(horizontal * 8.0D + vertical + sprint + fall, 0.0D, 1.0D);
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
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
