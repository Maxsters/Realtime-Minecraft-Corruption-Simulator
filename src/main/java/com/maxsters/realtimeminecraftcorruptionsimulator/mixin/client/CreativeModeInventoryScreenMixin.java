package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiSliderCorruptionHooks;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
@SuppressWarnings("target")
public abstract class CreativeModeInventoryScreenMixin {
    @Inject(
            method = {
                    "mouseScrolled(DDD)Z",
                    "m_6050_(DDD)Z"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CreativeModeInventoryScreen#mouseScrolled.")
    private void rmc$corruptCreativeScrollbarAfterScroll(double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            GuiSliderCorruptionHooks.corruptNestedSliderField(this, "scrollOffs", "f_98508_", "creative_inventory_scroll", 0x43534352);
        }
    }

    @Inject(
            method = {
                    "mouseDragged(DDIDD)Z",
                    "m_7979_(DDIDD)Z"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for CreativeModeInventoryScreen#mouseDragged.")
    private void rmc$corruptCreativeScrollbarAfterDrag(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            GuiSliderCorruptionHooks.corruptNestedSliderField(this, "scrollOffs", "f_98508_", "creative_inventory_scroll", 0x43445247 ^ button);
        }
    }
}
