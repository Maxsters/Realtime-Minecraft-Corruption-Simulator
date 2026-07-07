package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

final class EntityAttributeCorruptionMechanics {
    private static final float MIN_ENTITY_MECHANICS_INTENSITY = 0.00025F;
    private static final UUID LEGACY_NON_PLAYER_SPEED_ID = UUID.fromString("0e627e2a-9d3a-4d9b-a4b8-a1830ed20401");
    private static final UUID LEGACY_PLAYER_MAX_HEALTH_ID = UUID.fromString("3ed6bc4d-1875-4b4a-9e4f-80f359996081");
    private static final EntityMechanic[] ENTITY_MECHANICS = new EntityMechanic[]{
            new EntityMechanic("max_health", () -> Attributes.MAX_HEALTH, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.35D, -0.96D, 5.0D, 14.0D),
            new EntityMechanic("movement_speed", () -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.80D, -0.98D, 7.0D, 24.0D),
            new EntityMechanic("flying_speed", () -> Attributes.FLYING_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.65D, -0.98D, 6.0D, 18.0D),
            new EntityMechanic("attack_damage", () -> Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.65D, -1.0D, 6.0D, 18.0D),
            new EntityMechanic("attack_speed", () -> Attributes.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.90D, -0.99D, 10.0D, 30.0D),
            new EntityMechanic("attack_knockback", () -> Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADDITION, 3.00D, -4.0D, 8.0D, 24.0D),
            new EntityMechanic("knockback_resistance", () -> Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION, 1.20D, -1.0D, 2.0D, 4.0D),
            new EntityMechanic("armor", () -> Attributes.ARMOR, AttributeModifier.Operation.ADDITION, 16.0D, -24.0D, 48.0D, 96.0D),
            new EntityMechanic("armor_toughness", () -> Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION, 12.0D, -20.0D, 36.0D, 72.0D),
            new EntityMechanic("luck", () -> Attributes.LUCK, AttributeModifier.Operation.ADDITION, 32.0D, -96.0D, 96.0D, 192.0D),
            new EntityMechanic("jump_strength", () -> Attributes.JUMP_STRENGTH, AttributeModifier.Operation.MULTIPLY_TOTAL, 1.70D, -0.98D, 6.0D, 20.0D),
            new EntityMechanic("swim_speed", () -> ForgeMod.SWIM_SPEED.get(), AttributeModifier.Operation.MULTIPLY_TOTAL, 1.70D, -0.98D, 7.0D, 22.0D),
            new EntityMechanic("gravity", () -> ForgeMod.ENTITY_GRAVITY.get(), AttributeModifier.Operation.MULTIPLY_TOTAL, 1.90D, -0.99D, 8.0D, 28.0D),
            new EntityMechanic("step_height", () -> ForgeMod.STEP_HEIGHT_ADDITION.get(), AttributeModifier.Operation.ADDITION, 1.80D, -1.0D, 3.5D, 8.0D)
    };
    private static final Map<LivingEntity, Integer> SYNC_SIGNATURES = Collections.synchronizedMap(new WeakHashMap<>());

    private EntityAttributeCorruptionMechanics() {
    }

    static boolean shouldSync(LivingEntity entity, CorruptionEffectStack stack, boolean player) {
        if (entity == null || stack == null) {
            return false;
        }
        int signature = syncSignature(stack);
        Integer previous = SYNC_SIGNATURES.get(entity);
        if (previous == null || previous != signature) {
            SYNC_SIGNATURES.put(entity, signature);
            return true;
        }
        int cadence = stack.level() <= 0 ? 80 : player ? 10 : 20;
        return Math.floorMod(entity.tickCount + entity.getId(), cadence) == 0;
    }

    static void forceSync(LivingEntity entity, CorruptionEffectStack stack, String entityTargetId) {
        if (entity == null || stack == null) {
            return;
        }
        sync(entity, stack, entityTargetId);
        SYNC_SIGNATURES.put(entity, syncSignature(stack));
    }

