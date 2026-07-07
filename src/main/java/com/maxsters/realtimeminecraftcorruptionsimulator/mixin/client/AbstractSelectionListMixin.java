package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.GuiSelectionListCorruptionHooks;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSelectionList.class)
@SuppressWarnings({"rawtypes", "target"})
public abstract class AbstractSelectionListMixin {
    @Inject(
            method = {
                    "getRowLeft()I",
                    "m_5747_()I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getRowLeft.")
    private void rmc$corruptRowLeft(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.rowLeft((AbstractSelectionList) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getRowWidth()I",
                    "m_5759_()I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getRowWidth.")
    private void rmc$corruptRowWidth(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.rowWidth((AbstractSelectionList) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getRowTop(I)I",
                    "m_7610_(I)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getRowTop.")
    private void rmc$corruptRowTop(int row, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.rowTop((AbstractSelectionList) (Object) this, row, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getScrollbarPosition()I",
                    "m_5756_()I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getScrollbarPosition.")
    private void rmc$corruptScrollbarPosition(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.scrollbarPosition((AbstractSelectionList) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getMaxScroll()I",
                    "m_93518_()I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getMaxScroll.")
    private void rmc$corruptMaxScroll(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.maxScroll((AbstractSelectionList) (Object) this, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getScrollAmount()D",
                    "m_93517_()D"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractSelectionList#getScrollAmount.")
    private void rmc$corruptScrollAmount(CallbackInfoReturnable<Double> callback) {
        callback.setReturnValue(GuiSelectionListCorruptionHooks.scrollAmount((AbstractSelectionList) (Object) this, callback.getReturnValue()));
    }
}
