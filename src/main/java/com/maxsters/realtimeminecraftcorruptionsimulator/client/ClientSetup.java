package com.maxsters.realtimeminecraftcorruptionsimulator.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        CorruptionOverlayManager.registerKeyMappings(event);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        VisualCorruptionManager.registerBlockColors(event);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        VisualCorruptionManager.registerItemColors(event);
    }

    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        ItemTextureCorruptionManager.modifyBakedModels(event);
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        TextureMutationManager.onTextureStitchPost(event);
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        AudioCorruptionManager.onSoundEngineLoad(event);
    }
}
