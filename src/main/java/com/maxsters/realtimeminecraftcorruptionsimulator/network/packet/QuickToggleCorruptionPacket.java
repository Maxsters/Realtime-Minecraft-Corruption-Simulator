package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class QuickToggleCorruptionPacket {
    public void encode(FriendlyByteBuf buffer) {
    }

    public static QuickToggleCorruptionPacket decode(FriendlyByteBuf buffer) {
        return new QuickToggleCorruptionPacket();
    }

    public static void handle(QuickToggleCorruptionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.getServer() == null) {
                return;
            }
            if (!ModNetwork.canUpdateSettings(sender)) {
                ModNetwork.sendState(sender);
                return;
            }

            MinecraftServer server = sender.getServer();
            CorruptionSavedData data = CorruptionSavedData.get(server);
            if (!data.isInitialized()) {
                CorruptionRuntimeManager.copyGlobalSettingsToData(data);
            } else {
                CorruptionRuntimeManager.applySavedDataToGlobalSettings(data);
            }

            CorruptionStateSnapshot current = CorruptionStateSnapshot.from(data);
            if (data.hasQuickToggleRestore() && isQuickToggleOff(current)) {
                applySnapshot(server, data.quickToggleRestoreSnapshot());
                data.clearQuickToggleRestore();
                ModNetwork.broadcastState(server);
                return;
            }

            if (isQuickToggleOff(current)) {
                ModNetwork.sendState(sender);
                return;
            }

            data.setQuickToggleRestore(current);
            GlobalCorruptionSettings.apply(
                    0,
                    current.getFixedCorruptionSeed(),
                    current.getCorruptionSeedLabel(),
                    0,
                    0,
                    current.getAutoIncreaseAmount(),
                    current.isClientDriftEnabled(),
                    0
            );
            CorruptionMechanicsManager.onGlobalSettingsApplied(server);
            ModNetwork.broadcastState(server);
        });
        context.setPacketHandled(true);
    }

    private static void applySnapshot(MinecraftServer server, CorruptionStateSnapshot snapshot) {
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
        CorruptionMechanicsManager.onGlobalSettingsApplied(server);
    }

    private static boolean isQuickToggleOff(CorruptionStateSnapshot snapshot) {
        return snapshot.getCorruptionLevel() == 0
                && snapshot.getEnabledTargetsMask() == 0
                && snapshot.getAutoIncreaseIntervalTicks() == 0
                && snapshot.getSeedRandomizerIntervalTicks() == 0;
    }
}
