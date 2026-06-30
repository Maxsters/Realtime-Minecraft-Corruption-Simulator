package com.maxsters.realtimeminecraftcorruptionsimulator.achievements;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;

public final class AchievementRules {
    public static final int WARRANTY_VOIDED_CORRUPTION_LEVEL = 10;
    public static final int WARRANTY_VOIDED_REQUIRED_TARGETS = CorruptionTarget.ALL_MASK & ~CorruptionTarget.CAMERA.mask();

    private AchievementRules() {
    }

    public static boolean warrantySettingsActive(int corruptionLevel, int enabledTargetsMask) {
        return corruptionLevel == WARRANTY_VOIDED_CORRUPTION_LEVEL && warrantyTargetsActive(enabledTargetsMask);
    }

    public static boolean warrantyTargetsActive(int enabledTargetsMask) {
        int mask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        return (mask & WARRANTY_VOIDED_REQUIRED_TARGETS) == WARRANTY_VOIDED_REQUIRED_TARGETS;
    }
}
