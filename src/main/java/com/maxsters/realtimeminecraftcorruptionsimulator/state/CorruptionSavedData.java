package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.DeterministicCorruption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

public class CorruptionSavedData extends SavedData {
    public static final String DATA_NAME = "realtime_minecraft_corruption_simulator";
    private static final int MAX_DRAGON_IDS = 64;

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
    private boolean allowNonOpSettingsUpdates = true;
    private boolean quickToggleRestorePresent;
    private int quickToggleRestoreLevel;
    private long quickToggleRestoreSeed = DeterministicCorruption.DEFAULT_SEED;
    private String quickToggleRestoreSeedLabel = defaultSeedLabel();
    private int quickToggleRestoreTargetsMask = CorruptionTarget.ALL_MASK;
    private int quickToggleRestoreAutoIncreaseIntervalTicks;
    private int quickToggleRestoreAutoIncreaseAmount = 1;
    private boolean quickToggleRestoreClientDriftEnabled;
    private int quickToggleRestoreSeedRandomizerIntervalTicks;
    // Achievement eligibility is server-owned. Clients only mirror this state for display and
    // local award progress; deleting client config must not restore a world's qualification.
    private boolean serverAchievementDisqualified;
    private String serverAchievementDisqualificationReason = "";
    private boolean warrantyStarted;
    private boolean warrantyDisqualified;
    private final Set<String> armedDragonIds = new LinkedHashSet<>();
    private final Set<String> spoiledDragonIds = new LinkedHashSet<>();
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
        if (tag.contains("allow_non_op_settings_updates", Tag.TAG_BYTE)) {
            data.allowNonOpSettingsUpdates = tag.getBoolean("allow_non_op_settings_updates");
        }
        if (tag.contains("quick_toggle_restore_present", Tag.TAG_BYTE)) {
            data.quickToggleRestorePresent = tag.getBoolean("quick_toggle_restore_present");
        }
        if (tag.contains("quick_toggle_restore_level", Tag.TAG_INT)) {
            data.quickToggleRestoreLevel = clampPercent(tag.getInt("quick_toggle_restore_level"));
        }
        if (tag.contains("quick_toggle_restore_seed", Tag.TAG_LONG)) {
            data.quickToggleRestoreSeed = tag.getLong("quick_toggle_restore_seed");
        }
        if (tag.contains("quick_toggle_restore_seed_label", Tag.TAG_STRING)) {
            data.quickToggleRestoreSeedLabel = sanitizeSeedLabel(tag.getString("quick_toggle_restore_seed_label"), data.quickToggleRestoreSeed);
        } else {
            data.quickToggleRestoreSeedLabel = seedLabel(data.quickToggleRestoreSeed);
        }
        if (tag.contains("quick_toggle_restore_targets_mask", Tag.TAG_INT)) {
            data.quickToggleRestoreTargetsMask = CorruptionTarget.normalizeMask(tag.getInt("quick_toggle_restore_targets_mask"));
        }
        if (tag.contains("quick_toggle_restore_auto_interval_ticks", Tag.TAG_INT)) {
            data.quickToggleRestoreAutoIncreaseIntervalTicks = clampIntervalTicks(tag.getInt("quick_toggle_restore_auto_interval_ticks"));
        }
        if (tag.contains("quick_toggle_restore_auto_amount", Tag.TAG_INT)) {
            data.quickToggleRestoreAutoIncreaseAmount = clampAutoAmount(tag.getInt("quick_toggle_restore_auto_amount"));
        }
        if (tag.contains("quick_toggle_restore_client_drift", Tag.TAG_BYTE)) {
            data.quickToggleRestoreClientDriftEnabled = tag.getBoolean("quick_toggle_restore_client_drift");
        }
        if (tag.contains("quick_toggle_restore_seed_randomizer_ticks", Tag.TAG_INT)) {
            data.quickToggleRestoreSeedRandomizerIntervalTicks = clampIntervalTicks(tag.getInt("quick_toggle_restore_seed_randomizer_ticks"));
        }
        if (tag.contains("server_achievement_disqualified", Tag.TAG_BYTE)) {
            data.serverAchievementDisqualified = tag.getBoolean("server_achievement_disqualified");
        }
        if (tag.contains("server_achievement_disqualification_reason", Tag.TAG_STRING)) {
            data.serverAchievementDisqualificationReason = sanitizeDisqualificationReason(tag.getString("server_achievement_disqualification_reason"));
        }
        if (tag.contains("warranty_started", Tag.TAG_BYTE)) {
            data.warrantyStarted = tag.getBoolean("warranty_started");
        }
        if (tag.contains("warranty_disqualified", Tag.TAG_BYTE)) {
            data.warrantyDisqualified = tag.getBoolean("warranty_disqualified");
        }
        if (tag.contains("armed_dragon_ids", Tag.TAG_STRING)) {
            data.armedDragonIds.addAll(parseCsvSet(tag.getString("armed_dragon_ids")));
        }
        if (tag.contains("spoiled_dragon_ids", Tag.TAG_STRING)) {
            data.spoiledDragonIds.addAll(parseCsvSet(tag.getString("spoiled_dragon_ids")));
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
        tag.putBoolean("allow_non_op_settings_updates", allowNonOpSettingsUpdates);
        tag.putBoolean("quick_toggle_restore_present", quickToggleRestorePresent);
        tag.putInt("quick_toggle_restore_level", quickToggleRestoreLevel);
        tag.putLong("quick_toggle_restore_seed", quickToggleRestoreSeed);
        tag.putString("quick_toggle_restore_seed_label", quickToggleRestoreSeedLabel);
        tag.putInt("quick_toggle_restore_targets_mask", quickToggleRestoreTargetsMask);
        tag.putInt("quick_toggle_restore_auto_interval_ticks", quickToggleRestoreAutoIncreaseIntervalTicks);
        tag.putInt("quick_toggle_restore_auto_amount", quickToggleRestoreAutoIncreaseAmount);
        tag.putBoolean("quick_toggle_restore_client_drift", quickToggleRestoreClientDriftEnabled);
        tag.putInt("quick_toggle_restore_seed_randomizer_ticks", quickToggleRestoreSeedRandomizerIntervalTicks);
        tag.putBoolean("server_achievement_disqualified", serverAchievementDisqualified);
        tag.putString("server_achievement_disqualification_reason", serverAchievementDisqualificationReason);
        tag.putBoolean("warranty_started", warrantyStarted);
        tag.putBoolean("warranty_disqualified", warrantyDisqualified);
        tag.putString("armed_dragon_ids", csv(armedDragonIds));
        tag.putString("spoiled_dragon_ids", csv(spoiledDragonIds));
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

    public boolean allowNonOpSettingsUpdates() {
        return allowNonOpSettingsUpdates;
    }

    public boolean setAllowNonOpSettingsUpdates(boolean allowNonOpSettingsUpdates) {
        if (this.allowNonOpSettingsUpdates == allowNonOpSettingsUpdates) {
            return false;
        }
        this.allowNonOpSettingsUpdates = allowNonOpSettingsUpdates;
        setDirty();
        return true;
    }

    public boolean hasQuickToggleRestore() {
        return quickToggleRestorePresent;
    }

    public CorruptionStateSnapshot quickToggleRestoreSnapshot() {
        return new CorruptionStateSnapshot(
                quickToggleRestoreLevel,
                quickToggleRestoreSeed,
                quickToggleRestoreSeedLabel,
                quickToggleRestoreTargetsMask,
                quickToggleRestoreAutoIncreaseIntervalTicks,
                quickToggleRestoreAutoIncreaseAmount,
                quickToggleRestoreClientDriftEnabled,
                quickToggleRestoreSeedRandomizerIntervalTicks
        );
    }

    public void setQuickToggleRestore(CorruptionStateSnapshot snapshot) {
        if (snapshot == null) {
            clearQuickToggleRestore();
            return;
        }
        quickToggleRestorePresent = true;
        quickToggleRestoreLevel = clampPercent(snapshot.getCorruptionLevel());
        quickToggleRestoreSeed = snapshot.getFixedCorruptionSeed();
        quickToggleRestoreSeedLabel = sanitizeSeedLabel(snapshot.getCorruptionSeedLabel(), quickToggleRestoreSeed);
        quickToggleRestoreTargetsMask = CorruptionTarget.normalizeMask(snapshot.getEnabledTargetsMask());
        quickToggleRestoreAutoIncreaseIntervalTicks = clampIntervalTicks(snapshot.getAutoIncreaseIntervalTicks());
        quickToggleRestoreAutoIncreaseAmount = clampAutoAmount(snapshot.getAutoIncreaseAmount());
        quickToggleRestoreClientDriftEnabled = snapshot.isClientDriftEnabled();
        quickToggleRestoreSeedRandomizerIntervalTicks = clampIntervalTicks(snapshot.getSeedRandomizerIntervalTicks());
        setDirty();
    }

    public boolean clearQuickToggleRestore() {
        if (!quickToggleRestorePresent) {
            return false;
        }
        quickToggleRestorePresent = false;
        setDirty();
        return true;
    }

    public boolean isServerAchievementDisqualified() {
        return serverAchievementDisqualified;
    }

    public boolean isAchievementWorldDisqualified() {
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

    public boolean isWarrantyStarted() {
        return warrantyStarted;
    }

    public boolean setWarrantyStarted(boolean warrantyStarted) {
        if (this.warrantyStarted == warrantyStarted) {
            return false;
        }
        this.warrantyStarted = warrantyStarted;
        setDirty();
        return true;
    }

    public boolean isWarrantyDisqualified() {
        return warrantyDisqualified;
    }

    public boolean setWarrantyDisqualified(boolean warrantyDisqualified) {
        if (this.warrantyDisqualified == warrantyDisqualified) {
            return false;
        }
        this.warrantyDisqualified = warrantyDisqualified;
        setDirty();
        return true;
    }

    public Set<String> armedDragonIds() {
        return Set.copyOf(armedDragonIds);
    }

    public Set<String> spoiledDragonIds() {
        return Set.copyOf(spoiledDragonIds);
    }

    public boolean armDragonFight(String dragonId) {
        String sanitized = sanitizeDragonId(dragonId);
        if (sanitized.isBlank() || armedDragonIds.contains(sanitized) || armedDragonIds.size() >= MAX_DRAGON_IDS) {
            return false;
        }
        armedDragonIds.add(sanitized);
        setDirty();
        return true;
    }

    public boolean disarmDragonFight(String dragonId) {
        String sanitized = sanitizeDragonId(dragonId);
        if (sanitized.isBlank() || !armedDragonIds.remove(sanitized)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean spoilDragonFight(String dragonId) {
        String sanitized = sanitizeDragonId(dragonId);
        if (sanitized.isBlank() || spoiledDragonIds.contains(sanitized) || spoiledDragonIds.size() >= MAX_DRAGON_IDS) {
            return false;
        }
        spoiledDragonIds.add(sanitized);
        setDirty();
        return true;
    }

    public boolean isDragonFightArmed(String dragonId) {
        return armedDragonIds.contains(sanitizeDragonId(dragonId));
    }

    public boolean isDragonFightSpoiled(String dragonId) {
        return spoiledDragonIds.contains(sanitizeDragonId(dragonId));
    }

    public boolean clearArmedDragonFights() {
        if (armedDragonIds.isEmpty()) {
            return false;
        }
        armedDragonIds.clear();
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

    private static String sanitizeDragonId(String dragonId) {
        if (dragonId == null || dragonId.isBlank()) {
            return "";
        }
        String trimmed = dragonId.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static Set<String> parseCsvSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(",")) {
            String sanitized = sanitizeDragonId(part);
            if (!sanitized.isBlank()) {
                result.add(sanitized);
            }
            if (result.size() >= MAX_DRAGON_IDS) {
                break;
            }
        }
        return result;
    }

    private static String csv(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }
}
