package com.maxsters.realtimeminecraftcorruptionsimulator.network.packet;

import com.maxsters.realtimeminecraftcorruptionsimulator.state.AchievementWorldStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class CorruptionStateSyncPacket {
    private final CorruptionStateSnapshot snapshot;
    private final boolean serverCheatsExposed;
    private final boolean serverSettingsInitialized;
    private final AchievementWorldStateSnapshot achievementWorldState;
    private final boolean allowNonOpSettingsUpdates;
    private final boolean canUpdateSettings;
    private final boolean settingsOperator;

    public CorruptionStateSyncPacket(CorruptionStateSnapshot snapshot) {
        this(snapshot, false);
    }

    public CorruptionStateSyncPacket(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed) {
        this(snapshot, serverCheatsExposed, true, AchievementWorldStateSnapshot.empty());
    }

    public CorruptionStateSyncPacket(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed, boolean serverSettingsInitialized, AchievementWorldStateSnapshot achievementWorldState) {
        this(snapshot, serverCheatsExposed, serverSettingsInitialized, achievementWorldState, false, true, true);
    }

    public CorruptionStateSyncPacket(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed, boolean serverSettingsInitialized, AchievementWorldStateSnapshot achievementWorldState, boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean settingsOperator) {
        this.snapshot = snapshot;
        this.serverCheatsExposed = serverCheatsExposed;
        this.serverSettingsInitialized = serverSettingsInitialized;
        this.achievementWorldState = achievementWorldState == null ? AchievementWorldStateSnapshot.empty() : achievementWorldState;
        this.allowNonOpSettingsUpdates = allowNonOpSettingsUpdates;
        this.canUpdateSettings = canUpdateSettings;
        this.settingsOperator = settingsOperator;
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.encode(buffer);
        buffer.writeBoolean(serverCheatsExposed);
        buffer.writeBoolean(serverSettingsInitialized);
        achievementWorldState.encode(buffer);
        buffer.writeBoolean(allowNonOpSettingsUpdates);
        buffer.writeBoolean(canUpdateSettings);
        buffer.writeBoolean(settingsOperator);
        achievementWorldState.encodeDisqualificationReason(buffer);
    }

    public static CorruptionStateSyncPacket decode(FriendlyByteBuf buffer) {
        CorruptionStateSnapshot snapshot = CorruptionStateSnapshot.decode(buffer);
        boolean serverCheatsExposed = buffer.isReadable() && buffer.readBoolean();
        boolean serverSettingsInitialized = !buffer.isReadable() || buffer.readBoolean();
        AchievementWorldStateSnapshot achievementWorldState = buffer.isReadable() ? AchievementWorldStateSnapshot.decode(buffer) : AchievementWorldStateSnapshot.empty();
        boolean allowNonOpSettingsUpdates = buffer.isReadable() && buffer.readBoolean();
        boolean canUpdateSettings = !buffer.isReadable() || buffer.readBoolean();
        boolean settingsOperator = !buffer.isReadable() || buffer.readBoolean();
        if (buffer.isReadable()) {
            achievementWorldState = achievementWorldState.withDisqualificationReason(AchievementWorldStateSnapshot.decodeDisqualificationReason(buffer));
        }
        return new CorruptionStateSyncPacket(snapshot, serverCheatsExposed, serverSettingsInitialized, achievementWorldState, allowNonOpSettingsUpdates, canUpdateSettings, settingsOperator);
    }

    public static void handle(CorruptionStateSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleClientState(packet.snapshot, packet.serverCheatsExposed, packet.serverSettingsInitialized, packet.achievementWorldState, packet.allowNonOpSettingsUpdates, packet.canUpdateSettings, packet.settingsOperator));
        context.setPacketHandled(true);
    }

    private static void handleClientState(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed, boolean serverSettingsInitialized, AchievementWorldStateSnapshot achievementWorldState, boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean settingsOperator) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
            Method method = type.getMethod("handleState", CorruptionStateSnapshot.class, boolean.class, boolean.class, AchievementWorldStateSnapshot.class, boolean.class, boolean.class, boolean.class);
            method.invoke(null, snapshot, serverCheatsExposed, serverSettingsInitialized, achievementWorldState, allowNonOpSettingsUpdates, canUpdateSettings, settingsOperator);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            try {
                Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
                Method method = type.getMethod("handleState", CorruptionStateSnapshot.class, boolean.class, boolean.class, AchievementWorldStateSnapshot.class);
                method.invoke(null, snapshot, serverCheatsExposed, serverSettingsInitialized, achievementWorldState);
            } catch (ReflectiveOperationException | LinkageError ignoredAgain) {
                try {
                    Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientNetworkHandlers");
                    Method method = type.getMethod("handleState", CorruptionStateSnapshot.class);
                    method.invoke(null, snapshot);
                } catch (ReflectiveOperationException | LinkageError ignoredAThirdTime) {
                }
            }
        }
    }
}
