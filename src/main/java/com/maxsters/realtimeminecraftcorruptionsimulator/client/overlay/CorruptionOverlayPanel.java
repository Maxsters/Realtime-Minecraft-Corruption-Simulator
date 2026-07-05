package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.CorruptionAchievementManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class CorruptionOverlayPanel {
    public static final int HEADER_HEIGHT = 20;
    public static final int CONTROL_SIZE = 14;
    public static final int COLLAPSED_SIZE = 28;
    public static final int APPLY_BUTTON_WIDTH = 74;
    public static final int APPLY_BUTTON_HEIGHT = 16;
    public static final int CANCEL_BUTTON_WIDTH = 58;
    private static final int TAB_WIDTH = 64;
    private static final int TAB_HEIGHT = 16;
    private static final int SEED_BUTTON_WIDTH = 42;
    private static final int SEED_RANDOM_BUTTON_WIDTH = 56;
    private static final int SEED_BUTTON_GAP = 4;
    private static final int FUN_INPUT_WIDTH = 74;
    private static final int ACCESS_BUTTON_WIDTH = 126;
    private static final int TARGET_BULK_BUTTON_WIDTH = 58;
    private static final int TOGGLE_ROW_HEIGHT = 28;
    private static final int WARNING_HEIGHT = 28;
    private static final int HIGH_LEVEL_WARNING_THRESHOLD = 50;
    private static final int RESIZE_HANDLE_SIZE = 6;
    private static final int ACHIEVEMENT_ROW_HEIGHT = 34;
    private static final int ACHIEVEMENT_ROW_GAP = 3;
    private static final int ACHIEVEMENT_NOTE_HEIGHT = 40;
    private static final int ACHIEVEMENT_RESET_BUTTON_WIDTH = 72;
    private static final int ACHIEVEMENT_PIN_BUTTON_WIDTH = 28;
    private static final int PINNED_ROW_WIDTH = 176;
    private static final int PINNED_ROW_HEIGHT = 30;
    private static final int PINNED_ROW_GAP = 4;
    private static final int CONTENT_TOP_OFFSET = 50;
    public static final int MAX_AUTO_INTERVAL_TICKS = 144_000;
    public static final int MIN_AUTO_AMOUNT = -100;
    public static final int MAX_AUTO_AMOUNT = 100;
    private static final Map<Block, TextureAtlasSprite> ACHIEVEMENT_ICON_CACHE = new IdentityHashMap<>();

    private CorruptionOverlayPanel() {
    }

    public static Rect panelBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        return new Rect(layout.x(), layout.y(), layout.displayedWidth(screenWidth), layout.displayedHeight(screenHeight));
    }

    public static Rect headerBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x(), panel.y(), panel.width(), Math.min(HEADER_HEIGHT, panel.height()));
    }

    public static Rect minimizeButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + panel.width() - 38, panel.y() + 3, CONTROL_SIZE, CONTROL_SIZE);
    }

    public static Rect collapseButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + panel.width() - 20, panel.y() + 3, CONTROL_SIZE, CONTROL_SIZE);
    }

    public static Rect controlTabBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + 10, panel.y() + 26, TAB_WIDTH, TAB_HEIGHT);
    }

    public static Rect settingsTabBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect control = controlTabBounds(layout, screenWidth, screenHeight);
        return new Rect(control.x() + control.width() + 6, control.y(), TAB_WIDTH, TAB_HEIGHT);
    }

    public static Rect funTabBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect settings = settingsTabBounds(layout, screenWidth, screenHeight);
        return new Rect(settings.x() + settings.width() + 6, settings.y(), TAB_WIDTH, TAB_HEIGHT);
    }

    public static Rect achievementsTabBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect fun = funTabBounds(layout, screenWidth, screenHeight);
        return new Rect(fun.x() + fun.width() + 6, fun.y(), TAB_WIDTH, TAB_HEIGHT);
    }

    public static Rect hudTabBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect achievements = achievementsTabBounds(layout, screenWidth, screenHeight);
        return new Rect(achievements.x() + achievements.width() + 6, achievements.y(), TAB_WIDTH, TAB_HEIGHT);
    }

    public static Rect sliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int sliderY = panel.y() + 96;
        return new Rect(panel.x() + 12, sliderY, Math.max(80, panel.width() - 24), 12);
    }

    public static Rect sliderInteractionBounds(Rect slider) {
        return new Rect(slider.x() - 4, slider.y(), slider.width() + 8, slider.height());
    }

    public static Rect nonOpSettingsButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + panel.width() - ACCESS_BUTTON_WIDTH - 12, panel.y() + 70, ACCESS_BUTTON_WIDTH, 14);
    }

    public static Rect applyButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        return globalApplyButtonBounds(layout, Page.CONTROL, screenWidth, screenHeight);
    }

    public static Rect globalApplyButtonBounds(CorruptionOverlayLayout layout, Page page, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int y = Math.max(panel.y() + HEADER_HEIGHT + 6, panel.y() + panel.height() - APPLY_BUTTON_HEIGHT - 12);
        return new Rect(panel.x() + panel.width() - 12 - APPLY_BUTTON_WIDTH, y, APPLY_BUTTON_WIDTH, APPLY_BUTTON_HEIGHT);
    }

    public static Rect globalCancelButtonBounds(CorruptionOverlayLayout layout, Page page, int screenWidth, int screenHeight) {
        Rect apply = globalApplyButtonBounds(layout, page, screenWidth, screenHeight);
        return new Rect(apply.x() - 6 - CANCEL_BUTTON_WIDTH, apply.y(), CANCEL_BUTTON_WIDTH, apply.height());
    }

    public static Rect seedFieldBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int y = panel.y() + CONTENT_TOP_OFFSET + 14;
        int buttonWidth = seedButtonWidth(panel);
        int randomWidth = seedRandomButtonWidth(panel);
        int width = Math.max(64, panel.width() - 24 - buttonWidth * 3 - randomWidth - SEED_BUTTON_GAP * 4);
        return new Rect(panel.x() + 12, y, width, 16);
    }

    public static Rect seedCopyButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect field = seedFieldBounds(layout, screenWidth, screenHeight);
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(field.x() + field.width() + SEED_BUTTON_GAP, field.y(), seedButtonWidth(panel), 16);
    }

    public static Rect seedPasteButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect copy = seedCopyButtonBounds(layout, screenWidth, screenHeight);
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(copy.x() + copy.width() + SEED_BUTTON_GAP, copy.y(), seedButtonWidth(panel), 16);
    }

    public static Rect seedApplyButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect paste = seedPasteButtonBounds(layout, screenWidth, screenHeight);
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(paste.x() + paste.width() + SEED_BUTTON_GAP, paste.y(), seedButtonWidth(panel), 16);
    }

    public static Rect randomSeedButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect apply = seedApplyButtonBounds(layout, screenWidth, screenHeight);
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(apply.x() + apply.width() + SEED_BUTTON_GAP, apply.y(), seedRandomButtonWidth(panel), 16);
    }

    public static Rect enableAllTargetsButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = settingsListArea(layout, screenWidth, screenHeight);
        int width = Math.min(TARGET_BULK_BUTTON_WIDTH, Math.max(48, area.width() / 2));
        return new Rect(area.x() + area.width() - width * 2 - 4, area.y() - 2, width, 14);
    }

    public static Rect disableAllTargetsButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect enable = enableAllTargetsButtonBounds(layout, screenWidth, screenHeight);
        return new Rect(enable.x() + enable.width() + 4, enable.y(), enable.width(), enable.height());
    }

    public static List<TargetHitBox> targetHitBoxes(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = settingsListArea(layout, screenWidth, screenHeight);
        List<TargetHitBox> hitBoxes = new ArrayList<>();
        CorruptionTarget[] targets = CorruptionTarget.values();
        int columns = targetColumnCount(area);
        int columnGap = columns > 1 ? 8 : 0;
        int columnWidth = Math.max(80, (area.width() - columnGap * (columns - 1)) / columns);
        int rowsPerColumn = Math.max(1, (targets.length + columns - 1) / columns);
        int rowHeight = targetRowHeight(area);
        int maxRows = Math.max(0, (area.height() - 14) / rowHeight);
        for (int i = 0; i < targets.length; i++) {
            int column = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            if (row >= maxRows) {
                continue;
            }
            int x = area.x() + column * (columnWidth + columnGap);
            int y = area.y() + 14 + row * rowHeight;
            hitBoxes.add(new TargetHitBox(targets[i], new Rect(x, y - 2, columnWidth, rowHeight - 2)));
        }
        return hitBoxes;
    }

    public static Rect funIntervalSliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = funIntervalArea(layout, screenWidth, screenHeight);
        int width = Math.max(50, area.width() - FUN_INPUT_WIDTH - 8);
        return new Rect(area.x(), area.y() + 24, width, 12);
    }

    public static Rect funIntervalInputBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect slider = funIntervalSliderBounds(layout, screenWidth, screenHeight);
        return new Rect(slider.x() + slider.width() + 8, slider.y() - 6, FUN_INPUT_WIDTH, 18);
    }

    public static Rect funAmountSliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = funAmountArea(layout, screenWidth, screenHeight);
        int width = Math.max(50, area.width() - FUN_INPUT_WIDTH - 8);
        return new Rect(area.x(), area.y() + 24, width, 12);
    }

    public static Rect funAmountInputBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect slider = funAmountSliderBounds(layout, screenWidth, screenHeight);
        return new Rect(slider.x() + slider.width() + 8, slider.y() - 6, FUN_INPUT_WIDTH, 18);
    }

    public static Rect funSeedRandomizerSliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = funSeedRandomizerArea(layout, screenWidth, screenHeight);
        int width = Math.max(42, area.width() - FUN_INPUT_WIDTH - 8);
        return new Rect(area.x(), area.y() + 24, width, 12);
    }

    public static Rect funSeedRandomizerInputBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect slider = funSeedRandomizerSliderBounds(layout, screenWidth, screenHeight);
        return new Rect(slider.x() + slider.width() + 8, slider.y() - 6, FUN_INPUT_WIDTH, 18);
    }

    public static Rect funClientDriftButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = funClientDriftArea(layout, screenWidth, screenHeight);
        int width = Math.min(82, Math.max(54, area.width() / 2));
        return new Rect(area.x(), area.y() + 18, width, 18);
    }

    public static Rect achievementResetButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = achievementsArea(layout, screenWidth, screenHeight);
        return new Rect(area.x() + area.width() - ACHIEVEMENT_RESET_BUTTON_WIDTH, area.y() - 2, ACHIEVEMENT_RESET_BUTTON_WIDTH, 14);
    }

    public static Rect achievementsListBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = achievementsArea(layout, screenWidth, screenHeight);
        int top = area.y() + ACHIEVEMENT_NOTE_HEIGHT;
        return new Rect(area.x(), top, area.width(), Math.max(1, area.y() + area.height() - top));
    }

    public static int achievementsMaxScroll(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect list = achievementsListBounds(layout, screenWidth, screenHeight);
        int count = CorruptionAchievementManager.achievements().size();
        int contentHeight = Math.max(0, count * (ACHIEVEMENT_ROW_HEIGHT + ACHIEVEMENT_ROW_GAP) - ACHIEVEMENT_ROW_GAP);
        return Math.max(0, contentHeight - list.height());
    }

    public static List<AchievementHitBox> achievementHitBoxes(CorruptionOverlayLayout layout, int screenWidth, int screenHeight, int scroll) {
        List<AchievementHitBox> hitBoxes = new ArrayList<>();
        Rect area = achievementsArea(layout, screenWidth, screenHeight);
        Rect list = achievementsListBounds(layout, screenWidth, screenHeight);
        int maxScroll = achievementsMaxScroll(layout, screenWidth, screenHeight);
        int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
        int rowWidth = Math.max(24, area.width() - (maxScroll > 0 ? 6 : 0));
        int y = list.y() - clampedScroll;
        int bottom = list.y() + list.height();
        for (CorruptionAchievementManager.Achievement achievement : CorruptionAchievementManager.achievements()) {
            Rect row = new Rect(area.x(), y, rowWidth, ACHIEVEMENT_ROW_HEIGHT);
            if (y + ACHIEVEMENT_ROW_HEIGHT >= list.y() && y <= bottom) {
                Rect pin = new Rect(row.x() + row.width() - ACHIEVEMENT_PIN_BUTTON_WIDTH, row.y(), ACHIEVEMENT_PIN_BUTTON_WIDTH, 14);
                hitBoxes.add(new AchievementHitBox(achievement, row, pin));
            }
            y += ACHIEVEMENT_ROW_HEIGHT + ACHIEVEMENT_ROW_GAP;
        }
        return hitBoxes;
    }

    public static Rect hudCornerButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight, CorruptionAchievementManager.HudCorner corner) {
        Rect area = hudSettingsArea(layout, screenWidth, screenHeight);
        int gap = 6;
        int width = Math.max(70, (area.width() - gap) / 2);
        int height = 18;
        int column = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> 0;
            case TOP_RIGHT, BOTTOM_RIGHT -> 1;
        };
        int row = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> 0;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> 1;
        };
        return new Rect(area.x() + column * (width + gap), area.y() + 40 + row * (height + gap), width, height);
    }

    public static Rect horizontalResizeBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + panel.width() - RESIZE_HANDLE_SIZE, panel.y() + HEADER_HEIGHT + 4, RESIZE_HANDLE_SIZE, Math.max(16, panel.height() - HEADER_HEIGHT - RESIZE_HANDLE_SIZE - 8));
    }

    public static Rect verticalResizeBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + 4, panel.y() + panel.height() - RESIZE_HANDLE_SIZE, Math.max(16, panel.width() - RESIZE_HANDLE_SIZE - 8), RESIZE_HANDLE_SIZE);
    }

    public static Rect cornerResizeBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + panel.width() - RESIZE_HANDLE_SIZE, panel.y() + panel.height() - RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
    }

    public static void renderOpen(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, CorruptionStateSnapshot draftSnapshot, int pendingLevel, int pendingAutoIntervalTicks, int pendingAutoAmount, int pendingSeedRandomizerIntervalTicks, boolean draftDirty, Page page, boolean seedEditing, String seedEditText, boolean funIntervalEditing, String funIntervalEditText, boolean funAmountEditing, String funAmountEditText, boolean funSeedRandomizerEditing, String funSeedRandomizerEditText, int textCursor, int textSelectionAnchor, int achievementsScroll, int achievementResetPresses, CorruptionAchievementManager.HudCorner pinnedCorner, boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean showOperatorAccessControls, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, panel);
        renderHeader(graphics, font, layout, snapshot, panel, mouseX, mouseY, false);
        renderTabs(graphics, font, layout, page, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        if (page == Page.SETTINGS) {
            renderSettings(graphics, font, layout, draftSnapshot, seedEditing, seedEditText, textCursor, textSelectionAnchor, draftDirty, canUpdateSettings, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else if (page == Page.FUN) {
            renderFun(graphics, font, layout, draftSnapshot, pendingAutoIntervalTicks, pendingAutoAmount, pendingSeedRandomizerIntervalTicks, funIntervalEditing, funIntervalEditText, funAmountEditing, funAmountEditText, funSeedRandomizerEditing, funSeedRandomizerEditText, textCursor, textSelectionAnchor, draftDirty, canUpdateSettings, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else if (page == Page.ACHIEVEMENTS) {
            renderAchievements(graphics, font, layout, achievementsScroll, achievementResetPresses, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else if (page == Page.HUD) {
            renderHudSettings(graphics, font, layout, pinnedCorner, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else {
            renderControl(graphics, font, layout, snapshot, draftSnapshot, pendingLevel, draftDirty, allowNonOpSettingsUpdates, canUpdateSettings, showOperatorAccessControls, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        }
        renderResizeHandles(graphics, layout, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
    }

    public static void renderMinimized(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, panel);
        renderHeader(graphics, font, layout, snapshot, panel, mouseX, mouseY, true);
        int y = panel.y() + 21;
        int maxWidth = panel.width() - 16;
        String summary = snapshot.getCorruptionLevel() + "%  Seed " + snapshot.getCorruptionSeedLabel();
        drawClipped(graphics, font, summary, panel.x() + 8, y, maxWidth, 0xFFD4DEE1);
    }

    public static void renderCollapsed(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        CorruptionOverlayButton.render(graphics, font, panel.x(), panel.y(), COLLAPSED_SIZE, snapshot, panel.contains(mouseX, mouseY));
    }

    public static void renderHudStrip(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot) {
        if (layout.mode() == CorruptionOverlayLayout.Mode.COLLAPSED) {
            CorruptionOverlayButton.render(graphics, font, layout.x(), layout.y(), COLLAPSED_SIZE, snapshot, false);
            return;
        }
        if (layout.mode() == CorruptionOverlayLayout.Mode.MINIMIZED) {
            Rect strip = new Rect(layout.x(), layout.y(), Math.min(layout.displayedWidth(graphics.guiWidth()), graphics.guiWidth() - 8), 24);
            fillPanel(graphics, strip);
            String text = "RMCS " + snapshot.getCorruptionLevel() + "% | Seed " + snapshot.getCorruptionSeedLabel();
            drawClipped(graphics, font, text, strip.x() + 6, strip.y() + 8, strip.width() - 12, 0xFFD4DEE1);
        }
    }

    private static void renderHeader(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, Rect panel, int mouseX, int mouseY, boolean minimized) {
        graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + HEADER_HEIGHT, 0xF0263034);
        graphics.fill(panel.x(), panel.y() + HEADER_HEIGHT - 1, panel.x() + panel.width(), panel.y() + HEADER_HEIGHT, 0xFF52636A);
        String title = minimized ? "Realtime Corruption Simulator" : "Realtime Minecraft Corruption Simulator";
        drawClipped(graphics, font, title, panel.x() + 8, panel.y() + 6, panel.width() - 52, 0xFFEAF4F7);

        Rect min = minimizeButtonBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        Rect close = collapseButtonBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        drawControl(graphics, font, min, minimized ? "+" : "_", min.contains(mouseX, mouseY));
        drawControl(graphics, font, close, "X", close.contains(mouseX, mouseY));
    }

    private static void renderTabs(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, Page page, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        drawTab(graphics, font, controlTabBounds(layout, screenWidth, screenHeight), "Control", page == Page.CONTROL, mouseX, mouseY);
        drawTab(graphics, font, settingsTabBounds(layout, screenWidth, screenHeight), "Settings", page == Page.SETTINGS, mouseX, mouseY);
        drawTab(graphics, font, funTabBounds(layout, screenWidth, screenHeight), "Fun", page == Page.FUN, mouseX, mouseY);
        drawTab(graphics, font, achievementsTabBounds(layout, screenWidth, screenHeight), "Awards", page == Page.ACHIEVEMENTS, mouseX, mouseY);
        drawTab(graphics, font, hudTabBounds(layout, screenWidth, screenHeight), "HUD", page == Page.HUD, mouseX, mouseY);
    }

    private static void renderControl(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, CorruptionStateSnapshot draftSnapshot, int pendingLevel, boolean draftDirty, boolean allowNonOpSettingsUpdates, boolean canUpdateSettings, boolean showOperatorAccessControls, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        renderReadouts(graphics, font, panel, snapshot, draftSnapshot);
        if (showOperatorAccessControls) {
            Rect access = nonOpSettingsButtonBounds(layout, screenWidth, screenHeight);
            drawButton(graphics, font, access, "Non-OP Updates: " + (allowNonOpSettingsUpdates ? "On" : "Off"), true, access.contains(mouseX, mouseY));
        } else if (!canUpdateSettings) {
            drawClipped(graphics, font, "Only Operators Can Update Server Settings.", panel.x() + 12, panel.y() + 72, panel.width() - 24, 0xFFE07878);
        }
        renderSlider(graphics, font, layout, snapshot, pendingLevel, mouseX, mouseY, screenWidth, screenHeight);
        renderApplyControl(graphics, font, layout, snapshot, pendingLevel, draftDirty, canUpdateSettings, mouseX, mouseY, screenWidth, screenHeight);
    }

    private static void renderReadouts(GuiGraphics graphics, Font font, Rect panel, CorruptionStateSnapshot snapshot, CorruptionStateSnapshot draftSnapshot) {
        int x = panel.x() + 12;
        int y = panel.y() + 50;
        int leftWidth = Math.max(120, (panel.width() - 30) / 2);
        int rightX = x + leftWidth + 8;
        int rightWidth = Math.max(90, panel.width() - (rightX - panel.x()) - 12);

        drawLabelValue(graphics, font, "Active Corruption", snapshot.getCorruptionLevel() + "%", x, y, leftWidth);
        drawLabelValue(graphics, font, "Seed", draftSnapshot.getCorruptionSeedLabel(), x, y + 12, leftWidth);

        drawLabelValue(graphics, font, "Targets", countEnabledTargets(draftSnapshot.getEnabledTargetsMask()) + "/" + CorruptionTarget.values().length, rightX, y, rightWidth);
        drawLabelValue(graphics, font, "Client Drift", draftSnapshot.isClientDriftEnabled() ? "On" : "Off", rightX, y + 12, rightWidth);
    }

    private static void renderSlider(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, int pendingLevel, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect slider = sliderBounds(layout, screenWidth, screenHeight);
        int labelY = slider.y() - 12;
        int delta = Math.abs(pendingLevel - snapshot.getCorruptionLevel());
        String label = "Corruption Level: " + pendingLevel + "%  Change " + delta + "%";
        drawClipped(graphics, font, label, slider.x(), labelY, slider.width(), 0xFFEAF4F7);

        graphics.fill(slider.x(), slider.y() + 4, slider.x() + slider.width(), slider.y() + 8, 0xFF263136);
        int fillWidth = Math.max(2, slider.width() * pendingLevel / 100);
        graphics.fill(slider.x(), slider.y() + 4, slider.x() + fillWidth, slider.y() + 8, 0xFF5CA7B2);
        int handleX = slider.x() + slider.width() * pendingLevel / 100;
        graphics.fill(handleX - 3, slider.y(), handleX + 4, slider.y() + 12, sliderInteractionBounds(slider).contains(mouseX, mouseY) ? 0xFFEAF4F7 : 0xFFB8C8CD);

        if (shouldShowHighLevelWarning(pendingLevel)) {
            int warningY = slider.y() + 38;
            graphics.fill(slider.x(), warningY, slider.x() + slider.width(), warningY + WARNING_HEIGHT, 0xCC2B2417);
            drawClipped(graphics, font, "Higher corruption levels can cause lag spikes.", slider.x() + 5, warningY + 5, slider.width() - 10, 0xFFFFD58A);
            drawClipped(graphics, font, "Texture, model, sound, and chunk work scales up.", slider.x() + 5, warningY + 16, slider.width() - 10, 0xFFE7D5B0);
        }
    }

    private static void renderApplyControl(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, int pendingLevel, boolean draftDirty, boolean canUpdateSettings, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect slider = sliderBounds(layout, screenWidth, screenHeight);
        renderDraftActions(graphics, font, layout, Page.CONTROL, draftDirty, canUpdateSettings, mouseX, mouseY, screenWidth, screenHeight);

        Rect cancel = globalCancelButtonBounds(layout, Page.CONTROL, screenWidth, screenHeight);
        int textWidth = Math.max(0, cancel.x() - slider.x() - 8);
        String status = draftDirty ? "Draft Changes Are Not Applied Yet." : "Current Profile Is Applied.";
        drawClipped(graphics, font, status, slider.x(), slider.y() + 23, textWidth, draftDirty ? 0xFFD4DEE1 : 0xFF9FAEB4);
    }

    private static void renderSettings(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, boolean seedEditing, String seedEditText, int textCursor, int textSelectionAnchor, boolean draftDirty, boolean canUpdateSettings, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect field = seedFieldBounds(layout, screenWidth, screenHeight);
        Rect copy = seedCopyButtonBounds(layout, screenWidth, screenHeight);
        Rect paste = seedPasteButtonBounds(layout, screenWidth, screenHeight);
        Rect apply = seedApplyButtonBounds(layout, screenWidth, screenHeight);
        Rect random = randomSeedButtonBounds(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Seed", field.x(), field.y() - 14, Math.max(40, random.x() + random.width() - field.x()));
        drawTextField(graphics, font, field, seedEditing ? seedEditText : snapshot.getCorruptionSeedLabel(), canUpdateSettings && seedEditing, canUpdateSettings && seedEditing ? textCursor : 0, canUpdateSettings && seedEditing ? textSelectionAnchor : 0);
        drawButton(graphics, font, copy, "Copy", true, copy.contains(mouseX, mouseY));
        drawButton(graphics, font, paste, "Paste", canUpdateSettings, canUpdateSettings && paste.contains(mouseX, mouseY));
        drawButton(graphics, font, apply, "Set", canUpdateSettings && seedEditing, canUpdateSettings && seedEditing && apply.contains(mouseX, mouseY));
        drawButton(graphics, font, random, "Random", canUpdateSettings, canUpdateSettings && random.contains(mouseX, mouseY));
        renderDraftActions(graphics, font, layout, Page.SETTINGS, draftDirty, canUpdateSettings, mouseX, mouseY, screenWidth, screenHeight);
        Rect cancel = globalCancelButtonBounds(layout, Page.SETTINGS, screenWidth, screenHeight);
        int actionStatusWidth = Math.max(0, cancel.x() - field.x() - 8);
        drawClipped(
                graphics,
                font,
                !canUpdateSettings ? "Only Operators Can Update Server Settings." : draftDirty ? "Draft Changes Pending." : "No Unapplied Changes.",
                field.x(),
                cancel.y() + 5,
                actionStatusWidth,
                !canUpdateSettings ? 0xFFE07878 : draftDirty ? 0xFFD4DEE1 : 0xFF9FAEB4
        );

        Rect area = settingsListArea(layout, screenWidth, screenHeight);
        Rect enableAll = enableAllTargetsButtonBounds(layout, screenWidth, screenHeight);
        Rect disableAll = disableAllTargetsButtonBounds(layout, screenWidth, screenHeight);
        boolean allTargetsEnabled = countEnabledTargets(snapshot.getEnabledTargetsMask()) == CorruptionTarget.values().length;
        boolean noTargetsEnabled = countEnabledTargets(snapshot.getEnabledTargetsMask()) == 0;
        drawSectionTitle(graphics, font, "Target Areas", area.x(), area.y(), Math.max(30, enableAll.x() - area.x() - 4));
        drawButton(graphics, font, enableAll, "Enable", canUpdateSettings && !allTargetsEnabled, canUpdateSettings && !allTargetsEnabled && enableAll.contains(mouseX, mouseY));
        drawButton(graphics, font, disableAll, "Disable", canUpdateSettings && !noTargetsEnabled, canUpdateSettings && !noTargetsEnabled && disableAll.contains(mouseX, mouseY));
        CorruptionTarget[] targets = CorruptionTarget.values();
        int columns = targetColumnCount(area);
        int columnGap = columns > 1 ? 8 : 0;
        int columnWidth = Math.max(80, (area.width() - columnGap * (columns - 1)) / columns);
        int rowsPerColumn = Math.max(1, (targets.length + columns - 1) / columns);
        int rowHeight = targetRowHeight(area);
        boolean compactRows = rowHeight < TOGGLE_ROW_HEIGHT;
        int maxRows = Math.max(0, (area.height() - 14) / rowHeight);
        for (int i = 0; i < targets.length; i++) {
            int column = i / rowsPerColumn;
            int rowIndex = i % rowsPerColumn;
            if (rowIndex >= maxRows) {
                continue;
            }
            CorruptionTarget target = targets[i];
            boolean enabled = snapshot.isTargetEnabled(target);
            int rowX = area.x() + column * (columnWidth + columnGap);
            int y = area.y() + 14 + rowIndex * rowHeight;
            Rect row = new Rect(rowX, y - 2, columnWidth, rowHeight - 2);
            boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0x332A3940);
            }
            int boxX = rowX;
            int boxY = y + (compactRows ? 2 : 3);
            graphics.fill(boxX, boxY, boxX + 10, boxY + 10, enabled ? 0xFF5CA76E : 0xFF263136);
            graphics.fill(boxX + 1, boxY + 1, boxX + 9, boxY + 9, enabled ? 0xFF5CA76E : 0xFF101719);
            if (enabled) {
                ProtectedTextRenderer.drawString(graphics, "X", boxX + 2, boxY + 1, 0xFFEAF4F7);
            }
            int targetColor = !canUpdateSettings ? 0xFF76858A : enabled ? 0xFFEAF4F7 : 0xFF7B898E;
            int descriptionColor = !canUpdateSettings ? 0xFF5F6B70 : enabled ? 0xFF9AA8AD : 0xFF5F6B70;
            drawClipped(graphics, font, target.label(), rowX + 15, y + (compactRows ? 3 : 0), Math.max(20, columnWidth - 18), targetColor);
            if (!compactRows) {
                drawClipped(graphics, font, target.description(), rowX + 15, y + 11, Math.max(20, columnWidth - 18), descriptionColor);
            }
        }
    }

    private static void renderFun(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionStateSnapshot snapshot, int pendingAutoIntervalTicks, int pendingAutoAmount, int pendingSeedRandomizerIntervalTicks, boolean intervalEditing, String intervalEditText, boolean amountEditing, String amountEditText, boolean seedRandomizerEditing, String seedRandomizerEditText, int textCursor, int textSelectionAnchor, boolean draftDirty, boolean canUpdateSettings, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect intervalArea = funIntervalArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Auto Increase Interval", intervalArea.x(), intervalArea.y(), intervalArea.width());
        Rect intervalSlider = funIntervalSliderBounds(layout, screenWidth, screenHeight);
        Rect intervalInput = funIntervalInputBounds(layout, screenWidth, screenHeight);
        drawValueSlider(graphics, font, intervalSlider, intervalRatio(pendingAutoIntervalTicks), intervalLabel(pendingAutoIntervalTicks), mouseX, mouseY);
        drawTextField(graphics, font, intervalInput, intervalEditing ? intervalEditText : intervalLabel(pendingAutoIntervalTicks), canUpdateSettings && intervalEditing, canUpdateSettings && intervalEditing ? textCursor : 0, canUpdateSettings && intervalEditing ? textSelectionAnchor : 0);
        drawMarqueeClipped(graphics, font, "Off, seconds, or values like 30s, 5m, 2h.", intervalArea.x(), intervalSlider.y() + 18, intervalArea.width(), 0xFF9AA8AD, 0x464E544C);

        Rect amountArea = funAmountArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Increase Amount", amountArea.x(), amountArea.y(), amountArea.width());
        Rect amountSlider = funAmountSliderBounds(layout, screenWidth, screenHeight);
        Rect amountInput = funAmountInputBounds(layout, screenWidth, screenHeight);
        drawValueSlider(graphics, font, amountSlider, amountRatio(pendingAutoAmount), signedPercentLabel(pendingAutoAmount), mouseX, mouseY);
        drawTextField(graphics, font, amountInput, amountEditing ? amountEditText : signedPercentLabel(pendingAutoAmount), canUpdateSettings && amountEditing, canUpdateSettings && amountEditing ? textCursor : 0, canUpdateSettings && amountEditing ? textSelectionAnchor : 0);
        drawMarqueeClipped(graphics, font, "-100% to +100% per automatic step.", amountArea.x(), amountSlider.y() + 18, amountArea.width(), 0xFF9AA8AD, 0x46414D54);

        Rect seedArea = funSeedRandomizerArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Seed Shuffle", seedArea.x(), seedArea.y(), seedArea.width());
        Rect seedSlider = funSeedRandomizerSliderBounds(layout, screenWidth, screenHeight);
        Rect seedInput = funSeedRandomizerInputBounds(layout, screenWidth, screenHeight);
        drawValueSlider(graphics, font, seedSlider, intervalRatio(pendingSeedRandomizerIntervalTicks), intervalLabel(pendingSeedRandomizerIntervalTicks), mouseX, mouseY);
        drawTextField(graphics, font, seedInput, seedRandomizerEditing ? seedRandomizerEditText : intervalLabel(pendingSeedRandomizerIntervalTicks), canUpdateSettings && seedRandomizerEditing, canUpdateSettings && seedRandomizerEditing ? textCursor : 0, canUpdateSettings && seedRandomizerEditing ? textSelectionAnchor : 0);
        drawMarqueeClipped(graphics, font, "Randomizes the shared seed on this interval.", seedArea.x(), seedSlider.y() + 18, seedArea.width(), 0xFF9AA8AD, 0x46534544);

        Rect driftArea = funClientDriftArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Client Drift", driftArea.x(), driftArea.y(), driftArea.width());
        Rect driftButton = funClientDriftButtonBounds(layout, screenWidth, screenHeight);
        drawButton(graphics, font, driftButton, snapshot.isClientDriftEnabled() ? "On" : "Off", canUpdateSettings, canUpdateSettings && driftButton.contains(mouseX, mouseY));
        drawMarqueeClipped(graphics, font, "Each player gets private corruption patterns from the same shared seed.", driftArea.x(), driftButton.y() + 22, driftArea.width(), 0xFF9AA8AD, 0x46445246);

        Rect status = funStatusArea(layout, screenWidth, screenHeight);
        String interval = intervalLabel(pendingAutoIntervalTicks).toLowerCase();
        String autoText = pendingAutoIntervalTicks <= 0
                ? "Auto increase is disabled."
                : "Every " + interval + ", server changes corruption by " + signedPercentLabel(pendingAutoAmount) + ".";
        String seedText = pendingSeedRandomizerIntervalTicks <= 0
                ? "Seed Shuffle is disabled."
                : "Seed Shuffle changes the shared seed every " + intervalLabel(pendingSeedRandomizerIntervalTicks).toLowerCase() + ".";
        String driftText = snapshot.isClientDriftEnabled()
                ? "Client Drift is on, so players intentionally diverge."
                : "Client Drift is off; shared seed stays deterministic where inputs match.";
        Rect cancel = globalCancelButtonBounds(layout, Page.FUN, screenWidth, screenHeight);
        int statusWidth = Math.max(40, cancel.x() - status.x() - 8);
        drawWrapped(graphics, font, autoText + " " + seedText, status.x(), status.y(), statusWidth, 2, 0xFFC8D4D8);
        drawWrapped(graphics, font, driftText, status.x(), status.y() + 22, statusWidth, 2, 0xFF9AA8AD);

        renderDraftActions(graphics, font, layout, Page.FUN, draftDirty, canUpdateSettings, mouseX, mouseY, screenWidth, screenHeight);
        if (!canUpdateSettings) {
            Rect actions = globalCancelButtonBounds(layout, Page.FUN, screenWidth, screenHeight);
            int actionStatusWidth = Math.max(0, actions.x() - status.x() - 8);
            drawClipped(graphics, font, "Only Operators Can Update Server Settings.", status.x(), actions.y() + 5, actionStatusWidth, 0xFFE07878);
        }
    }

    private static void renderDraftActions(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, Page page, boolean draftDirty, boolean canUpdateSettings, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect cancel = globalCancelButtonBounds(layout, page, screenWidth, screenHeight);
        Rect apply = globalApplyButtonBounds(layout, page, screenWidth, screenHeight);
        drawButton(graphics, font, cancel, "Cancel", draftDirty, draftDirty && cancel.contains(mouseX, mouseY));
        drawButton(graphics, font, apply, "Apply", canUpdateSettings && draftDirty, canUpdateSettings && draftDirty && apply.contains(mouseX, mouseY));
    }

    private static void renderAchievements(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, int scroll, int resetPresses, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect area = achievementsArea(layout, screenWidth, screenHeight);
        Rect resetButton = achievementResetButtonBounds(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Achievements", area.x(), area.y(), Math.max(40, resetButton.x() - area.x() - 5));
        int remainingResetPresses = Math.max(1, 3 - resetPresses);
        String resetLabel = resetPresses <= 0 ? "Reset" : "Reset " + remainingResetPresses;
        drawButton(graphics, font, resetButton, resetLabel, true, resetButton.contains(mouseX, mouseY));
        drawWrapped(graphics, font, "All achievements require Survival with cheats disabled. Disqualified worlds show red.", area.x(), area.y() + 14, area.width(), 2, 0xFF9AA8AD);

        Rect list = achievementsListBounds(layout, screenWidth, screenHeight);
        int maxScroll = achievementsMaxScroll(layout, screenWidth, screenHeight);
        int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
        int bottom = list.y() + list.height();

        graphics.enableScissor(list.x(), list.y(), list.x() + list.width(), bottom);
        for (AchievementHitBox hitBox : achievementHitBoxes(layout, screenWidth, screenHeight, clampedScroll)) {
            CorruptionAchievementManager.Achievement achievement = hitBox.achievement();
            Rect row = hitBox.row();
            Rect pin = hitBox.pin();
            int rowBottom = row.y() + ACHIEVEMENT_ROW_HEIGHT;
            boolean unlocked = CorruptionAchievementManager.isUnlocked(achievement);
            boolean disqualified = CorruptionAchievementManager.isDisqualified(achievement);
            boolean pinned = CorruptionAchievementManager.isPinned(achievement);
            if (rowBottom >= list.y() && row.y() <= bottom && list.contains(mouseX, mouseY) && row.contains(mouseX, mouseY)) {
                graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0x332A3940);
            }
            if (rowBottom >= list.y() && row.y() <= bottom) {
                int iconColor = unlocked ? 0xFF83A3AC : disqualified ? 0xFF7A2F35 : 0xFF344047;
                graphics.fill(row.x(), row.y(), row.x() + 20, row.y() + 20, 0xFF101719);
                graphics.fill(row.x() + 1, row.y() + 1, row.x() + 19, row.y() + 19, iconColor);
                TextureAtlasSprite sprite = achievementIconSprite(achievement);
                graphics.blit(row.x() + 2, row.y() + 2, 0, 16, 16, sprite);
                if (!unlocked) {
                    graphics.fill(row.x() + 2, row.y() + 2, row.x() + 18, row.y() + 18, disqualified ? 0xAA3C0808 : 0x99000000);
                }
                int titleColor = unlocked ? 0xFFEAF4F7 : disqualified ? 0xFFD99A9A : 0xFF9AA8AD;
                int detailColor = unlocked ? 0xFFB8C8CD : disqualified ? 0xFF9D6262 : 0xFF667277;
                int textWidth = Math.max(20, pin.x() - row.x() - 29);
                drawMarqueeClipped(graphics, font, achievement.title(), row.x() + 25, row.y() + 1, textWidth, titleColor, achievement.id().hashCode());
                drawMarqueeClipped(graphics, font, achievement.description(), row.x() + 25, row.y() + 11, textWidth, detailColor, achievement.id().hashCode() ^ 0x445343);
                drawMarqueeClipped(graphics, font, CorruptionAchievementManager.statusText(achievement), row.x() + 25, row.y() + 21, textWidth, unlocked ? 0xFF8FD6A2 : disqualified ? 0xFFE07878 : 0xFF7BAAB3, achievement.id().hashCode() ^ 0x535441);
                drawButton(graphics, font, pin, pinned ? "On" : "Pin", true, pin.contains(mouseX, mouseY));
                if (!unlocked) {
                    int progressWidth = Math.max(1, pin.x() - row.x() - 25);
                    int fill = Math.round(progressWidth * CorruptionAchievementManager.progressRatio(achievement));
                    graphics.fill(row.x() + 25, row.y() + 31, row.x() + 25 + progressWidth, row.y() + 33, 0xFF263136);
                    graphics.fill(row.x() + 25, row.y() + 31, row.x() + 25 + fill, row.y() + 33, disqualified ? 0xFF8A3338 : 0xFF5CA7B2);
                }
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = area.x() + area.width() - 3;
            int trackHeight = Math.max(6, list.height());
            int thumbHeight = Math.max(8, trackHeight * list.height() / (list.height() + maxScroll));
            int thumbY = list.y() + (trackHeight - thumbHeight) * clampedScroll / maxScroll;
            graphics.fill(trackX, list.y(), trackX + 2, list.y() + trackHeight, 0xFF263136);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF83A3AC);
        }
    }

    private static void renderHudSettings(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionAchievementManager.HudCorner pinnedCorner, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect area = hudSettingsArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Pinned Awards HUD", area.x(), area.y(), area.width());
        drawWrapped(graphics, font, "Pinned achievements stay stacked vertically in the selected corner.", area.x(), area.y() + 14, area.width(), 2, 0xFF9AA8AD);
        for (CorruptionAchievementManager.HudCorner corner : CorruptionAchievementManager.HudCorner.values()) {
            Rect button = hudCornerButtonBounds(layout, screenWidth, screenHeight, corner);
            boolean selected = corner == pinnedCorner;
            drawButton(graphics, font, button, (selected ? "* " : "") + corner.label(), true, button.contains(mouseX, mouseY));
            if (selected) {
                graphics.fill(button.x(), button.y() + button.height() - 2, button.x() + button.width(), button.y() + button.height(), 0xFF8FD6A2);
            }
        }
    }

    public static void renderPinnedAchievements(GuiGraphics graphics, Font font, List<CorruptionAchievementManager.Achievement> achievements, CorruptionAchievementManager.HudCorner corner) {
        if (achievements == null || achievements.isEmpty()) {
            return;
        }
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int totalHeight = achievements.size() * PINNED_ROW_HEIGHT + Math.max(0, achievements.size() - 1) * PINNED_ROW_GAP;
        int margin = 8;
        int x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - PINNED_ROW_WIDTH - margin;
        };
        int y = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - totalHeight - margin;
        };
        x = Math.max(margin, Math.min(screenWidth - PINNED_ROW_WIDTH - margin, x));
        y = Math.max(margin, Math.min(screenHeight - totalHeight - margin, y));

        for (CorruptionAchievementManager.Achievement achievement : achievements) {
            renderPinnedAchievement(graphics, font, achievement, x, y);
            y += PINNED_ROW_HEIGHT + PINNED_ROW_GAP;
        }
    }

    public static int topRightPinnedToastOffset() {
        List<CorruptionAchievementManager.Achievement> achievements = CorruptionAchievementManager.pinnedAchievements();
        if (achievements.isEmpty() || CorruptionAchievementManager.pinnedCorner() != CorruptionAchievementManager.HudCorner.TOP_RIGHT) {
            return 0;
        }
        return 8 + achievements.size() * PINNED_ROW_HEIGHT + Math.max(0, achievements.size() - 1) * PINNED_ROW_GAP + PINNED_ROW_GAP;
    }

    private static void renderPinnedAchievement(GuiGraphics graphics, Font font, CorruptionAchievementManager.Achievement achievement, int x, int y) {
        boolean unlocked = CorruptionAchievementManager.isUnlocked(achievement);
        boolean disqualified = CorruptionAchievementManager.isDisqualified(achievement);
        int fillColor = unlocked ? 0xDD17251D : disqualified ? 0xDD251113 : 0xDD101719;
        int edgeColor = unlocked ? 0xFF6FA67D : disqualified ? 0xFF7A2F35 : 0xFF52636A;
        graphics.fill(x, y, x + PINNED_ROW_WIDTH, y + PINNED_ROW_HEIGHT, fillColor);
        graphics.fill(x, y, x + PINNED_ROW_WIDTH, y + 1, edgeColor);
        graphics.fill(x, y + PINNED_ROW_HEIGHT - 1, x + PINNED_ROW_WIDTH, y + PINNED_ROW_HEIGHT, edgeColor);
        graphics.fill(x, y, x + 1, y + PINNED_ROW_HEIGHT, edgeColor);
        graphics.fill(x + PINNED_ROW_WIDTH - 1, y, x + PINNED_ROW_WIDTH, y + PINNED_ROW_HEIGHT, edgeColor);

        graphics.fill(x + 4, y + 4, x + 24, y + 24, 0xFF101719);
        graphics.fill(x + 5, y + 5, x + 23, y + 23, unlocked ? 0xFF83A3AC : disqualified ? 0xFF7A2F35 : 0xFF344047);
        TextureAtlasSprite sprite = achievementIconSprite(achievement);
        graphics.blit(x + 6, y + 6, 0, 16, 16, sprite);
        if (!unlocked) {
            graphics.fill(x + 6, y + 6, x + 22, y + 22, disqualified ? 0xAA3C0808 : 0x99000000);
        }

        int textX = x + 29;
        int textWidth = PINNED_ROW_WIDTH - 34;
        int titleColor = unlocked ? 0xFFEAF4F7 : disqualified ? 0xFFD99A9A : 0xFFC8D4D8;
        int statusColor = unlocked ? 0xFF8FD6A2 : disqualified ? 0xFFE07878 : 0xFF7BAAB3;
        drawClipped(graphics, font, achievement.title(), textX, y + 4, textWidth, titleColor);
        drawMarqueeClipped(graphics, font, unlocked ? "Unlocked" : disqualified ? CorruptionAchievementManager.statusText(achievement) : CorruptionAchievementManager.progressLabel(achievement), textX, y + 14, textWidth, statusColor, achievement.id().hashCode() ^ 0x535441);
        int barWidth = Math.max(1, textWidth);
        int barFill = Math.round(barWidth * CorruptionAchievementManager.progressRatio(achievement));
        graphics.fill(textX, y + 25, textX + barWidth, y + 27, 0xFF263136);
        graphics.fill(textX, y + 25, textX + barFill, y + 27, unlocked ? 0xFF8FD6A2 : disqualified ? 0xFF8A3338 : 0xFF5CA7B2);
    }

    private static boolean shouldShowHighLevelWarning(int pendingLevel) {
        return pendingLevel >= HIGH_LEVEL_WARNING_THRESHOLD;
    }

    private static TextureAtlasSprite achievementIconSprite(CorruptionAchievementManager.Achievement achievement) {
        return ACHIEVEMENT_ICON_CACHE.computeIfAbsent(achievement.icon(), block -> Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(block.defaultBlockState())
                .getParticleIcon(ModelData.EMPTY));
    }

    private static Rect settingsListArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        Rect actions = globalCancelButtonBounds(layout, Page.SETTINGS, screenWidth, screenHeight);
        int top = seedFieldBounds(layout, screenWidth, screenHeight).y() + 30;
        int bottom = Math.min(panel.y() + panel.height() - 10, actions.y() - 8);
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(30, bottom - top));
    }

    private static Rect funIntervalArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect content = funContentArea(layout, screenWidth, screenHeight);
        if (content.width() < 260) {
            return new Rect(content.x(), content.y(), content.width(), 64);
        }
        Rect column = funColumn(content, 0);
        return new Rect(column.x(), column.y(), column.width(), 64);
    }

    private static Rect funAmountArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect content = funContentArea(layout, screenWidth, screenHeight);
        if (content.width() < 260) {
            return new Rect(content.x(), content.y() + 78, content.width(), 64);
        }
        Rect column = funColumn(content, 0);
        return new Rect(column.x(), column.y() + 78, column.width(), 64);
    }

    private static Rect funSeedRandomizerArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect content = funContentArea(layout, screenWidth, screenHeight);
        if (content.width() < 260) {
            return new Rect(content.x(), content.y() + 156, content.width(), 64);
        }
        Rect column = funColumn(content, 1);
        return new Rect(column.x(), column.y(), column.width(), 64);
    }

    private static Rect funClientDriftArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect content = funContentArea(layout, screenWidth, screenHeight);
        if (content.width() < 260) {
            return new Rect(content.x(), content.y() + 234, content.width(), 64);
        }
        Rect column = funColumn(content, 1);
        return new Rect(column.x(), column.y() + 78, column.width(), 64);
    }

    private static Rect funStatusArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect content = funContentArea(layout, screenWidth, screenHeight);
        int top = content.y() + (content.width() < 260 ? 312 : 156);
        int bottom = content.y() + content.height();
        return new Rect(content.x(), top, content.width(), Math.max(44, bottom - top));
    }

    private static Rect funContentArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        Rect actions = globalCancelButtonBounds(layout, Page.FUN, screenWidth, screenHeight);
        int top = panel.y() + CONTENT_TOP_OFFSET;
        int bottom = Math.min(panel.y() + panel.height() - 10, actions.y() - 8);
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(1, bottom - top));
    }

    private static Rect funColumn(Rect content, int index) {
        if (content.width() < 260) {
            return content;
        }
        int gap = 12;
        int width = Math.max(80, (content.width() - gap) / 2);
        int x = index <= 0 ? content.x() : content.x() + width + gap;
        return new Rect(x, content.y(), width, content.height());
    }

    private static Rect achievementsArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int top = panel.y() + CONTENT_TOP_OFFSET;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(30, panel.y() + panel.height() - top - 10));
    }

    private static Rect hudSettingsArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int top = panel.y() + CONTENT_TOP_OFFSET;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(80, panel.y() + panel.height() - top - 10));
    }

    private static int targetColumnCount(Rect area) {
        int maxColumns = area.width() >= 360 ? 4 : area.width() >= 270 ? 3 : area.width() >= 180 ? 2 : 1;
        int rows = Math.max(1, (area.height() - 14) / targetRowHeight(area));
        int neededColumns = Math.max(1, (CorruptionTarget.values().length + rows - 1) / rows);
        return Math.max(1, Math.min(maxColumns, neededColumns));
    }

    private static int targetRowHeight(Rect area) {
        int fullRows = Math.max(1, (area.height() - 14) / TOGGLE_ROW_HEIGHT);
        int fullColumns = area.width() >= 250 ? 2 : 1;
        if (fullRows * fullColumns >= CorruptionTarget.values().length) {
            return TOGGLE_ROW_HEIGHT;
        }
        return 18;
    }

    private static int seedButtonWidth(Rect panel) {
        return panel.width() < 320 ? 36 : SEED_BUTTON_WIDTH;
    }

    private static int seedRandomButtonWidth(Rect panel) {
        return panel.width() < 320 ? 46 : SEED_RANDOM_BUTTON_WIDTH;
    }

    private static void fillPanel(GuiGraphics graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), 0xE911171A);
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, 0xFF6C7A80);
        graphics.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), 0xFF6C7A80);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.height(), 0xFF6C7A80);
        graphics.fill(rect.x() + rect.width() - 1, rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), 0xFF6C7A80);
    }

    private static void renderResizeHandles(GuiGraphics graphics, CorruptionOverlayLayout layout, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect horizontal = horizontalResizeBounds(layout, screenWidth, screenHeight);
        Rect vertical = verticalResizeBounds(layout, screenWidth, screenHeight);
        Rect corner = cornerResizeBounds(layout, screenWidth, screenHeight);
        int edge = 0x8846575E;
        int hover = 0xCC83A3AC;
        graphics.fill(horizontal.x() + horizontal.width() - 2, horizontal.y(), horizontal.x() + horizontal.width(), horizontal.y() + horizontal.height(), horizontal.contains(mouseX, mouseY) ? hover : edge);
        graphics.fill(vertical.x(), vertical.y() + vertical.height() - 2, vertical.x() + vertical.width(), vertical.y() + vertical.height(), vertical.contains(mouseX, mouseY) ? hover : edge);
        int fill = corner.contains(mouseX, mouseY) ? hover : 0xAA6C7A80;
        int lineCount = Math.max(1, Math.min(3, corner.width() / 3));
        for (int line = 0; line < lineCount; line++) {
            int inset = 2 + line * 3;
            graphics.fill(corner.x() + inset, corner.y() + corner.height() - 2 - line * 3, corner.x() + corner.width(), corner.y() + corner.height() - line * 3, fill);
        }
    }

    private static void drawControl(GuiGraphics graphics, Font font, Rect rect, String label, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), hovered ? 0xFF344247 : 0xFF20292D);
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, 0xFF6C7A80);
        ProtectedTextRenderer.drawString(graphics, label, rect.x() + rect.width() / 2 - ProtectedTextRenderer.width(label) / 2, rect.y() + 3, 0xFFEAF4F7);
    }

    private static void drawTab(GuiGraphics graphics, Font font, Rect rect, String label, boolean selected, int mouseX, int mouseY) {
        int fill = selected ? 0xFF344247 : rect.contains(mouseX, mouseY) ? 0xFF29363A : 0xFF1B2225;
        int edge = selected ? 0xFF83A3AC : 0xFF465157;
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), fill);
        graphics.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), edge);
        ProtectedTextRenderer.drawString(graphics, label, rect.x() + rect.width() / 2 - ProtectedTextRenderer.width(label) / 2, rect.y() + 4, selected ? 0xFFEAF4F7 : 0xFFB8C8CD);
    }

    private static void drawButton(GuiGraphics graphics, Font font, Rect rect, String label, boolean enabled, boolean hovered) {
        int fill = !enabled ? 0xFF1B2225 : hovered ? 0xFF3D4E54 : 0xFF29363A;
        int edge = enabled ? 0xFF7D8A8F : 0xFF465157;
        int text = enabled ? 0xFFEAF4F7 : 0xFF76858A;
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), fill);
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, edge);
        graphics.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), edge);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.height(), edge);
        graphics.fill(rect.x() + rect.width() - 1, rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), edge);
        ProtectedTextRenderer.drawString(graphics, label, rect.x() + rect.width() / 2 - ProtectedTextRenderer.width(label) / 2, rect.y() + 4, text);
    }

    private static void drawValueSlider(GuiGraphics graphics, Font font, Rect slider, float ratio, String valueLabel, int mouseX, int mouseY) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        drawClipped(graphics, font, valueLabel, slider.x(), slider.y() - 12, slider.width(), 0xFFEAF4F7);
        graphics.fill(slider.x(), slider.y() + 4, slider.x() + slider.width(), slider.y() + 8, 0xFF263136);
        int handleX = slider.x() + Math.round(slider.width() * clamped);
        graphics.fill(slider.x(), slider.y() + 4, handleX, slider.y() + 8, 0xFF5CA7B2);
        graphics.fill(handleX - 3, slider.y(), handleX + 4, slider.y() + 12, sliderInteractionBounds(slider).contains(mouseX, mouseY) ? 0xFFEAF4F7 : 0xFFB8C8CD);
    }

    private static float intervalRatio(int ticks) {
        return Math.max(0.0F, Math.min(1.0F, ticks / (float) MAX_AUTO_INTERVAL_TICKS));
    }

    private static float amountRatio(int amount) {
        return (Math.max(MIN_AUTO_AMOUNT, Math.min(MAX_AUTO_AMOUNT, amount)) - MIN_AUTO_AMOUNT) / (float) (MAX_AUTO_AMOUNT - MIN_AUTO_AMOUNT);
    }

    private static String intervalLabel(int ticks) {
        if (ticks <= 0) {
            return "Off";
        }
        int seconds = Math.max(1, Math.round(ticks / 20.0F));
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        if (minutes < 60) {
            return remainingSeconds == 0 ? minutes + "m" : minutes + "m " + remainingSeconds + "s";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        return remainingMinutes == 0 ? hours + "h" : hours + "h " + remainingMinutes + "m";
    }

    private static String signedPercentLabel(int amount) {
        return (amount > 0 ? "+" : "") + amount + "%";
    }

    private static void drawTextField(GuiGraphics graphics, Font font, Rect rect, String text, boolean active, int cursor, int selectionAnchor) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), active ? 0xFF172126 : 0xFF101719);
        int edge = active ? 0xFF83A3AC : 0xFF465157;
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, edge);
        graphics.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), edge);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.height(), edge);
        graphics.fill(rect.x() + rect.width() - 1, rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), edge);

        String value = text == null ? "" : text;
        int textX = rect.x() + 4;
        int textY = rect.y() + 5;
        int textWidth = rect.width() - 8;
        if (!active) {
            drawClipped(graphics, font, value, textX, textY, textWidth, 0xFFC8D4D8);
            return;
        }

        int cursorIndex = clampIndex(cursor, value.length());
        int anchorIndex = clampIndex(selectionAnchor, value.length());
        int visibleStart = textFieldVisibleStart(value, cursorIndex, textWidth);
        String visibleText = visibleStart >= value.length() ? "" : ProtectedTextRenderer.plainSubstrByWidth(value.substring(visibleStart), textWidth);
        int visibleEnd = visibleStart + visibleText.length();
        int selectionStart = Math.min(cursorIndex, anchorIndex);
        int selectionEnd = Math.max(cursorIndex, anchorIndex);

        graphics.enableScissor(textX, textY - 1, textX + textWidth, textY + ProtectedTextRenderer.LINE_HEIGHT + 1);
        if (selectionStart < selectionEnd && selectionEnd > visibleStart && selectionStart < visibleEnd) {
            int highlightStartIndex = Math.max(selectionStart, visibleStart);
            int highlightEndIndex = Math.min(selectionEnd, visibleEnd);
            int highlightX = textX + ProtectedTextRenderer.width(value.substring(visibleStart, highlightStartIndex));
            int highlightEndX = textX + ProtectedTextRenderer.width(value.substring(visibleStart, highlightEndIndex));
            graphics.fill(highlightX, textY - 1, Math.max(highlightX + 1, highlightEndX), textY + ProtectedTextRenderer.LINE_HEIGHT, 0xAA5CA7B2);
        }

        ProtectedTextRenderer.drawString(graphics, visibleText, textX, textY, 0xFFEAF4F7);
        if (System.currentTimeMillis() / 500L % 2L == 0L) {
            int caretIndex = Math.max(visibleStart, Math.min(cursorIndex, visibleEnd));
            int caretX = textX + ProtectedTextRenderer.width(value.substring(visibleStart, caretIndex));
            graphics.fill(caretX, textY - 1, caretX + 1, textY + ProtectedTextRenderer.LINE_HEIGHT, 0xFFEAF4F7);
        }
        graphics.disableScissor();
    }

    private static void drawLabelValue(GuiGraphics graphics, Font font, String label, String value, int x, int y, int width) {
        String text = label + ": " + value;
        drawClipped(graphics, font, text, x, y, width, 0xFFC8D4D8);
    }

    private static int textFieldVisibleStart(String text, int cursor, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0 || ProtectedTextRenderer.width(text) <= maxWidth) {
            return 0;
        }
        int clampedCursor = clampIndex(cursor, text.length());
        String throughCursor = text.substring(0, clampedCursor);
        while (ProtectedTextRenderer.width(throughCursor) > maxWidth && !throughCursor.isEmpty()) {
            throughCursor = throughCursor.substring(1);
        }
        return clampedCursor - throughCursor.length();
    }

    private static int clampIndex(int index, int length) {
        return Math.max(0, Math.min(length, index));
    }

    private static void drawSectionTitle(GuiGraphics graphics, Font font, String title, int x, int y, int width) {
        drawClipped(graphics, font, title, x, y, width, 0xFFEAF4F7);
        graphics.fill(x, y + 10, x + width, y + 11, 0xFF314047);
    }

    private static void drawClipped(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return;
        }
        String clipped = text;
        if (ProtectedTextRenderer.width(clipped) > maxWidth) {
            int ellipsisWidth = ProtectedTextRenderer.width("...");
            clipped = ProtectedTextRenderer.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth));
            if (clipped.length() < text.length()) {
                clipped = clipped + "...";
            }
        }
        ProtectedTextRenderer.drawString(graphics, clipped, x, y, color);
    }

    private static void drawMarqueeClipped(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int color, int salt) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return;
        }
        int textWidth = ProtectedTextRenderer.width(text);
        if (textWidth <= maxWidth) {
            ProtectedTextRenderer.drawString(graphics, text, x, y, color);
            return;
        }

        int overflow = textWidth - maxWidth;
        int holdMs = 900;
        int travelMs = Math.max(1200, overflow * 55);
        int endHoldMs = 700;
        int cycleMs = holdMs + travelMs + endHoldMs;
        long phase = Math.floorMod(System.currentTimeMillis() + Math.floorMod(salt, cycleMs), cycleMs);
        int offset;
        if (phase < holdMs) {
            offset = 0;
        } else if (phase < holdMs + travelMs) {
            offset = Math.round(overflow * ((phase - holdMs) / (float) travelMs));
        } else {
            offset = overflow;
        }

        graphics.enableScissor(x, y, x + maxWidth, y + ProtectedTextRenderer.LINE_HEIGHT);
        ProtectedTextRenderer.drawString(graphics, text, x - offset, y, color);
        graphics.disableScissor();
    }

    private static int drawWrapped(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int maxLines, int color) {
        if (maxWidth <= 0 || maxLines <= 0 || text == null || text.isEmpty()) {
            return 0;
        }
        List<String> lines = ProtectedTextRenderer.wrap(text, maxWidth, maxLines);
        int drawn = Math.min(maxLines, lines.size());
        for (int i = 0; i < drawn; i++) {
            ProtectedTextRenderer.drawString(graphics, lines.get(i), x, y + i * ProtectedTextRenderer.LINE_HEIGHT, color);
        }
        return drawn * ProtectedTextRenderer.LINE_HEIGHT;
    }

    private static int countEnabledTargets(int mask) {
        int count = 0;
        for (CorruptionTarget target : CorruptionTarget.values()) {
            if (CorruptionTarget.enabled(mask, target)) {
                count++;
            }
        }
        return count;
    }

    public enum Page {
        CONTROL,
        SETTINGS,
        FUN,
        ACHIEVEMENTS,
        HUD
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record TargetHitBox(CorruptionTarget target, Rect rect) {
    }

    public record AchievementHitBox(CorruptionAchievementManager.Achievement achievement, Rect row, Rect pin) {
    }
}
