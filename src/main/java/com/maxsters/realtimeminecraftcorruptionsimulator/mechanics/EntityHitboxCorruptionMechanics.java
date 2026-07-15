package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class EntityHitboxCorruptionMechanics {
    private static final float MIN_ENTITY_MECHANICS_INTENSITY = 0.00025F;
    private static final Map<Entity, HitboxSignatureCache> SIGNATURE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final EntityHitboxMutation PASS = new EntityHitboxMutation(1.0F, 1.0F, 0);

    private EntityHitboxCorruptionMechanics() {
    }

    static AABB corruptBounds(Entity entity, AABB original, CorruptionEffectStack stack) {
        EntityHitboxMutation mutation = mutation(entity, stack);
        if (original == null || mutation == PASS) {
            return original;
        }

        double halfWidth = original.getXsize() * 0.5D * mutation.widthScale();
        double halfDepth = original.getZsize() * 0.5D * mutation.widthScale();
        double height = original.getYsize() * mutation.heightScale();
        if (!Double.isFinite(halfWidth) || !Double.isFinite(halfDepth) || !Double.isFinite(height)) {
            return original;
        }

        double centerX = (original.minX + original.maxX) * 0.5D;
        double centerZ = (original.minZ + original.maxZ) * 0.5D;
        return new AABB(centerX - halfWidth, original.minY, centerZ - halfDepth, centerX + halfWidth, original.minY + height, centerZ + halfDepth);
    }

    static int mutationSignature(Entity entity, CorruptionEffectStack stack) {
        if (entity == null || entity.level() == null) {
            return PASS.signature();
        }
        int profileSignature = profileSignature(stack);
        HitboxSignatureCache cached = SIGNATURE_CACHE.get(entity);
        if (cached != null
                && cached.profileSignature() == profileSignature
                && entity.tickCount < cached.nextCheckTick()) {
            return cached.signature();
        }

        int signature = mutation(entity, stack).signature();
        int cadence = stack.level() <= 0 ? 20 : stack.extreme(CorruptionSurface.ENTITY_STATE) || stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 5 : 10;
        SIGNATURE_CACHE.put(entity, new HitboxSignatureCache(
                profileSignature,
                signature,
                entity.tickCount + cadence + Math.floorMod(entity.getId(), Math.max(1, cadence))
        ));
        return signature;
    }

    static void clearCaches() {
        SIGNATURE_CACHE.clear();
    }

    private static int profileSignature(CorruptionEffectStack stack) {
        int signature = 29;
        signature = signature * 31 + stack.level();
        signature = signature * 31 + stack.enabledTargetsMask();
        signature = signature * 31 + (int) (stack.fixedSeed() ^ (stack.fixedSeed() >>> 32));
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.ENTITY_STATE) * 1000.0F);
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 1000.0F);
        return signature == 0 ? 1 : signature;
    }

    private static EntityHitboxMutation mutation(Entity entity, CorruptionEffectStack stack) {
        if (entity == null || entity.level() == null || !entity.isAddedToWorld() || stack.level() <= 0) {
            return PASS;
        }

        boolean stateActive = surfaceActive(stack, CorruptionSurface.ENTITY_STATE);
        boolean timingActive = surfaceActive(stack, CorruptionSurface.ANIMATION_TIMING);
        if (!stateActive && !timingActive) {
            return PASS;
        }

        String targetId = "entity_hitbox_dimensions:" + entityTargetId(entity);
        float stateIntensity = stack.extreme(CorruptionSurface.ENTITY_STATE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.ENTITY_STATE, targetId), stack.intensity(CorruptionSurface.ENTITY_STATE) * 0.74F);
        float timingIntensity = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId), stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.58F);
        float intensity = Mth.clamp(Math.max(stateIntensity, timingIntensity * 0.88F), 0.0F, 1.0F);
        if (intensity <= MIN_ENTITY_MECHANICS_INTENSITY) {
            return PASS;
        }

        CorruptionSurface surface = stateIntensity >= timingIntensity ? CorruptionSurface.ENTITY_STATE : CorruptionSurface.ANIMATION_TIMING;
        long seed = stack.stableLong(surface, targetId, 0x48495458);
        float widthScale = hitboxScale(seed ^ 0x5749445448L, intensity, stack.extreme(surface));
        float heightScale = hitboxScale(seed ^ 0x484549474854L, intensity, stack.extreme(surface));
        if (unitHash(seed ^ 0x4D4F4445L) < 0.06F + intensity * 0.30F) {
            int mode = Math.floorMod((int) (seed >>> 27), 7);
            switch (mode) {
                case 0 -> widthScale = Math.min(widthScale, 0.06F + unitHash(seed ^ 0x54494E59L) * 0.12F);
                case 1 -> heightScale = Math.min(heightScale, 0.06F + unitHash(seed ^ 0x464C4154L) * 0.14F);
                case 2 -> widthScale = Mth.clamp(widthScale * (2.5F + intensity * 7.5F), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                case 3 -> heightScale = Mth.clamp(heightScale * (2.5F + intensity * 7.5F), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                case 4 -> {
                    widthScale = Mth.clamp(widthScale * 0.18F, 0.05F, 2.5F);
                    heightScale = Mth.clamp(heightScale * (2.0F + intensity * 5.0F), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                }
                case 5 -> {
                    widthScale = Mth.clamp(widthScale * (2.0F + intensity * 5.0F), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                    heightScale = Mth.clamp(heightScale * 0.18F, 0.05F, 2.5F);
                }
                default -> {
                    widthScale = Mth.clamp(1.0F / Math.max(0.08F, widthScale), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                    heightScale = Mth.clamp(1.0F / Math.max(0.08F, heightScale), 0.05F, stack.extreme(surface) ? 12.0F : 6.5F);
                }
            }
        }

        int signature = 17;
        signature = signature * 31 + stack.level();
        signature = signature * 31 + stack.enabledTargetsMask();
        signature = signature * 31 + (int) (stack.fixedSeed() ^ (stack.fixedSeed() >>> 32));
        signature = signature * 31 + surface.ordinal();
        signature = signature * 31 + Math.round(widthScale * 1000.0F);
        signature = signature * 31 + Math.round(heightScale * 1000.0F);
        return new EntityHitboxMutation(widthScale, heightScale, signature == 0 ? 1 : signature);
    }

    private static boolean surfaceActive(CorruptionEffectStack stack, CorruptionSurface surface) {
        return stack.extreme(surface) || (stack.intensity(surface) >= MIN_ENTITY_MECHANICS_INTENSITY && stack.active(surface));
    }

    private static float hitboxScale(long seed, float intensity, boolean extreme) {
        double exponent = signedUnit(seed) * intensity * (extreme ? 5.2D : 3.0D);
        float scale = (float) Math.pow(2.0D, exponent);
        if (unitHash(seed ^ 0x53545554L) < 0.05F + intensity * 0.22F) {
            scale = unitHash(seed ^ 0x424947L) < 0.52F
                    ? 0.05F + unitHash(seed ^ 0x534D4C4CL) * (0.24F + intensity * 0.18F)
                    : 2.0F + unitHash(seed ^ 0x4C415247L) * (extreme ? 10.0F : 4.5F);
        }
        return Mth.clamp(scale, 0.05F, extreme ? 12.0F : 6.5F);
    }

    private static String entityTargetId(Entity entity) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return typeId == null ? entity.getType().toString() : typeId.toString();
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

    private static float signedUnit(long value) {
        return unitHash(value) * 2.0F - 1.0F;
    }

    private record EntityHitboxMutation(float widthScale, float heightScale, int signature) {
    }

    private record HitboxSignatureCache(int profileSignature, int signature, int nextCheckTick) {
    }
}
