package com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay;

import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CorruptionOverlayButton {
    private CorruptionOverlayButton() {
    }

    public static void render(GuiGraphics graphics, Font font, int x, int y, int size, CorruptionProfileSnapshot snapshot, boolean hovered) {
        int radius = size / 2;
        int centerX = x + radius;
        int centerY = y + radius;
        int accent = accentColor(snapshot);
        drawCircle(graphics, centerX, centerY, radius, hovered ? 0xFFE6EEF0 : 0xFF7D8A8F);
        drawCircle(graphics, centerX, centerY, radius - 2, 0xEE11181B);
        drawCircle(graphics, centerX, centerY, Math.max(3, radius - 8), accent);
        String label = "RC";
        ProtectedTextRenderer.drawString(graphics, label, centerX - ProtectedTextRenderer.width(label) / 2, centerY - 4, 0xFFEAF4F7);
    }

    private static void drawCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
        }
    }

    private static int accentColor(CorruptionProfileSnapshot snapshot) {
        if (snapshot == null) {
            return 0xFF5CA7B2;
        }
        if (snapshot.getStabilityDebt() >= 50) {
            return 0xFFD39A3E;
        }
        if (snapshot.getCorruptionLevel() >= 70) {
            return 0xFF4D8FD6;
        }
        return 0xFF5CA7B2;
    }
}
