package com.maxsters.realtimeminecraftcorruptionsimulator.calibration;

import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;

public final class CorruptionCalibrationManager {
    private CorruptionCalibrationManager() {
    }

    public static CalibrationResult applyCorruptionLevel(CorruptionSavedData data, int requestedLevel) {
        int clampedLevel = clampPercent(requestedLevel);
        int previousLevel = data.getCorruptionLevel();
        int delta = Math.abs(clampedLevel - previousLevel);

        data.setPreviousCorruptionLevel(previousLevel);
        data.setCorruptionDelta(delta);
        data.setCorruptionLevel(clampedLevel);

        if (clampedLevel == 0) {
            int previousDebt = data.getStabilityDebt();
            int previousConfidence = data.getCalibrationConfidence();
            int previousCoherence = data.getProfileCoherence();
            data.setStabilityDebt(0);
            data.setCalibrationConfidence(100);
            data.setProfileCoherence(100);
            data.setEmergenceScore(0);
            data.setLastKnownSafeCorruptionLevel(0);
            return new CalibrationResult(previousLevel, 0, delta, -previousDebt, 100 - previousConfidence, 100 - previousCoherence);
        }

        int debtAdded = Math.min(100, StabilityDebtCalculator.calculateDeltaDebt(delta, data.getCalibrationConfidence()));
        int confidencePenalty = Math.min(32, ProfileCoherenceCalculator.confidencePenaltyForDelta(delta));
        int coherencePenalty = Math.min(36, ProfileCoherenceCalculator.coherencePenaltyForDebt(debtAdded));

        data.setStabilityDebt(data.getStabilityDebt() + debtAdded);
        data.setCalibrationConfidence(Math.max(45, data.getCalibrationConfidence() - confidencePenalty));
        data.setProfileCoherence(Math.max(45, data.getProfileCoherence() - coherencePenalty));
        data.setEmergenceScore(Math.min(100, data.getEmergenceScore() + Math.max(1, delta / 8)));

        if (data.getStabilityDebt() <= 35 && data.getProfileCoherence() >= 60 && data.getCalibrationConfidence() >= 55) {
            data.setLastKnownSafeCorruptionLevel(clampedLevel);
        }

        return new CalibrationResult(previousLevel, clampedLevel, delta, debtAdded, -confidencePenalty, -coherencePenalty);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