    static float mobilityMotionIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.PLAYER_PHYSICS)) {
            return 1.0F;
        }
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.PLAYER_PHYSICS, targetId),
                stack.intensity(CorruptionSurface.PLAYER_PHYSICS) * 0.86F
        ), 0.0F, 1.0F);
    }

    static void clearCaches() {
        SYNC_SIGNATURES.clear();
    }

    private static int syncSignature(CorruptionEffectStack stack) {
        int signature = 23;
        signature = signature * 31 + stack.level();
        signature = signature * 31 + stack.enabledTargetsMask();
        signature = signature * 31 + (int) (stack.fixedSeed() ^ (stack.fixedSeed() >>> 32));
        signature = signature * 31 + Math.round(stack.instability() * 1000.0F);
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.ENTITY_KINEMATICS) * 1000.0F);
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.ENTITY_STATE) * 1000.0F);
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 1000.0F);
        signature = signature * 31 + Math.round(stack.intensity(CorruptionSurface.TICK_SPEED) * 1000.0F);
        return signature == 0 ? 1 : signature;
    }

    static void sync(LivingEntity entity, CorruptionEffectStack stack, String entityTargetId) {
        if (entity == null) {
            return;
        }

        String baseTargetId = "entity_mechanics:" + entityTargetId + (entity instanceof Player ? ":player" : ":living");
        for (EntityMechanic mechanic : ENTITY_MECHANICS) {
            AttributeInstance attribute = attributeInstance(entity, mechanic);
            if (attribute == null) {
                continue;
            }

            double amount = amount(entity, stack, baseTargetId, mechanic);
            if (Double.isNaN(amount)) {
                removeModifier(attribute, mechanic);
            } else {
                syncModifier(attribute, mechanic, amount);
            }
        }

        cleanupLegacyMechanics(entity);
        clampHealthToMax(entity);
    }

    private static AttributeInstance attributeInstance(LivingEntity entity, EntityMechanic mechanic) {
        try {
            Attribute attribute = mechanic.attribute().get();
            return attribute == null ? null : entity.getAttribute(attribute);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double amount(LivingEntity entity, CorruptionEffectStack stack, String baseTargetId, EntityMechanic mechanic) {
        if (stack.level() <= 0 || entity.isSpectator()) {
            return Double.NaN;
        }

        String targetId = baseTargetId + ":" + mechanic.id();
        float intensity = intensity(stack, targetId);
        if (intensity <= MIN_ENTITY_MECHANICS_INTENSITY) {
            return Double.NaN;
        }

        CorruptionSurface surface = surface(stack, targetId);
        long hash = stack.stableLong(surface, targetId, entityStableSalt(entity, mechanic.id().hashCode()));
        double high = stack.extreme(surface) ? mechanic.extremeMaxAmount() : mechanic.maxAmount();
        double span = mechanic.span() * (0.55D + intensity * 1.45D + stack.instability() * 0.60D);
        if (mechanic.operation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
            double exponent = signedUnit(hash ^ 0x4D554C54L) * span;
            if (unitHash(hash ^ 0x5354414CL) < 0.08F + intensity * 0.28F) {
                exponent = unitHash(hash ^ 0x46524545L) < 0.42F
                        ? -8.0D * (0.35D + intensity * 0.65D)
                        : 4.8D * (0.25D + intensity * 0.75D);
            }
            double amount = Math.pow(2.0D, exponent) - 1.0D;
            return Mth.clamp(amount, mechanic.minAmount(), high);
        }

        long mechanicClock = mechanicClock(entity, stack, surface, targetId, mechanic);
        double amount = CorruptionValueMutator.mutateScalar(stack, surface, targetId, 0.0D, span, mechanic.minAmount(), high, mechanic.id().hashCode(), mechanicClock);
        if (unitHash(hash ^ 0x42495446L) < 0.06F + intensity * 0.22F) {
            amount = unitHash(hash ^ 0x4D41584DL) < 0.5F ? mechanic.minAmount() : high;
        }
        return Mth.clamp(amount, mechanic.minAmount(), high);
    }

    private static long mechanicClock(LivingEntity entity, CorruptionEffectStack stack, CorruptionSurface surface, String targetId, EntityMechanic mechanic) {
        long seed = stack.stableLong(surface, targetId + ":mechanic_clock", mechanic.id().hashCode());
        // Attribute modifiers are synced state, not a tick animation. Keeping this clock
        // independent of client/server tick counters prevents no-drift clients from rerolling
        // a different max-health, speed, or jump-strength modifier than the server.
        return seed ^ (entityStableIdentity(entity) * 0x9E3779B97F4A7C15L);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        float intensity = Math.max(
                stack.extreme(CorruptionSurface.ENTITY_KINEMATICS) ? 1.0F : stack.targetIntensity(CorruptionSurface.ENTITY_KINEMATICS, targetId),
                (stack.extreme(CorruptionSurface.ENTITY_STATE) ? 1.0F : stack.targetIntensity(CorruptionSurface.ENTITY_STATE, targetId)) * 0.82F
        );
        return Mth.clamp(intensity, 0.0F, 1.0F);
    }

    private static CorruptionSurface surface(CorruptionEffectStack stack, String targetId) {
        CorruptionSurface best = CorruptionSurface.ENTITY_KINEMATICS;
        float bestIntensity = stack.extreme(best) ? 1.0F : stack.targetIntensity(best, targetId);
        float stateIntensity = stack.extreme(CorruptionSurface.ENTITY_STATE) ? 1.0F : stack.targetIntensity(CorruptionSurface.ENTITY_STATE, targetId) * 0.82F;
        if (stateIntensity > bestIntensity) {
            best = CorruptionSurface.ENTITY_STATE;
        }
        return best;
    }

    private static void syncModifier(AttributeInstance attribute, EntityMechanic mechanic, double amount) {
        AttributeModifier existing = attribute.getModifier(mechanic.uuid());
        if (existing != null && Math.abs(existing.getAmount() - amount) < 0.015D) {
            return;
        }
        removeModifier(attribute, mechanic);
        try {
            attribute.addTransientModifier(new AttributeModifier(
                    mechanic.uuid(),
                    "realtime_minecraft_corruption_simulator_" + mechanic.id(),
                    amount,
                    mechanic.operation()
            ));
        } catch (RuntimeException ignored) {
        }
    }

    private static void removeModifier(AttributeInstance attribute, EntityMechanic mechanic) {
        if (attribute.getModifier(mechanic.uuid()) != null) {
            attribute.removeModifier(mechanic.uuid());
        }
    }

    private static void cleanupLegacyMechanics(LivingEntity entity) {
        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null && movementSpeed.getModifier(LEGACY_NON_PLAYER_SPEED_ID) != null) {
            movementSpeed.removeModifier(LEGACY_NON_PLAYER_SPEED_ID);
        }
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(LEGACY_PLAYER_MAX_HEALTH_ID) != null) {
            maxHealth.removeModifier(LEGACY_PLAYER_MAX_HEALTH_ID);
        }
    }

    private static void clampHealthToMax(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        float max = entity.getMaxHealth();
        if (entity.getHealth() > max) {
            entity.setHealth(max);
        } else if (entity.getHealth() <= 0.0F && max > 0.0F && !entity.isDeadOrDying()) {
            entity.setHealth(Math.min(1.0F, max));
        }
    }

    private static int entityStableSalt(Entity entity, int salt) {
        return (int) mixLong(entityStableIdentity(entity) ^ Integer.toUnsignedLong(salt) * 0x9E3779B97F4A7C15L);
    }

    private static long entityStableIdentity(Entity entity) {
        UUID uuid = entity == null ? null : entity.getUUID();
        if (uuid == null) {
            return 0L;
        }
        return mixLong(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32));
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

    private static UUID mechanicUuid(String id) {
        return UUID.nameUUIDFromBytes(("realtime_minecraft_corruption_simulator:entity_mechanic:" + id).getBytes(StandardCharsets.UTF_8));
    }

    private record EntityMechanic(
            String id,
            Supplier<Attribute> attribute,
            AttributeModifier.Operation operation,
            double span,
            double minAmount,
            double maxAmount,
            double extremeMaxAmount,
            UUID uuid
    ) {
        private EntityMechanic(String id, Supplier<Attribute> attribute, AttributeModifier.Operation operation, double span, double minAmount, double maxAmount, double extremeMaxAmount) {
            this(id, attribute, operation, span, minAmount, maxAmount, extremeMaxAmount, mechanicUuid(id));
        }
    }
}
