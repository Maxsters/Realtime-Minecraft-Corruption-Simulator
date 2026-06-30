package com.maxsters.realtimeminecraftcorruptionsimulator.network;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.achievements.ServerAchievementStateManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.AchievementEventPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.ApplyCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.CorruptionStateSyncPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.InitializeCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.OpenCorruptionToolPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.RequestCorruptionStatePacket;
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
    private static final String PROTOCOL_VERSION = "6";

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
        CHANNEL.registerMessage(nextId(), CorruptionStateSyncPacket.class, CorruptionStateSyncPacket::encode, CorruptionStateSyncPacket::decode, CorruptionStateSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId(), OpenCorruptionToolPacket.class, OpenCorruptionToolPacket::encode, OpenCorruptionToolPacket::decode, OpenCorruptionToolPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId(), AchievementEventPacket.class, AchievementEventPacket::encode, AchievementEventPacket::decode, AchievementEventPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void openTool(ServerPlayer player) {
        sendState(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenCorruptionToolPacket());
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), syncPacket(data, initialized));
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
        CorruptionStateSyncPacket packet = syncPacket(data, initialized);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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

    private static int nextId() {
        return packetId++;
    }

    private static CorruptionStateSyncPacket syncPacket(CorruptionSavedData data, boolean initialized) {
        AchievementWorldStateSnapshot achievementWorldState = AchievementWorldStateSnapshot.from(data);
        return new CorruptionStateSyncPacket(
                CorruptionStateSnapshot.from(data),
                achievementWorldState.disqualified(),
                initialized,
                achievementWorldState
        );
    }
}
