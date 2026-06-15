package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiSliderCorruptionHooks;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Shadow(remap = false, aliases = "f_93577_")
    private double value;

    @Inject(
            method = {
                    "onClick(DD)V",
                    "m_5716_(DD)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSliderButton#onClick.")
    private void rmc$corruptSliderAfterClick(double mouseX, double mouseY, CallbackInfo callback) {
        rmc$applyImpossibleSliderValue();
    }

    @Inject(
            method = {
                    "onDrag(DDDD)V",
                    "m_7212_(DDDD)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSliderButton#onDrag.")
    private void rmc$corruptSliderAfterDrag(double mouseX, double mouseY, double dragX, double dragY, CallbackInfo callback) {
        rmc$applyImpossibleSliderValue();
    }

    @Inject(
            method = {
                    "keyPressed(III)Z",
                    "m_7933_(III)Z"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSliderButton#keyPressed.")
    private void rmc$corruptSliderAfterKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            rmc$applyImpossibleSliderValue();
        }
    }

    private void rmc$applyImpossibleSliderValue() {
        AbstractSliderButton button = (AbstractSliderButton) (Object) this;
        if (!GuiSliderCorruptionHooks.shouldCorruptSlider(button)) {
            return;
        }

        double corrupted = GuiSliderCorruptionHooks.corruptSliderValue(button, this.value);
        if (Double.compare(corrupted, this.value) == 0) {
            return;
        }
        this.value = corrupted;
        GuiSliderCorruptionHooks.beginImpossibleSliderValue();
        try {
            rmc$invokeSliderMethod("applyValue", "m_5697_");
            rmc$invokeSliderMethod("updateMessage", "m_5695_");
        } finally {
            GuiSliderCorruptionHooks.endImpossibleSliderValue();
        }
    }

    private void rmc$invokeSliderMethod(String mappedName, String srgName) {
        Method method = rmc$findSliderMethod(mappedName);
        if (method == null) {
            method = rmc$findSliderMethod(srgName);
        }
        if (method == null) {
            return;
        }

        try {
            method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method rmc$findSliderMethod(String name) {
        try {
            Method method = AbstractSliderButton.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
