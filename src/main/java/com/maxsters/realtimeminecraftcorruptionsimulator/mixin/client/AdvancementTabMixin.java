package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.AdvancementGuiCorruptionHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementTab.class)
@SuppressWarnings("target")
public abstract class AdvancementTabMixin {
    @Inject(
            method = {
                    "drawContents(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    "m_280047_(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementTab#drawContents.")
    private void rmc$beginCorruptedAdvancementTree(GuiGraphics graphics, int x, int y, CallbackInfo callback) {
        AdvancementGuiCorruptionHooks.beginTreeRender(this, graphics, x, y);
    }

    @Inject(
            method = {
                    "drawContents(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    "m_280047_(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementTab#drawContents.")
    private void rmc$endCorruptedAdvancementTree(GuiGraphics graphics, int x, int y, CallbackInfo callback) {
        AdvancementGuiCorruptionHooks.endTreeRender(graphics);
    }

    @ModifyVariable(
            method = {
                    "scroll(DD)V",
                    "m_97151_(DD)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementTab#scroll drag X delta.")
    private double rmc$corruptAdvancementDragX(double deltaX) {
        return AdvancementGuiCorruptionHooks.corruptDragDelta(this, deltaX, 0);
    }

    @ModifyVariable(
            method = {
                    "scroll(DD)V",
                    "m_97151_(DD)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AdvancementTab#scroll drag Y delta.")
    private double rmc$corruptAdvancementDragY(double deltaY) {
        return AdvancementGuiCorruptionHooks.corruptDragDelta(this, deltaY, 1);
    }
}
