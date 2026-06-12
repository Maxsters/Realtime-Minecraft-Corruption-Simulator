package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

import java.util.Locale;

public enum CorruptionTarget {
    CAMERA(0, "camera", "Camera", "View angle, roll, and FOV mutation."),
    MOBILITY(1, "mobility", "Mobility", "Player movement, collision, no-clip style, and animation timing mutation."),
    WORLD_VISUALS(2, "world_visuals", "World visuals", "Sky, fog, lighting, and biome color mutation."),
    TEXTURES(3, "textures", "Textures", "Texture memory, UV, atlas, font, and UI texture mutation."),
    AUDIO(4, "audio", "Audio", "Sound stream timing, position, and sample mutation."),
    GUI(5, "gui", "GUI", "Menu texture, widget, title screen, and input mutation."),
    ENTITY_BEHAVIOR(6, "entity_behavior", "Entity behavior", "Mob AI, entity state, speed, and spawn mutation."),
    PROJECTILES_AND_ITEMS(7, "projectiles_items", "Projectiles/items", "Projectile, dropped item, and impact mutation."),
    WORLD_MUTATION(8, "world_mutation", "World mutation", "World generation, block update, and interaction mutation."),
    MODELS(9, "models", "Models", "Block, item, entity, arm, and baked model geometry mutation.");

    public static final int ALL_MASK = allMask();

    private final int bit;
    private final String id;
    private final String label;
    private final String description;

    CorruptionTarget(int bit, String id, String label, String description) {
        this.bit = bit;
        this.id = id;
        this.label = label;
        this.description = description;
    }

    public int bit() {
        return bit;
    }

    public int mask() {
        return 1 << bit;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static int normalizeMask(int mask) {
        return mask & ALL_MASK;
    }

    public static boolean enabled(int mask, CorruptionTarget target) {
        return (normalizeMask(mask) & target.mask()) != 0;
    }

    public static CorruptionTarget byId(String id) {
        if (id == null || id.isBlank()) {
            return WORLD_VISUALS;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        if ("textures_models".equals(normalized)) {
            return TEXTURES;
        }
        for (CorruptionTarget target : values()) {
            if (target.id.equals(normalized)) {
                return target;
            }
        }
        return WORLD_VISUALS;
    }

    public static CorruptionTarget forSurface(CorruptionSurface surface) {
        return switch (surface) {
            case CAMERA_TRANSFORM -> CAMERA;
            case PLAYER_PHYSICS, BLOCK_COLLISION, ANIMATION_TIMING -> MOBILITY;
            case BIOME_TINT, LIGHT_FIELD, WORLD_RENDER -> WORLD_VISUALS;
            case MODEL_GEOMETRY -> MODELS;
            case MODEL_UV, TEXTURE_MEMORY -> TEXTURES;
            case SOUND_STREAM -> AUDIO;
            case GUI_SURFACE, TITLE_RENDER -> GUI;
            case ENTITY_KINEMATICS, ENTITY_STATE, SPAWN_RULES -> ENTITY_BEHAVIOR;
            case LOOSE_ENTITY_PHYSICS, PROJECTILE_PHYSICS, IMPACT_RESOLUTION -> PROJECTILES_AND_ITEMS;
            case WORLDGEN_SURFACE, INTERACTION_ROUTING, TICK_SPEED -> WORLD_MUTATION;
        };
    }

    private static int allMask() {
        int mask = 0;
        for (CorruptionTarget target : values()) {
            mask |= target.mask();
        }
        return mask;
    }
}
