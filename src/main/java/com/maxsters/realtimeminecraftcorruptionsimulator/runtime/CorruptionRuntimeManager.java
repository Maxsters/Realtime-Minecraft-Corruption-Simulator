package com.maxsters.realtimeminecraftcorruptionsimulator.runtime;

import com.maxsters.realtimeminecraftcorruptionsimulator.calibration.CorruptionCalibrationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;

public final class CorruptionRuntimeManager {
    private CorruptionRuntimeManager() {
    }

    public static boolean syncGlobalLevel(CorruptionSavedData data) {
        return applySavedDataToGlobalSettings(data);
    }

    public static boolean copyGlobalSettingsToData(CorruptionSavedData data) {
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

    public static boolean applySavedDataToGlobalSettings(CorruptionSavedData data) {
        if (data == null) {
            return false;
        }
        int activeLevel = GlobalCorruptionSettings.activeLevel();
        long seed = GlobalCorruptionSettings.seed();
        String seedLabel = GlobalCorruptionSettings.seedLabel();
        int enabledTargetsMask = GlobalCorruptionSettings.enabledTargetsMask();
        int autoIncreaseIntervalTicks = GlobalCorruptionSettings.autoIncreaseIntervalTicks();
        int autoIncreaseAmount = GlobalCorruptionSettings.autoIncreaseAmount();
        if (activeLevel == data.getCorruptionLevel()
                && seed == data.getFixedCorruptionSeed()
                && seedLabel.equals(data.getCorruptionSeedLabel())
                && enabledTargetsMask == data.getEnabledTargetsMask()
                && autoIncreaseIntervalTicks == data.getAutoIncreaseIntervalTicks()
                && autoIncreaseAmount == data.getAutoIncreaseAmount()) {
            return false;
        }
        GlobalCorruptionSettings.apply(
                data.getCorruptionLevel(),
                data.getFixedCorruptionSeed(),
                data.getCorruptionSeedLabel(),
                data.getEnabledTargetsMask(),
                data.getAutoIncreaseIntervalTicks(),
                data.getAutoIncreaseAmount()
        );
        return true;
    }
}
