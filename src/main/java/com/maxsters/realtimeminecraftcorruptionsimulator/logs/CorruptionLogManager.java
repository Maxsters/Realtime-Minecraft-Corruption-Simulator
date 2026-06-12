package com.maxsters.realtimeminecraftcorruptionsimulator.logs;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class CorruptionLogManager {
    private CorruptionLogManager() {
    }

    public static List<String> buildStatusLines(CorruptionProfileSnapshot snapshot) {
        List<String> logs = new ArrayList<>();
        logs.add("Active corruption: " + snapshot.getCorruptionLevel() + "%.");
        logs.add("Seed: " + snapshot.getCorruptionSeedLabel() + ".");
        logs.add("Target areas enabled: " + countEnabledTargets(snapshot.getEnabledTargetsMask()) + "/" + CorruptionTarget.values().length + ".");
        if (snapshot.getCorruptionDelta() > 0) {
            logs.add("Last live change: " + snapshot.getCorruptionDelta() + "%.");
        } else {
            logs.add("No live level change has been applied in this world yet.");
        }
        if (snapshot.getStabilityDebt() >= 50) {
            logs.add("Large recent changes are increasing mutation variance.");
        } else if (snapshot.getCalibrationConfidence() < 65) {
            logs.add("Small level adjustments will keep results easier to compare.");
        } else {
            logs.add("Runtime settings are synchronized.");
        }
        return logs;
    }

    private static int countEnabledTargets(int mask) {
        int count = 0;
        for (CorruptionTarget target : CorruptionTarget.values()) {
            if (CorruptionTarget.enabled(mask, target)) {
                count++;
            }
        }
        return count;
    }
}
