package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class AchievementEventPacket {
    public static final String DIAMOND_ORE_MINED = "diamond_ore_mined";

    private final String eventId;

    public AchievementEventPacket(String eventId) {
        this.eventId = eventId == null ? "" : eventId;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(eventId);
    }

    public static AchievementEventPacket decode(FriendlyByteBuf buffer) {
        return new AchievementEventPacket(buffer.readUtf(64));
    }

    public static void handle(AchievementEventPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleClientEvent(packet.eventId));
        context.setPacketHandled(true);
    }

    private static void handleClientEvent(String eventId) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
            Method method = type.getMethod("handleAchievementEvent", String.class);
            method.invoke(null, eventId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }
}
