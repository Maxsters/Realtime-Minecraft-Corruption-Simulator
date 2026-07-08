package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class InteractionRayCorruptionMechanics {
    private static final CorruptionSurface SURFACE = CorruptionSurface.INTERACTION_ROUTING;

    private InteractionRayCorruptionMechanics() {
    }

    public static RayMutation mutateCameraRay(CorruptionEffectStack stack, Entity cameraEntity, float partialTick, double vanillaRange, float minimumIntensity) {
        if (stack == null || cameraEntity == null || cameraEntity.level() == null || vanillaRange <= 0.0D) {
            return RayMutation.normal();
        }

        String targetId = cameraRayTargetId(cameraEntity.level());
        if (!active(stack, targetId, minimumIntensity)) {
            return RayMutation.normal();
        }

        float intensity = intensity(stack, targetId);
        Vec3 origin = cameraEntity.getEyePosition(partialTick);
        Vec3 direction = normalizeOrZero(cameraEntity.getViewVector(partialTick));
        if (direction.lengthSqr() < 1.0E-7D) {
            return RayMutation.normal();
        }

        int poseSalt = cameraPoseSalt(cameraEntity, origin, direction);
        long hash = stack.stableLong(SURFACE, targetId, poseSalt);
        float chance = stack.extreme(SURFACE)
                ? 1.0F
                : Mth.clamp(0.055F + intensity * 0.78F + stack.instability() * 0.12F, 0.0F, 0.96F);
        if (unitHash(hash ^ 0x5241595FL) > chance) {
            return RayMutation.normal();
        }

        float noShotChance = stack.extreme(SURFACE)
                ? 0.24F
                : Mth.clamp(0.04F + intensity * 0.20F + stack.instability() * 0.06F, 0.0F, 0.34F);
        if (unitHash(hash ^ 0x4E4F5241L) < noShotChance) {
            return RayMutation.disabled(origin, direction, targetId, intensity);
        }

        Vec3 mutatedOrigin = origin;
        Vec3 mutatedDirection = direction;
        double mutatedRange = vanillaRange;
        int layers = stack.extreme(SURFACE) ? 3 : Math.max(1, Math.min(3, 1 + Math.round(intensity * 2.0F)));
        for (int layer = 0; layer < layers; layer++) {
            long layerHash = mixLong(hash ^ (long) layer * 0x9E3779B97F4A7C15L);
            int mode = Math.floorMod((int) (layerHash >>> 29), 8);
            switch (mode) {
                case 0 -> mutatedDirection = skewDirection(mutatedDirection, layerHash, intensity, 0.42D + intensity * 2.2D);
                case 1 -> mutatedOrigin = offsetOrigin(mutatedOrigin, mutatedDirection, layerHash, intensity, 0.10D + intensity * 1.15D);
                case 2 -> mutatedRange *= 0.02D + unitHash(layerHash ^ 0x53484F52L) * (0.34D + intensity * 0.28D);
                case 3 -> mutatedRange *= 1.10D + unitHash(layerHash ^ 0x4C4F4E47L) * (1.8D + intensity * 6.0D);
                case 4 -> mutatedDirection = foldDirection(mutatedDirection, layerHash);
                case 5 -> mutatedDirection = mutatedDirection.reverse();
                case 6 -> {
                    mutatedOrigin = offsetOrigin(mutatedOrigin, mutatedDirection, layerHash, intensity, 0.08D + intensity * 0.72D);
                    mutatedDirection = skewDirection(mutatedDirection, layerHash ^ 0x53574159L, intensity, 0.28D + intensity * 1.65D);
                }
                default -> mutatedDirection = snapDirection(mutatedDirection, layerHash, intensity);
            }
        }

        mutatedDirection = normalizeOrZero(mutatedDirection);
        if (mutatedDirection.lengthSqr() < 1.0E-7D) {
            return RayMutation.disabled(origin, direction, targetId, intensity);
        }

        mutatedRange = Mth.clamp(mutatedRange, 0.03D, stack.extreme(SURFACE) ? 32.0D : 18.0D);
        return RayMutation.mutated(mutatedOrigin, mutatedDirection, mutatedRange, targetId, intensity);
    }

    public static String cameraRayTargetId(Level level) {
        return "interaction_ray:camera:" + dimension(level);
    }

    public static float intensity(CorruptionEffectStack stack, String targetId) {
        if (stack == null) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(SURFACE) ? 1.0F : stack.intensity(SURFACE),
                stack.targetIntensity(SURFACE, targetId)
        ), 0.0F, 1.0F);
    }

    public static int positionSalt(BlockPos pos) {
        return pos == null ? 0 : (int) mixLong(pos.asLong());
    }

    public static int entitySalt(Entity entity) {
        UUID uuid = entity == null ? null : entity.getUUID();
        if (uuid == null) {
            return 0;
        }
        return (int) mixLong(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32));
    }

    private static boolean active(CorruptionEffectStack stack, String targetId, float minimumIntensity) {
        return stack.extreme(SURFACE)
                || (stack.intensity(SURFACE) >= minimumIntensity && (stack.active(SURFACE) || stack.active(SURFACE, targetId)));
    }

    private static int cameraPoseSalt(Entity cameraEntity, Vec3 origin, Vec3 direction) {
        BlockPos originBlock = BlockPos.containing(origin);
        int yawBucket = Math.round(cameraEntity.getYRot() * 4.0F);
        int pitchBucket = Math.round(cameraEntity.getXRot() * 4.0F);
        int dirX = Math.round((float) direction.x * 64.0F);
        int dirY = Math.round((float) direction.y * 64.0F);
        int dirZ = Math.round((float) direction.z * 64.0F);
        long value = originBlock.asLong();
        value ^= (long) yawBucket * 0x9E3779B97F4A7C15L;
        value ^= (long) pitchBucket * 0xBF58476D1CE4E5B9L;
        value ^= ((long) dirX & 0xFFFFL) << 48;
        value ^= ((long) dirY & 0xFFFFL) << 24;
        value ^= (long) dirZ & 0xFFFFL;
        return (int) mixLong(value);
    }

    private static Vec3 offsetOrigin(Vec3 origin, Vec3 direction, long hash, float intensity, double amplitude) {
        Vec3 right = perpendicularRight(direction);
        Vec3 up = right.cross(direction).normalize();
        double forward = signedUnit(hash ^ 0x464F5257L) * amplitude * 0.55D;
        double sideways = signedUnit(hash ^ 0x53494445L) * amplitude;
        double vertical = signedUnit(hash ^ 0x5550444EL) * amplitude * 0.72D;
        double gain = 0.35D + intensity * 0.65D;
        return origin
                .add(direction.scale(forward * gain))
                .add(right.scale(sideways * gain))
                .add(up.scale(vertical * gain));
    }

    private static Vec3 skewDirection(Vec3 direction, long hash, float intensity, double amplitude) {
        Vec3 right = perpendicularRight(direction);
        Vec3 up = right.cross(direction).normalize();
        double lateral = signedUnit(hash ^ 0x584B4557L) * amplitude;
        double vertical = signedUnit(hash ^ 0x594B4557L) * amplitude;
        double forward = 1.0D + signedUnit(hash ^ 0x5A4B4557L) * intensity * 0.35D;
        return direction.scale(forward).add(right.scale(lateral)).add(up.scale(vertical)).normalize();
    }

    private static Vec3 foldDirection(Vec3 direction, long hash) {
        int mode = Math.floorMod((int) (hash >>> 17), 6);
        return switch (mode) {
            case 0 -> new Vec3(direction.z, direction.y, direction.x);
            case 1 -> new Vec3(-direction.x, direction.z, direction.y);
            case 2 -> new Vec3(direction.y, direction.x, -direction.z);
            case 3 -> new Vec3(direction.x, -direction.z, direction.y);
            case 4 -> new Vec3(-direction.z, direction.y, direction.x);
            default -> new Vec3(direction.y, -direction.x, direction.z);
        };
    }

    private static Vec3 snapDirection(Vec3 direction, long hash, float intensity) {
        Vec3[] axes = {
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, -1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 0.0D, -1.0D)
        };
        Vec3 axis = axes[Math.floorMod((int) hash, axes.length)];
        double axisWeight = 0.25D + intensity * 1.75D;
        return direction.add(axis.scale(axisWeight)).normalize();
    }

    private static Vec3 perpendicularRight(Vec3 direction) {
        Vec3 up = Math.abs(direction.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = direction.cross(up);
        if (right.lengthSqr() < 1.0E-7D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return right.normalize();
    }

    private static Vec3 normalizeOrZero(Vec3 value) {
        return value == null || value.lengthSqr() < 1.0E-7D ? Vec3.ZERO : value.normalize();
    }

    private static String dimension(Level level) {
        return level == null ? "unknown" : level.dimension().location().toString();
    }

    private static double signedUnit(long value) {
        return unitHash(value) * 2.0D - 1.0D;
    }

    private static long mixLong(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float unitHash(long value) {
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    public record RayMutation(boolean mutated, boolean disabled, Vec3 origin, Vec3 direction, double range, String targetId, float intensity) {
        private static RayMutation normal() {
            return new RayMutation(false, false, Vec3.ZERO, Vec3.ZERO, 0.0D, "", 0.0F);
        }

        private static RayMutation disabled(Vec3 origin, Vec3 direction, String targetId, float intensity) {
            return new RayMutation(true, true, origin, direction, 0.0D, targetId, intensity);
        }

        private static RayMutation mutated(Vec3 origin, Vec3 direction, double range, String targetId, float intensity) {
            return new RayMutation(true, false, origin, direction, range, targetId, intensity);
        }
    }
}
