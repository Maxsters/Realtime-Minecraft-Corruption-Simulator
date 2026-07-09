package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class UpdateSettingsAccessPacket {
    private final boolean allowNonOpSettingsUpdates;

    public UpdateSettingsAccessPacket(boolean allowNonOpSettingsUpdates) {
        this.allowNonOpSettingsUpdates = allowNonOpSettingsUpdates;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(allowNonOpSettingsUpdates);
    }

    public static UpdateSettingsAccessPacket decode(FriendlyByteBuf buffer) {
        return new UpdateSettingsAccessPacket(buffer.readBoolean());
    }

    public static void handle(UpdateSettingsAccessPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.getServer() == null) {
                return;
            }
            if (!sender.getServer().isDedicatedServer()) {
                CorruptionSavedData data = CorruptionSavedData.get(sender.getServer());
                if (data.setAllowNonOpSettingsUpdates(true)) {
                    ModNetwork.broadcastState(sender.getServer());
                } else {
                    ModNetwork.sendState(sender);
                }
                return;
            }
            if (!ModNetwork.isSettingsOperator(sender)) {
                ModNetwork.sendState(sender);
                return;
            }
            CorruptionSavedData data = CorruptionSavedData.get(sender.getServer());
            if (!data.isInitialized()) {
                CorruptionRuntimeManager.copyGlobalSettingsToData(data);
            }
            data.setAllowNonOpSettingsUpdates(packet.allowNonOpSettingsUpdates);
            ModNetwork.broadcastState(sender.getServer());
        });
        context.setPacketHandled(true);
    }
}
