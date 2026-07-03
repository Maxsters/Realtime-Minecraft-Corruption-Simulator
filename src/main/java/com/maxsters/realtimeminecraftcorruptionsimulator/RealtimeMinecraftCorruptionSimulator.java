package com.maxsters.realtimeminecraftcorruptionsimulator;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.diagnostics.CorruptionStallWatchdog;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod(RealtimeMinecraftCorruptionSimulator.MOD_ID)
public final class RealtimeMinecraftCorruptionSimulator {
    public static final String MOD_ID = "realtime_minecraft_corruption_simulator";
    public static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, Boolean> knownOperatorStatus = new HashMap<>();
    private int permissionSyncTicker;

    public RealtimeMinecraftCorruptionSimulator() {
        GlobalCorruptionSettings.ensureLoaded();
        ModNetwork.register();
        CorruptionStallWatchdog.bootstrap();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            knownOperatorStatus.put(player.getUUID(), ModNetwork.isSettingsOperator(player));
            ModNetwork.broadcastState(player.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            knownOperatorStatus.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++permissionSyncTicker % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        boolean changed = false;
        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            onlinePlayers.add(playerId);
            boolean operator = ModNetwork.isSettingsOperator(player);
            Boolean previous = knownOperatorStatus.put(playerId, operator);
            if (previous != null && previous != operator) {
                changed = true;
            }
        }
        knownOperatorStatus.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
        if (changed) {
            ModNetwork.broadcastState(server);
        }
    }
}
