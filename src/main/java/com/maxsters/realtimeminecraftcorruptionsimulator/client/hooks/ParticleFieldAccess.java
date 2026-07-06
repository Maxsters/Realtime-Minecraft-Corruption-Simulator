package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

@OnlyIn(Dist.CLIENT)
public final class ParticleFieldAccess {
    private static final VarHandle PARTICLE_X = findField(Particle.class, double.class, "x", "f_107212_");
    private static final VarHandle PARTICLE_Y = findField(Particle.class, double.class, "y", "f_107213_");
    private static final VarHandle PARTICLE_Z = findField(Particle.class, double.class, "z", "f_107214_");
    private static final VarHandle PARTICLE_XD = findField(Particle.class, double.class, "xd", "f_107215_");
    private static final VarHandle PARTICLE_YD = findField(Particle.class, double.class, "yd", "f_107216_");
    private static final VarHandle PARTICLE_ZD = findField(Particle.class, double.class, "zd", "f_107217_");
    private static final VarHandle PARTICLE_GRAVITY = findField(Particle.class, float.class, "gravity", "f_107226_");
    private static final VarHandle PARTICLE_WIDTH = findField(Particle.class, float.class, "bbWidth", "f_107221_");
    private static final VarHandle PARTICLE_HEIGHT = findField(Particle.class, float.class, "bbHeight", "f_107222_");
    private static final VarHandle PARTICLE_RED = findField(Particle.class, float.class, "rCol", "f_107227_");
    private static final VarHandle PARTICLE_GREEN = findField(Particle.class, float.class, "gCol", "f_107228_");
    private static final VarHandle PARTICLE_BLUE = findField(Particle.class, float.class, "bCol", "f_107229_");
    private static final VarHandle PARTICLE_ALPHA = findField(Particle.class, float.class, "alpha", "f_107230_");
    private static final VarHandle PARTICLE_ROLL = findField(Particle.class, float.class, "roll", "f_107231_");
    private static final VarHandle PARTICLE_FRICTION = findField(Particle.class, float.class, "friction", "f_172258_");
    private static final VarHandle PARTICLE_LIFETIME = findField(Particle.class, int.class, "lifetime", "f_107225_");
    private static final VarHandle PARTICLE_HAS_PHYSICS = findField(Particle.class, boolean.class, "hasPhysics", "f_107219_");
    private static final VarHandle PARTICLE_SPEED_UP_WHEN_BLOCKED = findField(Particle.class, boolean.class, "speedUpWhenYMotionIsBlocked", "f_172259_");
    private static final MethodHandle PARTICLE_SET_SIZE = findMethod(Particle.class, MethodType.methodType(void.class, float.class, float.class), "setSize", "m_107250_");
    private static final MethodHandle PARTICLE_SET_ALPHA = findMethod(Particle.class, MethodType.methodType(void.class, float.class), "setAlpha", "m_107271_");
    private static final VarHandle SINGLE_QUAD_SIZE = findField(SingleQuadParticle.class, float.class, "quadSize", "f_107663_");
    private static final VarHandle TEXTURE_SHEET_SPRITE = findField(TextureSheetParticle.class, TextureAtlasSprite.class, "sprite", "f_108321_");
    private static final MethodHandle TEXTURE_SHEET_SET_SPRITE = findMethod(TextureSheetParticle.class, MethodType.methodType(void.class, TextureAtlasSprite.class), "setSprite", "m_108337_");

    private static final boolean PARTICLE_AVAILABLE = PARTICLE_X != null
            && PARTICLE_Y != null
            && PARTICLE_Z != null
            && PARTICLE_XD != null
            && PARTICLE_YD != null
            && PARTICLE_ZD != null
            && PARTICLE_GRAVITY != null
            && PARTICLE_WIDTH != null
            && PARTICLE_HEIGHT != null
            && PARTICLE_RED != null
            && PARTICLE_GREEN != null
            && PARTICLE_BLUE != null
            && PARTICLE_ALPHA != null
            && PARTICLE_ROLL != null
            && PARTICLE_FRICTION != null
            && PARTICLE_LIFETIME != null
            && PARTICLE_HAS_PHYSICS != null
            && PARTICLE_SPEED_UP_WHEN_BLOCKED != null
            && PARTICLE_SET_SIZE != null
            && PARTICLE_SET_ALPHA != null;

    private ParticleFieldAccess() {
    }

    public static boolean particleAvailable() {
        return PARTICLE_AVAILABLE;
    }

    public static boolean singleQuadAvailable() {
        return SINGLE_QUAD_SIZE != null;
    }

