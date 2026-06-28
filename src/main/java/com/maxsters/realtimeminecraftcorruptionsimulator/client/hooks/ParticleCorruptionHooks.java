package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ParticleCorruptionHooks {
    private static final int MIN_PARTICLE_BUDGET = 4_500;
    private static final int MAX_PARTICLE_BUDGET = 8_500;
    private static long budgetTick = Long.MIN_VALUE;
    private static int particlesSeenThisTick;

    private ParticleCorruptionHooks() {
    }

    public static boolean shouldCullParticleForBudget(Particle particle) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = intensity(stack, targetId(particle, "budget"));
        if (intensity <= 0.01F) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (time != budgetTick) {
            budgetTick = time;
            particlesSeenThisTick = 0;
        }

        int seen = ++particlesSeenThisTick;
        int budget = particleBudget(intensity);
        if (seen <= budget) {
            return false;
        }

        float overflow = (seen - budget) / Math.max(1.0F, budget * 0.30F);
        if (overflow >= 1.0F) {
            return true;
        }

        int stableParticle = particleSalt(particle);
        long sample = stack.stableLong(CorruptionSurface.WORLD_RENDER, "particle_budget", stableParticle ^ seen ^ (int) time);
        return unit(sample) < Mth.clamp(0.20F + overflow * 0.72F, 0.0F, 0.95F);
    }

    public static boolean shouldProcessParticle(Particle particle) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "state");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        int stableParticle = particleSalt(particle);
        int cadence = stack.extreme(CorruptionSurface.WORLD_RENDER) ? 2 : intensity > 0.68F ? 3 : 5;
        if (Math.floorMod((int) time + stableParticle, cadence) != 0) {
            return false;
        }

        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 0.32F
                : Mth.clamp(0.025F + intensity * 0.16F + stack.instability() * 0.04F, 0.0F, 0.22F);
        long sample = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, stableParticle ^ ((int) (time / cadence) * 0x1F123BB5));
        return unit(sample) < chance;
    }

    public static ParticleState mutateParticle(Particle particle, ParticleState original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "state");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }

        long clock = clock(stack, targetId, particle);
        boolean extreme = stack.extreme(CorruptionSurface.WORLD_RENDER);
        Vec3 velocity = original.velocity();
        Vec3 position = original.position();
        float gravity = original.gravity();
        float width = original.width();
        float height = original.height();
        float quadSize = original.quadSize();
        float red = original.red();
        float green = original.green();
        float blue = original.blue();
        float alpha = original.alpha();
        float roll = original.roll();
        float friction = original.friction();
        int lifetime = original.lifetime();
        boolean hasPhysics = original.hasPhysics();
        boolean speedUpWhenBlocked = original.speedUpWhenBlocked();
        boolean changed = false;

        float velocityChance = Mth.clamp(0.12F + intensity * 0.44F + stack.instability() * 0.08F, 0.0F, extreme ? 0.74F : 0.58F);
        if (unit(clock ^ 0x56454C4FL) < velocityChance) {
            double maxVelocity = extreme ? 10.0D : 6.0D;
            velocity = CorruptionValueMutator.mutateVector(stack, CorruptionSurface.WORLD_RENDER, targetId + ":velocity", velocity, 0.05D + intensity * 0.75D, maxVelocity, 0x50415254, clock);
            int mode = Math.floorMod((int) (clock >>> 28), 8);
            velocity = switch (mode) {
                case 0 -> new Vec3(velocity.z, velocity.y, -velocity.x);
                case 1 -> new Vec3(-velocity.x, -velocity.y * (0.20D + intensity * 0.85D), -velocity.z);
                case 2 -> velocity.add(axisImpulse(clock, intensity, extreme ? 4.0D : 2.2D));
                case 3 -> new Vec3(velocity.x * signedScale(clock ^ 0x58415343L, intensity), velocity.y * signedScale(clock ^ 0x59415343L, intensity), velocity.z * signedScale(clock ^ 0x5A415343L, intensity));
                case 4 -> velocity.normalize().scale(0.02D + unit(clock ^ 0x53504544L) * maxVelocity);
                case 5 -> new Vec3(velocity.y, velocity.z, velocity.x).scale(0.20D + intensity * 1.15D);
                case 6 -> velocity.scale(unit(clock ^ 0x53544F50L) < 0.50F ? 0.0D : -1.0D - intensity * 0.85D);
                default -> velocity;
            };
            velocity = clampVector(velocity, maxVelocity);
            changed = true;
        }

        float gravityChance = Mth.clamp(0.08F + intensity * 0.40F + stack.instability() * 0.06F, 0.0F, extreme ? 0.72F : 0.52F);
        if (unit(clock ^ 0x47524156L) < gravityChance) {
            float span = extreme ? 6.0F : 1.2F + intensity * 3.6F;
            gravity = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":gravity", gravity, span, -12.0F, 12.0F, 0x4752, clock);
            if (unit(clock ^ 0x474D4F44L) < 0.08F + intensity * 0.22F) {
                gravity = signedUnit(clock ^ 0x47444952L) * (0.35F + unit(clock ^ 0x4753504EL) * (extreme ? 10.0F : 5.0F));
            }
            changed = true;
        }

        if (unit(clock ^ 0x504F534CL) < 0.015F + intensity * 0.08F) {
            Vec3 offset = axisImpulse(clock ^ 0x504F5349L, intensity, extreme ? 0.75D : 0.35D);
            position = position.add(offset);
            changed = true;
        }

        if (unit(clock ^ 0x53495A45L) < 0.04F + intensity * 0.20F) {
            width = mutateParticleSize(stack, targetId + ":width", width, clock ^ 0x57494454L, intensity, extreme);
            height = mutateParticleSize(stack, targetId + ":height", height, clock ^ 0x48454947L, intensity, extreme);
            if (!Float.isNaN(quadSize)) {
                quadSize = mutateParticleSize(stack, targetId + ":quad", quadSize, clock ^ 0x51554144L, intensity, extreme);
            }
            changed = true;
        }

        if (unit(clock ^ 0x434F4C52L) < 0.035F + intensity * 0.24F) {
            red = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":red", red, 2.0F + intensity * 5.0F, -4.0F, 6.0F, 0x52, clock);
            green = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":green", green, 2.0F + intensity * 5.0F, -4.0F, 6.0F, 0x47, clock);
            blue = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":blue", blue, 2.0F + intensity * 5.0F, -4.0F, 6.0F, 0x42, clock);
            alpha = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":alpha", alpha, 1.6F + intensity * 4.0F, -2.0F, 4.0F, 0x41, clock);
            changed = true;
        }

        if (unit(clock ^ 0x54494D45L) < 0.015F + intensity * 0.10F) {
            int maxLifetime = Math.max(1, original.lifetime());
            int minLifetime = Math.max(1, Math.round(maxLifetime * (extreme ? 0.05F : 0.15F)));
            lifetime = Mth.clamp(Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":lifetime", lifetime, 6.0F + intensity * 24.0F, minLifetime, maxLifetime, 0x4C54, clock)), minLifetime, maxLifetime);
            changed = true;
        }

        if (unit(clock ^ 0x524F4C4CL) < 0.025F + intensity * 0.18F) {
            roll = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":roll", roll, (float) Math.PI * (1.0F + intensity * 7.0F), (float) -Math.PI * 16.0F, (float) Math.PI * 16.0F, 0x524F, clock);
            changed = true;
        }

        if (unit(clock ^ 0x46524943L) < 0.025F + intensity * 0.14F) {
            friction = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId + ":friction", friction, 0.25F + intensity * 0.95F, 0.02F, 1.65F, 0x4652, clock);
            changed = true;
        }

        if (unit(clock ^ 0x50485953L) < 0.015F + intensity * 0.12F) {
            hasPhysics = unit(clock ^ 0x48504859L) >= 0.46F;
            changed = true;
        }
        if (unit(clock ^ 0x424C4F43L) < 0.015F + intensity * 0.12F) {
            speedUpWhenBlocked = unit(clock ^ 0x53505550L) < 0.64F;
            changed = true;
        }

        return changed ? new ParticleState(position, velocity, gravity, width, height, quadSize, red, green, blue, alpha, roll, friction, lifetime, hasPhysics, speedUpWhenBlocked) : original;
    }

    public static float mutateGravity(Particle particle, float originalGravity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "gravity");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
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
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
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
        return Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
    }

    private static String targetId(Particle particle, String feature) {
        return "particle:" + feature + ":" + (particle == null ? "unknown" : particle.getClass().getName());
    }

    private static long clock(CorruptionEffectStack stack, String targetId, Particle particle) {
        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, particleSalt(particle) ^ 0x50415254) ^ (time << 16);
    }

    private static int particleSalt(Particle particle) {
        if (particle == null) {
            return 0;
        }
        AABB box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5D;
        double y = box.minY;
        double z = (box.minZ + box.maxZ) * 0.5D;
        int qx = Mth.floor(x * 8.0D);
        int qy = Mth.floor(y * 8.0D);
        int qz = Mth.floor(z * 8.0D);
        int hash = particle.getClass().getName().hashCode();
        hash = 31 * hash + qx;
        hash = 31 * hash + qy;
        hash = 31 * hash + qz;
        hash = 31 * hash + particle.getLifetime();
        return hash;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static float signedUnit(long value) {
        return unit(value) * 2.0F - 1.0F;
    }

    private static double signedScale(long value, float intensity) {
        return Mth.clamp(1.0D + signedUnit(value) * intensity * 1.6D, -1.6D, 2.8D);
    }

    private static Vec3 axisImpulse(long seed, float intensity, double maxMagnitude) {
        return new Vec3(
                signedUnit(seed ^ 0x58415849L) * intensity * maxMagnitude,
                signedUnit(seed ^ 0x59415849L) * intensity * maxMagnitude,
                signedUnit(seed ^ 0x5A415849L) * intensity * maxMagnitude
        );
    }

    private static Vec3 clampVector(Vec3 value, double maxMagnitude) {
        if (value.lengthSqr() <= maxMagnitude * maxMagnitude) {
            return value;
        }
        return value.normalize().scale(maxMagnitude);
    }

    private static float mutateParticleSize(CorruptionEffectStack stack, String targetId, float original, long clock, float intensity, boolean extreme) {
        float safeOriginal = Float.isFinite(original) && original > 0.0F ? original : 0.02F;
        float exponent = signedUnit(clock ^ 0x5343414CL) * intensity * (extreme ? 3.5F : 2.4F);
        float scaled = (float) (safeOriginal * Math.pow(2.0D, exponent));
        if (unit(clock ^ 0x46554C4CL) < 0.06F + intensity * 0.16F) {
            scaled = unit(clock ^ 0x54494E59L) < 0.46F
                    ? 0.001F + unit(clock ^ 0x4D494E49L) * 0.018F
                    : safeOriginal + intensity * (extreme ? 4.0F : 1.8F) * (0.25F + unit(clock ^ 0x48554745L) * 0.75F);
        }
        return CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, scaled, safeOriginal * (extreme ? 5.0F : 2.5F), 0.001F, extreme ? 6.0F : 3.0F, targetId.hashCode(), clock);
    }

    private static int particleBudget(float intensity) {
        return Math.round(Mth.lerp(Mth.clamp(intensity, 0.0F, 1.0F), MAX_PARTICLE_BUDGET, MIN_PARTICLE_BUDGET));
    }

    public record ParticleState(
            Vec3 position,
            Vec3 velocity,
            float gravity,
            float width,
            float height,
            float quadSize,
            float red,
            float green,
            float blue,
            float alpha,
            float roll,
            float friction,
            int lifetime,
            boolean hasPhysics,
            boolean speedUpWhenBlocked
    ) {
    }
}
