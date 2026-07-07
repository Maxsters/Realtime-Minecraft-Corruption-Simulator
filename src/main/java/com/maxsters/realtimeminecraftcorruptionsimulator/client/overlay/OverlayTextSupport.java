package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

final class OverlayTextSupport {
    private static final int MAX_SEED_TEXT_LENGTH = 96;
    private static final int MAX_FUN_TEXT_LENGTH = 24;

    private OverlayTextSupport() {
    }

    static int parseIntervalTicks(String value, int fallbackTicks) {
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

    static int parseAutoAmount(String value, int fallbackAmount) {
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

    static String formatIntervalEdit(int ticks) {
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

    static String signedPercentLabel(int amount) {
        return (amount > 0 ? "+" : "") + amount + "%";
    }

    static String keyToSeedCharacter(int key, boolean shift) {
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

    static String keyToFunCharacter(int key, boolean shift) {
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

    static String sanitizeSeedText(String value) {
        return sanitizePrintableAscii(value, MAX_SEED_TEXT_LENGTH);
    }

    static String sanitizeFunEditText(String value) {
        return sanitizePrintableAscii(value, MAX_FUN_TEXT_LENGTH);
    }

    static long seedFromText(String text, long fallbackSeed) {
        String trimmed = sanitizeSeedText(text);
        if (trimmed.isBlank()) {
            return fallbackSeed;
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

    static int previousWordBoundary(String text, int cursor) {
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

    static int nextWordBoundary(String text, int cursor) {
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

    private static String sanitizePrintableAscii(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Math.min(maxLength, value.length()));
        for (int i = 0; i < value.length() && builder.length() < maxLength; i++) {
            char character = value.charAt(i);
            if (character >= 32 && character <= 126) {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    private static int clampTextIndex(int index, int length) {
        return Math.max(0, Math.min(length, index));
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
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
}
