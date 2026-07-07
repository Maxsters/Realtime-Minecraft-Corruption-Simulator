package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HudEffectLayoutHooks {
    private static final ThreadLocal<Integer> OFFSET_DEPTH = ThreadLocal.withInitial(() -> 0);

    private HudEffectLayoutHooks() {
    }

    public static void beginRender(GuiGraphics graphics) {
        if (graphics == null) {
            return;
        }
        int offset = CorruptionOverlayPanel.topRightPinnedToastOffset();
        if (offset <= 0) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, offset, 0.0F);
        OFFSET_DEPTH.set(OFFSET_DEPTH.get() + 1);
    }

    public static void endRender(GuiGraphics graphics) {
        if (graphics == null) {
            return;
        }
        int depth = OFFSET_DEPTH.get();
        if (depth <= 0) {
            return;
        }
        graphics.pose().popPose();
        OFFSET_DEPTH.set(depth - 1);
    }
}
