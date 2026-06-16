package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiSliderCorruptionHooks;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Unique
    private static Field rmc$valueField;
    @Unique
    private static boolean rmc$valueFieldChecked;

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
            rmc$repairSliderAfterCorruption(button);
            return;
        }

        Double value = rmc$sliderValue(button);
        if (value == null) {
            return;
        }

        double corrupted = GuiSliderCorruptionHooks.corruptSliderValue(button, value);
        if (Double.compare(corrupted, value) == 0 || !rmc$setSliderValue(button, corrupted)) {
            return;
        }

        GuiSliderCorruptionHooks.markSliderCorrupted(button);
        GuiSliderCorruptionHooks.beginImpossibleSliderValue();
        try {
            rmc$invokeSliderMethod("applyValue", "m_5697_");
            rmc$invokeSliderMethod("updateMessage", "m_5695_");
        } finally {
            GuiSliderCorruptionHooks.endImpossibleSliderValue();
        }
    }

    @Unique
    private void rmc$repairSliderAfterCorruption(AbstractSliderButton button) {
        Double value = rmc$sliderValue(button);
        if (value == null || !GuiSliderCorruptionHooks.consumeSliderRepair(button, value)) {
            return;
        }

        double repaired = Double.isNaN(value) ? 0.0D : Mth.clamp(value, 0.0D, 1.0D);
        boolean changed = Double.compare(repaired, value) != 0 && rmc$setSliderValue(button, repaired);
        if (changed || Double.compare(repaired, value) == 0) {
            rmc$invokeSliderMethod("applyValue", "m_5697_");
            rmc$invokeSliderMethod("updateMessage", "m_5695_");
        }
    }

    @Unique
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

    @Unique
    private static Method rmc$findSliderMethod(String name) {
        try {
            Method method = AbstractSliderButton.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @Unique
    private static Double rmc$sliderValue(AbstractSliderButton button) {
        Field field = rmc$valueField();
        if (field == null) {
            return null;
        }
        try {
            return field.getDouble(button);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static boolean rmc$setSliderValue(AbstractSliderButton button, double value) {
        Field field = rmc$valueField();
        if (field == null) {
            return false;
        }
        try {
            field.setDouble(button, value);
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static Field rmc$valueField() {
        if (!rmc$valueFieldChecked) {
            rmc$valueFieldChecked = true;
            for (String name : new String[]{"value", "f_93577_"}) {
                try {
                    Field field = AbstractSliderButton.class.getDeclaredField(name);
                    field.setAccessible(true);
                    rmc$valueField = field;
                    break;
                } catch (NoSuchFieldException | RuntimeException ignored) {
                }
            }
        }
        return rmc$valueField;
    }
}
