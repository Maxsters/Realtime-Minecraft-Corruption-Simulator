package com.maxsters.realtimeminecraftcorruptionsimulator.state;

public enum CorruptionBand {
    FUNCTIONAL_BASELINE(0, 5, "0-5% functional baseline"),
    TEXTURE_DRIFT(6, 10, "5-10% texture drift"),
    SOUND_DRIFT(11, 15, "10-15% mob sound drift"),
    GRAVITY_DRIFT(16, 20, "15-20% gravity drift"),
    TERRAIN_PERSISTENCE(21, 25, "20-25% persistent terrain faults"),
    RECOVERY_SAMPLING(26, 30, "25-30% recovery sampling"),
    PATHING_DRIFT(31, 35, "30-35% pathing drift"),
    ENTITY_RESPONSE(36, 40, "35-40% entity response drift"),
    PATTERN_RETENTION(41, 45, "40-45% pattern retention"),
    SAVE_STRAIN(46, 50, "45-50% save strain"),
    INTERFACE_LEAKAGE(51, 55, "50-55% interface leakage"),
    AUDIO_MEMORY(56, 60, "55-60% audio memory"),
    WORLD_MEMORY(61, 65, "60-65% world memory"),
    HIGH_INTENSITY(66, 70, "65-70% high intensity"),
    EXTREME_DISPLACEMENT(71, 85, "70-85% extreme displacement"),
    FULL_SIMULATION(86, 100, "85-100% full simulation");

    private final int minInclusive;
    private final int maxInclusive;
    private final String label;

    CorruptionBand(int minInclusive, int maxInclusive, String label) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.label = label;
    }

    public static CorruptionBand fromLevel(int level) {
        int clamped = Math.max(0, Math.min(100, level));
        for (CorruptionBand band : values()) {
            if (clamped >= band.minInclusive && clamped <= band.maxInclusive) {
                return band;
            }
        }
        return FULL_SIMULATION;
    }

    public String label() {
        return label;
    }
}
