package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class CorruptionStateSyncPacket {
    private final CorruptionProfileSnapshot snapshot;

    public CorruptionStateSyncPacket(CorruptionProfileSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.encode(buffer);
    }

    public static CorruptionStateSyncPacket decode(FriendlyByteBuf buffer) {
        return new CorruptionStateSyncPacket(CorruptionProfileSnapshot.decode(buffer));
    }

    public static void handle(CorruptionStateSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNetworkHandlers.handleState(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
