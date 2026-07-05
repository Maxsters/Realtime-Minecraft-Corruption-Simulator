package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.AchievementWorldStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final int SKYHOOK_REQUIRED_BLOCKS = 300;
    private static final int DIAMOND_REQUIRED_BLOCKS = 7;
    private static final long STABLE_RELEASE_IDLE_PAUSE_MS = 3L * 60L * 1000L;

    private static final Achievement[] ACHIEVEMENTS = new Achievement[]{
            new Achievement("still_play", "I Can Still Play This", "Survive 10m at 100% corruption with all targets enabled.", Blocks.OBSIDIAN, minutes(10)),
            new Achievement("dragon_ten", "Warranty Voided", "Start at 10% with all targets except optional Camera; never change settings; defeat the Ender Dragon.", Blocks.DRAGON_EGG, 1),
            new Achievement("skyhook", "Skyhook", "Gain 300 vertical blocks in 5s at 35%+ corruption with Entities & Timing enabled.", Blocks.SLIME_BLOCK, SKYHOOK_REQUIRED_BLOCKS),
            new Achievement("nightmare", "It Was Just a Nightmare", "Start at 100% corruption with all targets and auto -1% every 30s; never die before 0%.", Blocks.CRYING_OBSIDIAN, 100),
            new Achievement("diamond_blessing", "A Blessing in Disguise", "Mine 7 diamond ore blocks at 10%+ corruption with all targets enabled.", Blocks.DIAMOND_ORE, DIAMOND_REQUIRED_BLOCKS),
            new Achievement("stable_release", "Stable Release", "Play 90m at 0% corruption with auto off and every target disabled.", Blocks.EMERALD_BLOCK, minutes(90))
    };

    private static final Set<ResourceLocation> PROTECTED_ICON_SPRITES = Set.of(
            icon("block/obsidian"),
            icon("block/dragon_egg"),
            icon("block/slime_block"),
            icon("block/crying_obsidian"),
            icon("block/diamond_ore"),
            icon("block/emerald_block")
    );

    // Global state: unlocked awards and HUD preferences are player-wide.
    // World-specific disqualification and dragon-fight state is loaded into activeWorldState per world.
    private static final boolean[] UNLOCKED = new boolean[ACHIEVEMENTS.length];
    private static final int[] PROGRESS = new int[ACHIEVEMENTS.length];
    private static final Set<String> PINNED_IDS = new LinkedHashSet<>();
    private static final Deque<MovementSample> VERTICAL_SAMPLES = new ArrayDeque<>();
    private static WorldAchievementState activeWorldState = new WorldAchievementState();
    private static double skyhookProgressBlocks;
    private static boolean loaded;
    private static String activeWorldKey = "";
    private static HudCorner pinnedCorner = HudCorner.TOP_RIGHT;
    private static boolean nightmareActive;
    private static int nightmareLastLevel = -1;
    private static long nightmareLastLevelTick = Long.MIN_VALUE;
    private static boolean achievementWorldSynced;
    private static AchievementWorldStateSnapshot pendingWorldState;
    private static boolean pendingWorldStateAvailable;
    private static long lastPlayerInputMs = System.currentTimeMillis();
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;

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
        resetTransientProgress();
        save();
    }

    public static boolean isDisqualified(Achievement achievement) {
        ensureLoaded();
        String worldKey = currentWorldKey(Minecraft.getInstance());
        if (achievement == null || worldKey.isBlank()) {
            return false;
        }
        ensureWorldContext(worldKey);
        return activeWorldState.disqualified
                || indexOf(achievement) == WARRANTY_VOIDED && activeWorldState.warrantyDisqualified;
    }

    public static String disqualificationReason(Achievement achievement) {
        ensureLoaded();
        int index = indexOf(achievement);
        if (index < 0) {
            return "";
        }
        String worldKey = currentWorldKey(Minecraft.getInstance());
        if (worldKey.isBlank()) {
            return "";
        }
        ensureWorldContext(worldKey);
        if (!achievementWorldSynced) {
            return "";
        }
        if (activeWorldState.disqualified) {
            return humanDisqualificationReason(activeWorldState.disqualificationReason);
        }
        if (index == WARRANTY_VOIDED && activeWorldState.warrantyDisqualified) {
            return "world did not keep 10% corruption with all required targets";
        }
        if (index == WARRANTY_VOIDED && currentDragonFightSpoiled(Minecraft.getInstance())) {
            return "current Ender Dragon fight was disqualified before the kill";
        }
        return "";
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
        ensureWorldContext(worldKey);
        if (!achievementWorldSynced) {
            return "Waiting for server qualification";
        }
        String reason = disqualificationReason(achievement);
        if (!reason.isEmpty()) {
            return "Disqualified: " + reason;
        }
        if (index == STABLE_RELEASE) {
            String pausedReason = stableReleasePausedReason(Minecraft.getInstance(), ClientCorruptionState.snapshot());
            if (!pausedReason.isEmpty()) {
                return pausedReason + " - " + progressText(index);
            }
        }
        return progressText(index);
    }

    public static float progressRatio(Achievement achievement) {
        int index = indexOf(achievement);
        if (index < 0 || achievement.requiredProgress() <= 0) {
            return 0.0F;
        }
        if (UNLOCKED[index]) {
            return 1.0F;
        }
        if (index == SKYHOOK) {
            return Math.max(0.0F, Math.min(1.0F, (float) skyhookProgressBlocks / achievement.requiredProgress()));
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

    public static void setServerCheatsExposed(boolean exposed) {
    }

    public static void applyServerWorldState(AchievementWorldStateSnapshot snapshot) {
        ensureLoaded();
        pendingWorldState = snapshot == null ? AchievementWorldStateSnapshot.empty() : snapshot;
        pendingWorldStateAvailable = true;
        String worldKey = currentWorldKey(Minecraft.getInstance());
        if (worldKey.isBlank()) {
            return;
        }
        ensureWorldContext(worldKey);
        applyPendingWorldState();
    }

    public static void recordDiamondOreMined() {
        ensureLoaded();
        if (UNLOCKED[DIAMOND_BLESSING]) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CorruptionStateSnapshot snapshot = ClientCorruptionState.snapshot();
        if (diamondBlessingEligible(minecraft, snapshot)) {
            setProgress(DIAMOND_BLESSING, PROGRESS[DIAMOND_BLESSING] + 1);
        } else {
            setProgress(DIAMOND_BLESSING, 0);
        }
    }

    public static void recordWarrantyVoided() {
        ensureLoaded();
        if (!UNLOCKED[WARRANTY_VOIDED]) {
            setProgress(WARRANTY_VOIDED, ACHIEVEMENTS[WARRANTY_VOIDED].requiredProgress());
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) {
            recordPlayerInput();
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) {
            recordPlayerInput();
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (Math.abs(event.getScrollDelta()) > 0.0D) {
            recordPlayerInput();
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        recordPlayerInput();
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        recordPlayerInput();
    }

    @SubscribeEvent
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (Math.abs(event.getScrollDelta()) > 0.0D) {
            recordPlayerInput();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ensureLoaded();
        Minecraft minecraft = Minecraft.getInstance();
        CorruptionStateSnapshot snapshot = ClientCorruptionState.snapshot();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || snapshot == null) {
            pauseWorldTransientProgress();
            return;
        }

        updateHeldInputActivity(minecraft);
        updateWorldContext(minecraft);

        updateStillPlay(minecraft, snapshot);
        updateSkyhook(minecraft, snapshot);
        updateNightmare(minecraft, snapshot);
        updateDiamondBlessing(minecraft, snapshot);
        updateStableRelease(minecraft, snapshot);
    }

    private static void updateStillPlay(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
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

    private static void updateSkyhook(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
        if (UNLOCKED[SKYHOOK]) {
            return;
        }
        if (!eligibleSurvival(minecraft)
                || snapshot.getCorruptionLevel() < 35
                || !enabled(snapshot, CorruptionTarget.ENTITY_BEHAVIOR)) {
            VERTICAL_SAMPLES.clear();
            skyhookProgressBlocks = 0.0D;
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
        skyhookProgressBlocks = Math.min(SKYHOOK_REQUIRED_BLOCKS, Math.max(0.0D, bestGain));
        setProgress(SKYHOOK, skyhookProgressBlocks >= SKYHOOK_REQUIRED_BLOCKS ? SKYHOOK_REQUIRED_BLOCKS : (int) Math.floor(skyhookProgressBlocks));
    }

    private static void updateNightmare(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
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

    private static void updateDiamondBlessing(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
        if (UNLOCKED[DIAMOND_BLESSING]) {
            return;
        }
        if (!diamondBlessingEligible(minecraft, snapshot)) {
            setProgress(DIAMOND_BLESSING, 0);
        }
    }

    private static void updateStableRelease(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
        if (UNLOCKED[STABLE_RELEASE]) {
            return;
        }
        // Stable Release is a calm-play timer: invalid settings/worlds reset it, but death,
        // pause screens, and idle time only stop the clock.
        if (!stableReleaseSettingsActive(snapshot) || !eligibleStableReleaseWorld(minecraft)) {
            setProgress(STABLE_RELEASE, 0);
            return;
        }
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.isPaused() || !hasRecentPlayerInput()) {
            return;
        }
        setProgress(STABLE_RELEASE, PROGRESS[STABLE_RELEASE] + 1);
    }

    private static void updateWorldContext(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        if (worldKey.isBlank()) {
            achievementWorldSynced = false;
            return;
        }
        ensureWorldContext(worldKey);
    }

    private static void resetSessionProgress() {
        Arrays.fill(PROGRESS, 0);
        resetTransientProgress();
    }

    private static void resetTransientProgress() {
        // These helpers track short-lived movement/run windows; they should not survive world switches.
        VERTICAL_SAMPLES.clear();
        skyhookProgressBlocks = 0.0D;
        resetNightmare();
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
    }

    private static boolean eligibleSurvival(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        if (!worldKey.isBlank() && !worldKey.equals(activeWorldKey)) {
            ensureWorldContext(worldKey);
        }
        return inSurvivalWorld(minecraft)
                && !worldKey.isBlank()
                && worldKey.equals(activeWorldKey)
                && achievementWorldSynced
                && !activeWorldState.disqualified;
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
        if (minecraft.getCurrentServer() != null) {
            return "server:" + minecraft.getCurrentServer().ip;
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

    private static boolean allTargets(CorruptionStateSnapshot snapshot) {
        return snapshot.getEnabledTargetsMask() == CorruptionTarget.ALL_MASK;
    }

    private static boolean enabled(CorruptionStateSnapshot snapshot, CorruptionTarget target) {
        return snapshot.isTargetEnabled(target);
    }

    private static boolean diamondBlessingEligible(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
        return snapshot != null
                && eligibleSurvival(minecraft)
                && snapshot.getCorruptionLevel() >= 10
                && allTargets(snapshot);
    }

    private static boolean stableReleaseSettingsActive(CorruptionStateSnapshot snapshot) {
        return snapshot != null
                && snapshot.getCorruptionLevel() == 0
                && snapshot.getEnabledTargetsMask() == 0
                && snapshot.getAutoIncreaseIntervalTicks() == 0;
    }

    private static boolean eligibleStableReleaseWorld(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        if (!worldKey.isBlank() && !worldKey.equals(activeWorldKey)) {
            ensureWorldContext(worldKey);
        }
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.gameMode != null
                && minecraft.gameMode.getPlayerMode() == GameType.SURVIVAL
                && !minecraft.player.isSpectator()
                && !worldKey.isBlank()
                && worldKey.equals(activeWorldKey)
                && achievementWorldSynced
                && !activeWorldState.disqualified;
    }

    private static String stableReleasePausedReason(Minecraft minecraft, CorruptionStateSnapshot snapshot) {
        if (UNLOCKED[STABLE_RELEASE] || !stableReleaseSettingsActive(snapshot) || !eligibleStableReleaseWorld(minecraft)) {
            return "";
        }
        if (minecraft.player == null || !minecraft.player.isAlive()) {
            return "Paused while dead";
        }
        if (minecraft.isPaused()) {
            return "Paused";
        }
        if (!hasRecentPlayerInput()) {
            return "Paused: no input";
        }
        return "";
    }

    private static boolean hasRecentPlayerInput() {
        return System.currentTimeMillis() - lastPlayerInputMs < STABLE_RELEASE_IDLE_PAUSE_MS;
    }

    private static void recordPlayerInput() {
        lastPlayerInputMs = System.currentTimeMillis();
    }

    private static void updateHeldInputActivity(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (minecraft.options.keyUp.isDown()
                || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown()
                || minecraft.options.keyRight.isDown()
                || minecraft.options.keyJump.isDown()
                || minecraft.options.keyShift.isDown()
                || minecraft.options.keySprint.isDown()
                || minecraft.options.keyAttack.isDown()
                || minecraft.options.keyUse.isDown()) {
            recordPlayerInput();
        }
        if (minecraft.mouseHandler != null) {
            double mouseX = minecraft.mouseHandler.xpos();
            double mouseY = minecraft.mouseHandler.ypos();
            if (!Double.isNaN(lastMouseX) && (Math.abs(mouseX - lastMouseX) > 0.01D || Math.abs(mouseY - lastMouseY) > 0.01D)) {
                recordPlayerInput();
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    private static boolean nightmareAutoSettings(CorruptionStateSnapshot snapshot) {
        return snapshot.getAutoIncreaseAmount() == -1
                && Math.abs(snapshot.getAutoIncreaseIntervalTicks() - AUTO_NIGHTMARE_INTERVAL_TICKS) <= AUTO_NIGHTMARE_TOLERANCE_TICKS;
    }

    private static String dragonFightKey(Minecraft minecraft, EnderDragon dragon) {
        if (currentWorldKey(minecraft).isBlank() || dragon == null || dragon.getUUID() == null) {
            return "";
        }
        return dragon.getUUID().toString();
    }

    private static boolean currentDragonFightSpoiled(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || !isDimension(minecraft, Level.END)) {
            return false;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EnderDragon dragon) {
                String key = dragonFightKey(minecraft, dragon);
                if (!key.isBlank() && activeWorldState.spoiledDragonIds.contains(key)) {
                    return true;
                }
            }
        }
        return false;
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
            case SKYHOOK -> String.format(Locale.ROOT, "%.1f/%d blocks", Math.min(skyhookProgressBlocks, SKYHOOK_REQUIRED_BLOCKS), SKYHOOK_REQUIRED_BLOCKS);
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

    private static String humanDisqualificationReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown reason";
        }
        return switch (reason) {
            case "singleplayer_allow_commands" -> "singleplayer cheats were enabled";
            case "permissioned_player" -> "an operator was present";
            case "non_survival_player" -> "a player used Creative or Spectator";
            case "permissioned_command" -> "an operator command was used";
            case "unknown" -> "unknown reason";
            default -> reason.replace('_', ' ');
        };
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        // This file is deliberately global. Do not add per-world disqualification data here.
        Arrays.fill(UNLOCKED, false);
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

    private static void ensureWorldContext(String worldKey) {
        if (worldKey == null || worldKey.isBlank()) {
            activeWorldKey = "";
            activeWorldState = new WorldAchievementState();
            achievementWorldSynced = false;
            return;
        }
        if (worldKey.equals(activeWorldKey)) {
            return;
        }

        activeWorldKey = worldKey;
        activeWorldState = new WorldAchievementState();
        achievementWorldSynced = false;
        resetSessionProgress();
        if (pendingWorldStateAvailable) {
            applyPendingWorldState();
        }
    }

    private static void applyPendingWorldState() {
        activeWorldState = WorldAchievementState.from(pendingWorldState);
        achievementWorldSynced = true;
        pendingWorldState = null;
        pendingWorldStateAvailable = false;
    }

    private static final class WorldAchievementState {
        // Server-owned per-world mirror. This class must never save qualification state locally.
        private boolean disqualified;
        private String disqualificationReason = "";
        private boolean warrantyDisqualified;
        private final Set<String> spoiledDragonIds = new LinkedHashSet<>();

        private static WorldAchievementState from(AchievementWorldStateSnapshot snapshot) {
            WorldAchievementState state = new WorldAchievementState();
            if (snapshot == null) {
                return state;
            }
            state.disqualified = snapshot.disqualified();
            state.disqualificationReason = snapshot.disqualificationReason();
            state.warrantyDisqualified = snapshot.warrantyDisqualified();
            state.spoiledDragonIds.addAll(snapshot.spoiledDragonIds());
            return state;
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

    public enum HudCorner {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

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
