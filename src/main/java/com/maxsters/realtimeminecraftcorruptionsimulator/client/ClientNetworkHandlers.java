package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.FontTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.GuiTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.AchievementEventPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.InitializeCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.AchievementWorldStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void handleState(CorruptionStateSnapshot snapshot) {
        handleState(snapshot, false, true, AchievementWorldStateSnapshot.empty(), false, true, true);
    }

    public static void handleState(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed) {
        handleState(snapshot, serverCheatsExposed, true, AchievementWorldStateSnapshot.empty(), false, true, true);
    }

    public static void handleState(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed, boolean serverSettingsInitialized, AchievementWorldStateSnapshot achievementWorldState) {
        handleState(snapshot, serverCheatsExposed, serverSettingsInitialized, achievementWorldState, false, true, true);
    }

    public static void handleState(CorruptionStateSnapshot snapshot, boolean serverCheatsExposed, boolean serverSettingsInitialized, AchievementWorldStateSnapshot achievementWorldState, boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean settingsOperator) {
        Minecraft.getInstance().execute(() -> {
            CorruptionOverlayManager.applyServerPermissions(allowNonOpSettingsUpdates, canUpdateSettings, settingsOperator);
            if (!serverSettingsInitialized) {
                ModNetwork.sendToServer(new InitializeCorruptionSettingsPacket(ClientCorruptionState.localSnapshot()));
                return;
            }
            CorruptionStateSnapshot previous = ClientCorruptionState.snapshot();
            CorruptionAchievementManager.setServerCheatsExposed(serverCheatsExposed);
            CorruptionAchievementManager.applyServerWorldState(achievementWorldState);
            ClientCorruptionState.applySnapshot(snapshot);
            CorruptionStateSnapshot current = ClientCorruptionState.snapshot();
            CorruptionOverlayManager.applySnapshot(current);
            TextureMutationManager.onSettingsChanged(previous, current);
            FontTextureCorruptionManager.onSettingsChanged(previous, current);
            GuiTextureCorruptionManager.onSettingsChanged(previous, current);
            ItemTextureCorruptionManager.onSettingsChanged(previous, current);
            VisualCorruptionManager.onSettingsChanged(previous, current);
            AudioCorruptionManager.onSettingsChanged(previous, current);
        });
    }

    public static void openOverlayFromServer() {
        Minecraft.getInstance().execute(CorruptionOverlayManager::openOverlayForInteraction);
    }

    public static void handleAchievementEvent(String eventId) {
        Minecraft.getInstance().execute(() -> {
            if (AchievementEventPacket.DIAMOND_ORE_MINED.equals(eventId)) {
                CorruptionAchievementManager.recordDiamondOreMined();
            } else if (AchievementEventPacket.WARRANTY_VOIDED.equals(eventId)) {
                CorruptionAchievementManager.recordWarrantyVoided();
            }
        });
    }
}
