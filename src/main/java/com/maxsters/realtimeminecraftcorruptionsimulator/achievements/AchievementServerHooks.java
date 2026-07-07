package com.maxsters.realtimeminecraftcorruptionsimulator.achievements;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AchievementServerHooks {
    private AchievementServerHooks() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommand(CommandEvent event) {
        if (event.isCanceled() || event.getParseResults() == null) {
            return;
        }
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        // CommandEvent fires before execution. Flagging permissioned sources here prevents
        // command teleports from producing achievement progress before clients learn about it.
        if (source != null && source.hasPermission(2)) {
            if (ServerAchievementStateManager.markDisqualified(source.getServer(), "permissioned_command")) {
                ModNetwork.broadcastState(source.getServer());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        if (ServerAchievementStateManager.refresh(event.getServer())) {
            ModNetwork.broadcastState(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        ServerAchievementStateManager.handleDragonDeath(dragon);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            ServerAchievementStateManager.handleDiamondOreMined(player);
        }
    }
}
