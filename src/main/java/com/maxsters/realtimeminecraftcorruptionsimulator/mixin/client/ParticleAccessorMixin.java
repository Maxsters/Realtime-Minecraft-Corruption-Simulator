package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.ParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.particles.ParticleFieldAccess;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Particle.class)
public abstract class ParticleAccessorMixin implements ParticleAccessor {
    @Override
    public double rmc$getX() {
        return ParticleFieldAccess.particleX(rmc$self());
    }

    @Override
    public double rmc$getY() {
        return ParticleFieldAccess.particleY(rmc$self());
    }

    @Override
    public double rmc$getZ() {
        return ParticleFieldAccess.particleZ(rmc$self());
    }

    @Override
    public double rmc$getXd() {
        return ParticleFieldAccess.particleXd(rmc$self());
    }

    @Override
    public double rmc$getYd() {
        return ParticleFieldAccess.particleYd(rmc$self());
    }

    @Override
    public double rmc$getZd() {
        return ParticleFieldAccess.particleZd(rmc$self());
    }

    @Override
    public float rmc$getGravity() {
        return ParticleFieldAccess.particleGravity(rmc$self());
    }

    @Override
    public float rmc$getBbWidth() {
        return ParticleFieldAccess.particleWidth(rmc$self());
    }

    @Override
    public float rmc$getBbHeight() {
        return ParticleFieldAccess.particleHeight(rmc$self());
    }

    @Override
    public float rmc$getRed() {
        return ParticleFieldAccess.particleRed(rmc$self());
    }

    @Override
    public float rmc$getGreen() {
        return ParticleFieldAccess.particleGreen(rmc$self());
    }

    @Override
    public float rmc$getBlue() {
        return ParticleFieldAccess.particleBlue(rmc$self());
    }

    @Override
    public float rmc$getAlpha() {
        return ParticleFieldAccess.particleAlpha(rmc$self());
    }

    @Override
    public float rmc$getRoll() {
        return ParticleFieldAccess.particleRoll(rmc$self());
    }

    @Override
    public float rmc$getFriction() {
        return ParticleFieldAccess.particleFriction(rmc$self());
    }

    @Override
    public int rmc$getLifetime() {
        return ParticleFieldAccess.particleLifetime(rmc$self());
    }

    @Override
    public boolean rmc$getHasPhysics() {
        return ParticleFieldAccess.particleHasPhysics(rmc$self());
    }

    @Override
    public boolean rmc$getSpeedUpWhenBlocked() {
        return ParticleFieldAccess.particleSpeedUpWhenBlocked(rmc$self());
    }

    @Override
    public void rmc$setGravity(float value) {
        ParticleFieldAccess.setParticleGravity(rmc$self(), value);
    }

    @Override
    public void rmc$setRoll(float value) {
        ParticleFieldAccess.setParticleRoll(rmc$self(), value);
    }

    @Override
    public void rmc$setFriction(float value) {
        ParticleFieldAccess.setParticleFriction(rmc$self(), value);
    }

    @Override
    public void rmc$setHasPhysics(boolean value) {
        ParticleFieldAccess.setParticleHasPhysics(rmc$self(), value);
    }

    @Override
    public void rmc$setSpeedUpWhenBlocked(boolean value) {
        ParticleFieldAccess.setParticleSpeedUpWhenBlocked(rmc$self(), value);
    }

    @Override
    public void rmc$invokeSetSize(float width, float height) {
        ParticleFieldAccess.invokeParticleSetSize(rmc$self(), width, height);
    }

    @Override
    public void rmc$invokeSetAlpha(float alpha) {
        ParticleFieldAccess.invokeParticleSetAlpha(rmc$self(), alpha);
    }

    private Particle rmc$self() {
        return (Particle) (Object) this;
    }
}
