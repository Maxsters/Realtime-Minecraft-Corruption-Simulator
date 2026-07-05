package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.SubtitleCorruptionHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubtitleOverlay.class)
@SuppressWarnings("target")
public abstract class SubtitleOverlayMixin {
    @Inject(
            method = {
                    "onPlaySound(Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;)V",
                    "m_6985_(Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SubtitleOverlay#onPlaySound.")
    private void rmc$corruptSubtitleSoundBefore(SoundInstance sound, WeighedSoundEvents events, CallbackInfo callback) {
        if (SubtitleCorruptionHooks.beforeSound(this, sound, events)) {
            callback.cancel();
        }
    }

    @Inject(
            method = {
                    "onPlaySound(Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;)V",
                    "m_6985_(Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SubtitleOverlay#onPlaySound.")
    private void rmc$corruptSubtitleSoundAfter(SoundInstance sound, WeighedSoundEvents events, CallbackInfo callback) {
        SubtitleCorruptionHooks.afterSound(this, sound, events);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    "m_280227_(Lnet/minecraft/client/gui/GuiGraphics;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SubtitleOverlay#render.")
    private void rmc$beginCorruptedSubtitleRender(GuiGraphics graphics, CallbackInfo callback) {
        SubtitleCorruptionHooks.beginRender(this, graphics);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    "m_280227_(Lnet/minecraft/client/gui/GuiGraphics;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SubtitleOverlay#render.")
    private void rmc$endCorruptedSubtitleRender(GuiGraphics graphics, CallbackInfo callback) {
        SubtitleCorruptionHooks.endRender(graphics);
    }
}
