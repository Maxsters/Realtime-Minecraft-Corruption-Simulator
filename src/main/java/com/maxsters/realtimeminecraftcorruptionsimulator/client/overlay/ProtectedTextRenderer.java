package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ProtectedTextRenderer {
    public static final int LINE_HEIGHT = 10;
    private static final int GLYPH_WIDTH = 5;
    private static final int GLYPH_HEIGHT = 7;
    private static final int GLYPH_SPACING = 1;

    private ProtectedTextRenderer() {
    }

    public static void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = normalize(text.charAt(i));
            if (c != ' ') {
                drawGlyph(graphics, c, cursor, y, color);
            }
            cursor += advance(c);
        }
    }

    public static int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += advance(normalize(text.charAt(i)));
        }
        return Math.max(0, width - GLYPH_SPACING);
    }

    public static String plainSubstrByWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        int width = 0;
        int index = 0;
        while (index < text.length()) {
            int next = advance(normalize(text.charAt(index)));
            if (width + next > maxWidth) {
                break;
            }
            width += next;
            index++;
        }
        return text.substring(0, index);
    }

    public static List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
            return lines;
        }

        String[] words = text.split("\\s+");
        String current = "";
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate) <= maxWidth) {
                current = candidate;
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current);
                if (lines.size() >= maxLines) {
                    return lines;
                }
            }
            current = word;
            while (width(current) > maxWidth && lines.size() < maxLines) {
                String clipped = plainSubstrByWidth(current, maxWidth);
                if (clipped.isEmpty()) {
                    break;
                }
                lines.add(clipped);
                current = current.substring(clipped.length());
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current);
        }
        return lines;
    }

    private static void drawGlyph(GuiGraphics graphics, char c, int x, int y, int color) {
        String[] rows = glyph(c);
        for (int row = 0; row < GLYPH_HEIGHT; row++) {
            String pattern = rows[row];
            for (int column = 0; column < GLYPH_WIDTH; column++) {
                if (pattern.charAt(column) != '0') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }

    private static int advance(char c) {
        if (c == ' ') {
            return 4;
        }
        if (c == '.' || c == ',' || c == '\'' || c == ':' || c == ';' || c == '!' || c == '|') {
            return 3;
        }
        return GLYPH_WIDTH + GLYPH_SPACING;
    }

    private static char normalize(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32);
        }
        return c >= 32 && c <= 126 ? c : '?';
    }

    private static String[] glyph(char c) {
        return switch (c) {
            case 'A' -> rows("01110", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'B' -> rows("11110", "10001", "10001", "11110", "10001", "10001", "11110");
            case 'C' -> rows("01111", "10000", "10000", "10000", "10000", "10000", "01111");
            case 'D' -> rows("11110", "10001", "10001", "10001", "10001", "10001", "11110");
            case 'E' -> rows("11111", "10000", "10000", "11110", "10000", "10000", "11111");
            case 'F' -> rows("11111", "10000", "10000", "11110", "10000", "10000", "10000");
            case 'G' -> rows("01111", "10000", "10000", "10111", "10001", "10001", "01110");
            case 'H' -> rows("10001", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'I' -> rows("11111", "00100", "00100", "00100", "00100", "00100", "11111");
            case 'J' -> rows("00111", "00010", "00010", "00010", "10010", "10010", "01100");
            case 'K' -> rows("10001", "10010", "10100", "11000", "10100", "10010", "10001");
            case 'L' -> rows("10000", "10000", "10000", "10000", "10000", "10000", "11111");
            case 'M' -> rows("10001", "11011", "10101", "10101", "10001", "10001", "10001");
            case 'N' -> rows("10001", "11001", "10101", "10011", "10001", "10001", "10001");
            case 'O' -> rows("01110", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'P' -> rows("11110", "10001", "10001", "11110", "10000", "10000", "10000");
            case 'Q' -> rows("01110", "10001", "10001", "10001", "10101", "10010", "01101");
            case 'R' -> rows("11110", "10001", "10001", "11110", "10100", "10010", "10001");
            case 'S' -> rows("01111", "10000", "10000", "01110", "00001", "00001", "11110");
            case 'T' -> rows("11111", "00100", "00100", "00100", "00100", "00100", "00100");
            case 'U' -> rows("10001", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'V' -> rows("10001", "10001", "10001", "10001", "10001", "01010", "00100");
            case 'W' -> rows("10001", "10001", "10001", "10101", "10101", "10101", "01010");
            case 'X' -> rows("10001", "10001", "01010", "00100", "01010", "10001", "10001");
            case 'Y' -> rows("10001", "10001", "01010", "00100", "00100", "00100", "00100");
            case 'Z' -> rows("11111", "00001", "00010", "00100", "01000", "10000", "11111");
            case '0' -> rows("01110", "10001", "10011", "10101", "11001", "10001", "01110");
            case '1' -> rows("00100", "01100", "00100", "00100", "00100", "00100", "01110");
            case '2' -> rows("01110", "10001", "00001", "00010", "00100", "01000", "11111");
            case '3' -> rows("11110", "00001", "00001", "01110", "00001", "00001", "11110");
            case '4' -> rows("00010", "00110", "01010", "10010", "11111", "00010", "00010");
            case '5' -> rows("11111", "10000", "10000", "11110", "00001", "00001", "11110");
            case '6' -> rows("01110", "10000", "10000", "11110", "10001", "10001", "01110");
            case '7' -> rows("11111", "00001", "00010", "00100", "01000", "01000", "01000");
            case '8' -> rows("01110", "10001", "10001", "01110", "10001", "10001", "01110");
            case '9' -> rows("01110", "10001", "10001", "01111", "00001", "00001", "01110");
            case '.' -> rows("00000", "00000", "00000", "00000", "00000", "01100", "01100");
            case ',' -> rows("00000", "00000", "00000", "00000", "00000", "01100", "01000");
            case ':' -> rows("00000", "01100", "01100", "00000", "01100", "01100", "00000");
            case ';' -> rows("00000", "01100", "01100", "00000", "01100", "01000", "10000");
            case '-' -> rows("00000", "00000", "00000", "11111", "00000", "00000", "00000");
            case '_' -> rows("00000", "00000", "00000", "00000", "00000", "00000", "11111");
            case '+' -> rows("00000", "00100", "00100", "11111", "00100", "00100", "00000");
            case '=' -> rows("00000", "00000", "11111", "00000", "11111", "00000", "00000");
            case '!' -> rows("00100", "00100", "00100", "00100", "00100", "00000", "00100");
            case '?' -> rows("01110", "10001", "00001", "00010", "00100", "00000", "00100");
            case '%' -> rows("11001", "11010", "00010", "00100", "01000", "01011", "10011");
            case '/' -> rows("00001", "00010", "00010", "00100", "01000", "01000", "10000");
            case '\\' -> rows("10000", "01000", "01000", "00100", "00010", "00010", "00001");
            case '|' -> rows("00100", "00100", "00100", "00100", "00100", "00100", "00100");
            case '(' -> rows("00010", "00100", "01000", "01000", "01000", "00100", "00010");
            case ')' -> rows("01000", "00100", "00010", "00010", "00010", "00100", "01000");
            case '[' -> rows("01110", "01000", "01000", "01000", "01000", "01000", "01110");
            case ']' -> rows("01110", "00010", "00010", "00010", "00010", "00010", "01110");
            case '<' -> rows("00010", "00100", "01000", "10000", "01000", "00100", "00010");
            case '>' -> rows("01000", "00100", "00010", "00001", "00010", "00100", "01000");
            case '*' -> rows("00000", "10101", "01110", "11111", "01110", "10101", "00000");
            case '\'' -> rows("01100", "00100", "01000", "00000", "00000", "00000", "00000");
            case '"' -> rows("01010", "01010", "01010", "00000", "00000", "00000", "00000");
            case '&' -> rows("01100", "10010", "10100", "01000", "10101", "10010", "01101");
            case '#' -> rows("01010", "01010", "11111", "01010", "11111", "01010", "01010");
            case '@' -> rows("01110", "10001", "10111", "10101", "10111", "10000", "01110");
            default -> rows("11111", "00001", "00010", "00100", "00100", "00000", "00100");
        };
    }

    private static String[] rows(String a, String b, String c, String d, String e, String f, String g) {
        return new String[]{a, b, c, d, e, f, g};
    }
}
