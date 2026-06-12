package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Unique
    private static Field rmc$mobField;

    @Inject(
            method = {
                    "moveTo(DDDD)Z",
                    "m_26519_(DDDD)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$cancelCoordinateMove(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> callback) {
        Mob mob = rmc$mob();
        if (CorruptionMechanicsManager.shouldCancelNavigationMove(mob, null, new Vec3(x, y, z), speed, "coords")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                    "moveTo(Lnet/minecraft/world/entity/Entity;D)Z",
                    "m_5624_(Lnet/minecraft/world/entity/Entity;D)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$cancelEntityMove(Entity target, double speed, CallbackInfoReturnable<Boolean> callback) {
        Vec3 targetPosition = target == null ? null : target.position();
        if (CorruptionMechanicsManager.shouldCancelNavigationMove(rmc$mob(), target, targetPosition, speed, "entity")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                    "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z",
                    "m_26536_(Lnet/minecraft/world/level/pathfinder/Path;D)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$cancelPathMove(Path path, double speed, CallbackInfoReturnable<Boolean> callback) {
        if (CorruptionMechanicsManager.shouldCancelNavigationMove(rmc$mob(), null, null, speed, "path")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                    "tick()V",
                    "m_7638_()V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private void rmc$stopNavigationTick(CallbackInfo callback) {
        Mob mob = rmc$mob();
        if (CorruptionMechanicsManager.shouldStopNavigationTick(mob)) {
            ((PathNavigation) (Object) this).stop();
            callback.cancel();
        }
    }

    @ModifyVariable(
            method = {
                    "moveTo(DDDD)Z",
                    "m_26519_(DDDD)Z"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private double rmc$corruptCoordinateSpeed(double speed) {
        return CorruptionMechanicsManager.corruptNavigationSpeed(rmc$mob(), speed, "coords");
    }

    @ModifyVariable(
            method = {
                    "moveTo(Lnet/minecraft/world/entity/Entity;D)Z",
                    "m_5624_(Lnet/minecraft/world/entity/Entity;D)Z"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private double rmc$corruptEntitySpeed(double speed) {
        return CorruptionMechanicsManager.corruptNavigationSpeed(rmc$mob(), speed, "entity");
    }

    @ModifyVariable(
            method = {
                    "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z",
                    "m_26536_(Lnet/minecraft/world/level/pathfinder/Path;D)Z"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases.")
    private double rmc$corruptPathSpeed(double speed) {
        return CorruptionMechanicsManager.corruptNavigationSpeed(rmc$mob(), speed, "path");
    }

    @Unique
    private Mob rmc$mob() {
        try {
            Field field = rmc$mobField;
            if (field == null) {
                field = PathNavigation.class.getDeclaredField("mob");
                field.setAccessible(true);
                rmc$mobField = field;
            }
            Object value = field.get(this);
            return value instanceof Mob mob ? mob : null;
        } catch (ReflectiveOperationException namedFailure) {
            try {
                Field field = PathNavigation.class.getDeclaredField("f_26494_");
                field.setAccessible(true);
                rmc$mobField = field;
                Object value = field.get(this);
                return value instanceof Mob mob ? mob : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
