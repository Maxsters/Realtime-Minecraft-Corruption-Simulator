package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.AnimationSpeedCorruptionHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @ModifyVariable(
            method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets Screen#renderWithTooltip partial tick.")
    private float rmc$corruptScreenAnimationPartial(float partialTick, GuiGraphics graphics, int mouseX, int mouseY) {
        Screen screen = (Screen) (Object) this;
        return AnimationSpeedCorruptionHooks.mutateScreenPartial(partialTick, screen.getClass().getName());
    }
}
