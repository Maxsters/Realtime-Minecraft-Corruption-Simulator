package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class OpenCorruptionToolPacket {
    public void encode(FriendlyByteBuf buffer) {
    }

    public static OpenCorruptionToolPacket decode(FriendlyByteBuf buffer) {
        return new OpenCorruptionToolPacket();
    }

    public static void handle(OpenCorruptionToolPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientNetworkHandlers::openOverlayFromServer));
        context.setPacketHandled(true);
    }
}
