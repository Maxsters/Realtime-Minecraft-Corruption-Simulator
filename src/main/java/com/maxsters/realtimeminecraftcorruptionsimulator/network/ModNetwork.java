package com.maxsters.realtimeminecraftcorruptionsimulator.network;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.achievements.ServerAchievementStateManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.AchievementEventPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.ApplyCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.CorruptionStateSyncPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.InitializeCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.QuickToggleCorruptionPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.RequestCorruptionStatePacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.UpdateSettingsAccessPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.AchievementWorldStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "9";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(RealtimeMinecraftCorruptionSimulator.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;
    private static boolean registered;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        CHANNEL.registerMessage(nextId(), RequestCorruptionStatePacket.class, RequestCorruptionStatePacket::encode, RequestCorruptionStatePacket::decode, RequestCorruptionStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), InitializeCorruptionSettingsPacket.class, InitializeCorruptionSettingsPacket::encode, InitializeCorruptionSettingsPacket::decode, InitializeCorruptionSettingsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), ApplyCorruptionSettingsPacket.class, ApplyCorruptionSettingsPacket::encode, ApplyCorruptionSettingsPacket::decode, ApplyCorruptionSettingsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), QuickToggleCorruptionPacket.class, QuickToggleCorruptionPacket::encode, QuickToggleCorruptionPacket::decode, QuickToggleCorruptionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), UpdateSettingsAccessPacket.class, UpdateSettingsAccessPacket::encode, UpdateSettingsAccessPacket::decode, UpdateSettingsAccessPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), CorruptionStateSyncPacket.class, CorruptionStateSyncPacket::encode, CorruptionStateSyncPacket::decode, CorruptionStateSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId(), AchievementEventPacket.class, AchievementEventPacket::encode, AchievementEventPacket::decode, AchievementEventPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void sendState(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(player.getServer());
        boolean initialized = data.isInitialized();
        if (initialized) {
            CorruptionRuntimeManager.applySavedDataToGlobalSettings(data);
        }
        ServerAchievementStateManager.refresh(player.getServer());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), syncPacket(data, initialized, player));
    }

    public static void broadcastState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        boolean initialized = data.isInitialized();
        if (initialized) {
            CorruptionRuntimeManager.applySavedDataToGlobalSettings(data);
        }
        ServerAchievementStateManager.refresh(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CorruptionStateSyncPacket packet = syncPacket(data, initialized, player);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void markServerAchievementDisqualified(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (ServerAchievementStateManager.markDisqualified(server, "permissioned_command")) {
            broadcastState(server);
        }
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendAchievementEvent(ServerPlayer player, String eventId) {
        if (player != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AchievementEventPacket(eventId));
        }
    }

    public static boolean isSettingsOperator(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    public static boolean canUpdateSettings(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        if (!player.getServer().isDedicatedServer()) {
            return true;
        }
        if (isSettingsOperator(player)) {
            return true;
        }
        return CorruptionSavedData.get(player.getServer()).allowNonOpSettingsUpdates();
    }

    private static int nextId() {
        return packetId++;
    }

    private static CorruptionStateSyncPacket syncPacket(CorruptionSavedData data, boolean initialized, ServerPlayer player) {
        AchievementWorldStateSnapshot achievementWorldState = AchievementWorldStateSnapshot.from(data);
        boolean operator = isSettingsOperator(player);
        boolean dedicatedServer = player != null && player.getServer() != null && player.getServer().isDedicatedServer();
        boolean allowNonOpSettingsUpdates = !dedicatedServer || data.allowNonOpSettingsUpdates();
        return new CorruptionStateSyncPacket(
                CorruptionStateSnapshot.from(data),
                achievementWorldState.disqualified(),
                initialized,
                achievementWorldState,
                allowNonOpSettingsUpdates,
                operator || allowNonOpSettingsUpdates,
                dedicatedServer && operator
        );
    }
}
