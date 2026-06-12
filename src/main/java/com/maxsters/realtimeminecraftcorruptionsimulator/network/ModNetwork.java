package com.maxsters.realtimeminecraftcorruptionsimulator.network;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.ApplyCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.CorruptionStateSyncPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.OpenCorruptionToolPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.RequestCorruptionStatePacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
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
    private static final String PROTOCOL_VERSION = "2";

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
        CHANNEL.registerMessage(nextId(), ApplyCorruptionSettingsPacket.class, ApplyCorruptionSettingsPacket::encode, ApplyCorruptionSettingsPacket::decode, ApplyCorruptionSettingsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId(), CorruptionStateSyncPacket.class, CorruptionStateSyncPacket::encode, CorruptionStateSyncPacket::decode, CorruptionStateSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId(), OpenCorruptionToolPacket.class, OpenCorruptionToolPacket::encode, OpenCorruptionToolPacket::decode, OpenCorruptionToolPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
        CorruptionRuntimeManager.syncGlobalSettings(data);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CorruptionStateSyncPacket(CorruptionProfileSnapshot.from(data)));
    }

    public static void broadcastState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        CorruptionRuntimeManager.syncGlobalSettings(data);
        CorruptionStateSyncPacket packet = new CorruptionStateSyncPacket(CorruptionProfileSnapshot.from(data));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    private static int nextId() {
        return packetId++;
    }
}