    public static boolean textureSheetAvailable() {
        return TEXTURE_SHEET_SPRITE != null;
    }

    public static double particleX(Particle particle) {
        return (double) PARTICLE_X.get(particle);
    }

    public static double particleY(Particle particle) {
        return (double) PARTICLE_Y.get(particle);
    }

    public static double particleZ(Particle particle) {
        return (double) PARTICLE_Z.get(particle);
    }

    public static double particleXd(Particle particle) {
        return (double) PARTICLE_XD.get(particle);
    }

    public static double particleYd(Particle particle) {
        return (double) PARTICLE_YD.get(particle);
    }

    public static double particleZd(Particle particle) {
        return (double) PARTICLE_ZD.get(particle);
    }

    public static float particleGravity(Particle particle) {
        return (float) PARTICLE_GRAVITY.get(particle);
    }

    public static float particleWidth(Particle particle) {
        return (float) PARTICLE_WIDTH.get(particle);
    }

    public static float particleHeight(Particle particle) {
        return (float) PARTICLE_HEIGHT.get(particle);
    }

    public static float particleRed(Particle particle) {
        return (float) PARTICLE_RED.get(particle);
    }

    public static float particleGreen(Particle particle) {
        return (float) PARTICLE_GREEN.get(particle);
    }

    public static float particleBlue(Particle particle) {
        return (float) PARTICLE_BLUE.get(particle);
    }

    public static float particleAlpha(Particle particle) {
        return (float) PARTICLE_ALPHA.get(particle);
    }

    public static float particleRoll(Particle particle) {
        return (float) PARTICLE_ROLL.get(particle);
    }

    public static float particleFriction(Particle particle) {
        return (float) PARTICLE_FRICTION.get(particle);
    }

    public static int particleLifetime(Particle particle) {
        return (int) PARTICLE_LIFETIME.get(particle);
    }

    public static boolean particleHasPhysics(Particle particle) {
        return (boolean) PARTICLE_HAS_PHYSICS.get(particle);
    }

    public static boolean particleSpeedUpWhenBlocked(Particle particle) {
        return (boolean) PARTICLE_SPEED_UP_WHEN_BLOCKED.get(particle);
    }

    public static void setParticleGravity(Particle particle, float value) {
        PARTICLE_GRAVITY.set(particle, value);
    }

    public static void setParticleRoll(Particle particle, float value) {
        PARTICLE_ROLL.set(particle, value);
    }

    public static void setParticleFriction(Particle particle, float value) {
        PARTICLE_FRICTION.set(particle, value);
    }

    public static void setParticleHasPhysics(Particle particle, boolean value) {
        PARTICLE_HAS_PHYSICS.set(particle, value);
    }

    public static void setParticleSpeedUpWhenBlocked(Particle particle, boolean value) {
        PARTICLE_SPEED_UP_WHEN_BLOCKED.set(particle, value);
    }

    public static void invokeParticleSetSize(Particle particle, float width, float height) {
        try {
            PARTICLE_SET_SIZE.invokeExact(particle, width, height);
        } catch (Throwable ignored) {
        }
    }

    public static void invokeParticleSetAlpha(Particle particle, float alpha) {
        try {
            PARTICLE_SET_ALPHA.invokeExact(particle, alpha);
        } catch (Throwable ignored) {
        }
    }

    public static float singleQuadSize(SingleQuadParticle particle) {
        return (float) SINGLE_QUAD_SIZE.get(particle);
    }

    public static void setSingleQuadSize(SingleQuadParticle particle, float value) {
        SINGLE_QUAD_SIZE.set(particle, value);
    }

    public static TextureAtlasSprite textureSheetSprite(TextureSheetParticle particle) {
        return (TextureAtlasSprite) TEXTURE_SHEET_SPRITE.get(particle);
    }

    public static void setTextureSheetSprite(TextureSheetParticle particle, TextureAtlasSprite sprite) {
        if (TEXTURE_SHEET_SET_SPRITE != null) {
            try {
                TEXTURE_SHEET_SET_SPRITE.invokeExact(particle, sprite);
                return;
            } catch (Throwable ignored) {
            }
        }
        TEXTURE_SHEET_SPRITE.set(particle, sprite);
    }

    private static VarHandle findField(Class<?> owner, Class<?> type, String... names) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            for (String name : names) {
                try {
                    return lookup.findVarHandle(owner, name, type);
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static MethodHandle findMethod(Class<?> owner, MethodType type, String... names) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            for (String name : names) {
                try {
                    return lookup.findVirtual(owner, name, type);
                } catch (NoSuchMethodException | IllegalAccessException ignored) {
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }
}
