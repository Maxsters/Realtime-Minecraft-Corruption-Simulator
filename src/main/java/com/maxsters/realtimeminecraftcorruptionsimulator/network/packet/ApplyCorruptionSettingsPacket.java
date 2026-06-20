package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ApplyCorruptionSettingsPacket {
    private final int requestedLevel;
    private final long seed;
    private final String seedLabel;
    private final int enabledTargetsMask;
    private final int autoIncreaseIntervalTicks;
    private final int autoIncreaseAmount;
    private final boolean clientDriftEnabled;
    private final int seedRandomizerIntervalTicks;

    public ApplyCorruptionSettingsPacket(int requestedLevel, long seed, String seedLabel, int enabledTargetsMask) {
        this(requestedLevel, seed, seedLabel, enabledTargetsMask, GlobalCorruptionSettings.autoIncreaseIntervalTicks(), GlobalCorruptionSettings.autoIncreaseAmount(), GlobalCorruptionSettings.clientDriftEnabled(), GlobalCorruptionSettings.seedRandomizerIntervalTicks());
    }

    public ApplyCorruptionSettingsPacket(int requestedLevel, long seed, String seedLabel, int enabledTargetsMask, int autoIncreaseIntervalTicks, int autoIncreaseAmount) {
        this(requestedLevel, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount, GlobalCorruptionSettings.clientDriftEnabled(), GlobalCorruptionSettings.seedRandomizerIntervalTicks());
    }

    public ApplyCorruptionSettingsPacket(int requestedLevel, long seed, String seedLabel, int enabledTargetsMask, int autoIncreaseIntervalTicks, int autoIncreaseAmount, boolean clientDriftEnabled, int seedRandomizerIntervalTicks) {
        this.requestedLevel = Math.max(0, Math.min(100, requestedLevel));
        this.seed = seed;
        this.seedLabel = CorruptionSavedData.sanitizeSeedLabel(seedLabel, seed);
        this.enabledTargetsMask = CorruptionTarget.normalizeMask(enabledTargetsMask);
        this.autoIncreaseIntervalTicks = clampIntervalTicks(autoIncreaseIntervalTicks);
        this.autoIncreaseAmount = clampAutoAmount(autoIncreaseAmount);
        this.clientDriftEnabled = clientDriftEnabled;
        this.seedRandomizerIntervalTicks = clampIntervalTicks(seedRandomizerIntervalTicks);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestedLevel);
        buffer.writeLong(seed);
        buffer.writeUtf(seedLabel);
        buffer.writeVarInt(enabledTargetsMask);
        buffer.writeVarInt(autoIncreaseIntervalTicks);
        buffer.writeVarInt(autoIncreaseAmount);
        buffer.writeBoolean(clientDriftEnabled);
        buffer.writeVarInt(seedRandomizerIntervalTicks);
    }

    public static ApplyCorruptionSettingsPacket decode(FriendlyByteBuf buffer) {
        return new ApplyCorruptionSettingsPacket(buffer.readVarInt(), buffer.readLong(), buffer.readUtf(96), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(ApplyCorruptionSettingsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null && sender.getServer() != null) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.info(
                        "Applying corruption settings: level={} seed={} targets={}",
                        packet.requestedLevel,
                        CorruptionSavedData.seedLabel(packet.seed),
                        packet.enabledTargetsMask
                );
                GlobalCorruptionSettings.apply(packet.requestedLevel, packet.seed, packet.seedLabel, packet.enabledTargetsMask, packet.autoIncreaseIntervalTicks, packet.autoIncreaseAmount, packet.clientDriftEnabled, packet.seedRandomizerIntervalTicks);
                CorruptionMechanicsManager.onGlobalSettingsApplied(sender.getServer());
                ModNetwork.broadcastState(sender.getServer());
                RealtimeMinecraftCorruptionSimulator.LOGGER.info("Corruption settings applied and synchronized");
            }
        });
        context.setPacketHandled(true);
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
