package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuiEscapeSafetyManager {
    private GuiEscapeSafetyManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (screen == null) {
            return;
        }
        if (ClientCorruptionProtection.isSaveCriticalScreen(screen) || ClientCorruptionProtection.isDeathScreen(screen)) {
            return;
        }
        if (!ClientCorruptionEffects.current().activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return;
        }

        try {
            screen.onClose();
        } catch (RuntimeException ignored) {
            if (minecraft.level != null && minecraft.player != null) {
                minecraft.setScreen(null);
            }
        }

        if (minecraft.level != null && minecraft.player != null) {
            minecraft.setScreen(null);
            minecraft.player.closeContainer();
            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
        }

        event.setCanceled(true);
    }
}
