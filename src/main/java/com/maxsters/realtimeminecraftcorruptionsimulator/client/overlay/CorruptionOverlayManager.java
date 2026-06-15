package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionState;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.FontTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.ApplyCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.RequestCorruptionStatePacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionOverlayManager {
    private static final CorruptionOverlayLayout LAYOUT = CorruptionOverlayLayout.load();
    private static final float OVERLAY_Z = 1000.0F;

    private static KeyMapping overlayKey;
    private static CorruptionProfileSnapshot latestSnapshot = ClientCorruptionState.localSnapshot();
    private static String currentWorldKey = "";
    private static boolean requestedThisWorld;
    private static boolean autoOpenedThisWorld;
    private static boolean interactionMode;
    private static MouseAction mouseAction = MouseAction.NONE;
    private static PendingControl pendingControl = PendingControl.NONE;
    private static CorruptionOverlayPanel.Page currentPage = CorruptionOverlayPanel.Page.CONTROL;
    private static int pendingLevel = latestSnapshot.getCorruptionLevel();
    private static int lastKnownActiveLevel = latestSnapshot.getCorruptionLevel();
    private static CorruptionTarget pendingTarget;
    private static int pendingFunValue;
    private static boolean seedEditing;
    private static String seedEditText = latestSnapshot.getCorruptionSeedLabel();
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int resizeStartWidth;
    private static int resizeStartHeight;
    private static double pressX;
    private static double pressY;
    private static boolean dragMoved;

    private CorruptionOverlayManager() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        overlayKey = new KeyMapping(
                "key.realtime_minecraft_corruption_simulator.overlay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.realtime_minecraft_corruption_simulator"
        );
        event.register(overlayKey);
    }

    public static void applySnapshot(CorruptionProfileSnapshot snapshot) {
        latestSnapshot = snapshot == null ? ClientCorruptionState.localSnapshot() : snapshot;
        syncPendingLevelToActiveChange();
        if (!seedEditing) {
            seedEditText = latestSnapshot.getCorruptionSeedLabel();
        }
    }

    public static void openOverlayForInteraction() {
        LAYOUT.setMode(CorruptionOverlayLayout.Mode.OPEN);
        LAYOUT.save();
        interactionMode = true;
        releaseMouseForOverlay();
    }

    public static void openOverlay() {
        openOverlayForInteraction();
    }

    public static boolean isOverlayHit(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        LAYOUT.clampToScreen(screenWidth, screenHeight);
        return CorruptionOverlayPanel.panelBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY);
    }

    public static boolean isSeedEditing() {
        return seedEditing;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (!currentWorldKey.isEmpty()) {
                resetClientWorldState();
            }
            latestSnapshot = ClientCorruptionState.localSnapshot();
            syncPendingLevelToActiveChange();
            updateDragFromCurrentMouse();
            return;
        }

        String worldKey = minecraft.level.dimension().location().toString();
        if (!worldKey.equals(currentWorldKey)) {
            currentWorldKey = worldKey;
            requestedThisWorld = false;
            autoOpenedThisWorld = false;
            pendingLevel = latestSnapshot.getCorruptionLevel();
            lastKnownActiveLevel = pendingLevel;
            seedEditing = false;
            seedEditText = latestSnapshot.getCorruptionSeedLabel();
        }

        if (!requestedThisWorld && minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new RequestCorruptionStatePacket());
            requestedThisWorld = true;
        }

        if (!autoOpenedThisWorld && minecraft.screen == null) {
            interactionMode = false;
            autoOpenedThisWorld = true;
        }

        if (interactionMode && minecraft.screen == null) {
            releaseMouseForOverlay();
        }
        if (seedEditing) {
            interactionMode = true;
            releaseMouseForOverlay();
            KeyMapping.releaseAll();
        }

        updateDragFromCurrentMouse();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        if (seedEditing) {
            interactionMode = true;
            releaseMouseForOverlay();
            handleSeedEditKey(event.getKey(), event.getModifiers(), event.getAction(), true);
            event.setCanceled(true);
            return;
        }
        if (overlayKey == null) {
            return;
        }
        while (overlayKey.consumeClick()) {
            interactionMode = true;
            releaseMouseForOverlay();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (seedEditing && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            interactionMode = true;
            releaseMouseForOverlay();
            event.setCanceled(true);
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (!interactionMode && !seedEditing) {
            return;
        }

        double mouseX = scaledMouseX();
        double mouseY = scaledMouseY();
        if (event.getAction() == GLFW.GLFW_PRESS) {
            boolean consumed = handleMousePress(mouseX, mouseY);
            if (!consumed && interactionMode) {
                if (seedEditing) {
                    interactionMode = true;
                    releaseMouseForOverlay();
                    consumed = true;
                } else {
                    interactionMode = false;
                }
            }
            event.setCanceled(consumed);
            return;
        }

        if (event.getAction() == GLFW.GLFW_RELEASE && mouseAction != MouseAction.NONE) {
            handleMouseRelease(mouseX, mouseY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (seedEditing) {
                event.setCanceled(true);
            }
            return;
        }
        if (handleMousePress(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        } else if (seedEditing) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!seedEditing) {
            return;
        }
        handleSeedEditKey(event.getKeyCode(), event.getModifiers(), GLFW.GLFW_PRESS, true);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (seedEditing) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseAction == MouseAction.NONE) {
            return;
        }
        updateDrag(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseAction == MouseAction.NONE) {
            return;
        }
        handleMouseRelease(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }

        int mouseX = interactionMode ? (int) Math.round(scaledMouseX()) : -1;
        int mouseY = interactionMode ? (int) Math.round(scaledMouseY()) : -1;
        renderOverlay(event.getGuiGraphics(), minecraft, mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onRenderLevelAfter(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || minecraft.screen != null || !minecraft.options.hideGui) {
            return;
        }

        renderOverlayWithGuiProjection(minecraft);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        renderOverlay(event.getGuiGraphics(), Minecraft.getInstance(), event.getMouseX(), event.getMouseY());
    }

    private static void renderOverlayWithGuiProjection(Minecraft minecraft) {
        RenderSystem.backupProjectionMatrix();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            float farPlane = ForgeHooksClient.getGuiFarPlane();
            Matrix4f projection = new Matrix4f().setOrtho(
                    0.0F,
                    (float) minecraft.getWindow().getGuiScaledWidth(),
                    (float) minecraft.getWindow().getGuiScaledHeight(),
                    0.0F,
                    1000.0F,
                    farPlane
            );
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            modelView.translate(0.0D, 0.0D, 1000.0F - farPlane);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();

            GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            int mouseX = interactionMode ? (int) Math.round(scaledMouseX()) : -1;
            int mouseY = interactionMode ? (int) Math.round(scaledMouseY()) : -1;
            renderOverlay(graphics, minecraft, mouseX, mouseY);
            graphics.flush();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void renderOverlay(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        CorruptionProfileSnapshot snapshot = renderSnapshot();
        LAYOUT.clampToScreen(graphics.guiWidth(), graphics.guiHeight());
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        try {
            ClientCorruptionProtection.runProtectedGui(() -> {
                if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.COLLAPSED) {
                    CorruptionOverlayPanel.renderCollapsed(graphics, minecraft.font, LAYOUT, snapshot, mouseX, mouseY);
                } else if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.MINIMIZED) {
                    CorruptionOverlayPanel.renderMinimized(graphics, minecraft.font, LAYOUT, snapshot, mouseX, mouseY);
                } else {
                    int displayAutoInterval = mouseAction == MouseAction.FUN_INTERVAL ? pendingFunValue : snapshot.getAutoIncreaseIntervalTicks();
                    int displayAutoAmount = mouseAction == MouseAction.FUN_AMOUNT ? pendingFunValue : snapshot.getAutoIncreaseAmount();
                    CorruptionOverlayPanel.renderOpen(graphics, minecraft.font, LAYOUT, snapshot, pendingLevel, displayAutoInterval, displayAutoAmount, currentPage, seedEditing, seedEditText, mouseX, mouseY);
                }
                graphics.flush();
            });
        } finally {
            graphics.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    private static boolean handleMousePress(double mouseX, double mouseY) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        LAYOUT.clampToScreen(screenWidth, screenHeight);

        pressX = mouseX;
        pressY = mouseY;
        dragMoved = false;
        pendingControl = PendingControl.NONE;
        pendingTarget = null;

        if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.COLLAPSED) {
            CorruptionOverlayPanel.Rect button = CorruptionOverlayPanel.panelBounds(LAYOUT, screenWidth, screenHeight);
            if (button.contains(mouseX, mouseY)) {
                startDrag(MouseAction.COLLAPSED_BUTTON, mouseX, mouseY);
                return true;
            }
            return false;
        }

        CorruptionOverlayPanel.Rect collapseButton = CorruptionOverlayPanel.collapseButtonBounds(LAYOUT, screenWidth, screenHeight);
        if (collapseButton.contains(mouseX, mouseY)) {
            mouseAction = MouseAction.CONTROL;
            pendingControl = PendingControl.COLLAPSE;
            return true;
        }

        CorruptionOverlayPanel.Rect minimizeButton = CorruptionOverlayPanel.minimizeButtonBounds(LAYOUT, screenWidth, screenHeight);
        if (minimizeButton.contains(mouseX, mouseY)) {
            mouseAction = MouseAction.CONTROL;
            pendingControl = PendingControl.MINIMIZE;
            return true;
        }

        if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.OPEN) {
            if (CorruptionOverlayPanel.cornerResizeBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                startResize(MouseAction.RESIZE_BOTH);
                return true;
            }
            if (CorruptionOverlayPanel.horizontalResizeBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                startResize(MouseAction.RESIZE_HORIZONTAL);
                return true;
            }
            if (CorruptionOverlayPanel.verticalResizeBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                startResize(MouseAction.RESIZE_VERTICAL);
                return true;
            }
            if (CorruptionOverlayPanel.controlTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                currentPage = CorruptionOverlayPanel.Page.CONTROL;
                seedEditing = false;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.settingsTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                currentPage = CorruptionOverlayPanel.Page.SETTINGS;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.funTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                currentPage = CorruptionOverlayPanel.Page.FUN;
                seedEditing = false;
                mouseAction = MouseAction.PANEL;
                return true;
            }

            if (currentPage == CorruptionOverlayPanel.Page.SETTINGS) {
                CorruptionOverlayPanel.Rect seedField = CorruptionOverlayPanel.seedFieldBounds(LAYOUT, screenWidth, screenHeight);
                if (seedField.contains(mouseX, mouseY)) {
                    beginSeedEditing();
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect seedApply = CorruptionOverlayPanel.seedApplyButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (seedApply.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.SEED_APPLY;
                    return true;
                }
                CorruptionOverlayPanel.Rect randomSeed = CorruptionOverlayPanel.randomSeedButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (randomSeed.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.SEED_RANDOM;
                    return true;
                }
                if (seedEditing) {
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                for (CorruptionOverlayPanel.TargetHitBox hitBox : CorruptionOverlayPanel.targetHitBoxes(LAYOUT, screenWidth, screenHeight)) {
                    if (hitBox.rect().contains(mouseX, mouseY)) {
                        pendingTarget = hitBox.target();
                        mouseAction = MouseAction.TARGET_TOGGLE;
                        return true;
                    }
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.FUN) {
                CorruptionOverlayPanel.Rect intervalSlider = CorruptionOverlayPanel.funIntervalSliderBounds(LAYOUT, screenWidth, screenHeight);
                if (intervalSlider.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.FUN_INTERVAL;
                    updatePendingFunInterval(mouseX);
                    return true;
                }
                CorruptionOverlayPanel.Rect amountSlider = CorruptionOverlayPanel.funAmountSliderBounds(LAYOUT, screenWidth, screenHeight);
                if (amountSlider.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.FUN_AMOUNT;
                    updatePendingFunAmount(mouseX);
                    return true;
                }
            }

            CorruptionOverlayPanel.Rect applyButton = CorruptionOverlayPanel.applyButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (currentPage == CorruptionOverlayPanel.Page.CONTROL && applyButton.contains(mouseX, mouseY)) {
                mouseAction = MouseAction.APPLY;
                return true;
            }

            CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.sliderBounds(LAYOUT, screenWidth, screenHeight);
            if (currentPage == CorruptionOverlayPanel.Page.CONTROL && slider.contains(mouseX, mouseY)) {
                mouseAction = MouseAction.SLIDER;
                updatePendingLevel(mouseX);
                return true;
            }
        }

        if (CorruptionOverlayPanel.headerBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
            startDrag(MouseAction.WINDOW, mouseX, mouseY);
            return true;
        }

        if (CorruptionOverlayPanel.panelBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
            mouseAction = MouseAction.PANEL;
            return true;
        }
        return false;
    }

    private static void handleMouseRelease(double mouseX, double mouseY) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        updateDrag(mouseX, mouseY);

        if (mouseAction == MouseAction.COLLAPSED_BUTTON) {
            if (!dragMoved && CorruptionOverlayPanel.panelBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                LAYOUT.setMode(CorruptionOverlayLayout.Mode.OPEN);
            }
            LAYOUT.save();
        } else if (mouseAction == MouseAction.WINDOW || mouseAction == MouseAction.RESIZE_HORIZONTAL || mouseAction == MouseAction.RESIZE_VERTICAL || mouseAction == MouseAction.RESIZE_BOTH) {
            LAYOUT.save();
        } else if (mouseAction == MouseAction.APPLY) {
            CorruptionOverlayPanel.Rect applyButton = CorruptionOverlayPanel.applyButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (applyButton.contains(mouseX, mouseY) && pendingLevel != latestSnapshot.getCorruptionLevel()) {
                applyCurrentSettings(pendingLevel, latestSnapshot.getFixedCorruptionSeed(), latestSnapshot.getCorruptionSeedLabel(), latestSnapshot.getEnabledTargetsMask(), latestSnapshot.getAutoIncreaseIntervalTicks(), latestSnapshot.getAutoIncreaseAmount());
            }
        } else if (mouseAction == MouseAction.SEED_APPLY) {
            CorruptionOverlayPanel.Rect applyButton = CorruptionOverlayPanel.seedApplyButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (applyButton.contains(mouseX, mouseY)) {
                submitSeedEdit();
            }
        } else if (mouseAction == MouseAction.SEED_RANDOM) {
            CorruptionOverlayPanel.Rect randomButton = CorruptionOverlayPanel.randomSeedButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (randomButton.contains(mouseX, mouseY)) {
                randomizeSeed();
            }
        } else if (mouseAction == MouseAction.TARGET_TOGGLE) {
            if (pendingTarget != null) {
                for (CorruptionOverlayPanel.TargetHitBox hitBox : CorruptionOverlayPanel.targetHitBoxes(LAYOUT, screenWidth, screenHeight)) {
                    if (hitBox.target() == pendingTarget && hitBox.rect().contains(mouseX, mouseY)) {
                        toggleTarget(pendingTarget);
                        break;
                    }
                }
            }
        } else if (mouseAction == MouseAction.FUN_INTERVAL) {
            updatePendingFunInterval(mouseX);
            applyCurrentSettings(latestSnapshot.getCorruptionLevel(), latestSnapshot.getFixedCorruptionSeed(), latestSnapshot.getCorruptionSeedLabel(), latestSnapshot.getEnabledTargetsMask(), pendingFunValue, latestSnapshot.getAutoIncreaseAmount());
        } else if (mouseAction == MouseAction.FUN_AMOUNT) {
            updatePendingFunAmount(mouseX);
            applyCurrentSettings(latestSnapshot.getCorruptionLevel(), latestSnapshot.getFixedCorruptionSeed(), latestSnapshot.getCorruptionSeedLabel(), latestSnapshot.getEnabledTargetsMask(), latestSnapshot.getAutoIncreaseIntervalTicks(), pendingFunValue);
        } else if (mouseAction == MouseAction.CONTROL) {
            handleControlRelease(mouseX, mouseY, screenWidth, screenHeight);
        }

        mouseAction = MouseAction.NONE;
        pendingControl = PendingControl.NONE;
        pendingTarget = null;
        pendingFunValue = 0;
        dragMoved = false;
    }

    private static void handleControlRelease(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (pendingControl == PendingControl.COLLAPSE && CorruptionOverlayPanel.collapseButtonBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
            LAYOUT.setMode(CorruptionOverlayLayout.Mode.COLLAPSED);
            LAYOUT.save();
            return;
        }
        if (pendingControl == PendingControl.MINIMIZE && CorruptionOverlayPanel.minimizeButtonBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
            if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.MINIMIZED) {
                LAYOUT.setMode(CorruptionOverlayLayout.Mode.OPEN);
            } else {
                LAYOUT.setMode(CorruptionOverlayLayout.Mode.MINIMIZED);
            }
            LAYOUT.save();
        }
    }

    private static void applyCurrentSettings(int level, long seed, String seedLabel, int enabledTargetsMask, int autoIncreaseIntervalTicks, int autoIncreaseAmount) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new ApplyCorruptionSettingsPacket(level, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount));
        } else {
            CorruptionProfileSnapshot previous = ClientCorruptionState.snapshot();
            GlobalCorruptionSettings.apply(level, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount);
            ClientCorruptionState.reset();
            CorruptionProfileSnapshot current = ClientCorruptionState.snapshot();
            latestSnapshot = current;
            pendingLevel = latestSnapshot.getCorruptionLevel();
            lastKnownActiveLevel = pendingLevel;
            notifyLocalSettingsChanged(previous, current);
        }
    }

    private static void notifyLocalSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        TextureMutationManager.onSettingsChanged(previous, current);
        FontTextureCorruptionManager.onSettingsChanged(previous, current);
        ItemTextureCorruptionManager.onSettingsChanged(previous, current);
        VisualCorruptionManager.onSettingsChanged(previous, current);
        AudioCorruptionManager.onSettingsChanged(previous, current);
    }

    private static void submitSeedEdit() {
        if (!seedEditing) {
            return;
        }
        String label = sanitizeSeedText(seedEditText);
        if (label.isBlank()) {
            return;
        }
        long seed = seedFromText(label);
        seedEditing = false;
        seedEditText = CorruptionSavedData.sanitizeSeedLabel(label, seed);
        applyCurrentSettings(latestSnapshot.getCorruptionLevel(), seed, seedEditText, latestSnapshot.getEnabledTargetsMask(), latestSnapshot.getAutoIncreaseIntervalTicks(), latestSnapshot.getAutoIncreaseAmount());
    }

    private static void randomizeSeed() {
        long seed = ThreadLocalRandom.current().nextLong();
        String label = CorruptionSavedData.seedLabel(seed);
        seedEditing = false;
        seedEditText = label;
        applyCurrentSettings(latestSnapshot.getCorruptionLevel(), seed, label, latestSnapshot.getEnabledTargetsMask(), latestSnapshot.getAutoIncreaseIntervalTicks(), latestSnapshot.getAutoIncreaseAmount());
    }

    private static void toggleTarget(CorruptionTarget target) {
        int mask = latestSnapshot.getEnabledTargetsMask() ^ target.mask();
        applyCurrentSettings(latestSnapshot.getCorruptionLevel(), latestSnapshot.getFixedCorruptionSeed(), latestSnapshot.getCorruptionSeedLabel(), mask, latestSnapshot.getAutoIncreaseIntervalTicks(), latestSnapshot.getAutoIncreaseAmount());
    }

    private static void beginSeedEditing() {
        seedEditing = true;
        interactionMode = true;
        seedEditText = latestSnapshot.getCorruptionSeedLabel();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void startDrag(MouseAction action, double mouseX, double mouseY) {
        mouseAction = action;
        dragOffsetX = (int) Math.round(mouseX) - LAYOUT.x();
        dragOffsetY = (int) Math.round(mouseY) - LAYOUT.y();
    }

    private static void startResize(MouseAction action) {
        mouseAction = action;
        resizeStartWidth = LAYOUT.width();
        resizeStartHeight = LAYOUT.height();
    }

    private static void updateDragFromCurrentMouse() {
        if (mouseAction == MouseAction.NONE) {
            return;
        }
        updateDrag(scaledMouseX(), scaledMouseY());
    }

    private static void updateDrag(double mouseX, double mouseY) {
        if (mouseAction != MouseAction.WINDOW && mouseAction != MouseAction.COLLAPSED_BUTTON && mouseAction != MouseAction.SLIDER
                && mouseAction != MouseAction.FUN_INTERVAL && mouseAction != MouseAction.FUN_AMOUNT
                && mouseAction != MouseAction.RESIZE_HORIZONTAL && mouseAction != MouseAction.RESIZE_VERTICAL && mouseAction != MouseAction.RESIZE_BOTH) {
            return;
        }
        if (Math.abs(mouseX - pressX) > 3.0D || Math.abs(mouseY - pressY) > 3.0D) {
            dragMoved = true;
        }

        if (mouseAction == MouseAction.WINDOW || mouseAction == MouseAction.COLLAPSED_BUTTON) {
            LAYOUT.setPosition((int) Math.round(mouseX) - dragOffsetX, (int) Math.round(mouseY) - dragOffsetY);
            LAYOUT.clampToScreen(Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        } else if (mouseAction == MouseAction.RESIZE_HORIZONTAL || mouseAction == MouseAction.RESIZE_VERTICAL || mouseAction == MouseAction.RESIZE_BOTH) {
            int width = resizeStartWidth;
            int height = resizeStartHeight;
            if (mouseAction == MouseAction.RESIZE_HORIZONTAL || mouseAction == MouseAction.RESIZE_BOTH) {
                width = resizeStartWidth + (int) Math.round(mouseX - pressX);
            }
            if (mouseAction == MouseAction.RESIZE_VERTICAL || mouseAction == MouseAction.RESIZE_BOTH) {
                height = resizeStartHeight + (int) Math.round(mouseY - pressY);
            }
            LAYOUT.setSize(width, height);
            LAYOUT.clampToScreen(Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        } else if (mouseAction == MouseAction.SLIDER) {
            updatePendingLevel(mouseX);
        } else if (mouseAction == MouseAction.FUN_INTERVAL) {
            updatePendingFunInterval(mouseX);
        } else if (mouseAction == MouseAction.FUN_AMOUNT) {
            updatePendingFunAmount(mouseX);
        }
    }

    private static void updatePendingLevel(double mouseX) {
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.sliderBounds(
                LAYOUT,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight()
        );
        double ratio = (mouseX - slider.x()) / Math.max(1.0D, slider.width());
        pendingLevel = Math.max(0, Math.min(100, (int) Math.round(ratio * 100.0D)));
    }

    private static void updatePendingFunInterval(double mouseX) {
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.funIntervalSliderBounds(
                LAYOUT,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight()
        );
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - slider.x()) / Math.max(1.0D, slider.width())));
        int ticks = (int) Math.round(ratio * CorruptionOverlayPanel.MAX_AUTO_INTERVAL_TICKS);
        pendingFunValue = ticks < 20 ? 0 : Math.max(20, ticks);
    }

    private static void updatePendingFunAmount(double mouseX) {
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.funAmountSliderBounds(
                LAYOUT,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight()
        );
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - slider.x()) / Math.max(1.0D, slider.width())));
        pendingFunValue = CorruptionOverlayPanel.MIN_AUTO_AMOUNT + (int) Math.round(ratio * (CorruptionOverlayPanel.MAX_AUTO_AMOUNT - CorruptionOverlayPanel.MIN_AUTO_AMOUNT));
    }

    private static void syncPendingLevelToActiveChange() {
        int activeLevel = latestSnapshot.getCorruptionLevel();
        if (activeLevel != lastKnownActiveLevel && mouseAction != MouseAction.SLIDER && mouseAction != MouseAction.APPLY) {
            pendingLevel = activeLevel;
            lastKnownActiveLevel = activeLevel;
        }
    }

    private static boolean handleSeedEditKey(int key, int modifiers, int action, boolean captureUnhandled) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return captureUnhandled;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submitSeedEdit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            seedEditing = false;
            seedEditText = latestSnapshot.getCorruptionSeedLabel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!seedEditText.isEmpty()) {
                seedEditText = seedEditText.substring(0, seedEditText.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            seedEditText = "";
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) {
            seedEditText = sanitizeSeedText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        String character = keyToSeedCharacter(key, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        if (!character.isEmpty() && seedEditText.length() < 96) {
            seedEditText = sanitizeSeedText(seedEditText + character);
            return true;
        }
        return captureUnhandled;
    }

    private static String keyToSeedCharacter(int key, boolean shift) {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return Integer.toString(key - GLFW.GLFW_KEY_0);
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return Integer.toString(key - GLFW.GLFW_KEY_KP_0);
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char character = (char) ('A' + key - GLFW.GLFW_KEY_A);
            return shift ? Character.toString(character) : Character.toString(Character.toLowerCase(character));
        }
        return switch (key) {
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> shift ? "+" : "=";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_SLASH -> shift ? "?" : "/";
            case GLFW.GLFW_KEY_SEMICOLON -> shift ? ":" : ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> shift ? "\"" : "'";
            case GLFW.GLFW_KEY_SPACE -> " ";
            default -> "";
        };
    }

    private static String sanitizeSeedText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Math.min(96, value.length()));
        for (int i = 0; i < value.length() && builder.length() < 96; i++) {
            char character = value.charAt(i);
            if (character >= 32 && character <= 126) {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    private static long seedFromText(String text) {
        String trimmed = sanitizeSeedText(text);
        if (trimmed.isBlank()) {
            return latestSnapshot.getFixedCorruptionSeed();
        }
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return Long.parseUnsignedLong(trimmed.substring(2), 16);
            }
            if (trimmed.matches("-?\\d+")) {
                return Long.parseLong(trimmed);
            }
        } catch (NumberFormatException ignored) {
            try {
                return Long.parseUnsignedLong(trimmed.toLowerCase(Locale.ROOT), 36);
            } catch (NumberFormatException ignoredAgain) {
                return stableStringSeed(trimmed);
            }
        }
        return stableStringSeed(trimmed);
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

    private static CorruptionProfileSnapshot renderSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || minecraft.player == null ? ClientCorruptionState.localSnapshot() : latestSnapshot;
    }

    private static double scaledMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double scaledMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private static void releaseMouseForOverlay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    private static void resetClientWorldState() {
        currentWorldKey = "";
        requestedThisWorld = false;
        autoOpenedThisWorld = false;
        interactionMode = false;
        seedEditing = false;
        ClientCorruptionState.reset();
        latestSnapshot = ClientCorruptionState.localSnapshot();
        pendingLevel = latestSnapshot.getCorruptionLevel();
        lastKnownActiveLevel = pendingLevel;
        seedEditText = latestSnapshot.getCorruptionSeedLabel();
    }

    private enum MouseAction {
        NONE,
        PANEL,
        WINDOW,
        SLIDER,
        APPLY,
        SEED_APPLY,
        SEED_RANDOM,
        TARGET_TOGGLE,
        FUN_INTERVAL,
        FUN_AMOUNT,
        RESIZE_HORIZONTAL,
        RESIZE_VERTICAL,
        RESIZE_BOTH,
        CONTROL,
        COLLAPSED_BUTTON
    }

    private enum PendingControl {
        NONE,
        MINIMIZE,
        COLLAPSE
    }
}
