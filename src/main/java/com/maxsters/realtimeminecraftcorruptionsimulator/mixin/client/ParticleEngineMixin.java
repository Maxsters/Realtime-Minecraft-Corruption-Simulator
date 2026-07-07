package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.particles.ParticleCorruptionHooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
@SuppressWarnings("target")
public abstract class ParticleEngineMixin {
    @Inject(
            method = {
                    "tickParticle(Lnet/minecraft/client/particle/Particle;)V",
                    "m_107393_(Lnet/minecraft/client/particle/Particle;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ParticleEngine#tickParticle.")
    private void rmc$cullParticleBeforeTick(Particle particle, CallbackInfo callback) {
        if (ParticleCorruptionHooks.shouldCullParticleForBudget(particle)) {
            particle.remove();
            callback.cancel();
        }
    }

    @Inject(
            method = {
                    "tickParticle(Lnet/minecraft/client/particle/Particle;)V",
                    "m_107393_(Lnet/minecraft/client/particle/Particle;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ParticleEngine#tickParticle.")
    private void rmc$corruptTickedParticle(Particle particle, CallbackInfo callback) {
        ParticleCorruptionHooks.mutateTickedParticle(particle);
    }
}
