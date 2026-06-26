package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCorruptionProtection {
    private static int protectedGuiDepth;

    private ClientCorruptionProtection() {
    }

    public static void runProtectedGui(Runnable action) {
        protectedGuiDepth++;
        try {
            action.run();
        } finally {
            protectedGuiDepth = Math.max(0, protectedGuiDepth - 1);
        }
    }

    public static boolean isProtectedGuiRendering() {
        return protectedGuiDepth > 0;
    }

    public static boolean shouldSuppressClientCorruption() {
        if (protectedGuiDepth > 0) {
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        Screen screen = minecraft.screen;
        return isModScreen(screen)
                || isSaveCriticalScreen(screen)
                || (screen == null && minecraft.level == null && minecraft.player == null);
    }

    public static boolean isModScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("com.maxsters.realtimeminecraftcorruptionsimulator.client.");
    }

    public static boolean isLifecycleAccessScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        return name.endsWith("TitleScreen")
                || name.endsWith("PauseScreen")
                || isSaveCriticalScreen(screen)
                || name.contains("SelectWorldScreen")
                || name.contains("CreateWorldScreen")
                || name.contains("WorldSelection")
                || name.contains("JoinMultiplayerScreen")
                || name.contains("DirectJoinServerScreen")
                || name.contains("EditServerScreen")
                || name.contains("BackupConfirmScreen")
                || name.contains("ConfirmScreen");
    }

    public static boolean isSaveCriticalScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        return name.endsWith("ProgressScreen")
                || name.endsWith("ReceivingLevelScreen")
                || name.endsWith("GenericDirtMessageScreen")
                || name.endsWith("LevelLoadingScreen")
                || name.endsWith("DeathScreen")
                || name.endsWith("DisconnectedScreen");
    }

    public static boolean isProtectedResource(ResourceLocation id) {
        return id != null && RealtimeMinecraftCorruptionSimulator.MOD_ID.equals(id.getNamespace());
    }
}
