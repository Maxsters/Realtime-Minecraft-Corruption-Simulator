package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.TitleGuiCorruptionHooks;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
@SuppressWarnings("target")
public abstract class LogoRendererMixin {
    @Unique
    private boolean rmc$logoPosePushed;

    @Inject(
            method = {
                    "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V",
                    "m_280118_(Lnet/minecraft/client/gui/GuiGraphics;IFI)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LogoRenderer#renderLogo.")
    private void rmc$corruptLogoTransform(GuiGraphics graphics, int screenWidth, float alpha, int top, CallbackInfo callback) {
        TitleGuiCorruptionHooks.LogoTransform transform = TitleGuiCorruptionHooks.logoTransform(screenWidth, top);
        if (transform.identity()) {
            rmc$logoPosePushed = false;
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(transform.pivotX() + transform.dx(), transform.pivotY() + transform.dy(), 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(transform.rotation()));
        graphics.pose().scale(transform.scaleX(), transform.scaleY(), 1.0F);
        graphics.pose().translate(-transform.pivotX(), -transform.pivotY(), 0.0F);
        rmc$logoPosePushed = true;
    }

    @Inject(
            method = {
                    "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V",
                    "m_280118_(Lnet/minecraft/client/gui/GuiGraphics;IFI)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LogoRenderer#renderLogo.")
    private void rmc$restoreLogoTransform(GuiGraphics graphics, int screenWidth, float alpha, int top, CallbackInfo callback) {
        if (rmc$logoPosePushed) {
            graphics.pose().popPose();
            rmc$logoPosePushed = false;
        }
    }
}
