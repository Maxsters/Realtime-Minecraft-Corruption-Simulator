package com.maxsters.realtimeminecraftcorruptionsimulator.runtime;

import com.maxsters.realtimeminecraftcorruptionsimulator.calibration.CorruptionCalibrationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;

public final class CorruptionRuntimeManager {
    private CorruptionRuntimeManager() {
    }

    public static boolean syncGlobalLevel(CorruptionSavedData data) {
        return syncGlobalSettings(data);
    }

    public static boolean syncGlobalSettings(CorruptionSavedData data) {
        int activeLevel = GlobalCorruptionSettings.activeLevel();
        boolean changed = false;
        if (data.getFixedCorruptionSeed() != GlobalCorruptionSettings.seed()
                || !data.getCorruptionSeedLabel().equals(GlobalCorruptionSettings.seedLabel())) {
            data.setCorruptionSeed(GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.seedLabel());
            changed = true;
        }
        if (data.getEnabledTargetsMask() != GlobalCorruptionSettings.enabledTargetsMask()) {
            data.setEnabledTargetsMask(GlobalCorruptionSettings.enabledTargetsMask());
            changed = true;
        }
        if (data.getAutoIncreaseIntervalTicks() != GlobalCorruptionSettings.autoIncreaseIntervalTicks()) {
            data.setAutoIncreaseIntervalTicks(GlobalCorruptionSettings.autoIncreaseIntervalTicks());
            changed = true;
        }
        if (data.getAutoIncreaseAmount() != GlobalCorruptionSettings.autoIncreaseAmount()) {
            data.setAutoIncreaseAmount(GlobalCorruptionSettings.autoIncreaseAmount());
            changed = true;
        }
        if (data.getCorruptionLevel() != activeLevel) {
            CorruptionCalibrationManager.applyCorruptionLevel(data, activeLevel);
            changed = true;
        }
        return changed;
    }
}
