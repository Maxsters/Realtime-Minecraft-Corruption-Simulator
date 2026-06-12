package com.maxsters.realtimeminecraftcorruptionsimulator.config;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionProfileManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GlobalCorruptionSettings {
    private static final String CORRUPTION_LEVEL = "corruption_level";
    private static final String CORRUPTION_SEED = "corruption_seed";
    private static final String CORRUPTION_SEED_LABEL = "corruption_seed_label";
    private static final String ENABLED_TARGETS_MASK = "enabled_targets_mask";
    private static final String AUTO_INCREASE_INTERVAL_TICKS = "auto_increase_interval_ticks";
    private static final String AUTO_INCREASE_AMOUNT = "auto_increase_amount";

    private static boolean loaded;
    private static int activeLevel;
    private static long seed = CorruptionProfileManager.DEFAULT_PROFILE.fixedSeed();
    private static String seedLabel = CorruptionSavedData.seedLabel(seed);
    private static int enabledTargetsMask = CorruptionTarget.ALL_MASK;
    private static int autoIncreaseIntervalTicks;
    private static int autoIncreaseAmount = 1;
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RMC corruption settings save");
        thread.setDaemon(true);
        return thread;
    });

    private GlobalCorruptionSettings() {
    }

    public static synchronized int activeLevel() {
        ensureLoaded();
        return activeLevel;
    }

    public static synchronized long seed() {
        ensureLoaded();
        return seed;
    }

    public static synchronized String seedLabel() {
        ensureLoaded();
        return seedLabel;
    }

    public static synchronized int enabledTargetsMask() {
        ensureLoaded();
        return enabledTargetsMask;
    }

    public static synchronized int autoIncreaseIntervalTicks() {
        ensureLoaded();
        return autoIncreaseIntervalTicks;
    }

    public static synchronized int autoIncreaseAmount() {
        ensureLoaded();
        return autoIncreaseAmount;
    }

    public static synchronized boolean queueLevel(int requestedLevel) {
        apply(clampPercent(requestedLevel), seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount);
        return false;
    }

    public static synchronized void apply(int requestedLevel, long requestedSeed, String requestedSeedLabel, int requestedTargetsMask) {
        apply(requestedLevel, requestedSeed, requestedSeedLabel, requestedTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount);
    }

    public static synchronized void apply(int requestedLevel, long requestedSeed, String requestedSeedLabel, int requestedTargetsMask, int requestedAutoIncreaseIntervalTicks, int requestedAutoIncreaseAmount) {
        ensureLoaded();
        activeLevel = clampPercent(requestedLevel);
        seed = requestedSeed;
        seedLabel = CorruptionSavedData.sanitizeSeedLabel(requestedSeedLabel, requestedSeed);
        enabledTargetsMask = CorruptionTarget.normalizeMask(requestedTargetsMask);
        autoIncreaseIntervalTicks = clampIntervalTicks(requestedAutoIncreaseIntervalTicks);
        autoIncreaseAmount = clampAutoAmount(requestedAutoIncreaseAmount);
        saveAsync();
    }

    public static synchronized void applyAutoLevel(int requestedLevel) {
        ensureLoaded();
        activeLevel = clampPercent(requestedLevel);
        saveAsync();
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }

        Properties properties = new Properties();
        Path path = path();
        if (Files.isRegularFile(path)) {
            try (InputStream inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to load Realtime Minecraft Corruption Simulator settings", exception);
            }
        }

        activeLevel = clampPercent(parseInt(properties.getProperty(CORRUPTION_LEVEL), parseInt(properties.getProperty("active_corruption_level"), 0)));
        seed = parseLong(properties.getProperty(CORRUPTION_SEED), CorruptionProfileManager.DEFAULT_PROFILE.fixedSeed());
        seedLabel = CorruptionSavedData.sanitizeSeedLabel(properties.getProperty(CORRUPTION_SEED_LABEL), seed);
        enabledTargetsMask = CorruptionTarget.normalizeMask(parseInt(properties.getProperty(ENABLED_TARGETS_MASK), CorruptionTarget.ALL_MASK));
        autoIncreaseIntervalTicks = clampIntervalTicks(parseInt(properties.getProperty(AUTO_INCREASE_INTERVAL_TICKS), 0));
        autoIncreaseAmount = clampAutoAmount(parseInt(properties.getProperty(AUTO_INCREASE_AMOUNT), 1));

        loaded = true;
        saveAsync();
    }

    private static void saveAsync() {
        Properties properties = new Properties();
        properties.setProperty(CORRUPTION_LEVEL, Integer.toString(activeLevel));
        properties.setProperty(CORRUPTION_SEED, Long.toUnsignedString(seed));
        properties.setProperty(CORRUPTION_SEED_LABEL, seedLabel);
        properties.setProperty(ENABLED_TARGETS_MASK, Integer.toString(enabledTargetsMask));
        properties.setProperty(AUTO_INCREASE_INTERVAL_TICKS, Integer.toString(autoIncreaseIntervalTicks));
        properties.setProperty(AUTO_INCREASE_AMOUNT, Integer.toString(autoIncreaseAmount));

        Path path = path();
        SAVE_EXECUTOR.execute(() -> saveSnapshot(path, properties));
    }

    private static void saveSnapshot(Path path, Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "Realtime Minecraft Corruption Simulator settings");
            }
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to save Realtime Minecraft Corruption Simulator settings", exception);
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("realtime_minecraft_corruption_simulator.properties");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return Long.parseUnsignedLong(trimmed.substring(2), 16);
            }
            return Long.parseUnsignedLong(trimmed);
        } catch (NumberFormatException ignored) {
            return fallback;
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
}
