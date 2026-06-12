package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RequestCorruptionStatePacket {
    public void encode(FriendlyByteBuf buffer) {
    }

    public static RequestCorruptionStatePacket decode(FriendlyByteBuf buffer) {
        return new RequestCorruptionStatePacket();
    }

    public static void handle(RequestCorruptionStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ModNetwork.sendState(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
