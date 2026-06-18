package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class CorruptionStateSyncPacket {
    private final CorruptionProfileSnapshot snapshot;
    private final boolean serverCheatsExposed;

    public CorruptionStateSyncPacket(CorruptionProfileSnapshot snapshot) {
        this(snapshot, false);
    }

    public CorruptionStateSyncPacket(CorruptionProfileSnapshot snapshot, boolean serverCheatsExposed) {
        this.snapshot = snapshot;
        this.serverCheatsExposed = serverCheatsExposed;
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.encode(buffer);
        buffer.writeBoolean(serverCheatsExposed);
    }

    public static CorruptionStateSyncPacket decode(FriendlyByteBuf buffer) {
        CorruptionProfileSnapshot snapshot = CorruptionProfileSnapshot.decode(buffer);
        boolean serverCheatsExposed = buffer.isReadable() && buffer.readBoolean();
        return new CorruptionStateSyncPacket(snapshot, serverCheatsExposed);
    }

    public static void handle(CorruptionStateSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleClientState(packet.snapshot, packet.serverCheatsExposed));
        context.setPacketHandled(true);
    }

    private static void handleClientState(CorruptionProfileSnapshot snapshot, boolean serverCheatsExposed) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
            Method method = type.getMethod("handleState", CorruptionProfileSnapshot.class, boolean.class);
            method.invoke(null, snapshot, serverCheatsExposed);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            try {
                Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
                Method method = type.getMethod("handleState", CorruptionProfileSnapshot.class);
                method.invoke(null, snapshot);
            } catch (ReflectiveOperationException | LinkageError ignoredAgain) {
            }
        }
    }
}
