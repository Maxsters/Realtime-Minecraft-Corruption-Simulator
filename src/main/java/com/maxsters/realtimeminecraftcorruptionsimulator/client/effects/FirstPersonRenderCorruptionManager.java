package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionMutation;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionOperation;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonRenderCorruptionManager {
    private static long lastArmReportMs;

    private FirstPersonRenderCorruptionManager() {
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "first_person_arm:" + event.getArm().name().toLowerCase();
        float intensity = Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.46F
        ) * 0.48F;
        if (intensity <= 0.0035F) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, 0x41524D);
        float side = event.getArm().getOpposite() == event.getArm() ? 1.0F : (event.getArm().name().equals("RIGHT") ? 1.0F : -1.0F);
        float highBoost = stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.80F : 1.0F;
        float x = signed(seed >>> 5, (0.020F + intensity * 0.082F) * highBoost) * side;
        float y = signed(seed >>> 17, (0.014F + intensity * 0.076F) * highBoost);
        float z = signed(seed >>> 29, (0.022F + intensity * 0.110F) * highBoost);
        poseStack.translate(x, y, z);

        float scaleX = stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? firstPersonScale(seed ^ 0x585343414C45L, intensity) : Mth.clamp(1.0F + signed(seed >>> 41, 0.028F + intensity * 0.155F), 0.80F, 1.28F);
        float scaleY = stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? firstPersonScale(seed ^ 0x595343414C45L, intensity) : Mth.clamp(1.0F + signed(seed >>> 23, 0.034F + intensity * 0.180F), 0.78F, 1.30F);
        float scaleZ = stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? firstPersonScale(seed ^ 0x5A5343414C45L, intensity) : Mth.clamp(1.0F + signed(seed >>> 11, 0.026F + intensity * 0.135F), 0.82F, 1.26F);

        List<CorruptionMutation> mutations = stack.mutations(CorruptionSurface.ANIMATION_TIMING, targetId, 5);
        for (CorruptionMutation mutation : mutations) {
            float strength = mutation.strength();
            CorruptionOperation operation = mutation.operation();
            if (operation == CorruptionOperation.DAMPEN) {
                scaleY *= Mth.clamp(1.0F - strength * 0.084F, 0.82F, 1.0F);
            } else if (operation == CorruptionOperation.FOLD || operation == CorruptionOperation.INVERT) {
                scaleX *= 1.0F + strength * 0.072F;
                scaleZ *= 1.0F - strength * 0.056F;
            } else {
                poseStack.translate(mutation.signed(7, 0.030F + intensity * 0.095F), mutation.signed(11, 0.022F + intensity * 0.070F), mutation.signed(13, 0.036F + intensity * 0.110F));
            }
            rotate(poseStack, mutation.seed(), strength * 0.10F, intensity);
        }

        rotate(poseStack, seed, 0.065F + intensity, intensity);
        poseStack.scale(scaleX, scaleY, scaleZ);
        reportArmMutation();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "first_person_hand:" + event.getHand().name().toLowerCase() + ":" + event.getItemStack().getItem();
        float intensity = Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.52F
        ) * 0.52F;
        if (intensity <= 0.008F) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        int animationBucket = Math.round(swing * 1024.0F) ^ (Math.round(equip * 1024.0F) << 11);
        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, 0x48414E44);
        long clock = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId + ":clock", animationBucket);
        float corruptedSwing = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":swing", swing, 0.18F + intensity * 3.20F, -3.0F, 4.0F, 0x5348, clock);
        float corruptedEquip = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":equip", equip, 0.16F + intensity * 2.10F, -2.0F, 3.0F, 0x4551, clock);
        boolean extreme = stack.extreme(CorruptionSurface.ANIMATION_TIMING);
        if (extreme) {
            poseStack.translate(
                    signed(seed >>> 7, 0.85F + intensity * 1.15F) + corruptedSwing * signed(seed ^ 0x5357494EL, 0.36F),
                    signed(seed >>> 19, 0.70F + intensity * 1.35F),
                    signed(seed >>> 31, 0.80F + intensity * 1.00F) + corruptedEquip * signed(seed ^ 0x45515549L, 0.28F)
            );
            rotate(poseStack, seed ^ 0x48414E44524F54L, 1.15F + intensity * 0.90F + Math.abs(corruptedSwing) * 0.18F, intensity);
            poseStack.scale(firstPersonScale(seed ^ 0x48535A58L, intensity), firstPersonScale(seed ^ 0x48535A59L, intensity), firstPersonScale(seed ^ 0x48535A5AL, intensity));
        } else {
            poseStack.translate(
                    signed(seed >>> 7, 0.08F + intensity * 0.55F) + corruptedSwing * signed(seed ^ 0x5357494EL, 0.04F + intensity * 0.20F),
                    signed(seed >>> 19, 0.06F + intensity * 0.42F) + corruptedEquip * signed(seed ^ 0x45515549L, 0.03F + intensity * 0.18F),
                    signed(seed >>> 31, 0.08F + intensity * 0.50F)
            );
            rotate(poseStack, seed ^ 0x48414E44524F54L, 0.18F + intensity * 0.72F + Math.abs(corruptedSwing - swing) * 0.11F, intensity);
            poseStack.scale(
                    Mth.clamp(1.0F + signed(seed ^ 0x5358414EL, intensity * 0.80F) + corruptedSwing * intensity * 0.12F, 0.35F, 1.85F),
                    Mth.clamp(1.0F + signed(seed ^ 0x5359414EL, intensity * 0.70F) + corruptedEquip * intensity * 0.10F, 0.35F, 1.85F),
                    Mth.clamp(1.0F + signed(seed ^ 0x535A414EL, intensity * 0.75F), 0.35F, 1.85F)
            );
        }

        if (extreme || intensity > 0.02F) {
            reportArmMutation();
        }
    }

    private static void rotate(PoseStack poseStack, long seed, float strength, float intensity) {
        float angle = signed(seed >>> 31, 2.2F + intensity * 13.8F) * strength;
        float x = signed(seed >>> 7, 1.0F);
        float y = signed(seed >>> 19, 1.0F);
        float z = signed(seed >>> 43, 1.0F);
        if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 0.001F) {
            y = 1.0F;
        }
        poseStack.mulPose(new Quaternionf(new AxisAngle4f(angle * ((float) Math.PI / 180.0F), x, y, z)));
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static float firstPersonScale(long seed, float intensity) {
        float scale = 0.08F + unit(seed) * (1.0F + intensity * 3.40F);
        return unit(seed ^ 0x494E56455254L) < 0.36F + intensity * 0.16F ? -scale : scale;
    }

    private static void reportArmMutation() {
        long now = System.currentTimeMillis();
        if (now - lastArmReportMs > 1500L) {
            lastArmReportMs = now;
        }
    }
}
