package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ParticleCorruptionHooks;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class ParticleMixin {
    @Shadow(remap = false, aliases = "f_107226_")
    private float gravity;

    @Shadow(remap = false, aliases = "f_107216_")
    private double yd;

    @Inject(
            method = {
                    "tick()V",
                    "m_5989_()V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Particle#tick.")
    private void rmc$corruptParticleGravity(CallbackInfo callback) {
        Particle particle = (Particle) (Object) this;
        this.gravity = ParticleCorruptionHooks.mutateGravity(particle, this.gravity);
        this.yd = ParticleCorruptionHooks.mutateVerticalVelocity(particle, this.yd);
    }
}
