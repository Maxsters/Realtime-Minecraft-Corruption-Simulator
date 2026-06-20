package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionProfileManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCorruptionState {
    private static volatile CorruptionProfileSnapshot latestSnapshot = localSnapshot();

    private ClientCorruptionState() {
    }

    public static CorruptionProfileSnapshot snapshot() {
        return latestSnapshot;
    }

    public static void applySnapshot(CorruptionProfileSnapshot snapshot) {
        latestSnapshot = attachClientDriftSalt(snapshot == null ? localSnapshot() : snapshot);
    }

    public static void reset() {
        latestSnapshot = localSnapshot();
    }

    public static CorruptionProfileSnapshot localSnapshot() {
        int activeLevel = GlobalCorruptionSettings.activeLevel();
        return new CorruptionProfileSnapshot(
                activeLevel,
                activeLevel,
                0,
                100,
                0,
                100,
                0,
                activeLevel,
                CorruptionProfileManager.DEFAULT_PROFILE.id(),
                GlobalCorruptionSettings.seed(),
                CorruptionSavedData.sanitizeSeedLabel(GlobalCorruptionSettings.seedLabel(), GlobalCorruptionSettings.seed()),
                GlobalCorruptionSettings.enabledTargetsMask(),
                GlobalCorruptionSettings.autoIncreaseIntervalTicks(),
                GlobalCorruptionSettings.autoIncreaseAmount(),
                GlobalCorruptionSettings.clientDriftEnabled(),
                GlobalCorruptionSettings.seedRandomizerIntervalTicks(),
                clientDriftSalt()
        );
    }

    public static CorruptionProfileSnapshot attachClientDriftSalt(CorruptionProfileSnapshot snapshot) {
        if (snapshot == null) {
            return localSnapshot();
        }
        return snapshot.withClientDriftSalt(clientDriftSalt());
    }

    private static long clientDriftSalt() {
        String name = "local";
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getUser() != null && minecraft.getUser().getName() != null && !minecraft.getUser().getName().isBlank()) {
                name = minecraft.getUser().getName();
            }
        } catch (RuntimeException ignored) {
        }
        return stableStringSeed("client-drift:" + name);
    }

    private static long stableStringSeed(String text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
            hash ^= hash >>> 32;
        }
        return hash;
    }
}
