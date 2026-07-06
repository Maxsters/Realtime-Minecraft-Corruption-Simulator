package com.maxsters.realtimeminecraftcorruptionsimulator.client.access;

public interface ParticleAccessor {
    double rmc$getX();

    double rmc$getY();

    double rmc$getZ();

    double rmc$getXd();

    double rmc$getYd();

    double rmc$getZd();

    float rmc$getGravity();

    float rmc$getBbWidth();

    float rmc$getBbHeight();

    float rmc$getRed();

    float rmc$getGreen();

    float rmc$getBlue();

    float rmc$getAlpha();

    float rmc$getRoll();

    float rmc$getFriction();

    int rmc$getLifetime();

    boolean rmc$getHasPhysics();

    boolean rmc$getSpeedUpWhenBlocked();

    void rmc$setGravity(float value);

    void rmc$setRoll(float value);

    void rmc$setFriction(float value);

    void rmc$setHasPhysics(boolean value);

    void rmc$setSpeedUpWhenBlocked(boolean value);

    void rmc$invokeSetSize(float width, float height);

    void rmc$invokeSetAlpha(float alpha);
}
