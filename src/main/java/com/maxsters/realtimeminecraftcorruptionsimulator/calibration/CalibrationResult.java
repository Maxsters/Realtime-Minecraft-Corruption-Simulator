package com.maxsters.realtimeminecraftcorruptionsimulator.calibration;

public record CalibrationResult(
        int previousLevel,
        int requestedLevel,
        int corruptionDelta,
        int stabilityDebtAdded,
        int confidenceChange,
        int coherenceChange
) {
}
