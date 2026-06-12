package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionProfileManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCorruptionState {
    private static volatile CorruptionProfileSnapshot latestSnapshot = localSnapshot();

    private ClientCorruptionState() {
    }

    public static CorruptionProfileSnapshot snapshot() {
        return latestSnapshot;
    }

    public static void applySnapshot(CorruptionProfileSnapshot snapshot) {
        latestSnapshot = snapshot == null ? localSnapshot() : snapshot;
    }

    public static void reset() {
        latestSnapshot = localSnapshot();
    }

    public static CorruptionProfileSnapshot localSnapshot() {
        int activeLevel = GlobalCorruptionSettings.activeLevel();
        return new CorruptionProfileSnapshot(
                activeLevel,
                activeLevel,
                0,
                100,
                0,
                100,
                0,
                activeLevel,
                CorruptionProfileManager.DEFAULT_PROFILE.id(),
                GlobalCorruptionSettings.seed(),
                CorruptionSavedData.sanitizeSeedLabel(GlobalCorruptionSettings.seedLabel(), GlobalCorruptionSettings.seed()),
                GlobalCorruptionSettings.enabledTargetsMask(),
                GlobalCorruptionSettings.autoIncreaseIntervalTicks(),
                GlobalCorruptionSettings.autoIncreaseAmount()
        );
    }
}
