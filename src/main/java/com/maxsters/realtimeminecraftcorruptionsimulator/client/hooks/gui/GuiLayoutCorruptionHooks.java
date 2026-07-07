package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuiLayoutCorruptionHooks {
    private static final WeakHashMap<AbstractContainerScreen<?>, ContainerState> CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<AbstractWidget, WidgetState> WIDGETS = new WeakHashMap<>();
    private static Field leftPosField;
    private static Field topPosField;
    private static Field imageWidthField;
    private static Field imageHeightField;
    private static boolean fieldsChecked;

    private GuiLayoutCorruptionHooks() {
    }

    public static void applyContainerLayout(AbstractContainerScreen<?> screen) {
        ContainerLayout current = readLayout(screen);
        if (current == null) {
            return;
        }
        writeLayout(screen, containerLayout(screen, current.left(), current.top(), current.width(), current.height()));
    }

    public static void applyWidgetLayout(AbstractWidget widget) {
        if (widget == null) {
            return;
        }

        WidgetState state = widgetStateFor(widget);
        WidgetLayout original = new WidgetLayout(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        state.original = original;
        WidgetLayout layout = widgetLayout(widget, state, original);
        state.mutated = layout;
        if (!layout.equals(original)) {
            writeWidgetLayout(widget, layout);
            state.applied = true;
        }
    }

    public static void restoreContainerLayout(AbstractContainerScreen<?> screen) {
        ContainerLayout current = readLayout(screen);
        if (current == null) {
            CONTAINERS.remove(screen);
            return;
        }
        writeLayout(screen, restoreContainerLayout(screen, current.left(), current.top(), current.width(), current.height()));
    }

    public static void restoreWidgetLayout(AbstractWidget widget) {
        if (widget == null) {
            return;
        }

        WidgetState state = WIDGETS.get(widget);
        if (state != null && state.applied) {
            writeWidgetLayout(widget, state.original);
            state.applied = false;
        }
    }

    public static ContainerLayout containerLayout(AbstractContainerScreen<?> screen, int left, int top, int width, int height) {
        if (screen == null) {
            return new ContainerLayout(left, top, width, height);
        }

        ContainerState state = stateFor(screen, left, top, width, height);
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)
                || ClientCorruptionProtection.isModScreen(screen)
                || ClientCorruptionProtection.isDeathScreen(screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(screen)) {
            state.mutated = state.original;
            state.signature = Long.MIN_VALUE;
            return state.original;
        }

        long signature = signature(screen, stack, state.original);
        if (state.signature == signature && state.mutated != null) {
            return state.mutated;
        }

        state.signature = signature;
        state.mutated = mutateContainer(screen, stack, state.original);
        return state.mutated;
    }

    public static ContainerLayout restoreContainerLayout(AbstractContainerScreen<?> screen, int left, int top, int width, int height) {
        ContainerState state = CONTAINERS.remove(screen);
        return state == null ? new ContainerLayout(left, top, width, height) : state.original;
    }

    public static void forget(AbstractContainerScreen<?> screen) {
        if (screen != null) {
            CONTAINERS.remove(screen);
        }
    }

    private static ContainerLayout readLayout(AbstractContainerScreen<?> screen) {
        if (screen == null || !resolveFields()) {
            return null;
        }
        try {
            return new ContainerLayout(
                    leftPosField.getInt(screen),
                    topPosField.getInt(screen),
                    imageWidthField.getInt(screen),
                    imageHeightField.getInt(screen)
            );
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void writeLayout(AbstractContainerScreen<?> screen, ContainerLayout layout) {
        if (screen == null || layout == null || !resolveFields()) {
            return;
        }
        try {
            leftPosField.setInt(screen, layout.left());
            topPosField.setInt(screen, layout.top());
            imageWidthField.setInt(screen, layout.width());
            imageHeightField.setInt(screen, layout.height());
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static boolean resolveFields() {
        if (fieldsChecked) {
            return leftPosField != null && topPosField != null && imageWidthField != null && imageHeightField != null;
        }
        fieldsChecked = true;
        leftPosField = field("leftPos", "f_97735_");
        topPosField = field("topPos", "f_97736_");
        imageWidthField = field("imageWidth", "f_97726_");
        imageHeightField = field("imageHeight", "f_97727_");
        return leftPosField != null && topPosField != null && imageWidthField != null && imageHeightField != null;
    }

    private static Field field(String mappedName, String srgName) {
        for (Class<?> type = AbstractContainerScreen.class; type != null; type = type.getSuperclass()) {
            Field field = declaredField(type, mappedName);
            if (field == null) {
                field = declaredField(type, srgName);
            }
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }

    private static ContainerState stateFor(AbstractContainerScreen<?> screen, int left, int top, int width, int height) {
        ContainerState state = CONTAINERS.get(screen);
        ContainerLayout current = new ContainerLayout(left, top, width, height);
        if (state == null) {
            state = new ContainerState(current);
            CONTAINERS.put(screen, state);
            return state;
        }

        if (!current.equals(state.original) && !current.equals(state.mutated)) {
            state.original = current;
            state.mutated = current;
            state.signature = Long.MIN_VALUE;
        }
        return state;
    }

    private static WidgetState widgetStateFor(AbstractWidget widget) {
        WidgetState state = WIDGETS.get(widget);
        if (state == null) {
            WidgetLayout current = new WidgetLayout(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            state = new WidgetState(current);
            WIDGETS.put(widget, state);
            return state;
        }

        if (state.applied) {
            writeWidgetLayout(widget, state.original);
            state.applied = false;
        }
        return state;
    }

    private static void writeWidgetLayout(AbstractWidget widget, WidgetLayout layout) {
        widget.setX(layout.x());
        widget.setY(layout.y());
        widget.setWidth(layout.width());
        widget.setHeight(layout.height());
    }

    private static ContainerLayout mutateContainer(AbstractContainerScreen<?> screen, CorruptionEffectStack stack, ContainerLayout original) {
        String targetId = "gui_parent_layout:" + screen.getClass().getName();
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.72F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.035F) {
            return original;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.94F
                : Mth.clamp(0.08F + intensity * 0.70F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, 0x50415245) > chance) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x4C41594F);
        int screenWidth = Math.max(1, screen.width);
        int screenHeight = Math.max(1, screen.height);
        int dx = Math.round(signed(seed ^ 0x584F4646L, screenWidth * (0.04F + intensity * 0.22F)));
        int dy = Math.round(signed(seed ^ 0x594F4646L, screenHeight * (0.04F + intensity * 0.20F)));

        int width = original.width();
        int height = original.height();
        if (stack.extreme(CorruptionSurface.GUI_SURFACE) || unit(seed ^ 0x57494454L) < 0.22F + intensity * 0.46F) {
            width = Math.round(Mth.clamp(
                    original.width() + signed(seed ^ 0x57494445L, screenWidth * (0.04F + intensity * 0.20F)),
                    24.0F,
                    screenWidth * 2.0F
            ));
        }
        if (stack.extreme(CorruptionSurface.GUI_SURFACE) || unit(seed ^ 0x48454947L) < 0.22F + intensity * 0.46F) {
            height = Math.round(Mth.clamp(
                    original.height() + signed(seed ^ 0x48454948L, screenHeight * (0.04F + intensity * 0.20F)),
                    24.0F,
                    screenHeight * 2.0F
            ));
        }

        return new ContainerLayout(
                Mth.clamp(original.left() + dx, -screenWidth, screenWidth * 2),
                Mth.clamp(original.top() + dy, -screenHeight, screenHeight * 2),
                width,
                height
        );
    }

    private static WidgetLayout widgetLayout(AbstractWidget widget, WidgetState state, WidgetLayout original) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)
                || screen == null
                || ClientCorruptionProtection.isModScreen(screen)
                || ClientCorruptionProtection.isDeathScreen(screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(screen)
                || screen.getClass().getName().endsWith("ConfirmScreen")) {
            state.mutated = original;
            state.signature = Long.MIN_VALUE;
            return original;
        }

        long signature = widgetSignature(screen, widget, stack, original);
        if (state.signature == signature && state.mutated != null) {
            return state.mutated;
        }

        state.signature = signature;
        state.mutated = mutateWidget(screen, widget, stack, original);
        return state.mutated;
    }

    private static WidgetLayout mutateWidget(Screen screen, AbstractWidget widget, CorruptionEffectStack stack, WidgetLayout original) {
        String targetId = widgetTargetId(screen, widget);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.70F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.025F) {
            return original;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.96F
                : Mth.clamp(0.10F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.90F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, 0x57494447) > chance) {
            return original;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x574C594F);
        int screenWidth = Math.max(1, screen.width);
        int screenHeight = Math.max(1, screen.height);
        int minimumWidth = minimumWidgetWidth(widget, original);
        int minimumHeight = minimumWidgetHeight(widget, original);
        int dx = Math.round(signed(seed ^ 0x584F4646L, screenWidth * (0.025F + intensity * 0.17F)));
        int dy = Math.round(signed(seed ^ 0x594F4646L, screenHeight * (0.022F + intensity * 0.15F)));

        int width = original.width();
        int height = original.height();
        if (unit(seed ^ 0x57534846L) < 0.24F + intensity * 0.46F) {
            float multiplier = 1.0F + signed(seed ^ 0x574D554CL, 0.16F + intensity * 0.92F);
            width = Math.round(Mth.clamp(original.width() * multiplier, minimumWidth, Math.max(minimumWidth, screenWidth * 1.75F)));
        }
        if (unit(seed ^ 0x48534846L) < 0.18F + intensity * 0.38F) {
            float multiplier = 1.0F + signed(seed ^ 0x484D554CL, 0.14F + intensity * 0.70F);
            height = Math.round(Mth.clamp(original.height() * multiplier, minimumHeight, Math.max(minimumHeight, screenHeight * 1.20F)));
        }

        if (unit(seed ^ 0x534E4150L) < 0.06F + intensity * 0.18F) {
            int grid = 2 + Math.round(unit(seed ^ 0x47524944L) * (4.0F + intensity * 20.0F));
            dx = Math.round((float) dx / grid) * grid;
            dy = Math.round((float) dy / grid) * grid;
        }

        return new WidgetLayout(
                Mth.clamp(original.x() + dx, -screenWidth, screenWidth * 2),
                Mth.clamp(original.y() + dy, -screenHeight, screenHeight * 2),
                Math.max(minimumWidth, width),
                Math.max(minimumHeight, height)
        );
    }

    private static int minimumWidgetWidth(AbstractWidget widget, WidgetLayout original) {
        if (widget instanceof AbstractButton || widget instanceof AbstractSliderButton || widget instanceof TabButton) {
            return 20;
        }
        return Math.max(1, Math.min(Math.max(1, original.width()), 8));
    }

    private static int minimumWidgetHeight(AbstractWidget widget, WidgetLayout original) {
        if (widget instanceof AbstractButton || widget instanceof AbstractSliderButton || widget instanceof TabButton) {
            return 20;
        }
        return Math.max(1, Math.min(Math.max(1, original.height()), 8));
    }


    private static long signature(AbstractContainerScreen<?> screen, CorruptionEffectStack stack, ContainerLayout original) {
        long value = stack.fixedSeed();
        value ^= (long) stack.level() << 48;
        value ^= (long) stack.enabledTargetsMask() << 24;
        value ^= (long) stack.layerCount() << 8;
        value ^= screen.getClass().getName().hashCode() * 0x9E3779B97F4A7C15L;
        value ^= (long) screen.width * 0xBF58476D1CE4E5B9L;
        value ^= (long) screen.height * 0x94D049BB133111EBL;
        value ^= original.left() * 31L + original.top() * 17L + original.width() * 13L + original.height();
        return mix(value);
    }

    private static long widgetSignature(Screen screen, AbstractWidget widget, CorruptionEffectStack stack, WidgetLayout original) {
        long value = stack.fixedSeed();
        value ^= (long) stack.level() << 48;
        value ^= (long) stack.enabledTargetsMask() << 24;
        value ^= screen.getClass().getName().hashCode() * 0x9E3779B97F4A7C15L;
        value ^= widget.getClass().getName().hashCode() * 0xBF58476D1CE4E5B9L;
        value ^= widget.getMessage().getString().hashCode() * 0x94D049BB133111EBL;
        value ^= (long) screen.width * 0x632BE59BD9B4E019L;
        value ^= (long) screen.height * 0x85157AF5L;
        value ^= original.x() * 31L + original.y() * 17L + original.width() * 13L + original.height();
        return mix(value);
    }

    private static String widgetTargetId(Screen screen, AbstractWidget widget) {
        return "gui_widget_layout:"
                + screen.getClass().getName()
                + ":"
                + widget.getClass().getName()
                + ":"
                + widget.getMessage().getString();
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static final class ContainerState {
        private ContainerLayout original;
        private ContainerLayout mutated;
        private long signature = Long.MIN_VALUE;

        private ContainerState(ContainerLayout original) {
            this.original = original;
            this.mutated = original;
        }
    }

    private static final class WidgetState {
        private WidgetLayout original;
        private WidgetLayout mutated;
        private long signature = Long.MIN_VALUE;
        private boolean applied;

        private WidgetState(WidgetLayout original) {
            this.original = original;
            this.mutated = original;
        }
    }

    public record ContainerLayout(int left, int top, int width, int height) {
    }

    private record WidgetLayout(int x, int y, int width, int height) {
    }
}
