package com.maxsters.realtimeminecraftcorruptionsimulator.logs;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class CorruptionLogManager {
    private CorruptionLogManager() {
    }

    public static List<String> buildStatusLines(CorruptionStateSnapshot snapshot) {
        List<String> logs = new ArrayList<>();
        logs.add("Active corruption: " + snapshot.getCorruptionLevel() + "%.");
        logs.add("Seed: " + snapshot.getCorruptionSeedLabel() + ".");
        logs.add("Target areas enabled: " + countEnabledTargets(snapshot.getEnabledTargetsMask()) + "/" + CorruptionTarget.values().length + ".");
        logs.add("Runtime settings are synchronized.");
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
