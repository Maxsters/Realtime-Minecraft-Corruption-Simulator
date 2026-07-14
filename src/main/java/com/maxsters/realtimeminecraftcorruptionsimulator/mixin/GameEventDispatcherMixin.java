package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameEventDispatcher.class)
@SuppressWarnings("target")
public abstract class GameEventDispatcherMixin {
    @Unique
    private ServerLevel rmc$level;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rmc$captureLevel(ServerLevel level, CallbackInfo callback) {
        rmc$level = level;
    }

    @Inject(
            method = {
                    "post(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
                    "m_245905_(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameEventDispatcher#post.")
    private void rmc$corruptGameEventDelivery(GameEvent gameEvent, Vec3 source, GameEvent.Context context, CallbackInfo callback) {
        if (CorruptionMechanicsManager.shouldSuppressGameEvent(rmc$level, gameEvent)) {
            callback.cancel();
        }
    }
}
