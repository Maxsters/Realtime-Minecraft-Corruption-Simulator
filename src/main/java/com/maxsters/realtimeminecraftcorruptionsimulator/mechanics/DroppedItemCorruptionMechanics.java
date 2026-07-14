package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

final class DroppedItemCorruptionMechanics {
    private static final CorruptionSurface SURFACE = CorruptionSurface.LOOSE_ENTITY_PHYSICS;

    private DroppedItemCorruptionMechanics() {
    }

    static void mutateLaunchDirection(ItemEntity item, CorruptionEffectStack stack, float minimumIntensity) {
        if (item == null) {
            return;
        }
        Vec3 original = item.getDeltaMovement();
        Vec3 mutated = LaunchDirectionCorruptionMechanics.mutate(
                stack,
                "dropped_item:" + subject(item),
                original,
                minimumIntensity
        );
        if (mutated.distanceToSqr(original) > 1.0E-7D) {
            item.setDeltaMovement(mutated);
            item.hasImpulse = true;
        }
    }

    static void mutateInitialSpeed(ItemEntity item, CorruptionEffectStack stack, float minimumIntensity) {
        if (item == null || stack == null || !stack.activeOrExtreme(SURFACE)) {
            return;
        }
        float intensity = stack.extreme(SURFACE) ? 1.0F : stack.intensity(SURFACE);
        if (intensity < minimumIntensity) {
            return;
        }

        String subject = subject(item);
        if (!StableSubsystemFaults.broken(stack, SURFACE, "initial_speed", subject, 0x53504545,
                0.10F, 0.82F, 0.96F, minimumIntensity)) {
            return;
        }

        Vec3 original = item.getDeltaMovement();
        double speed = original.length();
        if (speed < 1.0E-7D) {
            return;
        }

        long seed = StableSubsystemFaults.seed(stack, SURFACE, "initial_speed_profile", subject, 0x4954454D);
        double speedScale = Math.pow(2.0D, StableSubsystemFaults.signedUnit(seed ^ 0x53504545L) * intensity * 2.5D);
        item.setDeltaMovement(clamp(original.scale(Math.max(0.005D, speed * speedScale) / speed), 4.0D));
        item.hasImpulse = true;
    }

    static double gravity(ItemEntity item, CorruptionEffectStack stack, double vanilla, float minimumIntensity) {
        if (!active(stack, minimumIntensity)) {
            return vanilla;
        }
        String subject = subject(item);
        if (!StableSubsystemFaults.broken(stack, SURFACE, "gravity", subject, 0x47524156,
                0.08F, 0.78F, 0.94F, minimumIntensity)) {
            return vanilla;
        }

        float intensity = stack.extreme(SURFACE) ? 1.0F : stack.intensity(SURFACE);
        long seed = StableSubsystemFaults.seed(stack, SURFACE, "gravity_profile", subject, 0x47524156);
        return switch (Math.floorMod((int) (seed >>> 31), 5)) {
            case 0 -> vanilla * (0.02D + StableSubsystemFaults.unit(seed ^ 0x4C4F5747L) * 0.16D);
            case 1 -> -vanilla * (0.25D + intensity * (0.75D + StableSubsystemFaults.unit(seed ^ 0x52455647L) * 2.5D));
            case 2 -> vanilla * (1.5D + intensity * (2.0D + StableSubsystemFaults.unit(seed ^ 0x48454156L) * 7.0D));
            case 3 -> 0.0D;
            default -> vanilla + StableSubsystemFaults.signedUnit(seed ^ 0x4F464647L) * intensity * 0.075D;
        };
    }

    static double bounce(ItemEntity item, CorruptionEffectStack stack, double vanilla, float minimumIntensity) {
        if (!active(stack, minimumIntensity)) {
            return vanilla;
        }
        String subject = subject(item);
        if (!StableSubsystemFaults.broken(stack, SURFACE, "ground_bounce", subject, 0x424F554E,
                0.06F, 0.68F, 0.90F, minimumIntensity)) {
            return vanilla;
        }

        float intensity = stack.extreme(SURFACE) ? 1.0F : stack.intensity(SURFACE);
        long seed = StableSubsystemFaults.seed(stack, SURFACE, "bounce_profile", subject, 0x424F554E);
        return switch (Math.floorMod((int) (seed >>> 34), 4)) {
            case 0 -> 0.0D;
            case 1 -> Mth.clamp(vanilla * (0.08D + intensity * 0.24D), -3.0D, 1.0D);
            case 2 -> Mth.clamp(vanilla * (1.5D + intensity * 3.5D), -3.0D, 1.0D);
            default -> Mth.clamp(-vanilla * (0.25D + intensity * 1.25D), -3.0D, 1.0D);
        };
    }

    private static boolean active(CorruptionEffectStack stack, float minimumIntensity) {
        return stack != null
                && stack.activeOrExtreme(SURFACE)
                && (stack.extreme(SURFACE) || stack.intensity(SURFACE) >= minimumIntensity);
    }

    private static String subject(ItemEntity item) {
        ResourceLocation id = item == null || item.getItem().isEmpty()
                ? null
                : ForgeRegistries.ITEMS.getKey(item.getItem().getItem());
        return id == null ? "unknown" : id.toString();
    }

    private static Vec3 clamp(Vec3 value, double maximum) {
        double lengthSqr = value.lengthSqr();
        return lengthSqr > maximum * maximum ? value.scale(maximum / Math.sqrt(lengthSqr)) : value;
    }
}
