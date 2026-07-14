package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class LaunchDirectionCorruptionMechanics {
    private static final CorruptionSurface SURFACE = CorruptionSurface.LAUNCH_DIRECTION;

    private LaunchDirectionCorruptionMechanics() {
    }

    static Vec3 mutate(CorruptionEffectStack stack, String targetId, Vec3 velocity, float minimumIntensity) {
        if (stack == null || velocity == null || !stack.activeOrExtreme(SURFACE)) {
            return velocity;
        }

        float intensity = stack.extreme(SURFACE) ? 1.0F : stack.intensity(SURFACE);
        if (intensity < minimumIntensity) {
            return velocity;
        }

        double speed = velocity.length();
        if (speed < 1.0E-7D) {
            return velocity;
        }

        long seed = stack.stableLong(SURFACE, targetId + ":direction", 0x4C41554E);
        float pressure = (float) Math.sqrt(intensity);
        float activation = Mth.clamp(0.15F + pressure * 1.10F + stack.instability() * 0.05F, 0.0F, 1.0F);
        if (StableSubsystemFaults.unit(seed ^ 0x41435449L) >= activation) {
            return velocity;
        }

        Vec3 direction = velocity.scale(1.0D / speed);
        Vec3 tangent = perpendicular(direction);
        Vec3 bitangent = direction.cross(tangent).normalize();
        double angle = Math.toRadians(65.0D + StableSubsystemFaults.unit(seed ^ 0x414E474CL) * 110.0D);
        double azimuth = StableSubsystemFaults.unit(seed ^ 0x415A494DL) * Math.PI * 2.0D;
        Vec3 radial = tangent.scale(Math.cos(azimuth)).add(bitangent.scale(Math.sin(azimuth)));
        Vec3 brokenDirection = direction.scale(Math.cos(angle)).add(radial.scale(Math.sin(angle))).normalize();

        double strength = Mth.clamp(0.20D + pressure, 0.0D, 1.0D);
        Vec3 mutatedDirection = direction.lerp(brokenDirection, strength);
        if (mutatedDirection.lengthSqr() < 1.0E-8D) {
            mutatedDirection = brokenDirection;
        } else {
            mutatedDirection = mutatedDirection.normalize();
        }
        return mutatedDirection.scale(speed);
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) < 0.85D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 perpendicular = direction.cross(reference);
        return perpendicular.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : perpendicular.normalize();
    }
}
