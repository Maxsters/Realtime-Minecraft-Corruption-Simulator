package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.InteractionRayCorruptionMechanics;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public final class InteractionCorruptionHooks {
    private static final float MIN_INTERACTION_INTENSITY = 0.00025F;

    private InteractionCorruptionHooks() {
    }

    public static boolean pickWithCorruptedRay(Minecraft minecraft, float partialTick) {
        if (minecraft == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGameplay();
        if (!stack.activeOrExtreme(CorruptionSurface.INTERACTION_ROUTING)) {
            return false;
        }

        InteractionRayCorruptionMechanics.RayMutation ray = InteractionRayCorruptionMechanics.mutateCameraRay(
                stack,
                cameraEntity,
                partialTick,
                minecraft.gameMode.getPickRange(),
                MIN_INTERACTION_INTENSITY
        );
        if (!ray.mutated()) {
            return false;
        }

        minecraft.getProfiler().push("pick");
        try {
            minecraft.crosshairPickEntity = null;
            if (ray.disabled()) {
                minecraft.hitResult = miss(ray.origin(), ray.direction());
                return true;
            }

            runCorruptedPick(minecraft, cameraEntity, ray);
            return true;
        } finally {
            minecraft.getProfiler().pop();
        }
    }

    private static void runCorruptedPick(Minecraft minecraft, Entity cameraEntity, InteractionRayCorruptionMechanics.RayMutation ray) {
        Vec3 origin = ray.origin();
        Vec3 direction = ray.direction().normalize();
        double blockRange = ray.range();
        Vec3 blockEnd = origin.add(direction.scale(blockRange));

        HitResult blockHit = minecraft.level.clip(new ClipContext(
                origin,
                blockEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                cameraEntity
        ));
        minecraft.hitResult = blockHit;

        double entityRange = blockRange;
        boolean limitedEntityRange = !minecraft.gameMode.hasFarPickRange() && entityRange > 3.0D;

        double maxEntityDistanceSqr = entityRange * entityRange;
        if (blockHit != null) {
            maxEntityDistanceSqr = blockHit.getLocation().distanceToSqr(origin);
        }

        Vec3 entityEnd = origin.add(direction.scale(entityRange));
        AABB searchBox = cameraEntity.getBoundingBox().expandTowards(direction.scale(entityRange)).inflate(1.0D);
        Predicate<Entity> predicate = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(cameraEntity, origin, entityEnd, searchBox, predicate, maxEntityDistanceSqr);
        if (entityHit == null) {
            return;
        }

        Entity target = entityHit.getEntity();
        Vec3 entityLocation = entityHit.getLocation();
        double entityDistanceSqr = origin.distanceToSqr(entityLocation);
        if (limitedEntityRange && entityDistanceSqr > 9.0D) {
            minecraft.hitResult = BlockHitResult.miss(entityLocation, hitDirection(direction), BlockPos.containing(entityLocation));
            return;
        }
        if (entityDistanceSqr >= maxEntityDistanceSqr && minecraft.hitResult != null) {
            return;
        }

        minecraft.hitResult = entityHit;
        if (target instanceof LivingEntity || target instanceof ItemFrame) {
            minecraft.crosshairPickEntity = target;
        }
    }

    private static BlockHitResult miss(Vec3 origin, Vec3 direction) {
        Vec3 location = origin == null ? Vec3.ZERO : origin;
        Vec3 rayDirection = direction == null || direction.lengthSqr() < 1.0E-7D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        return BlockHitResult.miss(location, hitDirection(rayDirection), BlockPos.containing(location));
    }

    private static Direction hitDirection(Vec3 direction) {
        return Direction.getNearest(direction.x, direction.y, direction.z);
    }
}
