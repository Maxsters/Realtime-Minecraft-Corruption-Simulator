package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ToastPopupCorruptionHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AdvancementToast.class, RecipeToast.class})
@SuppressWarnings("target")
public abstract class ToastPopupMixin {
    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;",
                    "m_7172_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementToast/RecipeToast#render.")
    private void rmc$beginCorruptedToastRender(GuiGraphics graphics, ToastComponent component, long visibleTime, CallbackInfoReturnable<Toast.Visibility> callback) {
        ToastPopupCorruptionHooks.beginToastRender((Toast) (Object) this, graphics);
    }

    @ModifyVariable(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;",
                    "m_7172_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementToast/RecipeToast visible time.")
    private long rmc$corruptToastVisibleTime(long visibleTime) {
        return ToastPopupCorruptionHooks.corruptToastTime((Toast) (Object) this, visibleTime);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;",
                    "m_7172_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementToast/RecipeToast#render.")
    private void rmc$endCorruptedToastRender(GuiGraphics graphics, ToastComponent component, long visibleTime, CallbackInfoReturnable<Toast.Visibility> callback) {
        ToastPopupCorruptionHooks.endToastRender(graphics);
        callback.setReturnValue(ToastPopupCorruptionHooks.corruptVisibility((Toast) (Object) this, callback.getReturnValue(), visibleTime));
    }
}
