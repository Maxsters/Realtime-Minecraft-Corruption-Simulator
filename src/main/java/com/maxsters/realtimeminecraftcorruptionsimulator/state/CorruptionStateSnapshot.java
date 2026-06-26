package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import net.minecraft.network.FriendlyByteBuf;

// Client mirror of the active runtime settings. Corruption intensity is derived
// from these fields by CorruptionEffectStack instead of being synced as state.
public record CorruptionStateSnapshot(
        int corruptionLevel,
        long fixedCorruptionSeed,
        String corruptionSeedLabel,
        int enabledTargetsMask,
        int autoIncreaseIntervalTicks,
        int autoIncreaseAmount,
        boolean clientDriftEnabled,
        int seedRandomizerIntervalTicks,
        long clientDriftSalt
) {
    public CorruptionStateSnapshot(
            int corruptionLevel,
            long fixedCorruptionSeed,
            String corruptionSeedLabel,
            int enabledTargetsMask,
            int autoIncreaseIntervalTicks,
            int autoIncreaseAmount
    ) {
        this(
                corruptionLevel,
                fixedCorruptionSeed,
                corruptionSeedLabel,
                enabledTargetsMask,
                autoIncreaseIntervalTicks,
                autoIncreaseAmount,
                false,
                0,
                0L
        );
    }

    public CorruptionStateSnapshot(
            int corruptionLevel,
            long fixedCorruptionSeed,
            String corruptionSeedLabel,
            int enabledTargetsMask,
            int autoIncreaseIntervalTicks,
            int autoIncreaseAmount,
            boolean clientDriftEnabled,
            int seedRandomizerIntervalTicks
    ) {
        this(
                corruptionLevel,
                fixedCorruptionSeed,
                corruptionSeedLabel,
                enabledTargetsMask,
                autoIncreaseIntervalTicks,
                autoIncreaseAmount,
                clientDriftEnabled,
                seedRandomizerIntervalTicks,
                0L
        );
    }

    public CorruptionStateSnapshot {
        corruptionLevel = clampPercent(corruptionLevel);
        corruptionSeedLabel = CorruptionSavedData.sanitizeSeedLabel(corruptionSeedLabel, fixedCorruptionSeed);
        enabledTargetsMask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        autoIncreaseIntervalTicks = clampIntervalTicks(autoIncreaseIntervalTicks);
        autoIncreaseAmount = clampAutoAmount(autoIncreaseAmount);
        seedRandomizerIntervalTicks = clampIntervalTicks(seedRandomizerIntervalTicks);
        clientDriftSalt = clientDriftEnabled ? clientDriftSalt : 0L;
    }

    public static CorruptionStateSnapshot from(CorruptionSavedData data) {
        return new CorruptionStateSnapshot(
                data.getCorruptionLevel(),
                data.getFixedCorruptionSeed(),
                data.getCorruptionSeedLabel(),
                data.getEnabledTargetsMask(),
                data.getAutoIncreaseIntervalTicks(),
                data.getAutoIncreaseAmount(),
                data.isClientDriftEnabled(),
                data.getSeedRandomizerIntervalTicks(),
                0L
        );
    }

    public static CorruptionStateSnapshot decode(FriendlyByteBuf buffer) {
        int corruptionLevel = buffer.readVarInt();
        long fixedCorruptionSeed = buffer.readLong();
        String corruptionSeedLabel = buffer.readUtf(96);
        int enabledTargetsMask = buffer.readVarInt();
        int autoIncreaseIntervalTicks = buffer.readVarInt();
        int autoIncreaseAmount = buffer.readVarInt();
        boolean clientDriftEnabled = buffer.readBoolean();
        int seedRandomizerIntervalTicks = buffer.readVarInt();
        return new CorruptionStateSnapshot(
                corruptionLevel,
                fixedCorruptionSeed,
                corruptionSeedLabel,
                enabledTargetsMask,
                autoIncreaseIntervalTicks,
                autoIncreaseAmount,
                clientDriftEnabled,
                seedRandomizerIntervalTicks,
                0L
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(corruptionLevel);
        buffer.writeLong(fixedCorruptionSeed);
        buffer.writeUtf(corruptionSeedLabel);
        buffer.writeVarInt(enabledTargetsMask);
        buffer.writeVarInt(autoIncreaseIntervalTicks);
        buffer.writeVarInt(autoIncreaseAmount);
        buffer.writeBoolean(clientDriftEnabled);
        buffer.writeVarInt(seedRandomizerIntervalTicks);
    }

    public int getCorruptionLevel() {
        return corruptionLevel;
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

    public boolean isClientDriftEnabled() {
        return clientDriftEnabled;
    }

    public int getSeedRandomizerIntervalTicks() {
        return seedRandomizerIntervalTicks;
    }

    public long getClientDriftSalt() {
        return clientDriftSalt;
    }

    public long getEffectiveCorruptionSeed() {
        return clientDriftEnabled ? fixedCorruptionSeed ^ clientDriftSalt : fixedCorruptionSeed;
    }

    public CorruptionStateSnapshot withClientDriftSalt(long salt) {
        return new CorruptionStateSnapshot(
                corruptionLevel,
                fixedCorruptionSeed,
                corruptionSeedLabel,
                enabledTargetsMask,
                autoIncreaseIntervalTicks,
                autoIncreaseAmount,
                clientDriftEnabled,
                seedRandomizerIntervalTicks,
                salt
        );
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
