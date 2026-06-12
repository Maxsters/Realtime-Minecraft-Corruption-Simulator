package com.maxsters.realtimeminecraftcorruptionsimulator.calibration;

public final class StabilityDebtCalculator {
    private StabilityDebtCalculator() {
    }

    public static int calculateDeltaDebt(int delta, int calibrationConfidence) {
        int calibratedRange = getCalibratedRange(calibrationConfidence);
        if (delta <= calibratedRange) {
            return Math.max(0, delta / 8);
        }
        int uncontrolledDelta = delta - calibratedRange;
        return Math.min(100, uncontrolledDelta * 2 + delta / 6);
    }

    public static int getCalibratedRange(int calibrationConfidence) {
        int confidence = Math.max(0, Math.min(100, calibrationConfidence));
        return 4 + confidence / 8;
    }
}
