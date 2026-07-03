package com.maxsters.realtimeminecraftcorruptionsimulator.diagnostics;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionStallWatchdog {
    private static final long STALL_THRESHOLD_MS = 12_000L;
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile long lastClientTickMs;
    private static volatile long lastServerTickMs;
    private static volatile boolean clientDumped;
    private static volatile boolean serverDumped;

    private CorruptionStallWatchdog() {
    }

    public static void bootstrap() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        resetThreadDumpLog();
        Thread thread = new Thread(CorruptionStallWatchdog::watchLoop, "RMC corruption stall watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            lastClientTickMs = System.currentTimeMillis();
            clientDumped = false;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            lastServerTickMs = System.currentTimeMillis();
            serverDumped = false;
        }
    }

    private static void watchLoop() {
        while (true) {
            try {
                Thread.sleep(2_000L);
                long now = System.currentTimeMillis();
                if (lastClientTickMs > 0L && !clientDumped && now - lastClientTickMs >= STALL_THRESHOLD_MS) {
                    clientDumped = true;
                    writeThreadDump("CLIENT", now - lastClientTickMs);
                }
                if (lastServerTickMs > 0L && !serverDumped && now - lastServerTickMs >= STALL_THRESHOLD_MS) {
                    serverDumped = true;
                    writeThreadDump("SERVER", now - lastServerTickMs);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Realtime Minecraft Corruption Simulator stall watchdog failed", exception);
            }
        }
    }

    private static void writeThreadDump(String side, long stalledMs) {
        Path path = threadDumpPath();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write("==== Realtime Minecraft Corruption Simulator stall detected ====");
                writer.newLine();
                writer.write("time=" + Instant.now() + " side=" + side + " stalled_ms=" + stalledMs);
                writer.newLine();
                Thread.getAllStackTraces().entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                        .forEach(entry -> writeThread(writer, entry));
                writer.newLine();
            }
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to write Realtime Minecraft Corruption Simulator stall dump", exception);
        }
    }

    private static void resetThreadDumpLog() {
        Path path = threadDumpPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to reset Realtime Minecraft Corruption Simulator stall dump", exception);
        }
    }

    private static Path threadDumpPath() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("realtime_minecraft_corruption_simulator-stall.txt");
    }

    private static void writeThread(BufferedWriter writer, Map.Entry<Thread, StackTraceElement[]> entry) {
        Thread thread = entry.getKey();
        try {
            writer.write('"' + thread.getName() + "\" state=" + thread.getState() + " daemon=" + thread.isDaemon());
            writer.newLine();
            for (StackTraceElement element : entry.getValue()) {
                writer.write("    at " + element);
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
