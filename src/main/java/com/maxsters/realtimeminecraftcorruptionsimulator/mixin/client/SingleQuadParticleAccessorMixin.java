package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.SingleQuadParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.particles.ParticleFieldAccess;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleAccessorMixin implements SingleQuadParticleAccessor {
    @Override
    public float rmc$getQuadSize() {
        return ParticleFieldAccess.singleQuadSize(rmc$self());
    }

    @Override
    public void rmc$setQuadSize(float value) {
        ParticleFieldAccess.setSingleQuadSize(rmc$self(), value);
    }

    private SingleQuadParticle rmc$self() {
        return (SingleQuadParticle) (Object) this;
    }
}
