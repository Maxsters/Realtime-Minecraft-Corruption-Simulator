package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.DeterministicCorruption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class CorruptionSavedData extends SavedData {
    public static final String DATA_NAME = "realtime_minecraft_corruption_simulator";

    // Keep world persistence to settings that actually change runtime behavior.
    // Old progression keys are intentionally ignored on load.
    private int corruptionLevel;
    private long fixedCorruptionSeed = DeterministicCorruption.DEFAULT_SEED;
    private String corruptionSeedLabel = defaultSeedLabel();
    private int enabledTargetsMask = CorruptionTarget.ALL_MASK;
    private int autoIncreaseIntervalTicks;
    private int autoIncreaseAmount = 1;
    private long lastAutoIncreaseGameTime;
    private boolean clientDriftEnabled;
    private int seedRandomizerIntervalTicks;
    private long lastSeedRandomizerGameTime;
    // Achievement eligibility is server-wide. One privileged command can affect any player,
    // so the disqualification must persist with the world instead of living on one client.
    private boolean serverAchievementDisqualified;
    private String serverAchievementDisqualificationReason = "";
    private boolean initialized;

    public static CorruptionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CorruptionSavedData::load,
                CorruptionSavedData::new,
                DATA_NAME
        );
    }

    public static CorruptionSavedData load(CompoundTag tag) {
        CorruptionSavedData data = new CorruptionSavedData();
        data.initialized = true;
        data.corruptionLevel = clampPercent(tag.getInt("corruption_level"));
        if (tag.contains("fixed_corruption_seed", Tag.TAG_LONG)) {
            data.fixedCorruptionSeed = tag.getLong("fixed_corruption_seed");
        }
        if (tag.contains("corruption_seed_label", Tag.TAG_STRING)) {
            data.corruptionSeedLabel = sanitizeSeedLabel(tag.getString("corruption_seed_label"), data.fixedCorruptionSeed);
        } else {
            data.corruptionSeedLabel = seedLabel(data.fixedCorruptionSeed);
        }
        if (tag.contains("enabled_targets_mask", Tag.TAG_INT)) {
            data.enabledTargetsMask = CorruptionTarget.normalizeMask(tag.getInt("enabled_targets_mask"));
        }
        if (tag.contains("auto_increase_interval_ticks", Tag.TAG_INT)) {
            data.autoIncreaseIntervalTicks = clampIntervalTicks(tag.getInt("auto_increase_interval_ticks"));
        }
        if (tag.contains("auto_increase_amount", Tag.TAG_INT)) {
            data.autoIncreaseAmount = clampAutoAmount(tag.getInt("auto_increase_amount"));
        }
        if (tag.contains("last_auto_increase_game_time", Tag.TAG_LONG)) {
            data.lastAutoIncreaseGameTime = tag.getLong("last_auto_increase_game_time");
        }
        if (tag.contains("client_drift_enabled", Tag.TAG_BYTE)) {
            data.clientDriftEnabled = tag.getBoolean("client_drift_enabled");
        }
        if (tag.contains("seed_randomizer_interval_ticks", Tag.TAG_INT)) {
            data.seedRandomizerIntervalTicks = clampIntervalTicks(tag.getInt("seed_randomizer_interval_ticks"));
        }
        if (tag.contains("last_seed_randomizer_game_time", Tag.TAG_LONG)) {
            data.lastSeedRandomizerGameTime = tag.getLong("last_seed_randomizer_game_time");
        }
        if (tag.contains("server_achievement_disqualified", Tag.TAG_BYTE)) {
            data.serverAchievementDisqualified = tag.getBoolean("server_achievement_disqualified");
        }
        if (tag.contains("server_achievement_disqualification_reason", Tag.TAG_STRING)) {
            data.serverAchievementDisqualificationReason = sanitizeDisqualificationReason(tag.getString("server_achievement_disqualification_reason"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("corruption_level", corruptionLevel);
        tag.putLong("fixed_corruption_seed", fixedCorruptionSeed);
        tag.putString("corruption_seed_label", corruptionSeedLabel);
        tag.putInt("enabled_targets_mask", enabledTargetsMask);
        tag.putInt("auto_increase_interval_ticks", autoIncreaseIntervalTicks);
        tag.putInt("auto_increase_amount", autoIncreaseAmount);
        tag.putLong("last_auto_increase_game_time", lastAutoIncreaseGameTime);
        tag.putBoolean("client_drift_enabled", clientDriftEnabled);
        tag.putInt("seed_randomizer_interval_ticks", seedRandomizerIntervalTicks);
        tag.putLong("last_seed_randomizer_game_time", lastSeedRandomizerGameTime);
        tag.putBoolean("server_achievement_disqualified", serverAchievementDisqualified);
        tag.putString("server_achievement_disqualification_reason", serverAchievementDisqualificationReason);
        tag.putBoolean("initialized", true);
        return tag;
    }

    public int getCorruptionLevel() {
        return corruptionLevel;
    }

    public void setCorruptionLevel(int corruptionLevel) {
        this.corruptionLevel = clampPercent(corruptionLevel);
        setDirty();
    }

    public long getFixedCorruptionSeed() {
        return fixedCorruptionSeed;
    }

    public String getCorruptionSeedLabel() {
        return corruptionSeedLabel;
    }

    public void setCorruptionSeed(long fixedCorruptionSeed, String corruptionSeedLabel) {
        this.fixedCorruptionSeed = fixedCorruptionSeed;
        this.corruptionSeedLabel = sanitizeSeedLabel(corruptionSeedLabel, fixedCorruptionSeed);
        setDirty();
    }

    public int getEnabledTargetsMask() {
        return enabledTargetsMask;
    }

    public void setEnabledTargetsMask(int enabledTargetsMask) {
        this.enabledTargetsMask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        setDirty();
    }

    public int getAutoIncreaseIntervalTicks() {
        return autoIncreaseIntervalTicks;
    }

    public void setAutoIncreaseIntervalTicks(int autoIncreaseIntervalTicks) {
        this.autoIncreaseIntervalTicks = clampIntervalTicks(autoIncreaseIntervalTicks);
        setDirty();
    }

    public int getAutoIncreaseAmount() {
        return autoIncreaseAmount;
    }

    public void setAutoIncreaseAmount(int autoIncreaseAmount) {
        this.autoIncreaseAmount = clampAutoAmount(autoIncreaseAmount);
        setDirty();
    }

    public long getLastAutoIncreaseGameTime() {
        return lastAutoIncreaseGameTime;
    }

    public void setLastAutoIncreaseGameTime(long lastAutoIncreaseGameTime) {
        this.lastAutoIncreaseGameTime = Math.max(0L, lastAutoIncreaseGameTime);
        setDirty();
    }

    public boolean isClientDriftEnabled() {
        return clientDriftEnabled;
    }

    public void setClientDriftEnabled(boolean clientDriftEnabled) {
        this.clientDriftEnabled = clientDriftEnabled;
        setDirty();
    }

    public int getSeedRandomizerIntervalTicks() {
        return seedRandomizerIntervalTicks;
    }

    public void setSeedRandomizerIntervalTicks(int seedRandomizerIntervalTicks) {
        this.seedRandomizerIntervalTicks = clampIntervalTicks(seedRandomizerIntervalTicks);
        setDirty();
    }

    public long getLastSeedRandomizerGameTime() {
        return lastSeedRandomizerGameTime;
    }

    public void setLastSeedRandomizerGameTime(long lastSeedRandomizerGameTime) {
        this.lastSeedRandomizerGameTime = Math.max(0L, lastSeedRandomizerGameTime);
        setDirty();
    }

    public boolean isServerAchievementDisqualified() {
        return serverAchievementDisqualified;
    }

    public boolean hasServerAchievementDisqualificationReason() {
        return serverAchievementDisqualified && !serverAchievementDisqualificationReason.isBlank();
    }

    public boolean markServerAchievementDisqualified(String reason) {
        String sanitizedReason = sanitizeDisqualificationReason(reason);
        if (serverAchievementDisqualified && !serverAchievementDisqualificationReason.isBlank()) {
            return false;
        }
        serverAchievementDisqualified = true;
        serverAchievementDisqualificationReason = sanitizedReason;
        setDirty();
        return true;
    }

    public void clearSourceLessServerAchievementDisqualification() {
        if (!serverAchievementDisqualified || !serverAchievementDisqualificationReason.isBlank()) {
            return;
        }
        serverAchievementDisqualified = false;
        setDirty();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        if (!initialized) {
            initialized = true;
            setDirty();
        }
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

    private static String defaultSeedLabel() {
        return seedLabel(DeterministicCorruption.DEFAULT_SEED);
    }

    public static String seedLabel(long seed) {
        return "0x" + Long.toUnsignedString(seed, 16).toUpperCase();
    }

    public static String sanitizeSeedLabel(String label, long seed) {
        if (label == null || label.isBlank()) {
            return seedLabel(seed);
        }
        String trimmed = label.trim();
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    private static String sanitizeDisqualificationReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String trimmed = reason.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
