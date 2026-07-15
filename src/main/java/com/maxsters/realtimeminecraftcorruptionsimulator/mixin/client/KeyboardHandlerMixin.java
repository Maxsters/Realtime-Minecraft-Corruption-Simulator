package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
@SuppressWarnings("target")
public abstract class KeyboardHandlerMixin {
    @Inject(
            method = {
                    "keyPress(JIIII)V",
                    "m_90893_(JIIII)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for KeyboardHandler#keyPress.")
    private void rmc$consumeOverlayTextEditKey(long window, int key, int scanCode, int action, int modifiers,
                                                CallbackInfo callback) {
        if (CorruptionOverlayManager.consumeWorldTextEditKey(window, key, action, modifiers)) {
            callback.cancel();
        }
    }
}
