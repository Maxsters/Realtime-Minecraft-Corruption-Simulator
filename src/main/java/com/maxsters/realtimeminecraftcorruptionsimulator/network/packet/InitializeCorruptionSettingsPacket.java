package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class InitializeCorruptionSettingsPacket {
    private final CorruptionStateSnapshot snapshot;

    public InitializeCorruptionSettingsPacket(CorruptionStateSnapshot snapshot) {
        this.snapshot = snapshot == null ? new CorruptionStateSnapshot(0, 0L, CorruptionSavedData.seedLabel(0L), CorruptionTarget.ALL_MASK, 0, 1) : snapshot;
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.encode(buffer);
    }

    public static InitializeCorruptionSettingsPacket decode(FriendlyByteBuf buffer) {
        return new InitializeCorruptionSettingsPacket(CorruptionStateSnapshot.decode(buffer));
    }

    public static void handle(InitializeCorruptionSettingsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.getServer() == null) {
                return;
            }
            CorruptionSavedData data = CorruptionSavedData.get(sender.getServer());
            if (data.isInitialized()) {
                ModNetwork.sendState(sender);
                return;
            }
            CorruptionStateSnapshot snapshot = packet.snapshot;
            GlobalCorruptionSettings.apply(
                    snapshot.getCorruptionLevel(),
                    snapshot.getFixedCorruptionSeed(),
                    snapshot.getCorruptionSeedLabel(),
                    snapshot.getEnabledTargetsMask(),
                    snapshot.getAutoIncreaseIntervalTicks(),
                    snapshot.getAutoIncreaseAmount(),
                    snapshot.isClientDriftEnabled(),
                    snapshot.getSeedRandomizerIntervalTicks()
            );
            CorruptionMechanicsManager.onGlobalSettingsApplied(sender.getServer());
            ModNetwork.broadcastState(sender.getServer());
        });
        context.setPacketHandled(true);
    }
}
