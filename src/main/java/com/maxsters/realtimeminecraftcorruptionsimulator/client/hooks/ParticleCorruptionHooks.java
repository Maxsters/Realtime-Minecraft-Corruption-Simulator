package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ParticleCorruptionHooks {
    private ParticleCorruptionHooks() {
    }

    public static float mutateGravity(Particle particle, float originalGravity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "gravity");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                && !stack.activeOrExtreme(CorruptionSurface.ENTITY_KINEMATICS)
                && !stack.activeOrExtreme(CorruptionSurface.TICK_SPEED)) {
            return originalGravity;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return originalGravity;
        }
        long clock = clock(stack, targetId, particle);
        float chance = Mth.clamp(0.08F + intensity * 0.80F + stack.instability() * 0.10F, 0.0F, 0.96F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && stack.unit(CorruptionSurface.WORLD_RENDER, targetId, (int) clock) > chance) {
            return originalGravity;
        }

        float span = stack.extreme(CorruptionSurface.WORLD_RENDER) ? 10.0F : 4.0F + intensity * 8.0F;
        float mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, originalGravity, span, -24.0F, 24.0F, 0x47524156, clock);
        if (unit(clock ^ 0x55505744L) < 0.18F + intensity * 0.42F) {
            mutated = -(2.0F + unit(clock ^ 0x55504641L) * (stack.extreme(CorruptionSurface.WORLD_RENDER) ? 42.0F : 18.0F));
        }
        return mutated;
    }

    public static double mutateVerticalVelocity(Particle particle, double originalVelocity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "vertical_velocity");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                && !stack.activeOrExtreme(CorruptionSurface.ENTITY_KINEMATICS)) {
            return originalVelocity;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return originalVelocity;
        }
        long clock = clock(stack, targetId, particle);
        float chance = Mth.clamp(0.04F + intensity * 0.58F + stack.instability() * 0.08F, 0.0F, 0.88F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ 0x5943484EL) > chance) {
            return originalVelocity;
        }

        double mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, originalVelocity, 0.08D + intensity * 1.80D, -12.0D, 16.0D, 0x5944, clock);
        if (unit(clock ^ 0x4C41554EL) < 0.10F + intensity * 0.34F) {
            mutated += 0.55D + unit(clock ^ 0x55505041L) * (stack.extreme(CorruptionSurface.WORLD_RENDER) ? 20.0D : 8.0D);
        }
        return Mth.clamp(mutated, -20.0D, 24.0D);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                Math.max(
                        (stack.extreme(CorruptionSurface.ENTITY_KINEMATICS) ? 1.0F : stack.intensity(CorruptionSurface.ENTITY_KINEMATICS)) * 0.72F,
                        (stack.extreme(CorruptionSurface.TICK_SPEED) ? 1.0F : stack.intensity(CorruptionSurface.TICK_SPEED)) * 0.42F
                )
        ), 0.0F, 1.0F);
    }

    private static String targetId(Particle particle, String feature) {
        return "particle:" + feature + ":" + (particle == null ? "unknown" : particle.getClass().getName());
    }

    private static long clock(CorruptionEffectStack stack, String targetId, Particle particle) {
        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        int identity = particle == null ? 0 : System.identityHashCode(particle);
        return stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, identity ^ 0x50415254) ^ (time << 16);
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
