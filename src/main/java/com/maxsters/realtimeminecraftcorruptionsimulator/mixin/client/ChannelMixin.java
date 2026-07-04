package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.AudioCorruptionManager;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Channel.class, remap = false)
@SuppressWarnings("target")
public abstract class ChannelMixin {
    @ModifyVariable(
            method = {
                    "setVolume(F)V",
                    "b(F)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    @Dynamic("Targets both the named and obfuscated Blaze3D Channel#setVolume method.")
    private float rmc$limitCorruptedSoundGain(float volume) {
        return AudioCorruptionManager.limitCorruptedGain(volume);
    }
}
