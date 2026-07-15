package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
public final class CorruptionOverlayLayout {
    public enum Mode {
        OPEN,
        MINIMIZED,
        COLLAPSED
    }

    private static final int DEFAULT_X = 12;
    private static final int DEFAULT_Y = 12;
    private static final int DEFAULT_WIDTH = 392;
    private static final int DEFAULT_HEIGHT = 270;
    private static final int MINIMIZED_WIDTH = 324;

    private int x = DEFAULT_X;
    private int y = DEFAULT_Y;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private Mode mode = Mode.OPEN;

    public static CorruptionOverlayLayout load() {
        CorruptionOverlayLayout layout = new CorruptionOverlayLayout();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return layout;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
            layout.x = parseInt(properties.getProperty("x"), DEFAULT_X);
            layout.y = parseInt(properties.getProperty("y"), DEFAULT_Y);
            layout.width = parseInt(properties.getProperty("width"), DEFAULT_WIDTH);
            layout.height = parseInt(properties.getProperty("height"), DEFAULT_HEIGHT);
            layout.mode = parseMode(properties.getProperty("mode"));
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to load Real-Time Minecraft Corruption Simulator overlay layout", exception);
        }
        return layout;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("width", Integer.toString(width));
        properties.setProperty("height", Integer.toString(height));
        properties.setProperty("mode", mode.name());

        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "Real-Time Minecraft Corruption Simulator overlay layout");
            }
        } catch (IOException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.warn("Unable to save Real-Time Minecraft Corruption Simulator overlay layout", exception);
        }
    }

    public void clampToScreen(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(120, screenWidth - 8);
        int availableHeight = Math.max(96, screenHeight - 8);
        int minWidth = Math.min(DEFAULT_WIDTH, availableWidth);
        int minHeight = Math.min(DEFAULT_HEIGHT, availableHeight);
        width = clamp(width, minWidth, availableWidth);
        height = clamp(height, minHeight, availableHeight);

        int displayedWidth = displayedWidth(screenWidth);
        int displayedHeight = displayedHeight(screenHeight);
        x = clamp(x, 4, Math.max(4, screenWidth - displayedWidth - 4));
        y = clamp(y, 4, Math.max(4, screenHeight - displayedHeight - 4));
    }

    public int displayedWidth(int screenWidth) {
        if (mode == Mode.COLLAPSED) {
            return 28;
        }
        if (mode == Mode.MINIMIZED) {
            return Math.min(MINIMIZED_WIDTH, Math.max(120, screenWidth - 8));
        }
        return Math.min(width, Math.max(120, screenWidth - 8));
    }

    public int displayedHeight(int screenHeight) {
        if (mode == Mode.COLLAPSED) {
            return 28;
        }
        if (mode == Mode.MINIMIZED) {
            return 34;
        }
        return Math.min(height, Math.max(96, screenHeight - 8));
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("realtime_minecraft_corruption_simulator_overlay.properties");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Mode parseMode(String value) {
        if (value == null) {
            return Mode.OPEN;
        }
        try {
            return Mode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Mode.OPEN;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
