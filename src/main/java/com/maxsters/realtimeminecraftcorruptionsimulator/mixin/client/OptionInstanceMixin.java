package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiSliderCorruptionHooks;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin<T> {
    @Unique
    private static Field rmc$valueField;
    @Unique
    private static boolean rmc$valueFieldChecked;
    @Unique
    private static Field rmc$onValueUpdateField;
    @Unique
    private static boolean rmc$onValueUpdateFieldChecked;

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

        if (rmc$setOptionValue((OptionInstance<?>) (Object) this, newValue)) {
            callback.cancel();
        }
    }

    @Unique
    private static boolean rmc$setOptionValue(OptionInstance<?> option, Object newValue) {
        Field valueField = rmc$valueField();
        Field updateField = rmc$onValueUpdateField();
        if (valueField == null || updateField == null) {
            return false;
        }

        try {
            Object oldValue = valueField.get(option);
            if (!Objects.equals(oldValue, newValue)) {
                valueField.set(option, newValue);
                Object updater = updateField.get(option);
                if (updater instanceof Consumer<?> consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<Object> objectConsumer = (Consumer<Object>) consumer;
                    objectConsumer.accept(newValue);
                }
            }
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static Field rmc$valueField() {
        if (!rmc$valueFieldChecked) {
            rmc$valueFieldChecked = true;
            rmc$valueField = rmc$findField("value", "f_231481_");
        }
        return rmc$valueField;
    }

    @Unique
    private static Field rmc$onValueUpdateField() {
        if (!rmc$onValueUpdateFieldChecked) {
            rmc$onValueUpdateFieldChecked = true;
            rmc$onValueUpdateField = rmc$findField("onValueUpdate", "f_231479_");
        }
        return rmc$onValueUpdateField;
    }

    @Unique
    private static Field rmc$findField(String mappedName, String srgName) {
        for (String name : new String[]{mappedName, srgName}) {
            try {
                Field field = OptionInstance.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        return null;
    }
}
