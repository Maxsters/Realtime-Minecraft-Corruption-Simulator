package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ToastPopupCorruptionHooks;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
@SuppressWarnings("target")
public abstract class ToastInstanceMixin {
    @ModifyArg(
            method = {
                    "render(ILnet/minecraft/client/gui/GuiGraphics;)Z",
                    "m_280442_(ILnet/minecraft/client/gui/GuiGraphics;)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_252880_(FFF)V",
                    remap = false
            ),
            index = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets ToastComponent.ToastInstance#render Y translation.")
    private float rmc$offsetPinnedAwardToastY(float y) {
        return ToastPopupCorruptionHooks.toastYOffset(y);
    }
}
