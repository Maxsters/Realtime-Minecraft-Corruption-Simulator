package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionState;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.CorruptionAchievementManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.FontTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.GuiTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.VisualCorruptionManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.ApplyCorruptionSettingsPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.QuickToggleCorruptionPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.RequestCorruptionStatePacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.packet.UpdateSettingsAccessPacket;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    private static final long RESET_CLICK_WINDOW_MS = 1500L;

    private static KeyMapping overlayKey;
    private static KeyMapping quickToggleKey;
    private static CorruptionStateSnapshot latestSnapshot = ClientCorruptionState.localSnapshot();
    private static QuickToggleSnapshot quickToggleRestore;
    private static boolean serverAllowsNonOpSettingsUpdates;
    private static boolean canUpdateServerSettings = true;
    private static boolean serverSettingsOperator = true;
    private static String currentWorldKey = "";
    private static boolean requestedThisWorld;
    private static boolean autoOpenedThisWorld;
    private static boolean interactionMode;
    private static MouseAction mouseAction = MouseAction.NONE;
    private static PendingControl pendingControl = PendingControl.NONE;
    private static CorruptionOverlayPanel.Page currentPage = CorruptionOverlayPanel.Page.CONTROL;
    private static int pendingLevel = latestSnapshot.getCorruptionLevel();
    private static long draftSeed = latestSnapshot.getFixedCorruptionSeed();
    private static String draftSeedLabel = latestSnapshot.getCorruptionSeedLabel();
    private static int draftTargetsMask = latestSnapshot.getEnabledTargetsMask();
    private static int draftAutoIntervalTicks = latestSnapshot.getAutoIncreaseIntervalTicks();
    private static int draftAutoAmount = latestSnapshot.getAutoIncreaseAmount();
    private static boolean draftClientDriftEnabled = latestSnapshot.isClientDriftEnabled();
    private static int draftSeedRandomizerIntervalTicks = latestSnapshot.getSeedRandomizerIntervalTicks();
    private static int achievementsScroll;
    private static CorruptionTarget pendingTarget;
    private static CorruptionAchievementManager.Achievement pendingAchievement;
    private static CorruptionAchievementManager.HudCorner pendingHudCorner;
    private static int pendingFunValue;
    private static FunEditField funEditField = FunEditField.NONE;
    private static String funIntervalEditText = "";
    private static String funAmountEditText = "";
    private static String funSeedRandomizerEditText = "";
    private static boolean seedEditing;
    private static String seedEditText = latestSnapshot.getCorruptionSeedLabel();
    private static int textCursor = seedEditText.length();
    private static int textSelectionAnchor = textCursor;
    private static int achievementResetPresses;
    private static long lastAchievementResetPressMs;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int resizeStartWidth;
    private static int resizeStartHeight;
    private static double pressX;
    private static double pressY;
    private static boolean dragMoved;
    private static boolean quickToggleScreenKeyDown;
    private static int quickToggleScreenKeyCode = GLFW.GLFW_KEY_UNKNOWN;

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
        quickToggleKey = new KeyMapping(
                "key.realtime_minecraft_corruption_simulator.toggle_all",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.realtime_minecraft_corruption_simulator"
        );
        event.register(quickToggleKey);
    }

    public static void applySnapshot(CorruptionStateSnapshot snapshot) {
        boolean preserveDraft = canUpdateSettingsNow() && (hasPendingChanges() || isTextEditing() || mouseAction == MouseAction.SLIDER
                || mouseAction == MouseAction.FUN_INTERVAL || mouseAction == MouseAction.FUN_AMOUNT || mouseAction == MouseAction.FUN_SEED_RANDOMIZER);
        latestSnapshot = snapshot == null ? ClientCorruptionState.localSnapshot() : snapshot;
        if (!preserveDraft) {
            syncDraftFromSnapshot();
        } else if (!seedEditing && !hasDraftChanges()) {
            seedEditText = draftSeedLabel;
        }
    }

    public static void applyServerPermissions(boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean settingsOperator) {
        serverAllowsNonOpSettingsUpdates = allowNonOpSettingsUpdates;
        canUpdateServerSettings = canUpdateSettings;
        serverSettingsOperator = settingsOperator;
        if (!canUpdateSettings) {
            seedEditing = false;
            funEditField = FunEditField.NONE;
            clearTextSelection();
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
        return isTextEditing();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        resetQuickToggleScreenKeyIfReleased(minecraft);
        if (minecraft.level == null || minecraft.player == null) {
            if (minecraft.getConnection() != null) {
                updateDragFromCurrentMouse();
                return;
            }
            if (!currentWorldKey.isEmpty()) {
                resetClientWorldState();
            }
            latestSnapshot = ClientCorruptionState.localSnapshot();
            if (!hasDraftChanges() && !isTextEditing()) {
                syncDraftFromSnapshot();
            }
            updateDragFromCurrentMouse();
            return;
        }

        String worldKey = minecraft.level.dimension().location().toString();
        if (!worldKey.equals(currentWorldKey)) {
            currentWorldKey = worldKey;
            requestedThisWorld = false;
            autoOpenedThisWorld = false;
            seedEditing = false;
            funEditField = FunEditField.NONE;
            syncDraftFromSnapshot();
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
        if (isTextEditing()) {
            interactionMode = true;
            releaseMouseForOverlay();
            KeyMapping.releaseAll();
        }

        updateDragFromCurrentMouse();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        if (isTextEditing()) {
            interactionMode = true;
            releaseMouseForOverlay();
            handleProtectedTextEditKey(event.getKey(), event.getModifiers(), event.getAction(), true);
            event.setCanceled(true);
            return;
        }
        if (quickToggleKey != null) {
            while (quickToggleKey.consumeClick()) {
                toggleAllCorruptionProtected();
            }
        }
        if (overlayKey != null) {
            while (overlayKey.consumeClick()) {
                interactionMode = true;
                releaseMouseForOverlay();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (isTextEditing() && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            interactionMode = true;
            releaseMouseForOverlay();
            event.setCanceled(true);
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (!interactionMode && !isTextEditing()) {
            return;
        }

        double mouseX = scaledMouseX();
        double mouseY = scaledMouseY();
        if (event.getAction() == GLFW.GLFW_PRESS) {
            boolean consumed = handleMousePress(mouseX, mouseY);
            if (!consumed && interactionMode) {
                if (isTextEditing()) {
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
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || (!interactionMode && !isTextEditing())) {
            return;
        }
        if (handleMouseScroll(scaledMouseX(), scaledMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isTextEditing()) {
                event.setCanceled(true);
            }
            return;
        }
        if (handleMousePress(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        } else if (isTextEditing()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (handleMouseScroll(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!isTextEditing() && handleScreenQuickToggleKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        if (!isTextEditing()) {
            return;
        }
        handleProtectedTextEditKey(event.getKeyCode(), event.getModifiers(), GLFW.GLFW_PRESS, true);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (isTextEditing()) {
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
        CorruptionStateSnapshot snapshot = renderSnapshot();
        CorruptionStateSnapshot draftSnapshot = draftSnapshot(snapshot);
        LAYOUT.clampToScreen(graphics.guiWidth(), graphics.guiHeight());
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        try {
            ClientCorruptionProtection.runProtectedGui(() -> {
                LightingCorruptionHooks.beginGuiLightProtection();
                try {
                    if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.COLLAPSED) {
                        CorruptionOverlayPanel.renderCollapsed(graphics, minecraft.font, LAYOUT, snapshot, mouseX, mouseY);
                    } else if (LAYOUT.mode() == CorruptionOverlayLayout.Mode.MINIMIZED) {
                        CorruptionOverlayPanel.renderMinimized(graphics, minecraft.font, LAYOUT, snapshot, mouseX, mouseY);
                    } else {
                        int displayAutoInterval = mouseAction == MouseAction.FUN_INTERVAL ? pendingFunValue : draftAutoIntervalTicks;
                        int displayAutoAmount = mouseAction == MouseAction.FUN_AMOUNT ? pendingFunValue : draftAutoAmount;
                        int displaySeedRandomizerInterval = mouseAction == MouseAction.FUN_SEED_RANDOMIZER ? pendingFunValue : draftSeedRandomizerIntervalTicks;
                        boolean draftDirty = hasPendingChanges();
                        achievementsScroll = clampAchievementsScroll(graphics.guiWidth(), graphics.guiHeight());
                        expireAchievementResetPresses();
                        CorruptionOverlayPanel.renderOpen(
                                graphics,
                                minecraft.font,
                                LAYOUT,
                                snapshot,
                                draftSnapshot,
                                pendingLevel,
                                displayAutoInterval,
                                displayAutoAmount,
                                displaySeedRandomizerInterval,
                                draftDirty,
                                currentPage,
                                seedEditing,
                                seedEditText,
                                funEditField == FunEditField.INTERVAL,
                                funIntervalEditText,
                                funEditField == FunEditField.AMOUNT,
                                funAmountEditText,
                                funEditField == FunEditField.SEED_RANDOMIZER,
                                funSeedRandomizerEditText,
                                textCursor,
                                textSelectionAnchor,
                                achievementsScroll,
                                achievementResetPresses,
                                CorruptionAchievementManager.pinnedCorner(),
                                serverAllowsNonOpSettingsUpdates,
                                canUpdateSettingsNow(),
                                shouldShowOperatorAccessControls(),
                                mouseX,
                                mouseY
                        );
                    }
                    if (minecraft.screen == null) {
                        CorruptionOverlayPanel.renderPinnedAchievements(graphics, minecraft.font, CorruptionAchievementManager.pinnedAchievements(), CorruptionAchievementManager.pinnedCorner());
                    }
                    graphics.flush();
                } finally {
                    LightingCorruptionHooks.endGuiLightProtection();
                }
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
        pendingAchievement = null;
        pendingHudCorner = null;

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
                finishTextEditingBeforeNavigation();
                currentPage = CorruptionOverlayPanel.Page.CONTROL;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.settingsTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                finishTextEditingBeforeNavigation();
                currentPage = CorruptionOverlayPanel.Page.SETTINGS;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.funTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                finishTextEditingBeforeNavigation();
                currentPage = CorruptionOverlayPanel.Page.FUN;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.achievementsTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                finishTextEditingBeforeNavigation();
                currentPage = CorruptionOverlayPanel.Page.ACHIEVEMENTS;
                mouseAction = MouseAction.PANEL;
                return true;
            }
            if (CorruptionOverlayPanel.hudTabBounds(LAYOUT, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                finishTextEditingBeforeNavigation();
                currentPage = CorruptionOverlayPanel.Page.HUD;
                mouseAction = MouseAction.PANEL;
                return true;
            }

            boolean canEditSettings = canUpdateSettingsNow();
            if (isProfileDraftPage(currentPage)) {
                CorruptionOverlayPanel.Rect globalApply = CorruptionOverlayPanel.globalApplyButtonBounds(LAYOUT, currentPage, screenWidth, screenHeight);
                if (globalApply.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.APPLY : MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect globalCancel = CorruptionOverlayPanel.globalCancelButtonBounds(LAYOUT, currentPage, screenWidth, screenHeight);
                if (globalCancel.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.CANCEL;
                    return true;
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.CONTROL && shouldShowOperatorAccessControls()) {
                CorruptionOverlayPanel.Rect access = CorruptionOverlayPanel.nonOpSettingsButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (access.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.ACCESS_TOGGLE;
                    return true;
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.SETTINGS) {
                CorruptionOverlayPanel.Rect seedField = CorruptionOverlayPanel.seedFieldBounds(LAYOUT, screenWidth, screenHeight);
                if (seedField.contains(mouseX, mouseY)) {
                    if (canEditSettings) {
                        beginSeedEditing();
                    }
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect seedCopy = CorruptionOverlayPanel.seedCopyButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (seedCopy.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.SEED_COPY;
                    return true;
                }
                CorruptionOverlayPanel.Rect seedPaste = CorruptionOverlayPanel.seedPasteButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (seedPaste.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.SEED_PASTE : MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect seedApply = CorruptionOverlayPanel.seedApplyButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (seedApply.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.SEED_APPLY : MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect randomSeed = CorruptionOverlayPanel.randomSeedButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (randomSeed.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.SEED_RANDOM : MouseAction.PANEL;
                    return true;
                }
                if (seedEditing) {
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect enableAll = CorruptionOverlayPanel.enableAllTargetsButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (enableAll.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.TARGET_ENABLE_ALL : MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect disableAll = CorruptionOverlayPanel.disableAllTargetsButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (disableAll.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.TARGET_DISABLE_ALL : MouseAction.PANEL;
                    return true;
                }
                for (CorruptionOverlayPanel.TargetHitBox hitBox : CorruptionOverlayPanel.targetHitBoxes(LAYOUT, screenWidth, screenHeight)) {
                    if (hitBox.rect().contains(mouseX, mouseY)) {
                        pendingTarget = hitBox.target();
                        mouseAction = canEditSettings ? MouseAction.TARGET_TOGGLE : MouseAction.PANEL;
                        return true;
                    }
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.FUN) {
                CorruptionOverlayPanel.Rect intervalInput = CorruptionOverlayPanel.funIntervalInputBounds(LAYOUT, screenWidth, screenHeight);
                if (intervalInput.contains(mouseX, mouseY)) {
                    if (canEditSettings) {
                        beginFunIntervalEditing();
                    }
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect amountInput = CorruptionOverlayPanel.funAmountInputBounds(LAYOUT, screenWidth, screenHeight);
                if (amountInput.contains(mouseX, mouseY)) {
                    if (canEditSettings) {
                        beginFunAmountEditing();
                    }
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                CorruptionOverlayPanel.Rect seedRandomizerInput = CorruptionOverlayPanel.funSeedRandomizerInputBounds(LAYOUT, screenWidth, screenHeight);
                if (seedRandomizerInput.contains(mouseX, mouseY)) {
                    if (canEditSettings) {
                        beginFunSeedRandomizerEditing();
                    }
                    mouseAction = MouseAction.PANEL;
                    return true;
                }
                if (funEditField != FunEditField.NONE) {
                    submitFunEdit();
                }
                CorruptionOverlayPanel.Rect intervalSlider = CorruptionOverlayPanel.funIntervalSliderBounds(LAYOUT, screenWidth, screenHeight);
                if (CorruptionOverlayPanel.sliderInteractionBounds(intervalSlider).contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.FUN_INTERVAL : MouseAction.PANEL;
                    if (canEditSettings) {
                        updatePendingFunInterval(mouseX);
                    }
                    return true;
                }
                CorruptionOverlayPanel.Rect amountSlider = CorruptionOverlayPanel.funAmountSliderBounds(LAYOUT, screenWidth, screenHeight);
                if (CorruptionOverlayPanel.sliderInteractionBounds(amountSlider).contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.FUN_AMOUNT : MouseAction.PANEL;
                    if (canEditSettings) {
                        updatePendingFunAmount(mouseX);
                    }
                    return true;
                }
                CorruptionOverlayPanel.Rect seedRandomizerSlider = CorruptionOverlayPanel.funSeedRandomizerSliderBounds(LAYOUT, screenWidth, screenHeight);
                if (CorruptionOverlayPanel.sliderInteractionBounds(seedRandomizerSlider).contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.FUN_SEED_RANDOMIZER : MouseAction.PANEL;
                    if (canEditSettings) {
                        updatePendingSeedRandomizerInterval(mouseX);
                    }
                    return true;
                }
                CorruptionOverlayPanel.Rect clientDrift = CorruptionOverlayPanel.funClientDriftButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (clientDrift.contains(mouseX, mouseY)) {
                    mouseAction = canEditSettings ? MouseAction.CLIENT_DRIFT_TOGGLE : MouseAction.PANEL;
                    return true;
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.ACHIEVEMENTS) {
                CorruptionOverlayPanel.Rect resetButton = CorruptionOverlayPanel.achievementResetButtonBounds(LAYOUT, screenWidth, screenHeight);
                if (resetButton.contains(mouseX, mouseY)) {
                    mouseAction = MouseAction.ACHIEVEMENT_RESET;
                    return true;
                }
                for (CorruptionOverlayPanel.AchievementHitBox hitBox : CorruptionOverlayPanel.achievementHitBoxes(LAYOUT, screenWidth, screenHeight, achievementsScroll)) {
                    if (hitBox.pin().contains(mouseX, mouseY)) {
                        pendingAchievement = hitBox.achievement();
                        mouseAction = MouseAction.ACHIEVEMENT_PIN;
                        return true;
                    }
                }
            }

            if (currentPage == CorruptionOverlayPanel.Page.HUD) {
                for (CorruptionAchievementManager.HudCorner corner : CorruptionAchievementManager.HudCorner.values()) {
                    CorruptionOverlayPanel.Rect button = CorruptionOverlayPanel.hudCornerButtonBounds(LAYOUT, screenWidth, screenHeight, corner);
                    if (button.contains(mouseX, mouseY)) {
                        pendingHudCorner = corner;
                        mouseAction = MouseAction.HUD_CORNER;
                        return true;
                    }
                }
            }

            CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.sliderBounds(LAYOUT, screenWidth, screenHeight);
            if (currentPage == CorruptionOverlayPanel.Page.CONTROL && CorruptionOverlayPanel.sliderInteractionBounds(slider).contains(mouseX, mouseY)) {
                mouseAction = canEditSettings ? MouseAction.SLIDER : MouseAction.PANEL;
                if (canEditSettings) {
                    updatePendingLevel(mouseX);
                }
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

    private static boolean handleMouseScroll(double mouseX, double mouseY, double delta) {
        if (LAYOUT.mode() != CorruptionOverlayLayout.Mode.OPEN) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        LAYOUT.clampToScreen(screenWidth, screenHeight);

        if (currentPage != CorruptionOverlayPanel.Page.ACHIEVEMENTS) {
            return false;
        }
        CorruptionOverlayPanel.Rect list = CorruptionOverlayPanel.achievementsListBounds(LAYOUT, screenWidth, screenHeight);
        if (!list.contains(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = CorruptionOverlayPanel.achievementsMaxScroll(LAYOUT, screenWidth, screenHeight);
        if (maxScroll <= 0) {
            achievementsScroll = 0;
            return false;
        }
        int amount = scrollAmount(delta);
        achievementsScroll = Math.max(0, Math.min(maxScroll, achievementsScroll - amount));
        return true;
    }

    private static int scrollAmount(double delta) {
        int amount = (int) Math.round(delta * 18.0D);
        if (amount == 0) {
            return delta > 0.0D ? 18 : -18;
        }
        return amount;
    }

    private static int clampAchievementsScroll(int screenWidth, int screenHeight) {
        if (currentPage != CorruptionOverlayPanel.Page.ACHIEVEMENTS) {
            return achievementsScroll;
        }
        int maxScroll = CorruptionOverlayPanel.achievementsMaxScroll(LAYOUT, screenWidth, screenHeight);
        return Math.max(0, Math.min(maxScroll, achievementsScroll));
    }

    private static void expireAchievementResetPresses() {
        if (achievementResetPresses > 0 && System.currentTimeMillis() - lastAchievementResetPressMs > RESET_CLICK_WINDOW_MS) {
            achievementResetPresses = 0;
            lastAchievementResetPressMs = 0L;
        }
    }

    private static void handleAchievementResetPress() {
        long now = System.currentTimeMillis();
        if (now - lastAchievementResetPressMs > RESET_CLICK_WINDOW_MS) {
            achievementResetPresses = 0;
        }
        achievementResetPresses++;
        lastAchievementResetPressMs = now;
        if (achievementResetPresses >= 3) {
            CorruptionAchievementManager.resetAll();
            achievementResetPresses = 0;
            lastAchievementResetPressMs = 0L;
        }
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
            CorruptionOverlayPanel.Rect applyButton = CorruptionOverlayPanel.globalApplyButtonBounds(LAYOUT, currentPage, screenWidth, screenHeight);
            if (applyButton.contains(mouseX, mouseY) && hasPendingChanges()) {
                applyDraftSettings();
            }
        } else if (mouseAction == MouseAction.CANCEL) {
            CorruptionOverlayPanel.Rect cancelButton = CorruptionOverlayPanel.globalCancelButtonBounds(LAYOUT, currentPage, screenWidth, screenHeight);
            if (cancelButton.contains(mouseX, mouseY) && hasPendingChanges()) {
                cancelDraftSettings();
            }
        } else if (mouseAction == MouseAction.SEED_COPY) {
            CorruptionOverlayPanel.Rect copyButton = CorruptionOverlayPanel.seedCopyButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (copyButton.contains(mouseX, mouseY)) {
                copySeedToClipboard();
            }
        } else if (mouseAction == MouseAction.SEED_PASTE) {
            CorruptionOverlayPanel.Rect pasteButton = CorruptionOverlayPanel.seedPasteButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (pasteButton.contains(mouseX, mouseY)) {
                pasteSeedFromClipboard();
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
        } else if (mouseAction == MouseAction.TARGET_ENABLE_ALL) {
            CorruptionOverlayPanel.Rect enableAll = CorruptionOverlayPanel.enableAllTargetsButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (enableAll.contains(mouseX, mouseY) && draftTargetsMask != CorruptionTarget.ALL_MASK) {
                setAllTargets(true);
            }
        } else if (mouseAction == MouseAction.TARGET_DISABLE_ALL) {
            CorruptionOverlayPanel.Rect disableAll = CorruptionOverlayPanel.disableAllTargetsButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (disableAll.contains(mouseX, mouseY) && draftTargetsMask != 0) {
                setAllTargets(false);
            }
        } else if (mouseAction == MouseAction.FUN_INTERVAL) {
            updatePendingFunInterval(mouseX);
            draftAutoIntervalTicks = pendingFunValue;
        } else if (mouseAction == MouseAction.FUN_AMOUNT) {
            updatePendingFunAmount(mouseX);
            draftAutoAmount = pendingFunValue;
        } else if (mouseAction == MouseAction.FUN_SEED_RANDOMIZER) {
            updatePendingSeedRandomizerInterval(mouseX);
            draftSeedRandomizerIntervalTicks = pendingFunValue;
        } else if (mouseAction == MouseAction.CLIENT_DRIFT_TOGGLE) {
            CorruptionOverlayPanel.Rect clientDrift = CorruptionOverlayPanel.funClientDriftButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (clientDrift.contains(mouseX, mouseY)) {
                draftClientDriftEnabled = !draftClientDriftEnabled;
            }
        } else if (mouseAction == MouseAction.ACHIEVEMENT_PIN) {
            if (pendingAchievement != null) {
                for (CorruptionOverlayPanel.AchievementHitBox hitBox : CorruptionOverlayPanel.achievementHitBoxes(LAYOUT, screenWidth, screenHeight, achievementsScroll)) {
                    if (hitBox.achievement().id().equals(pendingAchievement.id()) && hitBox.pin().contains(mouseX, mouseY)) {
                        CorruptionAchievementManager.togglePinned(pendingAchievement);
                        break;
                    }
                }
            }
        } else if (mouseAction == MouseAction.ACHIEVEMENT_RESET) {
            CorruptionOverlayPanel.Rect resetButton = CorruptionOverlayPanel.achievementResetButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (resetButton.contains(mouseX, mouseY)) {
                handleAchievementResetPress();
            }
        } else if (mouseAction == MouseAction.HUD_CORNER) {
            if (pendingHudCorner != null) {
                CorruptionOverlayPanel.Rect button = CorruptionOverlayPanel.hudCornerButtonBounds(LAYOUT, screenWidth, screenHeight, pendingHudCorner);
                if (button.contains(mouseX, mouseY)) {
                    CorruptionAchievementManager.setPinnedCorner(pendingHudCorner);
                }
            }
        } else if (mouseAction == MouseAction.ACCESS_TOGGLE) {
            CorruptionOverlayPanel.Rect access = CorruptionOverlayPanel.nonOpSettingsButtonBounds(LAYOUT, screenWidth, screenHeight);
            if (access.contains(mouseX, mouseY) && shouldShowOperatorAccessControls()) {
                ModNetwork.sendToServer(new UpdateSettingsAccessPacket(!serverAllowsNonOpSettingsUpdates));
            }
        } else if (mouseAction == MouseAction.CONTROL) {
            handleControlRelease(mouseX, mouseY, screenWidth, screenHeight);
        }

        mouseAction = MouseAction.NONE;
        pendingControl = PendingControl.NONE;
        pendingTarget = null;
        pendingAchievement = null;
        pendingHudCorner = null;
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

    private static void applyCurrentSettings(int level, long seed, String seedLabel, int enabledTargetsMask, int autoIncreaseIntervalTicks, int autoIncreaseAmount, boolean clientDriftEnabled, int seedRandomizerIntervalTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            if (!canUpdateServerSettings) {
                ModNetwork.sendToServer(new RequestCorruptionStatePacket());
                return;
            }
            ModNetwork.sendToServer(new ApplyCorruptionSettingsPacket(level, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount, clientDriftEnabled, seedRandomizerIntervalTicks));
        } else {
            CorruptionStateSnapshot previous = ClientCorruptionState.snapshot();
            GlobalCorruptionSettings.apply(level, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount, clientDriftEnabled, seedRandomizerIntervalTicks);
            ClientCorruptionState.reset();
            CorruptionStateSnapshot current = ClientCorruptionState.snapshot();
            latestSnapshot = current;
            syncDraftFromSnapshot();
            notifyLocalSettingsChanged(previous, current);
        }
    }

    private static void applyDraftSettings() {
        if (seedEditing) {
            submitSeedEdit();
        }
        if (funEditField != FunEditField.NONE) {
            submitFunEdit();
        }
        if (!hasDraftChanges()) {
            return;
        }
        applyCurrentSettings(pendingLevel, draftSeed, draftSeedLabel, draftTargetsMask, draftAutoIntervalTicks, draftAutoAmount, draftClientDriftEnabled, draftSeedRandomizerIntervalTicks);
    }

    private static void toggleAllCorruption() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            if (canUpdateServerSettings) {
                ModNetwork.sendToServer(new QuickToggleCorruptionPacket());
            } else {
                ModNetwork.sendToServer(new RequestCorruptionStatePacket());
            }
            return;
        }

        CorruptionStateSnapshot snapshot = latestSnapshot == null ? ClientCorruptionState.snapshot() : latestSnapshot;
        if (snapshot == null) {
            snapshot = ClientCorruptionState.localSnapshot();
        }

        if (quickToggleRestore != null && snapshot.getCorruptionLevel() == 0) {
            quickToggleRestore.apply();
            quickToggleRestore = null;
            return;
        }

        if (isQuickToggleOff(snapshot)) {
            return;
        }

        quickToggleRestore = QuickToggleSnapshot.from(snapshot);
        applyCurrentSettings(
                0,
                snapshot.getFixedCorruptionSeed(),
                snapshot.getCorruptionSeedLabel(),
                0,
                0,
                snapshot.getAutoIncreaseAmount(),
                snapshot.isClientDriftEnabled(),
                0
        );
    }

    private static void toggleAllCorruptionProtected() {
        ClientCorruptionProtection.runProtectedGui(CorruptionOverlayManager::toggleAllCorruption);
    }

    private static boolean handleScreenQuickToggleKey(int keyCode, int scanCode, int modifiers) {
        if (!matchesQuickToggleKey(keyCode, scanCode) || hasShortcutModifier(modifiers) || shouldIgnoreScreenShortcut(Minecraft.getInstance().screen)) {
            return false;
        }
        if (quickToggleScreenKeyDown) {
            return true;
        }

        quickToggleScreenKeyDown = true;
        quickToggleScreenKeyCode = keyCode;
        toggleAllCorruptionProtected();
        return true;
    }

    private static boolean matchesQuickToggleKey(int keyCode, int scanCode) {
        return quickToggleKey != null && quickToggleKey.matches(keyCode, scanCode);
    }

    private static boolean hasShortcutModifier(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private static boolean shouldIgnoreScreenShortcut(Screen screen) {
        if (screen == null) {
            return false;
        }

        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox) {
            return true;
        }

        String screenName = screen.getClass().getName();
        return screenName.endsWith("ControlsScreen") || screenName.endsWith("KeyBindsScreen");
    }

    private static void resetQuickToggleScreenKeyIfReleased(Minecraft minecraft) {
        if (!quickToggleScreenKeyDown || minecraft == null) {
            return;
        }
        if (quickToggleScreenKeyCode == GLFW.GLFW_KEY_UNKNOWN) {
            quickToggleScreenKeyDown = false;
            return;
        }
        if (!InputConstants.isKeyDown(minecraft.getWindow().getWindow(), quickToggleScreenKeyCode)) {
            quickToggleScreenKeyDown = false;
            quickToggleScreenKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    private static boolean isQuickToggleOff(CorruptionStateSnapshot snapshot) {
        return snapshot.getCorruptionLevel() == 0
                && snapshot.getEnabledTargetsMask() == 0
                && snapshot.getAutoIncreaseIntervalTicks() == 0
                && snapshot.getSeedRandomizerIntervalTicks() == 0;
    }

    private static void cancelDraftSettings() {
        seedEditing = false;
        funEditField = FunEditField.NONE;
        clearTextSelection();
        syncDraftFromSnapshot();
    }

    private static void notifyLocalSettingsChanged(CorruptionStateSnapshot previous, CorruptionStateSnapshot current) {
        TextureMutationManager.onSettingsChanged(previous, current);
        FontTextureCorruptionManager.onSettingsChanged(previous, current);
        GuiTextureCorruptionManager.onSettingsChanged(previous, current);
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
        draftSeed = seed;
        draftSeedLabel = CorruptionSavedData.sanitizeSeedLabel(label, seed);
        seedEditText = draftSeedLabel;
        moveTextCursorToEnd();
    }

    private static void randomizeSeed() {
        long seed = ThreadLocalRandom.current().nextLong();
        String label = CorruptionSavedData.seedLabel(seed);
        seedEditing = false;
        draftSeed = seed;
        draftSeedLabel = label;
        seedEditText = draftSeedLabel;
        moveTextCursorToEnd();
    }

    private static void copySeedToClipboard() {
        String text = seedEditing ? seedEditText : draftSeedLabel;
        Minecraft.getInstance().keyboardHandler.setClipboard(sanitizeSeedText(text));
    }

    private static void pasteSeedFromClipboard() {
        String pasted = sanitizeSeedText(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (pasted.isBlank()) {
            return;
        }
        seedEditing = true;
        interactionMode = true;
        seedEditText = pasted;
        moveTextCursorToEnd();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void toggleTarget(CorruptionTarget target) {
        draftTargetsMask = CorruptionTarget.normalizeMask(draftTargetsMask ^ target.mask());
    }

    private static void setAllTargets(boolean enabled) {
        draftTargetsMask = enabled ? CorruptionTarget.ALL_MASK : 0;
    }

    private static void beginSeedEditing() {
        funEditField = FunEditField.NONE;
        seedEditing = true;
        interactionMode = true;
        seedEditText = draftSeedLabel;
        moveTextCursorToEnd();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void beginFunIntervalEditing() {
        seedEditing = false;
        funEditField = FunEditField.INTERVAL;
        funIntervalEditText = formatIntervalEdit(draftAutoIntervalTicks);
        interactionMode = true;
        moveTextCursorToEnd();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void beginFunAmountEditing() {
        seedEditing = false;
        funEditField = FunEditField.AMOUNT;
        funAmountEditText = signedPercentLabel(draftAutoAmount);
        interactionMode = true;
        moveTextCursorToEnd();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void beginFunSeedRandomizerEditing() {
        seedEditing = false;
        funEditField = FunEditField.SEED_RANDOMIZER;
        funSeedRandomizerEditText = formatIntervalEdit(draftSeedRandomizerIntervalTicks);
        interactionMode = true;
        moveTextCursorToEnd();
        releaseMouseForOverlay();
        KeyMapping.releaseAll();
    }

    private static void finishTextEditingBeforeNavigation() {
        if (funEditField != FunEditField.NONE) {
            submitFunEdit();
        }
        seedEditing = false;
        clearTextSelection();
    }

    private static boolean isTextEditing() {
        return seedEditing || funEditField != FunEditField.NONE;
    }

    private static boolean canUpdateSettingsNow() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getConnection() == null || canUpdateServerSettings;
    }

    private static boolean shouldShowOperatorAccessControls() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getConnection() != null && serverSettingsOperator;
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
                && mouseAction != MouseAction.FUN_INTERVAL && mouseAction != MouseAction.FUN_AMOUNT && mouseAction != MouseAction.FUN_SEED_RANDOMIZER
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
        } else if (mouseAction == MouseAction.FUN_SEED_RANDOMIZER) {
            updatePendingSeedRandomizerInterval(mouseX);
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
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.funIntervalSliderBounds(
                LAYOUT,
                screenWidth,
                screenHeight
        );
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - slider.x()) / Math.max(1.0D, slider.width())));
        int ticks = (int) Math.round(ratio * CorruptionOverlayPanel.MAX_AUTO_INTERVAL_TICKS);
        pendingFunValue = ticks < 20 ? 0 : Math.max(20, ticks);
    }

    private static void updatePendingFunAmount(double mouseX) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.funAmountSliderBounds(
                LAYOUT,
                screenWidth,
                screenHeight
        );
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - slider.x()) / Math.max(1.0D, slider.width())));
        pendingFunValue = CorruptionOverlayPanel.MIN_AUTO_AMOUNT + (int) Math.round(ratio * (CorruptionOverlayPanel.MAX_AUTO_AMOUNT - CorruptionOverlayPanel.MIN_AUTO_AMOUNT));
    }

    private static void updatePendingSeedRandomizerInterval(double mouseX) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        CorruptionOverlayPanel.Rect slider = CorruptionOverlayPanel.funSeedRandomizerSliderBounds(
                LAYOUT,
                screenWidth,
                screenHeight
        );
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - slider.x()) / Math.max(1.0D, slider.width())));
        int ticks = (int) Math.round(ratio * CorruptionOverlayPanel.MAX_AUTO_INTERVAL_TICKS);
        pendingFunValue = ticks < 20 ? 0 : Math.max(20, ticks);
    }

    private static boolean handleTextEditKey(int key, int modifiers, int action, boolean captureUnhandled) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return captureUnhandled;
        }
        if (!isTextEditing()) {
            return captureUnhandled;
        }

        clampTextSelection();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (seedEditing) {
                submitSeedEdit();
            } else {
                submitFunEdit();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelActiveTextEdit();
            return true;
        }
        return handleEditBufferKey(key, modifiers, captureUnhandled);
    }

    private static boolean handleProtectedTextEditKey(int key, int modifiers, int action, boolean captureUnhandled) {
        boolean[] result = new boolean[]{captureUnhandled};
        ClientCorruptionProtection.runProtectedGui(() -> result[0] = handleTextEditKey(key, modifiers, action, captureUnhandled));
        return result[0];
    }

    private static boolean handleEditBufferKey(int key, int modifiers, boolean captureUnhandled) {
        boolean command = isCommandModifier(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (command && key == GLFW.GLFW_KEY_A) {
            selectAllEditText();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_C) {
            copyActiveTextSelection();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_X) {
            cutActiveTextSelection();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_V) {
            insertActiveText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (command && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (command) {
                deletePreviousWord();
            } else {
                deletePreviousCharacter();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (command) {
                deleteNextWord();
            } else {
                deleteNextCharacter();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            if (!shift && hasTextSelection()) {
                moveTextCursor(selectionStart(), false);
            } else {
                moveTextCursor(command ? previousWordBoundary(activeEditText(), textCursor) : textCursor - 1, shift);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (!shift && hasTextSelection()) {
                moveTextCursor(selectionEnd(), false);
            } else {
                moveTextCursor(command ? nextWordBoundary(activeEditText(), textCursor) : textCursor + 1, shift);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            moveTextCursor(0, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            moveTextCursor(activeEditText().length(), shift);
            return true;
        }

        if (command || (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            return true;
        }

        String character = seedEditing ? keyToSeedCharacter(key, shift) : keyToFunCharacter(key, shift);
        if (!character.isEmpty()) {
            insertActiveText(character);
            return true;
        }
        return captureUnhandled;
    }

    private static boolean isCommandModifier(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private static void cancelActiveTextEdit() {
        if (seedEditing) {
            seedEditing = false;
            seedEditText = draftSeedLabel;
        } else {
            funEditField = FunEditField.NONE;
        }
        clearTextSelection();
    }

    private static String activeEditText() {
        if (seedEditing) {
            return seedEditText;
        }
        return switch (funEditField) {
            case INTERVAL -> funIntervalEditText;
            case AMOUNT -> funAmountEditText;
            case SEED_RANDOMIZER -> funSeedRandomizerEditText;
            case NONE -> seedEditText;
        };
    }

    private static int activeEditMaxLength() {
        return seedEditing ? 96 : 24;
    }

    private static String sanitizeActiveEditText(String value) {
        return seedEditing ? sanitizeSeedText(value) : sanitizeFunEditText(value);
    }

    private static void setActiveEditText(String value, int requestedCursor) {
        String sanitized = sanitizeActiveEditText(value);
        if (seedEditing) {
            seedEditText = sanitized;
        } else if (funEditField == FunEditField.INTERVAL) {
            funIntervalEditText = sanitized;
        } else if (funEditField == FunEditField.AMOUNT) {
            funAmountEditText = sanitized;
        } else if (funEditField == FunEditField.SEED_RANDOMIZER) {
            funSeedRandomizerEditText = sanitized;
        }
        textCursor = clampTextIndex(requestedCursor, sanitized.length());
        textSelectionAnchor = textCursor;
    }

    private static void insertActiveText(String value) {
        String insert = sanitizeActiveEditText(value);
        if (insert.isEmpty()) {
            return;
        }
        String text = activeEditText();
        int start = selectionStart();
        int end = selectionEnd();
        int remaining = activeEditMaxLength() - (text.length() - (end - start));
        if (remaining <= 0) {
            return;
        }
        if (insert.length() > remaining) {
            insert = insert.substring(0, remaining);
        }
        setActiveEditText(text.substring(0, start) + insert + text.substring(end), start + insert.length());
    }

    private static void copyActiveTextSelection() {
        String text = activeEditText();
        String copied = hasTextSelection() ? text.substring(selectionStart(), selectionEnd()) : text;
        Minecraft.getInstance().keyboardHandler.setClipboard(copied);
    }

    private static void cutActiveTextSelection() {
        if (!hasTextSelection()) {
            return;
        }
        copyActiveTextSelection();
        deleteTextSelection();
    }

    private static void selectAllEditText() {
        textSelectionAnchor = 0;
        textCursor = activeEditText().length();
    }

    private static void deletePreviousCharacter() {
        if (deleteTextSelection()) {
            return;
        }
        String text = activeEditText();
        if (textCursor <= 0) {
            return;
        }
        int start = textCursor - 1;
        setActiveEditText(text.substring(0, start) + text.substring(textCursor), start);
    }

    private static void deleteNextCharacter() {
        if (deleteTextSelection()) {
            return;
        }
        String text = activeEditText();
        if (textCursor >= text.length()) {
            return;
        }
        setActiveEditText(text.substring(0, textCursor) + text.substring(textCursor + 1), textCursor);
    }

    private static void deletePreviousWord() {
        if (deleteTextSelection()) {
            return;
        }
        String text = activeEditText();
        int start = previousWordBoundary(text, textCursor);
        if (start < textCursor) {
            setActiveEditText(text.substring(0, start) + text.substring(textCursor), start);
        }
    }

    private static void deleteNextWord() {
        if (deleteTextSelection()) {
            return;
        }
        String text = activeEditText();
        int end = nextWordBoundary(text, textCursor);
        if (end > textCursor) {
            setActiveEditText(text.substring(0, textCursor) + text.substring(end), textCursor);
        }
    }

    private static boolean deleteTextSelection() {
        if (!hasTextSelection()) {
            return false;
        }
        String text = activeEditText();
        int start = selectionStart();
        int end = selectionEnd();
        setActiveEditText(text.substring(0, start) + text.substring(end), start);
        return true;
    }

    private static void moveTextCursor(int position, boolean selecting) {
        String text = activeEditText();
        int clamped = clampTextIndex(position, text.length());
        if (!selecting && hasTextSelection()) {
            clamped = position < textCursor ? selectionStart() : selectionEnd();
        }
        textCursor = clamped;
        if (!selecting) {
            textSelectionAnchor = textCursor;
        }
    }

    private static void moveTextCursorToEnd() {
        textCursor = activeEditText().length();
        textSelectionAnchor = textCursor;
    }

    private static void clearTextSelection() {
        textCursor = clampTextIndex(textCursor, activeEditText().length());
        textSelectionAnchor = textCursor;
    }

    private static void clampTextSelection() {
        int length = activeEditText().length();
        textCursor = clampTextIndex(textCursor, length);
        textSelectionAnchor = clampTextIndex(textSelectionAnchor, length);
    }

    private static boolean hasTextSelection() {
        clampTextSelection();
        return textCursor != textSelectionAnchor;
    }

    private static int selectionStart() {
        clampTextSelection();
        return Math.min(textCursor, textSelectionAnchor);
    }

    private static int selectionEnd() {
        clampTextSelection();
        return Math.max(textCursor, textSelectionAnchor);
    }

    private static int clampTextIndex(int index, int length) {
        return Math.max(0, Math.min(length, index));
    }

    private static int previousWordBoundary(String text, int cursor) {
        int index = clampTextIndex(cursor, text.length());
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        if (index <= 0) {
            return 0;
        }
        boolean word = isWordCharacter(text.charAt(index - 1));
        while (index > 0 && isWordCharacter(text.charAt(index - 1)) == word && !Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static int nextWordBoundary(String text, int cursor) {
        int index = clampTextIndex(cursor, text.length());
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= text.length()) {
            return text.length();
        }
        boolean word = isWordCharacter(text.charAt(index));
        while (index < text.length() && isWordCharacter(text.charAt(index)) == word && !Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static void submitFunEdit() {
        if (funEditField == FunEditField.INTERVAL) {
            int ticks = parseIntervalTicks(funIntervalEditText, draftAutoIntervalTicks);
            funEditField = FunEditField.NONE;
            draftAutoIntervalTicks = ticks;
            clearTextSelection();
        } else if (funEditField == FunEditField.AMOUNT) {
            int amount = parseAutoAmount(funAmountEditText, draftAutoAmount);
            funEditField = FunEditField.NONE;
            draftAutoAmount = amount;
            clearTextSelection();
        } else if (funEditField == FunEditField.SEED_RANDOMIZER) {
            int ticks = parseIntervalTicks(funSeedRandomizerEditText, draftSeedRandomizerIntervalTicks);
            funEditField = FunEditField.NONE;
            draftSeedRandomizerIntervalTicks = ticks;
            clearTextSelection();
        }
    }

    private static int parseIntervalTicks(String value, int fallbackTicks) {
        String text = sanitizeFunEditText(value).toLowerCase(Locale.ROOT).replace(" ", "");
        if (text.isBlank()) {
            return fallbackTicks;
        }
        if (text.equals("off") || text.equals("none") || text.equals("disabled") || text.equals("0")) {
            return 0;
        }
        double multiplier = 20.0D;
        if (text.endsWith("ticks")) {
            multiplier = 1.0D;
            text = text.substring(0, text.length() - 5);
        } else if (text.endsWith("tick")) {
            multiplier = 1.0D;
            text = text.substring(0, text.length() - 4);
        } else if (text.endsWith("t")) {
            multiplier = 1.0D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("h")) {
            multiplier = 72_000.0D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 1_200.0D;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("s")) {
            multiplier = 20.0D;
            text = text.substring(0, text.length() - 1);
        }
        try {
            double valueNumber = Double.parseDouble(text);
            if (!Double.isFinite(valueNumber)) {
                return fallbackTicks;
            }
            int ticks = (int) Math.round(valueNumber * multiplier);
            if (ticks <= 0) {
                return 0;
            }
            return Math.max(20, Math.min(CorruptionOverlayPanel.MAX_AUTO_INTERVAL_TICKS, ticks));
        } catch (NumberFormatException ignored) {
            return fallbackTicks;
        }
    }

    private static int parseAutoAmount(String value, int fallbackAmount) {
        String text = sanitizeFunEditText(value).replace("%", "").trim();
        if (text.isBlank()) {
            return fallbackAmount;
        }
        try {
            double parsed = Double.parseDouble(text);
            if (!Double.isFinite(parsed)) {
                return fallbackAmount;
            }
            return Math.max(CorruptionOverlayPanel.MIN_AUTO_AMOUNT, Math.min(CorruptionOverlayPanel.MAX_AUTO_AMOUNT, (int) Math.round(parsed)));
        } catch (NumberFormatException ignored) {
            return fallbackAmount;
        }
    }

    private static String formatIntervalEdit(int ticks) {
        if (ticks <= 0) {
            return "off";
        }
        int seconds = Math.max(1, Math.round(ticks / 20.0F));
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    private static String signedPercentLabel(int amount) {
        return (amount > 0 ? "+" : "") + amount + "%";
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

    private static String keyToFunCharacter(int key, boolean shift) {
        if (shift && key == GLFW.GLFW_KEY_5) {
            return "%";
        }
        String character = keyToSeedCharacter(key, shift);
        if (character.length() != 1) {
            return "";
        }
        char c = character.charAt(0);
        if (Character.isLetterOrDigit(c) || c == '-' || c == '+' || c == '.' || c == '%' || c == ' ') {
            return character;
        }
        return "";
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

    private static String sanitizeFunEditText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Math.min(24, value.length()));
        for (int i = 0; i < value.length() && builder.length() < 24; i++) {
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
            return draftSeed;
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

    private static CorruptionStateSnapshot renderSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || minecraft.player == null ? ClientCorruptionState.localSnapshot() : latestSnapshot;
    }

    private static CorruptionStateSnapshot draftSnapshot(CorruptionStateSnapshot base) {
        CorruptionStateSnapshot snapshot = base == null ? latestSnapshot : base;
        return new CorruptionStateSnapshot(
                pendingLevel,
                draftSeed,
                draftSeedLabel,
                draftTargetsMask,
                draftAutoIntervalTicks,
                draftAutoAmount,
                draftClientDriftEnabled,
                draftSeedRandomizerIntervalTicks,
                snapshot.getClientDriftSalt()
        );
    }

    private static boolean hasDraftChanges() {
        return pendingLevel != latestSnapshot.getCorruptionLevel()
                || draftSeed != latestSnapshot.getFixedCorruptionSeed()
                || !draftSeedLabel.equals(latestSnapshot.getCorruptionSeedLabel())
                || draftTargetsMask != latestSnapshot.getEnabledTargetsMask()
                || draftAutoIntervalTicks != latestSnapshot.getAutoIncreaseIntervalTicks()
                || draftAutoAmount != latestSnapshot.getAutoIncreaseAmount()
                || draftClientDriftEnabled != latestSnapshot.isClientDriftEnabled()
                || draftSeedRandomizerIntervalTicks != latestSnapshot.getSeedRandomizerIntervalTicks();
    }

    private static boolean hasPendingChanges() {
        return hasDraftChanges() || hasEditorChanges();
    }

    private static boolean hasEditorChanges() {
        if (seedEditing) {
            String label = sanitizeSeedText(seedEditText);
            return !label.isBlank() && (!label.equals(draftSeedLabel) || seedFromText(label) != draftSeed);
        }
        if (funEditField == FunEditField.INTERVAL) {
            return parseIntervalTicks(funIntervalEditText, draftAutoIntervalTicks) != draftAutoIntervalTicks;
        }
        if (funEditField == FunEditField.AMOUNT) {
            return parseAutoAmount(funAmountEditText, draftAutoAmount) != draftAutoAmount;
        }
        if (funEditField == FunEditField.SEED_RANDOMIZER) {
            return parseIntervalTicks(funSeedRandomizerEditText, draftSeedRandomizerIntervalTicks) != draftSeedRandomizerIntervalTicks;
        }
        return false;
    }

    private static void syncDraftFromSnapshot() {
        pendingLevel = latestSnapshot.getCorruptionLevel();
        draftSeed = latestSnapshot.getFixedCorruptionSeed();
        draftSeedLabel = latestSnapshot.getCorruptionSeedLabel();
        draftTargetsMask = latestSnapshot.getEnabledTargetsMask();
        draftAutoIntervalTicks = latestSnapshot.getAutoIncreaseIntervalTicks();
        draftAutoAmount = latestSnapshot.getAutoIncreaseAmount();
        draftClientDriftEnabled = latestSnapshot.isClientDriftEnabled();
        draftSeedRandomizerIntervalTicks = latestSnapshot.getSeedRandomizerIntervalTicks();
        seedEditText = draftSeedLabel;
        funIntervalEditText = formatIntervalEdit(draftAutoIntervalTicks);
        funAmountEditText = signedPercentLabel(draftAutoAmount);
        funSeedRandomizerEditText = formatIntervalEdit(draftSeedRandomizerIntervalTicks);
        moveTextCursorToEnd();
    }

    private static boolean isProfileDraftPage(CorruptionOverlayPanel.Page page) {
        return page == CorruptionOverlayPanel.Page.CONTROL
                || page == CorruptionOverlayPanel.Page.SETTINGS
                || page == CorruptionOverlayPanel.Page.FUN;
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
        quickToggleRestore = null;
        serverAllowsNonOpSettingsUpdates = false;
        canUpdateServerSettings = true;
        serverSettingsOperator = true;
        seedEditing = false;
        funEditField = FunEditField.NONE;
        achievementsScroll = 0;
        ClientCorruptionState.reset();
        latestSnapshot = ClientCorruptionState.localSnapshot();
        syncDraftFromSnapshot();
    }

    private enum MouseAction {
        NONE,
        PANEL,
        WINDOW,
        SLIDER,
        APPLY,
        CANCEL,
        SEED_APPLY,
        SEED_RANDOM,
        SEED_COPY,
        SEED_PASTE,
        TARGET_TOGGLE,
        TARGET_ENABLE_ALL,
        TARGET_DISABLE_ALL,
        FUN_INTERVAL,
        FUN_AMOUNT,
        FUN_SEED_RANDOMIZER,
        CLIENT_DRIFT_TOGGLE,
        ACHIEVEMENT_PIN,
        ACHIEVEMENT_RESET,
        HUD_CORNER,
        ACCESS_TOGGLE,
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

    private enum FunEditField {
        NONE,
        INTERVAL,
        AMOUNT,
        SEED_RANDOMIZER
    }

    private record QuickToggleSnapshot(
            int level,
            long seed,
            String seedLabel,
            int enabledTargetsMask,
            int autoIncreaseIntervalTicks,
            int autoIncreaseAmount,
            boolean clientDriftEnabled,
            int seedRandomizerIntervalTicks
    ) {
        private static QuickToggleSnapshot from(CorruptionStateSnapshot snapshot) {
            return new QuickToggleSnapshot(
                    snapshot.getCorruptionLevel(),
                    snapshot.getFixedCorruptionSeed(),
                    snapshot.getCorruptionSeedLabel(),
                    snapshot.getEnabledTargetsMask(),
                    snapshot.getAutoIncreaseIntervalTicks(),
                    snapshot.getAutoIncreaseAmount(),
                    snapshot.isClientDriftEnabled(),
                    snapshot.getSeedRandomizerIntervalTicks()
            );
        }

        private void apply() {
            applyCurrentSettings(level, seed, seedLabel, enabledTargetsMask, autoIncreaseIntervalTicks, autoIncreaseAmount, clientDriftEnabled, seedRandomizerIntervalTicks);
        }
    }
}
