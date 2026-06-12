package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionProfileManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class CorruptionSavedData extends SavedData {
    public static final String DATA_NAME = "realtime_minecraft_corruption_simulator";
    private static final int MAX_PROCESSED_WORLDGEN_CHUNKS = 2048;

    private int corruptionLevel;
    private int previousCorruptionLevel;
    private int corruptionDelta;
    private String activeProfile = CorruptionProfileManager.DEFAULT_PROFILE.id();
    private long fixedCorruptionSeed = CorruptionProfileManager.DEFAULT_PROFILE.fixedSeed();
    private String corruptionSeedLabel = defaultSeedLabel();
    private int calibrationConfidence = 100;
    private int stabilityDebt;
    private int profileCoherence = 100;
    private int emergenceScore;
    private final Set<String> processedWorldgenChunks = new LinkedHashSet<>();
    private final Set<String> seenEvents = new LinkedHashSet<>();
    private final Set<String> externalMediaSeen = new LinkedHashSet<>();
    private int lastKnownSafeCorruptionLevel;
    private int enabledTargetsMask = CorruptionTarget.ALL_MASK;
    private int autoIncreaseIntervalTicks;
    private int autoIncreaseAmount = 1;
    private long lastAutoIncreaseGameTime;

    public static CorruptionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CorruptionSavedData::load,
                CorruptionSavedData::new,
                DATA_NAME
        );
    }

    public static CorruptionSavedData load(CompoundTag tag) {
        CorruptionSavedData data = new CorruptionSavedData();
        data.corruptionLevel = clampPercent(tag.getInt("corruption_level"));
        data.previousCorruptionLevel = clampPercent(tag.getInt("previous_corruption_level"));
        data.corruptionDelta = clampPercent(tag.getInt("corruption_delta"));
        if (tag.contains("active_profile", Tag.TAG_STRING)) {
            data.activeProfile = normalizeProfile(tag.getString("active_profile"));
        }
        if (tag.contains("fixed_corruption_seed", Tag.TAG_LONG)) {
            data.fixedCorruptionSeed = tag.getLong("fixed_corruption_seed");
        }
        if (tag.contains("corruption_seed_label", Tag.TAG_STRING)) {
            data.corruptionSeedLabel = sanitizeSeedLabel(tag.getString("corruption_seed_label"), data.fixedCorruptionSeed);
        } else {
            data.corruptionSeedLabel = seedLabel(data.fixedCorruptionSeed);
        }
        if (tag.contains("calibration_confidence", Tag.TAG_INT)) {
            data.calibrationConfidence = clampPercent(tag.getInt("calibration_confidence"));
        }
        data.stabilityDebt = clampPercent(tag.getInt("stability_debt"));
        if (tag.contains("profile_coherence", Tag.TAG_INT)) {
            data.profileCoherence = clampPercent(tag.getInt("profile_coherence"));
        }
        data.emergenceScore = clampPercent(tag.getInt("emergence_score"));
        data.processedWorldgenChunks.addAll(readStringSet(tag, "processed_worldgen_chunks"));
        trimToLimit(data.processedWorldgenChunks, MAX_PROCESSED_WORLDGEN_CHUNKS);
        data.seenEvents.addAll(readStringSet(tag, "seen_events"));
        data.externalMediaSeen.addAll(readStringSet(tag, "external_media_seen"));
        data.lastKnownSafeCorruptionLevel = clampPercent(tag.getInt("last_known_safe_corruption_level"));
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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("corruption_level", corruptionLevel);
        tag.putInt("previous_corruption_level", previousCorruptionLevel);
        tag.putInt("corruption_delta", corruptionDelta);
        tag.putString("active_profile", activeProfile);
        tag.putLong("fixed_corruption_seed", fixedCorruptionSeed);
        tag.putString("corruption_seed_label", corruptionSeedLabel);
        tag.putInt("calibration_confidence", calibrationConfidence);
        tag.putInt("stability_debt", stabilityDebt);
        tag.putInt("profile_coherence", profileCoherence);
        tag.putInt("emergence_score", emergenceScore);
        tag.put("processed_worldgen_chunks", writeStringSet(processedWorldgenChunks));
        tag.put("seen_events", writeStringSet(seenEvents));
        tag.put("external_media_seen", writeStringSet(externalMediaSeen));
        tag.putInt("last_known_safe_corruption_level", lastKnownSafeCorruptionLevel);
        tag.putInt("enabled_targets_mask", enabledTargetsMask);
        tag.putInt("auto_increase_interval_ticks", autoIncreaseIntervalTicks);
        tag.putInt("auto_increase_amount", autoIncreaseAmount);
        tag.putLong("last_auto_increase_game_time", lastAutoIncreaseGameTime);
        return tag;
    }

    public boolean markWorldgenChunkProcessed(String chunkKey) {
        boolean changed = processedWorldgenChunks.add(chunkKey);
        if (changed) {
            trimToLimit(processedWorldgenChunks, MAX_PROCESSED_WORLDGEN_CHUNKS);
            setDirty();
        }
        return changed;
    }

    public boolean isWorldgenChunkProcessed(String chunkKey) {
        return processedWorldgenChunks.contains(chunkKey);
    }

    public int getCorruptionLevel() {
        return corruptionLevel;
    }

    public void setCorruptionLevel(int corruptionLevel) {
        this.corruptionLevel = clampPercent(corruptionLevel);
        setDirty();
    }

    public int getPreviousCorruptionLevel() {
        return previousCorruptionLevel;
    }

    public void setPreviousCorruptionLevel(int previousCorruptionLevel) {
        this.previousCorruptionLevel = clampPercent(previousCorruptionLevel);
        setDirty();
    }

    public int getCorruptionDelta() {
        return corruptionDelta;
    }

    public void setCorruptionDelta(int corruptionDelta) {
        this.corruptionDelta = clampPercent(corruptionDelta);
        setDirty();
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

    public void setCorruptionSeed(long fixedCorruptionSeed, String corruptionSeedLabel) {
        this.fixedCorruptionSeed = fixedCorruptionSeed;
        this.corruptionSeedLabel = sanitizeSeedLabel(corruptionSeedLabel, fixedCorruptionSeed);
        setDirty();
    }

    public int getCalibrationConfidence() {
        return calibrationConfidence;
    }

    public void setCalibrationConfidence(int calibrationConfidence) {
        this.calibrationConfidence = clampPercent(calibrationConfidence);
        setDirty();
    }

    public int getStabilityDebt() {
        return stabilityDebt;
    }

    public void setStabilityDebt(int stabilityDebt) {
        this.stabilityDebt = clampPercent(stabilityDebt);
        setDirty();
    }

    public int getProfileCoherence() {
        return profileCoherence;
    }

    public void setProfileCoherence(int profileCoherence) {
        this.profileCoherence = clampPercent(profileCoherence);
        setDirty();
    }

    public int getEmergenceScore() {
        return emergenceScore;
    }

    public void setEmergenceScore(int emergenceScore) {
        this.emergenceScore = clampPercent(emergenceScore);
        setDirty();
    }

    public int getLastKnownSafeCorruptionLevel() {
        return lastKnownSafeCorruptionLevel;
    }

    public void setLastKnownSafeCorruptionLevel(int lastKnownSafeCorruptionLevel) {
        this.lastKnownSafeCorruptionLevel = clampPercent(lastKnownSafeCorruptionLevel);
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

    private static String normalizeProfile(String activeProfile) {
        if (activeProfile == null || activeProfile.isBlank()) {
            return CorruptionProfileManager.DEFAULT_PROFILE.id();
        }
        return activeProfile;
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
        return seedLabel(CorruptionProfileManager.DEFAULT_PROFILE.fixedSeed());
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

    private static Set<String> readStringSet(CompoundTag tag, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return values;
        }
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            values.add(list.getString(i));
        }
        return values;
    }

    private static ListTag writeStringSet(Set<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static void trimToLimit(Set<String> values, int maxSize) {
        Iterator<String> iterator = values.iterator();
        while (values.size() > maxSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
