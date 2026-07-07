package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
final class OverlayTextEditState {
    private static final int SEED_MAX_LENGTH = 96;
    private static final int FUN_MAX_LENGTH = 24;

    private boolean seedEditing;
    private FunField funField = FunField.NONE;
    private String seedText;
    private String intervalText = "";
    private String amountText = "";
    private String seedRandomizerText = "";
    private int cursor;
    private int selectionAnchor;

    OverlayTextEditState(String seedText) {
        this.seedText = OverlayTextSupport.sanitizeSeedText(seedText);
        moveCursorToEnd();
    }

    boolean isEditing() {
        return seedEditing || funField != FunField.NONE;
    }

    boolean isSeedEditing() {
        return seedEditing;
    }

    boolean isFunEditing() {
        return funField != FunField.NONE;
    }

    FunField funField() {
        return funField;
    }

    boolean isIntervalEditing() {
        return funField == FunField.INTERVAL;
    }

    boolean isAmountEditing() {
        return funField == FunField.AMOUNT;
    }

    boolean isSeedRandomizerEditing() {
        return funField == FunField.SEED_RANDOMIZER;
    }

    String seedText() {
        return seedText;
    }

    String intervalText() {
        return intervalText;
    }

    String amountText() {
        return amountText;
    }

    String seedRandomizerText() {
        return seedRandomizerText;
    }

    int cursor() {
        return cursor;
    }

    int selectionAnchor() {
        return selectionAnchor;
    }

    void beginSeed(String text) {
        funField = FunField.NONE;
        seedEditing = true;
        seedText = OverlayTextSupport.sanitizeSeedText(text);
        moveCursorToEnd();
    }

    void beginInterval(int ticks) {
        seedEditing = false;
        funField = FunField.INTERVAL;
        intervalText = OverlayTextSupport.formatIntervalEdit(ticks);
        moveCursorToEnd();
    }

    void beginAmount(int amount) {
        seedEditing = false;
        funField = FunField.AMOUNT;
        amountText = OverlayTextSupport.signedPercentLabel(amount);
        moveCursorToEnd();
    }

    void beginSeedRandomizer(int ticks) {
        seedEditing = false;
        funField = FunField.SEED_RANDOMIZER;
        seedRandomizerText = OverlayTextSupport.formatIntervalEdit(ticks);
        moveCursorToEnd();
    }

    void finishSeed(String text) {
        seedEditing = false;
        seedText = OverlayTextSupport.sanitizeSeedText(text);
        moveCursorToEnd();
    }

    void finishFun() {
        funField = FunField.NONE;
        clearSelection();
    }

    void cancel(String seedLabel) {
        if (seedEditing) {
            seedEditing = false;
            seedText = OverlayTextSupport.sanitizeSeedText(seedLabel);
        } else {
            funField = FunField.NONE;
        }
        clearSelection();
    }

    void reset(String seedLabel, int intervalTicks, int amount, int seedRandomizerTicks) {
        seedEditing = false;
        funField = FunField.NONE;
        seedText = OverlayTextSupport.sanitizeSeedText(seedLabel);
        intervalText = OverlayTextSupport.formatIntervalEdit(intervalTicks);
        amountText = OverlayTextSupport.signedPercentLabel(amount);
        seedRandomizerText = OverlayTextSupport.formatIntervalEdit(seedRandomizerTicks);
        moveCursorToEnd();
    }

    void setSeedTextWhenIdle(String text) {
        if (!seedEditing) {
            seedText = OverlayTextSupport.sanitizeSeedText(text);
            clampSelection();
        }
    }

    void stopSeedEditing() {
        seedEditing = false;
    }

