package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.HudEffectLayoutHooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
@SuppressWarnings("target")
public abstract class GuiEffectOverlayMixin {
    @Inject(
            method = {
                    "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    "m_280523_(Lnet/minecraft/client/gui/GuiGraphics;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Gui#renderEffects.")
    private void rmc$beginPinnedAwardEffectOffset(GuiGraphics graphics, CallbackInfo callback) {
        HudEffectLayoutHooks.beginRender(graphics);
    }

    @Inject(
            method = {
                    "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    "m_280523_(Lnet/minecraft/client/gui/GuiGraphics;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Gui#renderEffects.")
    private void rmc$endPinnedAwardEffectOffset(GuiGraphics graphics, CallbackInfo callback) {
        HudEffectLayoutHooks.endRender(graphics);
    }
}
