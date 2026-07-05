package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiInteractionCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.GuiLayoutCorruptionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
@SuppressWarnings("target")
public abstract class AbstractContainerScreenMixin {
    @Inject(
            method = {
                    "init()V",
                    "m_7856_()V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractContainerScreen#init.")
    private void rmc$restoreParentLayoutBeforeInit(CallbackInfo callback) {
        GuiLayoutCorruptionHooks.restoreContainerLayout((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractContainerScreen#render.")
    private void rmc$corruptParentLayout(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        GuiLayoutCorruptionHooks.applyContainerLayout((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
            method = {
                    "removed()V",
                    "m_7861_()V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractContainerScreen#removed.")
    private void rmc$restoreParentLayoutBeforeRemoved(CallbackInfo callback) {
        GuiLayoutCorruptionHooks.restoreContainerLayout((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
            method = {
                    "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
                    "m_6597_(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractContainerScreen#slotClicked.")
    private void rmc$corruptSlotClick(Slot slot, int slotId, int button, ClickType clickType, CallbackInfo callback) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiInteractionCorruptionHooks.SlotClickMutation mutation = GuiInteractionCorruptionHooks.corruptSlotClick(screen, slot, slotId, button, clickType);
        if (mutation.cancel()) {
            callback.cancel();
            return;
        }
        if (!mutation.replace()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, mutation.slotId(), mutation.button(), mutation.clickType(), minecraft.player);
        }
        callback.cancel();
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderFloatingItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                    remap = false
            ),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets AbstractContainerScreen#renderFloatingItem carried item X coordinate.")
    private int rmc$corruptFloatingItemX(int x) {
        return GuiInteractionCorruptionHooks.corruptFloatingItemCoordinate((AbstractContainerScreen<?>) (Object) this, x, 0);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderFloatingItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                    remap = false
            ),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets AbstractContainerScreen#renderFloatingItem carried item Y coordinate.")
    private int rmc$corruptFloatingItemY(int y) {
        return GuiInteractionCorruptionHooks.corruptFloatingItemCoordinate((AbstractContainerScreen<?>) (Object) this, y, 1);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;m_280211_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                    remap = false
            ),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets AbstractContainerScreen#m_280211_ carried item X coordinate.")
    private int rmc$corruptFloatingItemXSrg(int x) {
        return GuiInteractionCorruptionHooks.corruptFloatingItemCoordinate((AbstractContainerScreen<?>) (Object) this, x, 0);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;m_280211_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                    remap = false
            ),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets AbstractContainerScreen#m_280211_ carried item Y coordinate.")
    private int rmc$corruptFloatingItemYSrg(int y) {
        return GuiInteractionCorruptionHooks.corruptFloatingItemCoordinate((AbstractContainerScreen<?>) (Object) this, y, 1);
    }

}
