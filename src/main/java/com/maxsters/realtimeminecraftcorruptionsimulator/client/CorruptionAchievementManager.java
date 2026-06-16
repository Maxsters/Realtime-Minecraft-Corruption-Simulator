package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionAchievementManager {
    private static final int STILL_PLAY = 0;
    private static final int WARRANTY_VOIDED = 1;
    private static final int SKYHOOK = 2;
    private static final int NIGHTMARE = 3;
    private static final int DIAMOND_BLESSING = 4;
    private static final int STABLE_RELEASE = 5;

    private static final int AUTO_NIGHTMARE_INTERVAL_TICKS = seconds(30);
    private static final int AUTO_NIGHTMARE_TOLERANCE_TICKS = seconds(2);
    private static final int SKYHOOK_REQUIRED_BLOCKS = 500;
    private static final int DIAMOND_REQUIRED_BLOCKS = 7;

    private static final Achievement[] ACHIEVEMENTS = new Achievement[]{
            new Achievement("still_play", "I Can Still Play This", "Survive 10m at 100% with all targets.", Blocks.OBSIDIAN, minutes(10)),
            new Achievement("dragon_ten", "Warranty Voided", "Defeat the Ender Dragon at 10%+ corruption.", Blocks.DRAGON_EGG, 1),
            new Achievement("skyhook", "Skyhook", "Gain 500 vertical blocks in 5s with Mobility corruption.", Blocks.SLIME_BLOCK, SKYHOOK_REQUIRED_BLOCKS),
            new Achievement("nightmare", "It Was Just a Nightmare", "Let 100% drain to 0% at -1% every 30s without dying with all targets.", Blocks.CRYING_OBSIDIAN, 100),
            new Achievement("diamond_blessing", "A Blessing in Disguise", "Mine 7 diamond ore blocks at 10% with all targets.", Blocks.DIAMOND_ORE, DIAMOND_REQUIRED_BLOCKS),
            new Achievement("stable_release", "Stable Release", "Play 90m with level 0, auto off, and every target disabled.", Blocks.EMERALD_BLOCK, minutes(90))
    };

    private static final Set<ResourceLocation> PROTECTED_ICON_SPRITES = Set.of(
            icon("block/obsidian"),
            icon("block/dragon_egg"),
            icon("block/slime_block"),
            icon("block/crying_obsidian"),
            icon("block/diamond_ore"),
            icon("block/emerald_block")
    );

    private static final boolean[] UNLOCKED = new boolean[ACHIEVEMENTS.length];
    private static final int[] PROGRESS = new int[ACHIEVEMENTS.length];
    private static final Set<String> DISQUALIFIED_WORLD_KEYS = new LinkedHashSet<>();
    private static final Set<String> PINNED_IDS = new LinkedHashSet<>();
    private static final Deque<MovementSample> VERTICAL_SAMPLES = new ArrayDeque<>();
    private static boolean loaded;
    private static String activeWorldKey = "";
    private static HudCorner pinnedCorner = HudCorner.TOP_RIGHT;
    private static boolean dragonSeenEligible;
    private static boolean nightmareActive;
    private static int nightmareLastLevel = -1;
    private static long nightmareLastLevelTick = Long.MIN_VALUE;
    private static int diamondStatsBaseline = -1;
    private static RunSignature lastKnownRunSignature;
    private static RunSignature suspendedRunSignature;

    private CorruptionAchievementManager() {
    }

    public static List<Achievement> achievements() {
        ensureLoaded();
        return List.of(ACHIEVEMENTS);
    }

    public static boolean isUnlocked(Achievement achievement) {
        ensureLoaded();
        int index = indexOf(achievement);
        return index >= 0 && UNLOCKED[index];
    }

    public static boolean isPinned(Achievement achievement) {
        ensureLoaded();
        return achievement != null && PINNED_IDS.contains(achievement.id());
    }

    public static void togglePinned(Achievement achievement) {
        ensureLoaded();
        if (achievement == null) {
            return;
        }
        if (PINNED_IDS.contains(achievement.id())) {
            PINNED_IDS.remove(achievement.id());
        } else if (indexOf(achievement) >= 0) {
            PINNED_IDS.add(achievement.id());
        }
        save();
    }

    public static List<Achievement> pinnedAchievements() {
        ensureLoaded();
        List<Achievement> pinned = new ArrayList<>();
        for (String id : PINNED_IDS) {
            Achievement achievement = achievementById(id);
            if (achievement != null) {
                pinned.add(achievement);
            }
        }
        return pinned;
    }

    public static HudCorner pinnedCorner() {
        ensureLoaded();
        return pinnedCorner;
    }

    public static void setPinnedCorner(HudCorner corner) {
        ensureLoaded();
        if (corner == null || pinnedCorner == corner) {
            return;
        }
        pinnedCorner = corner;
        save();
    }

    public static void resetAll() {
        ensureLoaded();
        Arrays.fill(UNLOCKED, false);
        Arrays.fill(PROGRESS, 0);
        DISQUALIFIED_WORLD_KEYS.clear();
        activeWorldKey = "";
        lastKnownRunSignature = null;
        suspendedRunSignature = null;
        resetTransientProgress();
        save();
    }

    public static boolean isDisqualified(Achievement achievement) {
        ensureLoaded();
        String worldKey = currentWorldKey(Minecraft.getInstance());
        return achievement != null && !worldKey.isBlank() && DISQUALIFIED_WORLD_KEYS.contains(worldKey);
    }

    public static String statusText(Achievement achievement) {
        ensureLoaded();
        int index = indexOf(achievement);
        if (index < 0) {
            return "";
        }
        if (UNLOCKED[index]) {
            return "Unlocked";
        }
        String worldKey = currentWorldKey(Minecraft.getInstance());
        if (worldKey.isBlank()) {
            return "Load a survival world - " + progressText(index);
        }
        if (DISQUALIFIED_WORLD_KEYS.contains(worldKey)) {
            return "Disqualified in this world";
        }
        return progressText(index);
    }

    public static float progressRatio(Achievement achievement) {
        int index = indexOf(achievement);
        if (index < 0 || achievement.requiredProgress() <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, PROGRESS[index] / (float) achievement.requiredProgress()));
    }

    public static String progressLabel(Achievement achievement) {
        ensureLoaded();
        int index = indexOf(achievement);
        return index < 0 ? "" : progressText(index);
    }

    public static boolean isAchievementIconSprite(ResourceLocation spriteId) {
        return spriteId != null && PROTECTED_ICON_SPRITES.contains(spriteId);
    }

    public static void recordWrongSound(ResourceLocation original, ResourceLocation replacement) {
        // Kept as a compatibility hook for sound corruption; the sound achievement was removed.
    }

    public static void recordHoverCorruption() {
        // Kept as a compatibility hook for item/action corruption; the hover achievement was removed.
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ensureLoaded();
        Minecraft minecraft = Minecraft.getInstance();
        CorruptionProfileSnapshot snapshot = ClientCorruptionState.snapshot();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || snapshot == null) {
            suspendRunIfNeeded();
            pauseWorldTransientProgress();
            return;
        }

        updateWorldContext(minecraft);
        resumeSuspendedRunIfNeeded(minecraft, snapshot);
        lastKnownRunSignature = RunSignature.from(currentWorldKey(minecraft), snapshot);

        updateStillPlay(minecraft, snapshot);
        updateWarrantyVoided(minecraft, snapshot);
        updateSkyhook(minecraft, snapshot);
        updateNightmare(minecraft, snapshot);
        updateDiamondBlessing(minecraft, snapshot);
        updateStableRelease(minecraft, snapshot);
    }

    private static void updateStillPlay(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[STILL_PLAY]) {
            return;
        }
        if (eligibleSurvival(minecraft) && snapshot.getCorruptionLevel() >= 100 && allTargets(snapshot)) {
            if (!minecraft.isPaused()) {
                setProgress(STILL_PLAY, PROGRESS[STILL_PLAY] + 1);
            }
        } else {
            setProgress(STILL_PLAY, 0);
        }
    }

    private static void updateWarrantyVoided(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[WARRANTY_VOIDED]) {
            return;
        }
        if (!eligibleSurvival(minecraft)
                || snapshot.getCorruptionLevel() < 10
                || !isDimension(minecraft, Level.END)) {
            dragonSeenEligible = false;
            return;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EnderDragon dragon) {
                if (dragon.isAlive() && dragon.getHealth() > 0.0F) {
                    dragonSeenEligible = true;
                } else if (dragonSeenEligible || dragon.getHealth() <= 0.0F || dragon.isDeadOrDying()) {
                    setProgress(WARRANTY_VOIDED, 1);
                }
            }
        }
    }

    private static void updateSkyhook(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[SKYHOOK]) {
            return;
        }
        if (!eligibleSurvival(minecraft)
                || snapshot.getCorruptionLevel() < 35
                || !enabled(snapshot, CorruptionTarget.MOBILITY)) {
            VERTICAL_SAMPLES.clear();
            setProgress(SKYHOOK, 0);
            return;
        }

        MovementSample sample = sample(minecraft);
        VERTICAL_SAMPLES.addLast(sample);
        trimSamples(VERTICAL_SAMPLES, sample.tick(), seconds(5));
        double bestGain = 0.0D;
        for (MovementSample previous : VERTICAL_SAMPLES) {
            bestGain = Math.max(bestGain, sample.y() - previous.y());
        }
        setProgress(SKYHOOK, (int) Math.min(SKYHOOK_REQUIRED_BLOCKS, Math.round(bestGain)));
    }

    private static void updateNightmare(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[NIGHTMARE]) {
            return;
        }
        if (!eligibleSurvival(minecraft) || !allTargets(snapshot) || !nightmareAutoSettings(snapshot)) {
            resetNightmare();
            return;
        }

        int level = snapshot.getCorruptionLevel();
        long tick = minecraft.level.getGameTime();
        if (!nightmareActive) {
            if (level == 100) {
                nightmareActive = true;
                nightmareLastLevel = level;
                nightmareLastLevelTick = tick;
                setProgress(NIGHTMARE, 0);
            } else {
                setProgress(NIGHTMARE, 0);
            }
            return;
        }

        if (level == nightmareLastLevel) {
            setProgress(NIGHTMARE, 100 - level);
            return;
        }

        boolean expectedAutoStep = level == nightmareLastLevel - 1
                && tick - nightmareLastLevelTick >= AUTO_NIGHTMARE_INTERVAL_TICKS - AUTO_NIGHTMARE_TOLERANCE_TICKS;
        if (!expectedAutoStep) {
            resetNightmare();
            return;
        }

        nightmareLastLevel = level;
        nightmareLastLevelTick = tick;
        setProgress(NIGHTMARE, 100 - level);
        if (level <= 0) {
            setProgress(NIGHTMARE, ACHIEVEMENTS[NIGHTMARE].requiredProgress());
        }
    }

    private static void updateDiamondBlessing(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[DIAMOND_BLESSING]) {
            return;
        }
        if (!eligibleSurvival(minecraft) || snapshot.getCorruptionLevel() != 10 || !allTargets(snapshot)) {
            diamondStatsBaseline = -1;
            setProgress(DIAMOND_BLESSING, 0);
            return;
        }

        int mined = diamondOreMined(minecraft.player);
        if (diamondStatsBaseline < 0 || mined < diamondStatsBaseline) {
            diamondStatsBaseline = mined;
        }
        setProgress(DIAMOND_BLESSING, mined - diamondStatsBaseline);
    }

    private static void updateStableRelease(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[STABLE_RELEASE]) {
            return;
        }
        if (eligibleSurvival(minecraft)
                && snapshot.getCorruptionLevel() == 0
                && snapshot.getEnabledTargetsMask() == 0
                && snapshot.getAutoIncreaseIntervalTicks() == 0) {
            if (!minecraft.isPaused()) {
                setProgress(STABLE_RELEASE, PROGRESS[STABLE_RELEASE] + 1);
            }
        } else {
            setProgress(STABLE_RELEASE, 0);
        }
    }

    private static void updateWorldContext(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        if (worldKey.isBlank()) {
            return;
        }
        if (!worldKey.equals(activeWorldKey)) {
            activeWorldKey = worldKey;
            resetSessionProgress();
        }
        if (!worldKey.isBlank() && worldCheatsExposed(minecraft) && DISQUALIFIED_WORLD_KEYS.add(worldKey)) {
            save();
        }
    }

    private static void resetSessionProgress() {
        Arrays.fill(PROGRESS, 0);
        resetTransientProgress();
    }

    private static void resetTransientProgress() {
        VERTICAL_SAMPLES.clear();
        dragonSeenEligible = false;
        resetNightmare();
        diamondStatsBaseline = -1;
    }

    private static void resetNightmare() {
        nightmareActive = false;
        nightmareLastLevel = -1;
        nightmareLastLevelTick = Long.MIN_VALUE;
        if (!UNLOCKED[NIGHTMARE]) {
            setProgress(NIGHTMARE, 0);
        }
    }

    private static void pauseWorldTransientProgress() {
        VERTICAL_SAMPLES.clear();
        dragonSeenEligible = false;
    }

    private static void suspendRunIfNeeded() {
        if (lastKnownRunSignature != null) {
            suspendedRunSignature = lastKnownRunSignature;
        }
    }

    private static void resumeSuspendedRunIfNeeded(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (suspendedRunSignature == null) {
            return;
        }
        RunSignature current = RunSignature.from(currentWorldKey(minecraft), snapshot);
        if (!suspendedRunSignature.equals(current)) {
            resetSessionProgress();
        }
        suspendedRunSignature = null;
    }

    private static boolean eligibleSurvival(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        return inSurvivalWorld(minecraft)
                && !worldKey.isBlank()
                && !DISQUALIFIED_WORLD_KEYS.contains(worldKey);
    }

    private static boolean worldCheatsExposed(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }
        if (minecraft.gameMode != null) {
            GameType mode = minecraft.gameMode.getPlayerMode();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
                return true;
            }
        }
        try {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null && server.getWorldData() != null && server.getWorldData().getAllowCommands()) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            return minecraft.player.hasPermissions(2);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String currentWorldKey(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return "";
        }
        try {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                Path path = server.getWorldPath(LevelResource.ROOT);
                return "single:" + path.toAbsolutePath().normalize();
            }
        } catch (RuntimeException ignored) {
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            return "server:" + server.ip;
        }
        return "client:" + minecraft.level.dimension().location();
    }

    private static boolean inSurvivalWorld(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.gameMode != null
                && minecraft.gameMode.getPlayerMode() == GameType.SURVIVAL
                && minecraft.player.isAlive()
                && !minecraft.player.isSpectator();
    }

    private static boolean isDimension(Minecraft minecraft, ResourceKey<Level> dimension) {
        return minecraft != null && minecraft.level != null && minecraft.level.dimension() == dimension;
    }

    private static boolean allTargets(CorruptionProfileSnapshot snapshot) {
        return snapshot.getEnabledTargetsMask() == CorruptionTarget.ALL_MASK;
    }

    private static boolean enabled(CorruptionProfileSnapshot snapshot, CorruptionTarget target) {
        return snapshot.isTargetEnabled(target);
    }

    private static boolean nightmareAutoSettings(CorruptionProfileSnapshot snapshot) {
        return snapshot.getAutoIncreaseAmount() == -1
                && Math.abs(snapshot.getAutoIncreaseIntervalTicks() - AUTO_NIGHTMARE_INTERVAL_TICKS) <= AUTO_NIGHTMARE_TOLERANCE_TICKS;
    }

    private static int diamondOreMined(LocalPlayer player) {
        return player.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE))
                + player.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.DEEPSLATE_DIAMOND_ORE));
    }

    private static MovementSample sample(Minecraft minecraft) {
        Player player = minecraft.player;
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return new MovementSample(tick, player.getX(), player.getY(), player.getZ());
    }

    private static void trimSamples(Deque<MovementSample> samples, long now, int maxAgeTicks) {
        while (!samples.isEmpty() && now - samples.peekFirst().tick() > maxAgeTicks) {
            samples.removeFirst();
        }
    }

    private static void setProgress(int index, int value) {
        int clamped = Math.max(0, Math.min(ACHIEVEMENTS[index].requiredProgress(), value));
        if (PROGRESS[index] != clamped) {
            PROGRESS[index] = clamped;
        }
        if (!UNLOCKED[index] && PROGRESS[index] >= ACHIEVEMENTS[index].requiredProgress()) {
            UNLOCKED[index] = true;
            save();
        }
    }

    private static int indexOf(Achievement achievement) {
        for (int index = 0; index < ACHIEVEMENTS.length; index++) {
            if (ACHIEVEMENTS[index] == achievement || ACHIEVEMENTS[index].id().equals(achievement.id())) {
                return index;
            }
        }
        return -1;
    }

    private static Achievement achievementById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Achievement achievement : ACHIEVEMENTS) {
            if (achievement.id().equals(id)) {
                return achievement;
            }
        }
        return null;
    }

    private static String progressText(int index) {
        Achievement achievement = ACHIEVEMENTS[index];
        int progress = Math.min(PROGRESS[index], achievement.requiredProgress());
        return switch (index) {
            case STILL_PLAY, STABLE_RELEASE -> ticksText(progress) + "/" + ticksText(achievement.requiredProgress());
            case SKYHOOK -> progress + "/" + SKYHOOK_REQUIRED_BLOCKS + " blocks";
            case NIGHTMARE -> progress + "%/100%";
            case DIAMOND_BLESSING -> progress + "/" + DIAMOND_REQUIRED_BLOCKS + " diamonds";
            default -> progress + "/" + achievement.requiredProgress();
        };
    }

    private static String ticksText(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        if (seconds >= 60) {
            return (seconds / 60) + "m" + (seconds % 60 == 0 ? "" : " " + (seconds % 60) + "s");
        }
        return seconds + "s";
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Arrays.fill(UNLOCKED, false);
        DISQUALIFIED_WORLD_KEYS.clear();
        PINNED_IDS.clear();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            for (int index = 0; index < ACHIEVEMENTS.length; index++) {
                UNLOCKED[index] = Boolean.parseBoolean(properties.getProperty(ACHIEVEMENTS[index].id(), "false"));
            }
            String worlds = properties.getProperty("disqualifiedWorlds", "");
            if (!worlds.isBlank()) {
                for (String encoded : worlds.split(",")) {
                    String decoded = decodeWorldKey(encoded);
                    if (!decoded.isBlank()) {
                        DISQUALIFIED_WORLD_KEYS.add(decoded);
                    }
                }
            }
            String pinned = properties.getProperty("pinned", "");
            if (!pinned.isBlank()) {
                for (String id : pinned.split(",")) {
                    String trimmed = id.trim();
                    if (achievementById(trimmed) != null) {
                        PINNED_IDS.add(trimmed);
                    }
                }
            }
            pinnedCorner = HudCorner.parse(properties.getProperty("pinnedCorner"));
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to load corruption achievements", exception);
        }
    }

    private static void save() {
        Properties properties = new Properties();
        for (int index = 0; index < ACHIEVEMENTS.length; index++) {
            properties.setProperty(ACHIEVEMENTS[index].id(), Boolean.toString(UNLOCKED[index]));
        }
        if (!DISQUALIFIED_WORLD_KEYS.isEmpty()) {
            StringBuilder worlds = new StringBuilder();
            for (String key : DISQUALIFIED_WORLD_KEYS) {
                if (!worlds.isEmpty()) {
                    worlds.append(',');
                }
                worlds.append(encodeWorldKey(key));
            }
            properties.setProperty("disqualifiedWorlds", worlds.toString());
        }
        if (!PINNED_IDS.isEmpty()) {
            properties.setProperty("pinned", String.join(",", PINNED_IDS));
        }
        properties.setProperty("pinnedCorner", pinnedCorner.name());
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Realtime Minecraft Corruption Simulator achievements");
            }
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to save corruption achievements", exception);
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("realtime_minecraft_corruption_simulator_achievements.properties");
    }

    private static String encodeWorldKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeWorldKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static ResourceLocation icon(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    private static int seconds(int value) {
        return value * 20;
    }

    private static int minutes(int value) {
        return seconds(value * 60);
    }

    private record MovementSample(long tick, double x, double y, double z) {
    }

    private record RunSignature(String worldKey,
                                int corruptionLevel,
                                String activeProfile,
                                long fixedSeed,
                                int enabledTargetsMask,
                                int autoIncreaseIntervalTicks,
                                int autoIncreaseAmount) {
        private static RunSignature from(String worldKey, CorruptionProfileSnapshot snapshot) {
            return new RunSignature(
                    worldKey == null ? "" : worldKey,
                    snapshot.getCorruptionLevel(),
                    snapshot.getActiveProfile(),
                    snapshot.getFixedCorruptionSeed(),
                    snapshot.getEnabledTargetsMask(),
                    snapshot.getAutoIncreaseIntervalTicks(),
                    snapshot.getAutoIncreaseAmount()
            );
        }
    }

    public enum HudCorner {
        TOP_LEFT("Top left"),
        TOP_RIGHT("Top right"),
        BOTTOM_LEFT("Bottom left"),
        BOTTOM_RIGHT("Bottom right");

        private final String label;

        HudCorner(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        private static HudCorner parse(String value) {
            if (value == null || value.isBlank()) {
                return TOP_RIGHT;
            }
            try {
                return HudCorner.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return TOP_RIGHT;
            }
        }
    }

    public record Achievement(String id, String title, String description, Block icon, int requiredProgress) {
    }
}
