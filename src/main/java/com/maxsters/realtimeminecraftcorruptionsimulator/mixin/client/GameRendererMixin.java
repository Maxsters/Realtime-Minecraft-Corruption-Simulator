package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.InteractionCorruptionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@SuppressWarnings("target")
public abstract class GameRendererMixin {
    @Inject(
            method = {
                    "pick(F)V",
                    "m_109087_(F)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#pick.")
    private void rmc$corruptPickResult(float partialTick, CallbackInfo callback) {
        InteractionCorruptionHooks.corruptPick(Minecraft.getInstance(), partialTick);
    }
}
