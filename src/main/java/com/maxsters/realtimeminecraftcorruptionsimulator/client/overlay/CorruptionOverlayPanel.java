package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.calibration.StabilityDebtCalculator;
import com.maxsters.realtimeminecraftcorruptionsimulator.logs.CorruptionLogManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class CorruptionOverlayPanel {
    public static final int HEADER_HEIGHT = 20;
    public static final int CONTROL_SIZE = 14;
    public static final int COLLAPSED_SIZE = 28;
    public static final int APPLY_BUTTON_WIDTH = 74;
    public static final int APPLY_BUTTON_HEIGHT = 16;
    private static final int TAB_WIDTH = 72;
    private static final int TAB_HEIGHT = 16;
    private static final int SEED_BUTTON_WIDTH = 64;
    private static final int TOGGLE_ROW_HEIGHT = 28;
    private static final int WARNING_HEIGHT = 28;
    private static final int WARNING_GAP = 8;
    private static final int RESIZE_HANDLE_SIZE = 10;
    public static final int MAX_AUTO_INTERVAL_TICKS = 144_000;
    public static final int MIN_AUTO_AMOUNT = -100;
    public static final int MAX_AUTO_AMOUNT = 100;

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

    public static Rect sliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int sliderY = panel.y() + 96;
        return new Rect(panel.x() + 12, sliderY, Math.max(80, panel.width() - 24), 12);
    }

    public static Rect applyButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect slider = sliderBounds(layout, screenWidth, screenHeight);
        return new Rect(slider.x() + slider.width() - APPLY_BUTTON_WIDTH, slider.y() + 19, APPLY_BUTTON_WIDTH, APPLY_BUTTON_HEIGHT);
    }

    public static Rect seedFieldBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int y = panel.y() + 60;
        int width = Math.max(88, panel.width() - 24 - SEED_BUTTON_WIDTH * 2 - 12);
        return new Rect(panel.x() + 12, y, width, 16);
    }

    public static Rect seedApplyButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect field = seedFieldBounds(layout, screenWidth, screenHeight);
        return new Rect(field.x() + field.width() + 6, field.y(), SEED_BUTTON_WIDTH, 16);
    }

    public static Rect randomSeedButtonBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect apply = seedApplyButtonBounds(layout, screenWidth, screenHeight);
        return new Rect(apply.x() + apply.width() + 6, apply.y(), SEED_BUTTON_WIDTH, 16);
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
        return new Rect(area.x(), area.y() + 24, area.width(), 12);
    }

    public static Rect funAmountSliderBounds(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect area = funAmountArea(layout, screenWidth, screenHeight);
        return new Rect(area.x(), area.y() + 24, area.width(), 12);
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

    public static void renderOpen(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int pendingAutoIntervalTicks, int pendingAutoAmount, Page page, boolean seedEditing, String seedEditText, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, panel);
        renderHeader(graphics, font, layout, snapshot, panel, mouseX, mouseY, false);
        renderTabs(graphics, font, layout, page, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        if (page == Page.SETTINGS) {
            renderSettings(graphics, font, layout, snapshot, seedEditing, seedEditText, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else if (page == Page.FUN) {
            renderFun(graphics, font, layout, snapshot, pendingAutoIntervalTicks, pendingAutoAmount, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        } else {
            renderControl(graphics, font, layout, snapshot, pendingLevel, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        }
        renderResizeHandles(graphics, layout, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
    }

    public static void renderMinimized(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, panel);
        renderHeader(graphics, font, layout, snapshot, panel, mouseX, mouseY, true);
        int y = panel.y() + 21;
        int maxWidth = panel.width() - 16;
        String summary = "corruption " + snapshot.getCorruptionLevel() + "%  seed " + snapshot.getCorruptionSeedLabel();
        drawClipped(graphics, font, summary, panel.x() + 8, y, maxWidth, 0xFFD4DEE1);
    }

    public static void renderCollapsed(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int mouseX, int mouseY) {
        Rect panel = panelBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        CorruptionOverlayButton.render(graphics, font, panel.x(), panel.y(), COLLAPSED_SIZE, snapshot, panel.contains(mouseX, mouseY));
    }

    public static void renderHudStrip(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot) {
        if (layout.mode() == CorruptionOverlayLayout.Mode.COLLAPSED) {
            CorruptionOverlayButton.render(graphics, font, layout.x(), layout.y(), COLLAPSED_SIZE, snapshot, false);
            return;
        }
        if (layout.mode() == CorruptionOverlayLayout.Mode.MINIMIZED) {
            Rect strip = new Rect(layout.x(), layout.y(), Math.min(layout.displayedWidth(graphics.guiWidth()), graphics.guiWidth() - 8), 24);
            fillPanel(graphics, strip);
            String text = "RMCS " + snapshot.getCorruptionLevel() + "% | seed " + snapshot.getCorruptionSeedLabel();
            drawClipped(graphics, font, text, strip.x() + 6, strip.y() + 8, strip.width() - 12, 0xFFD4DEE1);
        }
    }

    private static void renderHeader(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, Rect panel, int mouseX, int mouseY, boolean minimized) {
        graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + HEADER_HEIGHT, 0xF0263034);
        graphics.fill(panel.x(), panel.y() + HEADER_HEIGHT - 1, panel.x() + panel.width(), panel.y() + HEADER_HEIGHT, 0xFF52636A);
        String title = minimized ? "Realtime Corruption Simulator" : "Realtime Minecraft Corruption Simulator";
        drawClipped(graphics, font, title, panel.x() + 8, panel.y() + 6, panel.width() - 52, 0xFFEAF4F7);

        Rect min = minimizeButtonBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        Rect close = collapseButtonBounds(layout, graphics.guiWidth(), graphics.guiHeight());
        drawControl(graphics, font, min, minimized ? "+" : "_", min.contains(mouseX, mouseY));
        drawControl(graphics, font, close, "x", close.contains(mouseX, mouseY));
    }

    private static void renderTabs(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, Page page, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        drawTab(graphics, font, controlTabBounds(layout, screenWidth, screenHeight), "Control", page == Page.CONTROL, mouseX, mouseY);
        drawTab(graphics, font, settingsTabBounds(layout, screenWidth, screenHeight), "Settings", page == Page.SETTINGS, mouseX, mouseY);
        drawTab(graphics, font, funTabBounds(layout, screenWidth, screenHeight), "Fun", page == Page.FUN, mouseX, mouseY);
    }

    private static void renderControl(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        renderReadouts(graphics, font, panel, snapshot, pendingLevel);
        renderSlider(graphics, font, layout, snapshot, pendingLevel, mouseX, mouseY, screenWidth, screenHeight);
        renderApplyControl(graphics, font, layout, snapshot, pendingLevel, mouseX, mouseY, screenWidth, screenHeight);
        renderStatus(graphics, font, layout, snapshot, pendingLevel, screenWidth, screenHeight);
    }

    private static void renderReadouts(GuiGraphics graphics, Font font, Rect panel, CorruptionProfileSnapshot snapshot, int pendingLevel) {
        int x = panel.x() + 12;
        int y = panel.y() + 50;
        int leftWidth = Math.max(120, (panel.width() - 30) / 2);
        int rightX = x + leftWidth + 8;
        int rightWidth = Math.max(90, panel.width() - (rightX - panel.x()) - 12);

        drawLabelValue(graphics, font, "Active corruption", snapshot.getCorruptionLevel() + "%", x, y, leftWidth);
        drawLabelValue(graphics, font, "Selected level", pendingLevel + "%", x, y + 12, leftWidth);
        drawLabelValue(graphics, font, "Seed", snapshot.getCorruptionSeedLabel(), x, y + 24, leftWidth);

        drawLabelValue(graphics, font, "Update mode", "live", rightX, y, rightWidth);
        drawLabelValue(graphics, font, "Targets", countEnabledTargets(snapshot.getEnabledTargetsMask()) + "/" + CorruptionTarget.values().length, rightX, y + 12, rightWidth);
        drawLabelValue(graphics, font, "Last change", snapshot.getCorruptionDelta() + "%", rightX, y + 24, rightWidth);
    }

    private static void renderSlider(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect slider = sliderBounds(layout, screenWidth, screenHeight);
        int labelY = slider.y() - 12;
        int delta = Math.abs(pendingLevel - snapshot.getCorruptionLevel());
        String label = "Corruption level: " + pendingLevel + "%  change " + delta + "%";
        drawClipped(graphics, font, label, slider.x(), labelY, slider.width(), 0xFFEAF4F7);

        graphics.fill(slider.x(), slider.y() + 4, slider.x() + slider.width(), slider.y() + 8, 0xFF263136);
        int fillWidth = Math.max(2, slider.width() * pendingLevel / 100);
        graphics.fill(slider.x(), slider.y() + 4, slider.x() + fillWidth, slider.y() + 8, 0xFF5CA7B2);
        int handleX = slider.x() + slider.width() * pendingLevel / 100;
        graphics.fill(handleX - 3, slider.y(), handleX + 4, slider.y() + 12, slider.contains(mouseX, mouseY) ? 0xFFEAF4F7 : 0xFFB8C8CD);

        int calibratedRange = StabilityDebtCalculator.getCalibratedRange(snapshot.getCalibrationConfidence());
        if (delta > calibratedRange) {
            int warningY = slider.y() + 38;
            graphics.fill(slider.x(), warningY, slider.x() + slider.width(), warningY + WARNING_HEIGHT, 0xCC2B2417);
            drawClipped(graphics, font, "Large value changes can cause lag spikes.", slider.x() + 5, warningY + 5, slider.width() - 10, 0xFFFFD58A);
            drawClipped(graphics, font, "Texture, model, sound, and chunk state may rebuild.", slider.x() + 5, warningY + 16, slider.width() - 10, 0xFFE7D5B0);
        }
    }

    private static void renderApplyControl(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect slider = sliderBounds(layout, screenWidth, screenHeight);
        Rect button = applyButtonBounds(layout, screenWidth, screenHeight);
        boolean enabled = pendingLevel != snapshot.getCorruptionLevel();
        boolean hovered = enabled && button.contains(mouseX, mouseY);
        drawButton(graphics, font, button, "Apply now", enabled, hovered);

        int textWidth = Math.max(0, button.x() - slider.x() - 8);
        String status = enabled ? "Applies to every connected player." : "This level is active.";
        drawClipped(graphics, font, status, slider.x(), slider.y() + 23, textWidth, enabled ? 0xFFD4DEE1 : 0xFF9FAEB4);
    }

    private static void renderStatus(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int screenWidth, int screenHeight) {
        Rect area = statusArea(layout, snapshot, pendingLevel, screenWidth, screenHeight);
        if (area.width() <= 0 || area.height() <= 0) {
            return;
        }
        drawSectionTitle(graphics, font, "Runtime Status", area.x(), area.y(), area.width());
        List<String> logs = CorruptionLogManager.buildStatusLines(snapshot);
        int y = area.y() + 13;
        int bottom = area.y() + area.height();
        for (String log : logs) {
            int height = wrappedHeight(font, log, area.width(), 2);
            if (y + height > bottom) {
                break;
            }
            y += drawWrapped(graphics, font, log, area.x(), y, area.width(), 2, 0xFFC8D4D8) + 2;
        }
    }

    private static void renderSettings(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, boolean seedEditing, String seedEditText, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect field = seedFieldBounds(layout, screenWidth, screenHeight);
        Rect apply = seedApplyButtonBounds(layout, screenWidth, screenHeight);
        Rect random = randomSeedButtonBounds(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Seed", field.x(), field.y() - 14, Math.max(40, random.x() + random.width() - field.x()));
        drawTextField(graphics, font, field, seedEditing ? seedEditText : snapshot.getCorruptionSeedLabel(), seedEditing);
        drawButton(graphics, font, apply, "Set", seedEditing, seedEditing && apply.contains(mouseX, mouseY));
        drawButton(graphics, font, random, "Random", true, random.contains(mouseX, mouseY));

        Rect area = settingsListArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Target Areas", area.x(), area.y(), area.width());
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
            drawClipped(graphics, font, target.label(), rowX + 15, y + (compactRows ? 3 : 0), Math.max(20, columnWidth - 18), enabled ? 0xFFEAF4F7 : 0xFF7B898E);
            if (!compactRows) {
                drawClipped(graphics, font, target.description(), rowX + 15, y + 11, Math.max(20, columnWidth - 18), enabled ? 0xFF9AA8AD : 0xFF5F6B70);
            }
        }
    }

    private static void renderFun(GuiGraphics graphics, Font font, CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingAutoIntervalTicks, int pendingAutoAmount, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Rect intervalArea = funIntervalArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Auto Increase Interval", intervalArea.x(), intervalArea.y(), intervalArea.width());
        Rect intervalSlider = funIntervalSliderBounds(layout, screenWidth, screenHeight);
        drawValueSlider(graphics, font, intervalSlider, intervalRatio(pendingAutoIntervalTicks), intervalLabel(pendingAutoIntervalTicks), mouseX, mouseY);
        drawClipped(graphics, font, "Off to 2h between automatic level changes.", intervalArea.x(), intervalSlider.y() + 18, intervalArea.width(), 0xFF9AA8AD);

        Rect amountArea = funAmountArea(layout, screenWidth, screenHeight);
        drawSectionTitle(graphics, font, "Increase Amount", amountArea.x(), amountArea.y(), amountArea.width());
        Rect amountSlider = funAmountSliderBounds(layout, screenWidth, screenHeight);
        drawValueSlider(graphics, font, amountSlider, amountRatio(pendingAutoAmount), signedPercentLabel(pendingAutoAmount), mouseX, mouseY);
        drawClipped(graphics, font, "-100% to +100% per automatic step.", amountArea.x(), amountSlider.y() + 18, amountArea.width(), 0xFF9AA8AD);

        Rect status = funStatusArea(layout, screenWidth, screenHeight);
        String interval = intervalLabel(pendingAutoIntervalTicks).toLowerCase();
        String text = pendingAutoIntervalTicks <= 0
                ? "Auto increase is disabled."
                : "Every " + interval + ", server changes corruption by " + signedPercentLabel(pendingAutoAmount) + ".";
        drawWrapped(graphics, font, text, status.x(), status.y(), status.width(), 2, 0xFFC8D4D8);
        drawWrapped(graphics, font, "Settings are server-owned and broadcast to connected players.", status.x(), status.y() + 22, status.width(), 2, 0xFF9AA8AD);
    }

    private static Rect statusArea(CorruptionOverlayLayout layout, CorruptionProfileSnapshot snapshot, int pendingLevel, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int delta = Math.abs(pendingLevel - snapshot.getCorruptionLevel());
        int warningOffset = delta > StabilityDebtCalculator.getCalibratedRange(snapshot.getCalibrationConfidence()) ? WARNING_HEIGHT + WARNING_GAP : 0;
        int top = panel.y() + 142 + warningOffset;
        int bottom = panel.y() + panel.height() - 10;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(30, bottom - top));
    }

    private static Rect settingsListArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int top = panel.y() + 98;
        int bottom = panel.y() + panel.height() - 10;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(30, bottom - top));
    }

    private static Rect funIntervalArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + 12, panel.y() + 60, panel.width() - 24, 64);
    }

    private static Rect funAmountArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        return new Rect(panel.x() + 12, panel.y() + 138, panel.width() - 24, 64);
    }

    private static Rect funStatusArea(CorruptionOverlayLayout layout, int screenWidth, int screenHeight) {
        Rect panel = panelBounds(layout, screenWidth, screenHeight);
        int top = panel.y() + 194;
        return new Rect(panel.x() + 12, top, panel.width() - 24, Math.max(32, panel.y() + panel.height() - top - 10));
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
        for (int line = 0; line < 3; line++) {
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
        graphics.fill(handleX - 3, slider.y(), handleX + 4, slider.y() + 12, slider.contains(mouseX, mouseY) ? 0xFFEAF4F7 : 0xFFB8C8CD);
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

    private static void drawTextField(GuiGraphics graphics, Font font, Rect rect, String text, boolean active) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), active ? 0xFF172126 : 0xFF101719);
        int edge = active ? 0xFF83A3AC : 0xFF465157;
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, edge);
        graphics.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), edge);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.height(), edge);
        graphics.fill(rect.x() + rect.width() - 1, rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), edge);
        drawClipped(graphics, font, text + (active && System.currentTimeMillis() / 400L % 2L == 0L ? "_" : ""), rect.x() + 4, rect.y() + 5, rect.width() - 8, active ? 0xFFEAF4F7 : 0xFFC8D4D8);
    }

    private static void drawLabelValue(GuiGraphics graphics, Font font, String label, String value, int x, int y, int width) {
        String text = label + ": " + value;
        drawClipped(graphics, font, text, x, y, width, 0xFFC8D4D8);
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

    private static int wrappedHeight(Font font, String text, int maxWidth, int maxLines) {
        if (maxWidth <= 0 || maxLines <= 0 || text == null || text.isEmpty()) {
            return 0;
        }
        return Math.min(maxLines, ProtectedTextRenderer.wrap(text, maxWidth, maxLines).size()) * ProtectedTextRenderer.LINE_HEIGHT;
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
        FUN
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record TargetHitBox(CorruptionTarget target, Rect rect) {
    }
}