    boolean handleEditBufferKey(int key, int modifiers, boolean captureUnhandled) {
        boolean command = isCommandModifier(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (command && key == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_C) {
            copySelection();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_X) {
            cutSelection();
            return true;
        }
        if (command && key == GLFW.GLFW_KEY_V) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
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
            if (!shift && hasSelection()) {
                moveCursor(selectionStart(), false);
            } else {
                moveCursor(command ? OverlayTextSupport.previousWordBoundary(activeText(), cursor) : cursor - 1, shift);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (!shift && hasSelection()) {
                moveCursor(selectionEnd(), false);
            } else {
                moveCursor(command ? OverlayTextSupport.nextWordBoundary(activeText(), cursor) : cursor + 1, shift);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            moveCursor(0, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            moveCursor(activeText().length(), shift);
            return true;
        }

        if (command || (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            return true;
        }

        String character = seedEditing ? OverlayTextSupport.keyToSeedCharacter(key, shift) : OverlayTextSupport.keyToFunCharacter(key, shift);
        if (!character.isEmpty()) {
            insert(character);
            return true;
        }
        return captureUnhandled;
    }

    boolean hasEditorChanges(String draftSeedLabel, long draftSeed, int draftIntervalTicks, int draftAmount, int draftSeedRandomizerTicks) {
        if (seedEditing) {
            String label = OverlayTextSupport.sanitizeSeedText(seedText);
            return !label.isBlank() && (!label.equals(draftSeedLabel) || OverlayTextSupport.seedFromText(label, draftSeed) != draftSeed);
        }
        if (funField == FunField.INTERVAL) {
            return OverlayTextSupport.parseIntervalTicks(intervalText, draftIntervalTicks) != draftIntervalTicks;
        }
        if (funField == FunField.AMOUNT) {
            return OverlayTextSupport.parseAutoAmount(amountText, draftAmount) != draftAmount;
        }
        if (funField == FunField.SEED_RANDOMIZER) {
            return OverlayTextSupport.parseIntervalTicks(seedRandomizerText, draftSeedRandomizerTicks) != draftSeedRandomizerTicks;
        }
        return false;
    }

    void clearSelection() {
        cursor = clampIndex(cursor, activeText().length());
        selectionAnchor = cursor;
    }

    void clampSelection() {
        int length = activeText().length();
        cursor = clampIndex(cursor, length);
        selectionAnchor = clampIndex(selectionAnchor, length);
    }

    private String activeText() {
        if (seedEditing) {
            return seedText;
        }
        return switch (funField) {
            case INTERVAL -> intervalText;
            case AMOUNT -> amountText;
            case SEED_RANDOMIZER -> seedRandomizerText;
            case NONE -> seedText;
        };
    }

    private int activeMaxLength() {
        return seedEditing ? SEED_MAX_LENGTH : FUN_MAX_LENGTH;
    }

    private String sanitizeActiveText(String value) {
        return seedEditing ? OverlayTextSupport.sanitizeSeedText(value) : OverlayTextSupport.sanitizeFunEditText(value);
    }

    private void setActiveText(String value, int requestedCursor) {
        String sanitized = sanitizeActiveText(value);
        if (seedEditing) {
            seedText = sanitized;
        } else if (funField == FunField.INTERVAL) {
            intervalText = sanitized;
        } else if (funField == FunField.AMOUNT) {
            amountText = sanitized;
        } else if (funField == FunField.SEED_RANDOMIZER) {
            seedRandomizerText = sanitized;
        }
        cursor = clampIndex(requestedCursor, sanitized.length());
        selectionAnchor = cursor;
    }

    private void insert(String value) {
        String insert = sanitizeActiveText(value);
        if (insert.isEmpty()) {
            return;
        }
        String text = activeText();
        int start = selectionStart();
        int end = selectionEnd();
        int remaining = activeMaxLength() - (text.length() - (end - start));
        if (remaining <= 0) {
            return;
        }
        if (insert.length() > remaining) {
            insert = insert.substring(0, remaining);
        }
        setActiveText(text.substring(0, start) + insert + text.substring(end), start + insert.length());
    }

    private void copySelection() {
        String text = activeText();
        String copied = hasSelection() ? text.substring(selectionStart(), selectionEnd()) : text;
        Minecraft.getInstance().keyboardHandler.setClipboard(copied);
    }

    private void cutSelection() {
        if (!hasSelection()) {
            return;
        }
        copySelection();
        deleteSelection();
    }

    private void selectAll() {
        selectionAnchor = 0;
        cursor = activeText().length();
    }

    private void deletePreviousCharacter() {
        if (deleteSelection()) {
            return;
        }
        String text = activeText();
        if (cursor <= 0) {
            return;
        }
        int start = cursor - 1;
        setActiveText(text.substring(0, start) + text.substring(cursor), start);
    }

    private void deleteNextCharacter() {
        if (deleteSelection()) {
            return;
        }
        String text = activeText();
        if (cursor >= text.length()) {
            return;
        }
        setActiveText(text.substring(0, cursor) + text.substring(cursor + 1), cursor);
    }

    private void deletePreviousWord() {
        if (deleteSelection()) {
            return;
        }
        String text = activeText();
        int start = OverlayTextSupport.previousWordBoundary(text, cursor);
        if (start < cursor) {
            setActiveText(text.substring(0, start) + text.substring(cursor), start);
        }
    }

    private void deleteNextWord() {
        if (deleteSelection()) {
            return;
        }
        String text = activeText();
        int end = OverlayTextSupport.nextWordBoundary(text, cursor);
        if (end > cursor) {
            setActiveText(text.substring(0, cursor) + text.substring(end), cursor);
        }
    }

    private boolean deleteSelection() {
        if (!hasSelection()) {
            return false;
        }
        String text = activeText();
        int start = selectionStart();
        int end = selectionEnd();
        setActiveText(text.substring(0, start) + text.substring(end), start);
        return true;
    }

    private void moveCursor(int position, boolean selecting) {
        String text = activeText();
        int clamped = clampIndex(position, text.length());
        if (!selecting && hasSelection()) {
            clamped = position < cursor ? selectionStart() : selectionEnd();
        }
        cursor = clamped;
        if (!selecting) {
            selectionAnchor = cursor;
        }
    }

    private void moveCursorToEnd() {
        cursor = activeText().length();
        selectionAnchor = cursor;
    }

    private boolean hasSelection() {
        clampSelection();
        return cursor != selectionAnchor;
    }

    private int selectionStart() {
        clampSelection();
        return Math.min(cursor, selectionAnchor);
    }

    private int selectionEnd() {
        clampSelection();
        return Math.max(cursor, selectionAnchor);
    }

    private static boolean isCommandModifier(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private static int clampIndex(int index, int length) {
        return Math.max(0, Math.min(length, index));
    }

    enum FunField {
        NONE,
        INTERVAL,
        AMOUNT,
        SEED_RANDOMIZER
    }
}
