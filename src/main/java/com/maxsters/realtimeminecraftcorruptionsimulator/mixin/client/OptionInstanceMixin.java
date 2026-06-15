package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiSliderCorruptionHooks;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.function.Consumer;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin<T> {
    @Shadow(remap = false, aliases = "f_231481_")
    private T value;

    @Shadow(remap = false, aliases = "f_231479_")
    @Final
    private Consumer<T> onValueUpdate;

    @Inject(
            method = {
                    "set(Ljava/lang/Object;)V",
                    "m_231514_(Ljava/lang/Object;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for OptionInstance#set.")
    private void rmc$acceptImpossibleSliderValue(T newValue, CallbackInfo callback) {
        if (!GuiSliderCorruptionHooks.isApplyingImpossibleSliderValue()) {
            return;
        }

        if (!Objects.equals(this.value, newValue)) {
            this.value = newValue;
            this.onValueUpdate.accept(newValue);
        }
        callback.cancel();
    }
}
