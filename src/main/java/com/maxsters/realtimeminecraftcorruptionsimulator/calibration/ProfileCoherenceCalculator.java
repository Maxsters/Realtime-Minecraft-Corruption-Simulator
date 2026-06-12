package com.maxsters.realtimeminecraftcorruptionsimulator.calibration;

public final class ProfileCoherenceCalculator {
    private ProfileCoherenceCalculator() {
    }

    public static int coherencePenaltyForDebt(int stabilityDebtAdded) {
        if (stabilityDebtAdded <= 0) {
            return 0;
        }
        return Math.min(40, stabilityDebtAdded / 2 + stabilityDebtAdded / 5);
    }

    public static int confidencePenaltyForDelta(int corruptionDelta) {
        if (corruptionDelta <= 8) {
            return 0;
        }
        return Math.min(35, (corruptionDelta - 8) / 2);
    }
}
