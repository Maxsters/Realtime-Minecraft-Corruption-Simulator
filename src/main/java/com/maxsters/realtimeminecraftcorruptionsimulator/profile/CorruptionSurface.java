package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

public enum CorruptionSurface {
    ENTITY_KINEMATICS(0.86F, 0.34F, 0.18F, 0.18F, CorruptionOperation.AMPLIFY, CorruptionOperation.DAMPEN, CorruptionOperation.STUTTER, CorruptionOperation.DRIFT),
    SOUND_STREAM(0.82F, 0.30F, 0.18F, 0.24F, CorruptionOperation.AMPLIFY, CorruptionOperation.DAMPEN, CorruptionOperation.STUTTER, CorruptionOperation.ECHO),
    BIOME_TINT(0.88F, 0.26F, 0.14F, 0.12F, CorruptionOperation.REMAP, CorruptionOperation.DAMPEN, CorruptionOperation.INVERT, CorruptionOperation.SMEAR),
    CAMERA_TRANSFORM(0.78F, 0.36F, 0.24F, 0.10F, CorruptionOperation.OFFSET, CorruptionOperation.DRIFT, CorruptionOperation.STUTTER, CorruptionOperation.FOLD),
    MODEL_GEOMETRY(0.76F, 0.34F, 0.20F, 0.34F, CorruptionOperation.AMPLIFY, CorruptionOperation.DAMPEN, CorruptionOperation.OFFSET, CorruptionOperation.FOLD, CorruptionOperation.INVERT),
    ANIMATION_TIMING(0.74F, 0.40F, 0.30F, 0.36F, CorruptionOperation.DRIFT, CorruptionOperation.STUTTER, CorruptionOperation.DESYNC, CorruptionOperation.FOLD, CorruptionOperation.OFFSET),
    TICK_SPEED(0.72F, 0.44F, 0.32F, 0.34F, CorruptionOperation.AMPLIFY, CorruptionOperation.DAMPEN, CorruptionOperation.STUTTER, CorruptionOperation.DESYNC, CorruptionOperation.OFFSET),
    TITLE_RENDER(0.62F, 0.40F, 0.22F, 0.12F, CorruptionOperation.ECHO, CorruptionOperation.SMEAR, CorruptionOperation.STUTTER),
    PLAYER_PHYSICS(0.80F, 0.36F, 0.24F, 0.18F, CorruptionOperation.DAMPEN, CorruptionOperation.DRIFT, CorruptionOperation.OFFSET, CorruptionOperation.DESYNC),
    LIGHT_FIELD(0.74F, 0.36F, 0.22F, 0.24F, CorruptionOperation.REMAP, CorruptionOperation.SMEAR, CorruptionOperation.FOLD, CorruptionOperation.NOISE),
    MODEL_UV(0.72F, 0.34F, 0.18F, 0.42F, CorruptionOperation.REMAP, CorruptionOperation.FOLD, CorruptionOperation.SMEAR, CorruptionOperation.INVERT),
    TEXTURE_MEMORY(0.82F, 0.42F, 0.24F, 0.48F, CorruptionOperation.REMAP, CorruptionOperation.SMEAR, CorruptionOperation.FOLD, CorruptionOperation.INVERT),
    WORLD_RENDER(0.70F, 0.38F, 0.24F, 0.20F, CorruptionOperation.SMEAR, CorruptionOperation.FOLD, CorruptionOperation.ECHO, CorruptionOperation.DRIFT),
    LOOSE_ENTITY_PHYSICS(0.78F, 0.40F, 0.26F, 0.24F, CorruptionOperation.DRIFT, CorruptionOperation.STUTTER, CorruptionOperation.OFFSET),
    WORLDGEN_SURFACE(0.66F, 0.42F, 0.20F, 0.38F, CorruptionOperation.REPLACE, CorruptionOperation.SMEAR, CorruptionOperation.FOLD),
    ENTITY_STATE(0.76F, 0.42F, 0.28F, 0.34F, CorruptionOperation.DESYNC, CorruptionOperation.STUTTER, CorruptionOperation.DAMPEN, CorruptionOperation.REPLACE),
    INTERACTION_ROUTING(0.78F, 0.46F, 0.30F, 0.38F, CorruptionOperation.DESYNC, CorruptionOperation.STUTTER, CorruptionOperation.INVERT, CorruptionOperation.REPLACE, CorruptionOperation.DAMPEN),
    BLOCK_COLLISION(0.74F, 0.46F, 0.30F, 0.38F, CorruptionOperation.DESYNC, CorruptionOperation.FOLD, CorruptionOperation.DAMPEN, CorruptionOperation.NOISE),
    SPAWN_RULES(0.70F, 0.48F, 0.28F, 0.30F, CorruptionOperation.INVERT, CorruptionOperation.REPLACE, CorruptionOperation.STUTTER, CorruptionOperation.DESYNC),
    GUI_SURFACE(0.72F, 0.44F, 0.24F, 0.30F, CorruptionOperation.NOISE, CorruptionOperation.SMEAR, CorruptionOperation.REMAP, CorruptionOperation.STUTTER),
    PROJECTILE_PHYSICS(0.74F, 0.46F, 0.28F, 0.34F, CorruptionOperation.DRIFT, CorruptionOperation.OFFSET, CorruptionOperation.DESYNC, CorruptionOperation.STUTTER),
    IMPACT_RESOLUTION(0.68F, 0.48F, 0.30F, 0.30F, CorruptionOperation.DESYNC, CorruptionOperation.ECHO, CorruptionOperation.STUTTER),
    FIRE_MECHANICS(0.76F, 0.48F, 0.32F, 0.36F, CorruptionOperation.DESYNC, CorruptionOperation.STUTTER, CorruptionOperation.INVERT, CorruptionOperation.DAMPEN, CorruptionOperation.AMPLIFY),
    POWDER_SNOW_MECHANICS(0.74F, 0.46F, 0.32F, 0.34F, CorruptionOperation.DESYNC, CorruptionOperation.STUTTER, CorruptionOperation.INVERT, CorruptionOperation.DAMPEN, CorruptionOperation.AMPLIFY);

    private final float affinity;
    private final float instabilityBias;
    private final float entropyBias;
    private final float targetBias;
    private final CorruptionOperation[] operations;

    // affinity: how readily the surface activates; instability/entropy/target tune its failure shape.
    CorruptionSurface(float affinity, float instabilityBias, float entropyBias, float targetBias, CorruptionOperation... operations) {
        this.affinity = affinity;
        this.instabilityBias = instabilityBias;
        this.entropyBias = entropyBias;
        this.targetBias = targetBias;
        this.operations = operations;
    }

    public int salt() {
        return 0x5304_0000 ^ name().hashCode();
    }

    public float affinity() {
        return affinity;
    }

    public float instabilityBias() {
        return instabilityBias;
    }

    public float entropyBias() {
        return entropyBias;
    }

    public float targetBias() {
        return targetBias;
    }

    public CorruptionOperation operationFor(long hash) {
        if (operations.length == 0) {
            return CorruptionOperation.NOISE;
        }
        return operations[Math.floorMod((int) (hash ^ (hash >>> 32)), operations.length)];
    }
}
