package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.FontTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void handleState(CorruptionProfileSnapshot snapshot) {
        Minecraft.getInstance().execute(() -> {
            CorruptionProfileSnapshot previous = ClientCorruptionState.snapshot();
            ClientCorruptionState.applySnapshot(snapshot);
            CorruptionOverlayManager.applySnapshot(snapshot);
            CorruptionProfileSnapshot current = ClientCorruptionState.snapshot();
            TextureMutationManager.onSettingsChanged(previous, current);
            FontTextureCorruptionManager.onSettingsChanged(previous, current);
            ItemTextureCorruptionManager.onSettingsChanged(previous, current);
            VisualCorruptionManager.onSettingsChanged(previous, current);
            AudioCorruptionManager.onSettingsChanged(previous, current);
        });
    }

    public static void openOverlayFromServer() {
        Minecraft.getInstance().execute(CorruptionOverlayManager::openOverlayForInteraction);
    }
}
