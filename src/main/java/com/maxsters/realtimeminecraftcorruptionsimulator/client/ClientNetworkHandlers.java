package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.FontTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.GuiTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.AchievementEventPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void handleState(CorruptionProfileSnapshot snapshot) {
        handleState(snapshot, false);
    }

    public static void handleState(CorruptionProfileSnapshot snapshot, boolean serverCheatsExposed) {
        Minecraft.getInstance().execute(() -> {
            CorruptionProfileSnapshot previous = ClientCorruptionState.snapshot();
            CorruptionAchievementManager.setServerCheatsExposed(serverCheatsExposed);
            ClientCorruptionState.applySnapshot(snapshot);
            CorruptionProfileSnapshot current = ClientCorruptionState.snapshot();
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
            }
        });
    }
}
