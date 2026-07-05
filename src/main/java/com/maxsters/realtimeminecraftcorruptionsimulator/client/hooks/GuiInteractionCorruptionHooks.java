package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class GuiInteractionCorruptionHooks {
    private static int routedWidgetDepth;

    private GuiInteractionCorruptionHooks() {
    }

    public static Boolean corruptWidgetClick(AbstractWidget widget, double mouseX, double mouseY, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = widgetTargetId(screen, widget);
        float intensity = guiFunctionalityIntensity(stack, targetId);
        if (routedWidgetDepth > 0 || intensity <= 0.01F || protectedScreen(screen)) {
            return null;
        }

        long seed = interactionSeed(stack, targetId, widgetSalt(widget) ^ button);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.92F
                : Mth.clamp(0.05F + intensity * 0.64F + stack.instability() * 0.10F, 0.0F, 0.82F);
        if (unit(seed ^ 0x47415445L) > chance) {
            return null;
        }

        int mode = Math.floorMod((int) (seed >>> 28), 6);
        if (mode <= 1 || unit(seed ^ 0x44454144L) < 0.20F + intensity * 0.30F) {
            return Boolean.FALSE;
        }

        AbstractWidget routed = routedWidget(screen, widget, seed);
        if (routed == null) {
            return Boolean.FALSE;
        }

        double routedX = routed.getX() + routed.getWidth() * (0.24D + unit(seed ^ 0x58504F53L) * 0.52D);
        double routedY = routed.getY() + routed.getHeight() * (0.24D + unit(seed ^ 0x59504F53L) * 0.52D);
        routedWidgetDepth++;
        try {
            routed.mouseClicked(routedX, routedY, button);
        } finally {
            routedWidgetDepth = Math.max(0, routedWidgetDepth - 1);
        }
        return Boolean.TRUE;
    }

    public static SlotClickMutation corruptSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ClickType clickType) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null || screen == null || protectedScreen(minecraft.screen)) {
            return SlotClickMutation.pass(slotId, button, clickType);
        }

        AbstractContainerMenu menu = screen.getMenu();
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "gui_container_click:" + screen.getClass().getName() + ":" + clickType.name().toLowerCase(Locale.ROOT);
        float intensity = guiFunctionalityIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return SlotClickMutation.pass(slotId, button, clickType);
        }

        boolean carriedFlow = !menu.getCarried().isEmpty() || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP;
        long seed = interactionSeed(stack, targetId, slotId ^ (button << 8) ^ (clickType.ordinal() << 16));
        float base = carriedFlow ? 0.09F : 0.025F;
        float scale = carriedFlow ? 0.70F : 0.28F;
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? (carriedFlow ? 0.94F : 0.62F)
                : Mth.clamp(base + intensity * scale + stack.instability() * 0.08F, 0.0F, carriedFlow ? 0.88F : 0.46F);
        if (unit(seed ^ 0x534C4F54L) > chance) {
            return SlotClickMutation.pass(slotId, button, clickType);
        }

        int mode = Math.floorMod((int) (seed >>> 29), 7);
        if (mode == 0 || unit(seed ^ 0x4E4F4F50L) < 0.12F + intensity * 0.20F) {
            return SlotClickMutation.cancel(slotId, button, clickType);
        }

        int mutatedSlotId = slotId;
        int mutatedButton = button;
        ClickType mutatedClickType = clickType;
        if (mode == 1 || mode == 4) {
            mutatedSlotId = routedSlotId(menu, slot, slotId, seed, intensity);
        }
        if (mode == 2 || mode == 4) {
            mutatedButton = button == 0 ? 1 : 0;
        }
        if (mode == 3 || mode == 5) {
            mutatedClickType = mutatedClickType(clickType, seed, carriedFlow);
        }
        if (mode == 6 && carriedFlow && unit(seed ^ 0x44524F50L) < intensity * 0.34F) {
            mutatedSlotId = -999;
            mutatedClickType = ClickType.PICKUP;
        }

        if (mutatedSlotId == slotId && mutatedButton == button && mutatedClickType == clickType) {
            return SlotClickMutation.cancel(slotId, button, clickType);
        }
        return SlotClickMutation.replace(mutatedSlotId, mutatedButton, mutatedClickType);
    }

    public static int corruptFloatingItemCoordinate(AbstractContainerScreen<?> screen, int original, int axis) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || screen == null || protectedScreen(minecraft.screen)) {
            return original;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "gui_carried_item_follow:" + screen.getClass().getName();
        float intensity = guiFunctionalityIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }

        long seed = interactionSeed(stack, targetId, axis ^ 0x43415252);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.96F
                : Mth.clamp(0.08F + intensity * 0.74F + stack.instability() * 0.08F, 0.0F, 0.88F);
        if (unit(seed ^ 0x464C4F41L) > chance) {
            return original;
        }

        int mode = Math.floorMod((int) (seed >>> 30), 6);
        double span = (axis == 0 ? 28.0D : 18.0D) + intensity * (axis == 0 ? 128.0D : 92.0D);
        double mutated = switch (mode) {
            case 0 -> original + signed(seed ^ 0x4F464653L, span);
            case 1 -> quantize(original, 4.0D + unit(seed ^ 0x534E4150L) * (12.0D + intensity * 58.0D));
            case 2 -> original + signed(seed ^ 0x57415645L, span * (0.35D + unit(seed ^ 0x5354524EL) * 0.65D));
            case 3 -> unit(seed ^ 0x535455434BL) * (axis == 0 ? Math.max(32, screen.getXSize()) : Math.max(32, screen.getYSize()));
            case 4 -> original * (unit(seed ^ 0x5343414CL) < 0.48F ? -0.50D - intensity * 1.25D : 0.08D + intensity * 0.44D);
            default -> original + signed(seed ^ 0x50554C5345L, span * 1.35D);
        };
        return Mth.clamp((int) Math.round(mutated), -512, 512 + (axis == 0 ? screen.getXSize() : screen.getYSize()));
    }

    private static AbstractWidget routedWidget(Screen screen, AbstractWidget original, long seed) {
        if (screen == null) {
            return null;
        }
        List<AbstractWidget> candidates = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget != original && widget.visible && widget.active) {
                candidates.add(widget);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(Math.floorMod((int) (seed ^ (seed >>> 32)), candidates.size()));
    }

    private static int routedSlotId(AbstractContainerMenu menu, Slot originalSlot, int originalSlotId, long seed, float intensity) {
        if (menu == null || menu.slots.isEmpty()) {
            return originalSlotId;
        }
        if (unit(seed ^ 0x4F555453494445L) < intensity * 0.10F) {
            return -999;
        }

        List<Slot> candidates = new ArrayList<>();
        for (Slot candidate : menu.slots) {
            if (candidate != originalSlot && candidate.isActive()) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return originalSlotId;
        }
        return candidates.get(Math.floorMod((int) (seed >>> 24), candidates.size())).index;
    }

    private static ClickType mutatedClickType(ClickType original, long seed, boolean carriedFlow) {
        if (original == ClickType.CLONE || original == ClickType.THROW) {
            return ClickType.PICKUP;
        }
        int mode = Math.floorMod((int) (seed >>> 17), carriedFlow ? 4 : 3);
        return switch (mode) {
            case 0 -> ClickType.PICKUP;
            case 1 -> ClickType.QUICK_MOVE;
            case 2 -> ClickType.SWAP;
            default -> ClickType.QUICK_CRAFT;
        };
    }

    private static boolean protectedScreen(Screen screen) {
        return ClientCorruptionProtection.isModScreen(screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(screen)
                || ClientCorruptionProtection.isDeathScreen(screen);
    }

    private static float guiFunctionalityIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 1.0F : stack.intensity(CorruptionSurface.GUI_FUNCTIONALITY),
                stack.targetIntensity(CorruptionSurface.GUI_FUNCTIONALITY, targetId)
        ), 0.0F, 1.0F);
    }

    private static long interactionSeed(CorruptionEffectStack stack, String targetId, int salt) {
        return stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, salt ^ 0x47554946);
    }

    private static int widgetSalt(AbstractWidget widget) {
        if (widget == null) {
            return 0;
        }
        int hash = widget.getClass().getName().hashCode();
        hash = 31 * hash + widget.getX();
        hash = 31 * hash + widget.getY();
        hash = 31 * hash + widget.getWidth();
        hash = 31 * hash + widget.getHeight();
        hash = 31 * hash + widget.getMessage().getString().hashCode();
        return hash;
    }

    private static String widgetTargetId(Screen screen, AbstractWidget widget) {
        String screenName = screen == null ? "no_screen" : screen.getClass().getName();
        String widgetName = widget == null ? "unknown" : widget.getClass().getName();
        String label = widget == null ? "" : widget.getMessage().getString().toLowerCase(Locale.ROOT);
        return "gui_widget_click:" + screenName + ":" + widgetName + ":" + label;
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static double signed(long seed, double amplitude) {
        return (unit(seed) * 2.0D - 1.0D) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    public record SlotClickMutation(boolean cancel, boolean replace, int slotId, int button, ClickType clickType) {
        public static SlotClickMutation pass(int slotId, int button, ClickType clickType) {
            return new SlotClickMutation(false, false, slotId, button, clickType);
        }

        public static SlotClickMutation cancel(int slotId, int button, ClickType clickType) {
            return new SlotClickMutation(true, false, slotId, button, clickType);
        }

        public static SlotClickMutation replace(int slotId, int button, ClickType clickType) {
            return new SlotClickMutation(false, true, slotId, button, clickType);
        }
    }
}
