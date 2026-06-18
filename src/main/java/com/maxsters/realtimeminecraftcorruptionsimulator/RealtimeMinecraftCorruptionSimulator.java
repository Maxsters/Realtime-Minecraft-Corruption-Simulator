package com.maxsters.realtimeminecraftcorruptionsimulator;

import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.diagnostics.CorruptionStallWatchdog;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RealtimeMinecraftCorruptionSimulator.MOD_ID)
public final class RealtimeMinecraftCorruptionSimulator {
    public static final String MOD_ID = "realtime_minecraft_corruption_simulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealtimeMinecraftCorruptionSimulator(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        GlobalCorruptionSettings.ensureLoaded();
        ModNetwork.register();
        CorruptionStallWatchdog.bootstrap();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.broadcastState(player.getServer());
        }
    }
}
