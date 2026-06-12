package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import net.minecraft.network.FriendlyByteBuf;

public record CorruptionProfileSnapshot(
        int corruptionLevel,
        int previousCorruptionLevel,
        int corruptionDelta,
        int calibrationConfidence,
        int stabilityDebt,
        int profileCoherence,
        int emergenceScore,
        int lastKnownSafeCorruptionLevel,
        String activeProfile,
        long fixedCorruptionSeed,
        String corruptionSeedLabel,
        int enabledTargetsMask,
        int autoIncreaseIntervalTicks,
        int autoIncreaseAmount
) {
    public CorruptionProfileSnapshot {
        corruptionLevel = clampPercent(corruptionLevel);
        previousCorruptionLevel = clampPercent(previousCorruptionLevel);
        corruptionDelta = clampPercent(corruptionDelta);
        calibrationConfidence = clampPercent(calibrationConfidence);
        stabilityDebt = clampPercent(stabilityDebt);
        profileCoherence = clampPercent(profileCoherence);
        emergenceScore = clampPercent(emergenceScore);
        lastKnownSafeCorruptionLevel = clampPercent(lastKnownSafeCorruptionLevel);
        activeProfile = activeProfile == null || activeProfile.isBlank() ? "REALTIME_CORRUPTION_SIMULATOR" : activeProfile;
        corruptionSeedLabel = CorruptionSavedData.sanitizeSeedLabel(corruptionSeedLabel, fixedCorruptionSeed);
        enabledTargetsMask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        autoIncreaseIntervalTicks = clampIntervalTicks(autoIncreaseIntervalTicks);
        autoIncreaseAmount = clampAutoAmount(autoIncreaseAmount);
    }

    public static CorruptionProfileSnapshot from(CorruptionSavedData data) {
        return new CorruptionProfileSnapshot(
                data.getCorruptionLevel(),
                data.getPreviousCorruptionLevel(),
                data.getCorruptionDelta(),
                data.getCalibrationConfidence(),
                data.getStabilityDebt(),
                data.getProfileCoherence(),
                data.getEmergenceScore(),
                data.getLastKnownSafeCorruptionLevel(),
                data.getActiveProfile(),
                data.getFixedCorruptionSeed(),
                data.getCorruptionSeedLabel(),
                data.getEnabledTargetsMask(),
                data.getAutoIncreaseIntervalTicks(),
                data.getAutoIncreaseAmount()
        );
    }

    public static CorruptionProfileSnapshot decode(FriendlyByteBuf buffer) {
        int corruptionLevel = buffer.readVarInt();
        int previousCorruptionLevel = buffer.readVarInt();
        int corruptionDelta = buffer.readVarInt();
        int calibrationConfidence = buffer.readVarInt();
        int stabilityDebt = buffer.readVarInt();
        int profileCoherence = buffer.readVarInt();
        int emergenceScore = buffer.readVarInt();
        int lastKnownSafeCorruptionLevel = buffer.readVarInt();
        String activeProfile = buffer.readUtf(64);
        long fixedCorruptionSeed = buffer.readLong();
        String corruptionSeedLabel = buffer.readUtf(96);
        int enabledTargetsMask = buffer.readVarInt();
        int autoIncreaseIntervalTicks = buffer.readVarInt();
        int autoIncreaseAmount = buffer.readVarInt();
        return new CorruptionProfileSnapshot(
                corruptionLevel,
                previousCorruptionLevel,
                corruptionDelta,
                calibrationConfidence,
                stabilityDebt,
                profileCoherence,
                emergenceScore,
                lastKnownSafeCorruptionLevel,
                activeProfile,
                fixedCorruptionSeed,
                corruptionSeedLabel,
                enabledTargetsMask,
                autoIncreaseIntervalTicks,
                autoIncreaseAmount
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(corruptionLevel);
        buffer.writeVarInt(previousCorruptionLevel);
        buffer.writeVarInt(corruptionDelta);
        buffer.writeVarInt(calibrationConfidence);
        buffer.writeVarInt(stabilityDebt);
        buffer.writeVarInt(profileCoherence);
        buffer.writeVarInt(emergenceScore);
        buffer.writeVarInt(lastKnownSafeCorruptionLevel);
        buffer.writeUtf(activeProfile);
        buffer.writeLong(fixedCorruptionSeed);
        buffer.writeUtf(corruptionSeedLabel);
        buffer.writeVarInt(enabledTargetsMask);
        buffer.writeVarInt(autoIncreaseIntervalTicks);
        buffer.writeVarInt(autoIncreaseAmount);
    }

    public int getCorruptionLevel() {
        return corruptionLevel;
    }

    public int getPreviousCorruptionLevel() {
        return previousCorruptionLevel;
    }

    public int getCorruptionDelta() {
        return corruptionDelta;
    }

    public int getCalibrationConfidence() {
        return calibrationConfidence;
    }

    public int getStabilityDebt() {
        return stabilityDebt;
    }

    public int getProfileCoherence() {
        return profileCoherence;
    }

    public int getEmergenceScore() {
        return emergenceScore;
    }

    public int getLastKnownSafeCorruptionLevel() {
        return lastKnownSafeCorruptionLevel;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public long getFixedCorruptionSeed() {
        return fixedCorruptionSeed;
    }

    public String getCorruptionSeedLabel() {
        return corruptionSeedLabel;
    }

    public int getEnabledTargetsMask() {
        return enabledTargetsMask;
    }

    public int getAutoIncreaseIntervalTicks() {
        return autoIncreaseIntervalTicks;
    }

    public int getAutoIncreaseAmount() {
        return autoIncreaseAmount;
    }

    public boolean isTargetEnabled(CorruptionTarget target) {
        return CorruptionTarget.enabled(enabledTargetsMask, target);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int clampIntervalTicks(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.max(20, Math.min(144_000, value));
    }

    private static int clampAutoAmount(int value) {
        return Math.max(-100, Math.min(100, value));
    }
}
