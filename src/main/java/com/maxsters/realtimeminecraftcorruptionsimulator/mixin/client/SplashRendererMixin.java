package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.TitleGuiCorruptionHooks;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SplashRenderer.class)
@SuppressWarnings("target")
public abstract class SplashRendererMixin {
    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", remap = false),
            index = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private float rmc$corruptSplashX(float x) {
        return TitleGuiCorruptionHooks.splashX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", remap = false),
            index = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private float rmc$corruptSplashY(float y) {
        return TitleGuiCorruptionHooks.splashY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", remap = false),
            index = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private float rmc$corruptSplashRotation(float degrees) {
        return TitleGuiCorruptionHooks.splashRotation(degrees);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = false),
            index = 0,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private float rmc$corruptSplashScaleX(float scale) {
        return TitleGuiCorruptionHooks.splashScale(scale);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", remap = false),
            index = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private float rmc$corruptSplashScaleY(float scale) {
        return TitleGuiCorruptionHooks.splashScale(scale);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private int rmc$corruptSplashTextX(int x) {
        return TitleGuiCorruptionHooks.splashTextX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private int rmc$corruptSplashTextY(int y) {
        return TitleGuiCorruptionHooks.splashTextY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SplashRenderer#render.")
    private int rmc$corruptSplashColor(int color) {
        return TitleGuiCorruptionHooks.splashColor(color);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptSplashTextXSrg(int x) {
        return TitleGuiCorruptionHooks.splashTextX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptSplashTextYSrg(int y) {
        return TitleGuiCorruptionHooks.splashTextY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V",
                    "m_280672_(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptSplashColorSrg(int color) {
        return TitleGuiCorruptionHooks.splashColor(color);
    }
}
