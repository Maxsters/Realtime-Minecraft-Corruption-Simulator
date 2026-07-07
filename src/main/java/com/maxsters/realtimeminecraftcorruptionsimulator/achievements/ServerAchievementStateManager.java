package com.maxsters.realtimeminecraftcorruptionsimulator.achievements;

import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.AchievementEventPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class ServerAchievementStateManager {
    private static final float DRAGON_FULL_HEALTH_EPSILON = 0.5F;
    private static final AABB END_DRAGON_SCAN_BOUNDS = new AABB(-1024.0D, -256.0D, -1024.0D, 1024.0D, 512.0D, 1024.0D);

    private ServerAchievementStateManager() {
    }

    public static boolean refresh(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        boolean changed = updateCheatDisqualification(server, data);
        if (!data.isInitialized()) {
            return changed;
        }
        changed |= updateWarrantyWorldState(data);
        changed |= updateBlessingWorldState(data);
        changed |= updateLiveDragonFights(server, data);
        return changed;
    }

    public static boolean markDisqualified(MinecraftServer server, String reason) {
        if (server == null) {
            return false;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        boolean changed = data.markServerAchievementDisqualified(reason);
        changed |= data.setWarrantyDisqualified(true);
        changed |= data.clearArmedDragonFights();
        return changed;
    }

    public static void handleDragonDeath(EnderDragon dragon) {
        if (dragon == null || !(dragon.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        CorruptionSavedData data = CorruptionSavedData.get(server);
        String dragonId = dragonId(dragon);
        if (dragonId.isBlank()) {
            return;
        }
        boolean eligible = warrantyEligible(data)
                && data.isDragonFightArmed(dragonId)
                && !data.isDragonFightSpoiled(dragonId);
        boolean changed = data.disarmDragonFight(dragonId);
        if (eligible) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() == Level.END && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.isSpectator()) {
                    ModNetwork.sendAchievementEvent(player, AchievementEventPacket.WARRANTY_VOIDED);
                }
            }
        }
        if (changed || eligible) {
            ModNetwork.broadcastState(server);
        }
    }

    public static void handleDiamondOreMined(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || player.isSpectator()) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(player.getServer());
        if (blessingEligible(data)) {
            ModNetwork.sendAchievementEvent(player, AchievementEventPacket.DIAMOND_ORE_MINED);
        }
    }

    private static boolean updateCheatDisqualification(MinecraftServer server, CorruptionSavedData data) {
        if (data.hasServerAchievementDisqualificationReason()) {
            return false;
        }
        try {
            if (!server.isDedicatedServer() && server.getWorldData() != null && server.getWorldData().getAllowCommands()) {
                return markDisqualified(server, "singleplayer_allow_commands");
            }
        } catch (RuntimeException ignored) {
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                return markDisqualified(server, "permissioned_player");
            }
            GameType gameType = player.gameMode.getGameModeForPlayer();
            if (gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR || player.isSpectator()) {
                return markDisqualified(server, "non_survival_player");
            }
        }
        return false;
    }

    private static boolean updateWarrantyWorldState(CorruptionSavedData data) {
        if (data.isAchievementWorldDisqualified() || data.isWarrantyDisqualified()) {
            return false;
        }

        boolean exactWarrantySettings = AchievementRules.warrantySettingsActive(data.getCorruptionLevel(), data.getEnabledTargetsMask());
        if (!data.isWarrantyStarted()) {
            if (exactWarrantySettings) {
                return data.setWarrantyStarted(true);
            }
            return data.setWarrantyDisqualified(true);
        }
        if (!exactWarrantySettings) {
            boolean changed = data.setWarrantyDisqualified(true);
            changed |= data.clearArmedDragonFights();
            return changed;
        }
        return false;
    }

    private static boolean updateBlessingWorldState(CorruptionSavedData data) {
        if (data.isAchievementWorldDisqualified() || data.isBlessingDisqualified()) {
            return false;
        }

        boolean runtimeSettings = AchievementRules.blessingRuntimeSettingsActive(data.getCorruptionLevel(), data.getEnabledTargetsMask());
        if (!data.isBlessingStarted()) {
            if (AchievementRules.blessingStartSettingsActive(data.getCorruptionLevel(), data.getEnabledTargetsMask())) {
                return data.setBlessingStarted(true);
            }
            return data.setBlessingDisqualified(true);
        }
        if (!runtimeSettings) {
            return data.setBlessingDisqualified(true);
        }
        return false;
    }

    private static boolean updateLiveDragonFights(MinecraftServer server, CorruptionSavedData data) {
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            return false;
        }
        boolean eligible = warrantyEligible(data);
        boolean changed = false;
        for (EnderDragon dragon : end.getEntitiesOfClass(EnderDragon.class, END_DRAGON_SCAN_BOUNDS)) {
            String dragonId = dragonId(dragon);
            if (dragonId.isBlank()) {
                continue;
            }
            boolean alive = dragon.isAlive() && dragon.getHealth() > 0.0F && !dragon.isDeadOrDying();
            if (!alive) {
                continue;
            }
            if (!eligible) {
                changed |= data.spoilDragonFight(dragonId);
                changed |= data.disarmDragonFight(dragonId);
            } else if (!data.isDragonFightSpoiled(dragonId)
                    && dragon.getHealth() >= dragon.getMaxHealth() - DRAGON_FULL_HEALTH_EPSILON) {
                changed |= data.armDragonFight(dragonId);
            }
        }
        return changed;
    }

    private static boolean warrantyEligible(CorruptionSavedData data) {
        return data != null
                && data.isInitialized()
                && !data.isAchievementWorldDisqualified()
                && data.isWarrantyStarted()
                && !data.isWarrantyDisqualified()
                && AchievementRules.warrantySettingsActive(data.getCorruptionLevel(), data.getEnabledTargetsMask());
    }

    private static boolean blessingEligible(CorruptionSavedData data) {
        return data != null
                && data.isInitialized()
                && !data.isAchievementWorldDisqualified()
                && data.isBlessingStarted()
                && !data.isBlessingDisqualified()
                && AchievementRules.blessingRuntimeSettingsActive(data.getCorruptionLevel(), data.getEnabledTargetsMask());
    }

    private static String dragonId(Entity dragon) {
        return dragon == null || dragon.getUUID() == null ? "" : dragon.getUUID().toString();
    }
}
