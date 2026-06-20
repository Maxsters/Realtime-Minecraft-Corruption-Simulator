package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
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

    private static final String DISQUALIFICATION_SCHEMA = "disqualificationSchema";
    private static final String DISQUALIFICATION_SCHEMA_VERSION = "2";
    private static final String DISQUALIFIED_WORLDS = "disqualifiedWorlds";
    private static final String SPOILED_DRAGON_FIGHTS = "spoiledDragonFights";
    private static final String ARMED_DRAGON_FIGHTS = "armedDragonFights";
    private static final String WARRANTY_VOIDED_STARTED_WORLDS = "warrantyVoidedStartedWorlds";
    private static final String WARRANTY_VOIDED_DISQUALIFIED_WORLDS = "warrantyVoidedDisqualifiedWorlds";

    private static final int AUTO_NIGHTMARE_INTERVAL_TICKS = seconds(30);
    private static final int AUTO_NIGHTMARE_TOLERANCE_TICKS = seconds(2);
    private static final int CHEAT_DISQUALIFICATION_GRACE_TICKS = seconds(3);
    private static final int WARRANTY_VOIDED_CORRUPTION_LEVEL = 10;
    private static final int SKYHOOK_REQUIRED_BLOCKS = 500;
    private static final int DIAMOND_REQUIRED_BLOCKS = 7;
    private static final float DRAGON_FULL_HEALTH_EPSILON = 0.5F;

    private static final Achievement[] ACHIEVEMENTS = new Achievement[]{
            new Achievement("still_play", "I Can Still Play This", "Survive 10m at 100% corruption with all targets enabled.", Blocks.OBSIDIAN, minutes(10)),
            new Achievement("dragon_ten", "Warranty Voided", "Create the world at exactly 10% corruption with all targets enabled, never change it, then defeat the Ender Dragon.", Blocks.DRAGON_EGG, 1),
            new Achievement("skyhook", "Skyhook", "Gain 500 vertical blocks in 5s at 35%+ corruption with Mobility enabled.", Blocks.SLIME_BLOCK, SKYHOOK_REQUIRED_BLOCKS),
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

    private static final boolean[] UNLOCKED = new boolean[ACHIEVEMENTS.length];
    private static final int[] PROGRESS = new int[ACHIEVEMENTS.length];
    private static final Set<String> DISQUALIFIED_WORLD_KEYS = new LinkedHashSet<>();
    private static final Set<String> SPOILED_DRAGON_KEYS = new LinkedHashSet<>();
    private static final Set<String> ARMED_DRAGON_KEYS = new LinkedHashSet<>();
    private static final Set<String> WARRANTY_STARTED_WORLD_KEYS = new LinkedHashSet<>();
    private static final Set<String> WARRANTY_DISQUALIFIED_WORLD_KEYS = new LinkedHashSet<>();
    private static final Set<String> PINNED_IDS = new LinkedHashSet<>();
    private static final Deque<MovementSample> VERTICAL_SAMPLES = new ArrayDeque<>();
    private static double skyhookProgressBlocks;
    private static boolean loaded;
    private static String activeWorldKey = "";
    private static int activeWorldTicks;
    private static HudCorner pinnedCorner = HudCorner.TOP_RIGHT;
    private static boolean nightmareActive;
    private static int nightmareLastLevel = -1;
    private static long nightmareLastLevelTick = Long.MIN_VALUE;
    private static RunSignature lastKnownRunSignature;
    private static RunSignature suspendedRunSignature;
    private static boolean serverCheatsExposed;

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
        Minecraft minecraft = Minecraft.getInstance();
        String currentWorldKey = currentWorldKey(minecraft);
        boolean currentWorldCheatsExposed = !currentWorldKey.isBlank() && worldCheatsExposed(minecraft);
        Arrays.fill(UNLOCKED, false);
        Arrays.fill(PROGRESS, 0);
        if (currentWorldCheatsExposed) {
            DISQUALIFIED_WORLD_KEYS.add(currentWorldKey);
        }
        activeWorldKey = currentWorldKey;
        activeWorldTicks = currentWorldCheatsExposed ? CHEAT_DISQUALIFICATION_GRACE_TICKS : 0;
        lastKnownRunSignature = null;
        suspendedRunSignature = null;
        ARMED_DRAGON_KEYS.clear();
        resetTransientProgress();
        save();
    }

    public static boolean isDisqualified(Achievement achievement) {
        ensureLoaded();
        String worldKey = currentWorldKey(Minecraft.getInstance());
        if (achievement == null || worldKey.isBlank()) {
            return false;
        }
        return DISQUALIFIED_WORLD_KEYS.contains(worldKey)
                || indexOf(achievement) == WARRANTY_VOIDED && WARRANTY_DISQUALIFIED_WORLD_KEYS.contains(worldKey);
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
        if (index == WARRANTY_VOIDED && WARRANTY_DISQUALIFIED_WORLD_KEYS.contains(worldKey)) {
            return "World did not keep 10% + all targets";
        }
        if (index == WARRANTY_VOIDED && currentDragonFightSpoiled(Minecraft.getInstance())) {
            return "Current dragon fight disqualified";
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
        serverCheatsExposed = exposed;
        if (exposed) {
            ensureLoaded();
            disqualifyCurrentWorld(Minecraft.getInstance());
        }
    }

    public static void recordDiamondOreMined() {
        ensureLoaded();
        if (UNLOCKED[DIAMOND_BLESSING]) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CorruptionProfileSnapshot snapshot = ClientCorruptionState.snapshot();
        if (diamondBlessingEligible(minecraft, snapshot)) {
            setProgress(DIAMOND_BLESSING, PROGRESS[DIAMOND_BLESSING] + 1);
        } else {
            setProgress(DIAMOND_BLESSING, 0);
        }
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
        updateWarrantyWorldState(minecraft, snapshot);
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
        if (!isDimension(minecraft, Level.END)) {
            return;
        }

        boolean eligible = warrantyVoidedEligible(minecraft, snapshot);
        boolean changed = false;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EnderDragon dragon) {
                String fightKey = dragonFightKey(minecraft, dragon);
                if (fightKey.isBlank()) {
                    continue;
                }
                boolean alive = dragon.isAlive() && dragon.getHealth() > 0.0F && !dragon.isDeadOrDying();
                if (alive) {
                    if (!eligible) {
                        changed |= SPOILED_DRAGON_KEYS.add(fightKey);
                        changed |= ARMED_DRAGON_KEYS.remove(fightKey);
                    } else if (!SPOILED_DRAGON_KEYS.contains(fightKey)
                            && dragon.getHealth() >= dragon.getMaxHealth() - DRAGON_FULL_HEALTH_EPSILON) {
                        changed |= ARMED_DRAGON_KEYS.add(fightKey);
                    }
                } else if (eligible && ARMED_DRAGON_KEYS.contains(fightKey) && !SPOILED_DRAGON_KEYS.contains(fightKey)) {
                    setProgress(WARRANTY_VOIDED, 1);
                    changed |= ARMED_DRAGON_KEYS.remove(fightKey);
                }
            }
        }
        if (changed) {
            save();
        }
    }

    private static void updateWarrantyWorldState(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        if (UNLOCKED[WARRANTY_VOIDED]) {
            return;
        }
        String worldKey = currentWorldKey(minecraft);
        if (worldKey.isBlank() || snapshot == null || WARRANTY_DISQUALIFIED_WORLD_KEYS.contains(worldKey)) {
            return;
        }

        boolean exactWarrantySettings = warrantySettingsActive(snapshot);
        boolean changed = false;
        if (!WARRANTY_STARTED_WORLD_KEYS.contains(worldKey)) {
            if (exactWarrantySettings) {
                changed = WARRANTY_STARTED_WORLD_KEYS.add(worldKey);
            } else {
                changed = WARRANTY_DISQUALIFIED_WORLD_KEYS.add(worldKey);
            }
        } else if (!exactWarrantySettings) {
            changed = WARRANTY_DISQUALIFIED_WORLD_KEYS.add(worldKey);
            removeArmedDragonFightsForWorld(worldKey);
        }
        if (changed) {
            save();
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
        if (!diamondBlessingEligible(minecraft, snapshot)) {
            setProgress(DIAMOND_BLESSING, 0);
        }
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
            activeWorldTicks = 0;
            return;
        }
        if (!worldKey.equals(activeWorldKey)) {
            activeWorldKey = worldKey;
            activeWorldTicks = 0;
            resetSessionProgress();
        }
        activeWorldTicks = Math.min(CHEAT_DISQUALIFICATION_GRACE_TICKS, activeWorldTicks + 1);
        if (shouldDisqualifyCurrentWorld(minecraft) && DISQUALIFIED_WORLD_KEYS.add(worldKey)) {
            save();
        }
    }

    private static boolean shouldDisqualifyCurrentWorld(Minecraft minecraft) {
        return confirmedWorldCheatsExposed(minecraft)
                || activeWorldTicks >= CHEAT_DISQUALIFICATION_GRACE_TICKS && worldCheatsExposed(minecraft);
    }

    private static void disqualifyCurrentWorld(Minecraft minecraft) {
        String worldKey = currentWorldKey(minecraft);
        if (!worldKey.isBlank() && DISQUALIFIED_WORLD_KEYS.add(worldKey)) {
            activeWorldKey = worldKey;
            activeWorldTicks = CHEAT_DISQUALIFICATION_GRACE_TICKS;
            resetTransientProgress();
            save();
        }
    }

    private static void resetSessionProgress() {
        Arrays.fill(PROGRESS, 0);
        resetTransientProgress();
    }

    private static void resetTransientProgress() {
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
        if (serverCheatsExposed || minecraft.player.hasPermissions(2)) {
            return true;
        }
        return confirmedWorldCheatsExposed(minecraft);
    }

    private static boolean confirmedWorldCheatsExposed(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        if (serverCheatsExposed) {
            return true;
        }
        try {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                return server.getWorldData() != null && server.getWorldData().getAllowCommands();
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
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

    private static boolean diamondBlessingEligible(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        return snapshot != null
                && eligibleSurvival(minecraft)
                && snapshot.getCorruptionLevel() >= 10
                && allTargets(snapshot);
    }

    private static boolean warrantyVoidedEligible(Minecraft minecraft, CorruptionProfileSnapshot snapshot) {
        String worldKey = currentWorldKey(minecraft);
        return snapshot != null
                && eligibleSurvival(minecraft)
                && !worldKey.isBlank()
                && WARRANTY_STARTED_WORLD_KEYS.contains(worldKey)
                && !WARRANTY_DISQUALIFIED_WORLD_KEYS.contains(worldKey)
                && warrantySettingsActive(snapshot);
    }

    private static boolean warrantySettingsActive(CorruptionProfileSnapshot snapshot) {
        return snapshot != null
                && snapshot.getCorruptionLevel() == WARRANTY_VOIDED_CORRUPTION_LEVEL
                && allTargets(snapshot);
    }

    private static boolean nightmareAutoSettings(CorruptionProfileSnapshot snapshot) {
        return snapshot.getAutoIncreaseAmount() == -1
                && Math.abs(snapshot.getAutoIncreaseIntervalTicks() - AUTO_NIGHTMARE_INTERVAL_TICKS) <= AUTO_NIGHTMARE_TOLERANCE_TICKS;
    }

    private static String dragonFightKey(Minecraft minecraft, EnderDragon dragon) {
        String worldKey = currentWorldKey(minecraft);
        if (worldKey.isBlank() || dragon == null || dragon.getUUID() == null) {
            return "";
        }
        return worldKey + "\nender_dragon:" + dragon.getUUID();
    }

    private static boolean currentDragonFightSpoiled(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || !isDimension(minecraft, Level.END)) {
            return false;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EnderDragon dragon) {
                String key = dragonFightKey(minecraft, dragon);
                if (!key.isBlank() && SPOILED_DRAGON_KEYS.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeArmedDragonFightsForWorld(String worldKey) {
        if (worldKey == null || worldKey.isBlank()) {
            return;
        }
        ARMED_DRAGON_KEYS.removeIf(key -> key.startsWith(worldKey + "\nender_dragon:"));
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

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Arrays.fill(UNLOCKED, false);
        DISQUALIFIED_WORLD_KEYS.clear();
        SPOILED_DRAGON_KEYS.clear();
        ARMED_DRAGON_KEYS.clear();
        WARRANTY_STARTED_WORLD_KEYS.clear();
        WARRANTY_DISQUALIFIED_WORLD_KEYS.clear();
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
            String worlds = DISQUALIFICATION_SCHEMA_VERSION.equals(properties.getProperty(DISQUALIFICATION_SCHEMA))
                    ? properties.getProperty(DISQUALIFIED_WORLDS, "")
                    : "";
            loadEncodedSet(worlds, DISQUALIFIED_WORLD_KEYS);
            loadEncodedSet(properties.getProperty(SPOILED_DRAGON_FIGHTS, ""), SPOILED_DRAGON_KEYS);
            loadEncodedSet(properties.getProperty(ARMED_DRAGON_FIGHTS, ""), ARMED_DRAGON_KEYS);
            loadEncodedSet(properties.getProperty(WARRANTY_VOIDED_STARTED_WORLDS, ""), WARRANTY_STARTED_WORLD_KEYS);
            loadEncodedSet(properties.getProperty(WARRANTY_VOIDED_DISQUALIFIED_WORLDS, ""), WARRANTY_DISQUALIFIED_WORLD_KEYS);
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
        properties.setProperty(DISQUALIFICATION_SCHEMA, DISQUALIFICATION_SCHEMA_VERSION);
        if (!DISQUALIFIED_WORLD_KEYS.isEmpty()) {
            properties.setProperty(DISQUALIFIED_WORLDS, encodedSet(DISQUALIFIED_WORLD_KEYS));
        }
        if (!SPOILED_DRAGON_KEYS.isEmpty()) {
            properties.setProperty(SPOILED_DRAGON_FIGHTS, encodedSet(SPOILED_DRAGON_KEYS));
        }
        if (!ARMED_DRAGON_KEYS.isEmpty()) {
            properties.setProperty(ARMED_DRAGON_FIGHTS, encodedSet(ARMED_DRAGON_KEYS));
        }
        if (!WARRANTY_STARTED_WORLD_KEYS.isEmpty()) {
            properties.setProperty(WARRANTY_VOIDED_STARTED_WORLDS, encodedSet(WARRANTY_STARTED_WORLD_KEYS));
        }
        if (!WARRANTY_DISQUALIFIED_WORLD_KEYS.isEmpty()) {
            properties.setProperty(WARRANTY_VOIDED_DISQUALIFIED_WORLDS, encodedSet(WARRANTY_DISQUALIFIED_WORLD_KEYS));
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

    private static String encodedSet(Set<String> keys) {
        StringBuilder encoded = new StringBuilder();
        for (String key : keys) {
            if (!encoded.isEmpty()) {
                encoded.append(',');
            }
            encoded.append(encodeWorldKey(key));
        }
        return encoded.toString();
    }

    private static void loadEncodedSet(String value, Set<String> target) {
        if (value == null || value.isBlank() || target == null) {
            return;
        }
        for (String encoded : value.split(",")) {
            String decoded = decodeWorldKey(encoded);
            if (!decoded.isBlank()) {
                target.add(decoded);
            }
        }
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
                                int autoIncreaseAmount,
                                boolean clientDriftEnabled,
                                int seedRandomizerIntervalTicks) {
        private static RunSignature from(String worldKey, CorruptionProfileSnapshot snapshot) {
            return new RunSignature(
                    worldKey == null ? "" : worldKey,
                    snapshot.getCorruptionLevel(),
                    snapshot.getActiveProfile(),
                    snapshot.getFixedCorruptionSeed(),
                    snapshot.getEnabledTargetsMask(),
                    snapshot.getAutoIncreaseIntervalTicks(),
                    snapshot.getAutoIncreaseAmount(),
                    snapshot.isClientDriftEnabled(),
                    snapshot.getSeedRandomizerIntervalTicks()
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
