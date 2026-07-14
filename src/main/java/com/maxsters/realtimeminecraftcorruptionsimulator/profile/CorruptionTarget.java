package com.maxsters.realtimeminecraftcorruptionsimulator.profile;

import java.util.Locale;

public enum CorruptionTarget {
    CAMERA(0, "camera", "Camera", "How the game controls your view."),
    MOBILITY(1, "mobility", "Mobility", "How movement through the world works."),
    WORLD_VISUALS(2, "world_visuals", "World Visuals", "How the world is drawn around you."),
    TEXTURES(3, "textures", "Textures", "The textures used throughout the game world."),
    AUDIO(4, "audio", "Audio", "How sounds are chosen and played."),
    GUI(5, "gui", "GUI", "How menus and on-screen interfaces work and look."),
    ENTITY_BEHAVIOR(6, "entity_behavior", "Entities & Timing", "How entities and events behave."),
    PROJECTILES_AND_ITEMS(7, "projectiles_items", "Items & Actions", "How items and block actions work."),
    WORLD_MUTATION(8, "world_mutation", "Worldgen & Terrain", "The rules that shape newly generated terrain."),
    MODELS(9, "models", "Models", "The geometry used to render objects.");

    private static final int LEGACY_FIRE_TARGET_MASK = 1 << 10;
    private static final int LEGACY_POWDER_SNOW_TARGET_MASK = 1 << 11;
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
        int normalized = mask & ALL_MASK;
        if ((mask & LEGACY_FIRE_TARGET_MASK) != 0) {
            normalized |= ENTITY_BEHAVIOR.mask();
        }
        if ((mask & LEGACY_POWDER_SNOW_TARGET_MASK) != 0) {
            normalized |= MOBILITY.mask();
        }
        return normalized;
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
        if ("fire".equals(normalized)) {
            return ENTITY_BEHAVIOR;
        }
        if ("powder_snow".equals(normalized)) {
            return MOBILITY;
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
            case PLAYER_PHYSICS, BLOCK_COLLISION -> MOBILITY;
            case BIOME_TINT, LIGHT_FIELD, WORLD_RENDER -> WORLD_VISUALS;
            case MODEL_GEOMETRY -> MODELS;
            case MODEL_UV, TEXTURE_MEMORY -> TEXTURES;
            case SOUND_STREAM -> AUDIO;
            case GUI_SURFACE, GUI_FUNCTIONALITY, TITLE_RENDER -> GUI;
            case ENTITY_KINEMATICS, ENTITY_STATE, SPAWN_RULES, ANIMATION_TIMING, TICK_SPEED, LOOSE_ENTITY_PHYSICS, PROJECTILE_PHYSICS -> ENTITY_BEHAVIOR;
            case LAUNCH_DIRECTION, IMPACT_RESOLUTION, INTERACTION_ROUTING, REDSTONE_MECHANICS, BLOCK_BEHAVIOR -> PROJECTILES_AND_ITEMS;
            case WORLDGEN_SURFACE -> WORLD_MUTATION;
            case FIRE_MECHANICS -> ENTITY_BEHAVIOR;
            case POWDER_SNOW_MECHANICS -> MOBILITY;
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
