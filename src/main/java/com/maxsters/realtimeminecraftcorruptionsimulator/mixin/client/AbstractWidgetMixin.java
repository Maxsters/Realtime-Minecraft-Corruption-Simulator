package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiInteractionCorruptionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractWidget.class)
@SuppressWarnings("target")
public abstract class AbstractWidgetMixin {
    @Inject(
            method = {
                    "mouseClicked(DDI)Z",
                    "m_6375_(DDI)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractWidget#mouseClicked.")
    private void rmc$corruptWidgetClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callback) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (button != 0 || !widget.isActive()
                || mouseX < widget.getX()
                || mouseY < widget.getY()
                || mouseX >= widget.getX() + widget.getWidth()
                || mouseY >= widget.getY() + widget.getHeight()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || ClientCorruptionProtection.isLifecycleAccessScreen(minecraft.screen)) {
            return;
        }

        Boolean result;
        try {
            result = GuiInteractionCorruptionHooks.corruptWidgetClick(widget, mouseX, mouseY, button);
        } catch (LinkageError ignored) {
            return;
        }
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
