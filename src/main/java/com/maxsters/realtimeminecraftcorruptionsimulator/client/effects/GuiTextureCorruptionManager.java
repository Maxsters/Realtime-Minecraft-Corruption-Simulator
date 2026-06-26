package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.overlay.CorruptionOverlayManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuiTextureCorruptionManager {
    private static final WeakHashMap<AbstractWidget, WidgetSnapshot> ORIGINAL_WIDGETS = new WeakHashMap<>();
    private static Field packedFgColorField;
    private static boolean packedFgColorFieldChecked;
    private static long lastWidgetStateReportMs;
    private static long lastInputReportMs;

    private GuiTextureCorruptionManager() {
    }

    public static void onSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        CorruptionEffectStack previousStack = CorruptionEffectStack.from(previous);
        CorruptionEffectStack currentStack = CorruptionEffectStack.from(current);
        if (previousStack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)
                && !currentStack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            restoreCurrentScreenWidgets();
        }
    }

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (ClientCorruptionProtection.isModScreen(event.getScreen()) || ClientCorruptionProtection.isSaveCriticalScreen(event.getScreen())) {
            return;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return;
        }
        TextureMutationManager.requestGuiTextureScan();
        mutateWidgetState(event.getScreen(), event.getListenersList(), stack);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        if (screen == null || ClientCorruptionProtection.isModScreen(screen) || ClientCorruptionProtection.isSaveCriticalScreen(screen)) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            mutateWidgetState(screen, screen.children(), stack);
        } else {
            restoreWidgetState(screen, screen.children());
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (CorruptionOverlayManager.isOverlayHit(event.getMouseX(), event.getMouseY())) {
            return;
        }
        if (breakInput(event.getScreen(), "mouse_press", event.getButton() ^ (int) event.getMouseX() ^ ((int) event.getMouseY() << 8))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (CorruptionOverlayManager.isOverlayHit(event.getMouseX(), event.getMouseY())) {
            return;
        }
        if (breakInput(event.getScreen(), "mouse_release", event.getButton() ^ (int) event.getMouseX() ^ ((int) event.getMouseY() << 8))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (CorruptionOverlayManager.isOverlayHit(event.getMouseX(), event.getMouseY())) {
            return;
        }
        if (breakInput(event.getScreen(), "mouse_drag", event.getMouseButton() ^ (int) Math.round(event.getDragX() * 31.0D) ^ (int) Math.round(event.getDragY() * 17.0D))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (CorruptionOverlayManager.isOverlayHit(event.getMouseX(), event.getMouseY())) {
            return;
        }
        if (breakInput(event.getScreen(), "mouse_scroll", (int) Math.round(event.getScrollDelta() * 100.0D) ^ (int) event.getMouseX() ^ ((int) event.getMouseY() << 6))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (CorruptionOverlayManager.isSeedEditing()) {
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        if (breakInput(event.getScreen(), "key_press", event.getKeyCode() ^ (event.getScanCode() << 8) ^ (event.getModifiers() << 16))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (CorruptionOverlayManager.isSeedEditing()) {
            return;
        }
        if (breakInput(event.getScreen(), "char_typed", event.getCodePoint() ^ (event.getModifiers() << 12))) {
            event.setCanceled(true);
        }
    }

    private static void mutateWidgetState(Screen screen, Iterable<? extends GuiEventListener> listeners, CorruptionEffectStack stack) {
        if (ClientCorruptionProtection.isModScreen(screen)) {
            return;
        }
        Set<AbstractWidget> widgets = collectWidgets(screen, listeners);
        if (widgets.isEmpty()) {
            return;
        }

        boolean mutated = false;
        int index = 0;
        for (AbstractWidget widget : widgets) {
            mutated |= mutateWidgetState(screen, widget, stack, index++);
        }

        long now = System.currentTimeMillis();
        if (mutated && now - lastWidgetStateReportMs > 1500L) {
            lastWidgetStateReportMs = now;
        }
    }

    private static Set<AbstractWidget> collectWidgets(Screen screen, Iterable<? extends GuiEventListener> listeners) {
        Set<AbstractWidget> widgets = new LinkedHashSet<>();
        for (Renderable renderable : screen.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                widget.visitWidgets(widgets::add);
            }
        }
        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget) {
                widget.visitWidgets(widgets::add);
            }
        }
        return widgets;
    }

    private static boolean mutateWidgetState(Screen screen, AbstractWidget widget, CorruptionEffectStack stack, int index) {
        WidgetSnapshot original = ORIGINAL_WIDGETS.computeIfAbsent(widget, WidgetSnapshot::capture);
        String targetId = widgetTarget(screen, widget, index, original);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.58F);
        if (intensity <= 0.035F) {
            return false;
        }
        float chance = Math.min(1.0F, 0.16F + intensity * 0.78F + stack.instability() * 0.16F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, 0x515549) > chance) {
            return false;
        }

        long clock = screen.getClass().getName().hashCode() * 31L + stack.level() * 17L + stack.layerCount();
        int screenWidth = Math.max(1, screen.width);
        boolean changed = false;
        boolean preserveWorldAccess = ClientCorruptionProtection.isLifecycleAccessScreen(screen);

        widget.active = original.active();
        widget.visible = original.visible();
        widget.setX(original.x());
        widget.setY(original.y());
        if (!preserveWorldAccess && (stack.extreme(CorruptionSurface.GUI_SURFACE) || CorruptionValueMutator.decision(stack, CorruptionSurface.GUI_SURFACE, targetId + ":width", index ^ 0x72, 0.48F))) {
            int width = stack.extreme(CorruptionSurface.GUI_SURFACE)
                    ? Math.round(clampFloat(original.width() + signed(stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId + ":w", 0x3A), screenWidth * (0.18F + intensity * 0.58F)), 1.0F, screenWidth * 2.0F))
                    : Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.GUI_SURFACE, targetId + ":w", original.width(), screenWidth * (0.18F + intensity * 0.58F), 1.0F, screenWidth * 2.0F, 0x3A, clock));
            widget.setWidth(width);
            changed = true;
        } else if (!preserveWorldAccess) {
            widget.setWidth(original.width());
        }
        if (stack.extreme(CorruptionSurface.GUI_SURFACE) || CorruptionValueMutator.decision(stack, CorruptionSurface.GUI_SURFACE, targetId + ":alpha", index ^ 0x1D, 0.42F)) {
            float minAlpha = preserveWorldAccess ? 0.34F : 0.0F;
            widget.setAlpha(stack.extreme(CorruptionSurface.GUI_SURFACE)
                    ? clampFloat(0.55F + signed(stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId + ":a", 0x43), 0.62F), minAlpha, 1.0F)
                    : CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.GUI_SURFACE, targetId + ":a", original.alpha(), 1.2F, minAlpha, 1.0F, 0x43, clock));
            changed = true;
        } else {
            widget.setAlpha(original.alpha());
        }
        if (stack.extreme(CorruptionSurface.GUI_SURFACE) || CorruptionValueMutator.decision(stack, CorruptionSurface.GUI_SURFACE, targetId + ":color", index ^ 0x53, 0.54F)) {
            widget.setFGColor(stack.extreme(CorruptionSurface.GUI_SURFACE)
                    ? directColor(stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId + ":fg", 0x58))
                    : CorruptionValueMutator.mutateColor(stack, CorruptionSurface.GUI_SURFACE, targetId + ":fg", original.fgColor(), 0x58, clock));
            changed = true;
        } else {
            original.restoreFgColor(widget);
        }
        widget.setMessage(original.message());
        return changed;
    }

    private static void restoreCurrentScreenWidgets() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        if (screen == null || ClientCorruptionProtection.isModScreen(screen) || ClientCorruptionProtection.isSaveCriticalScreen(screen)) {
            return;
        }
        restoreWidgetState(screen, screen.children());
    }

    private static void restoreWidgetState(Screen screen, Iterable<? extends GuiEventListener> listeners) {
        boolean clearLifecycleColors = ClientCorruptionProtection.isLifecycleAccessScreen(screen);
        for (AbstractWidget widget : collectWidgets(screen, listeners)) {
            WidgetSnapshot original = ORIGINAL_WIDGETS.get(widget);
            if (original != null) {
                original.restore(widget);
            } else if (clearLifecycleColors) {
                widget.clearFGColor();
            }
            if (clearLifecycleColors) {
                widget.clearFGColor();
            }
        }
    }

    private static boolean breakInput(Screen screen, String action, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (screen == null
                || !stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)
                || ClientCorruptionProtection.isModScreen(screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(screen)) {
            return false;
        }
        String targetId = "input:" + screen.getClass().getName() + ":" + action;
        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.62F);
        float chance = Math.min(0.72F, 0.05F + intensity * 0.44F + stack.instability() * 0.14F);
        boolean broken = stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) < chance;
        if (broken) {
            long now = System.currentTimeMillis();
            if (now - lastInputReportMs > 1500L) {
                lastInputReportMs = now;
            }
        }
        return broken;
    }

    private static String widgetTarget(Screen screen, AbstractWidget widget, int index, WidgetSnapshot original) {
        return screen.getClass().getName() + ":" + widget.getClass().getName() + ":" + index + ":" + original.x() + "," + original.y() + ":" + original.width() + "x" + widget.getHeight();
    }

    private static int stableHash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) value;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float signed(long value, float amplitude) {
        return (((stableHash(value) >>> 8) & 0xFFFF) / 32767.5F - 1.0F) * amplitude;
    }

    private static int directColor(long value) {
        int hash = stableHash(value);
        int red = hash & 0xFF;
        int green = (hash >>> 8) & 0xFF;
        int blue = (hash >>> 16) & 0xFF;
        if (((hash >>> 24) & 3) == 0) {
            red = green = blue = ((hash >>> 27) & 1) == 0 ? 0 : 255;
        }
        return red << 16 | green << 8 | blue;
    }

    private static int packedFgColor(AbstractWidget widget) {
        Field field = packedFgColorField();
        if (field == null) {
            return AbstractWidget.UNSET_FG_COLOR;
        }
        try {
            return field.getInt(widget);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return AbstractWidget.UNSET_FG_COLOR;
        }
    }

    private static Field packedFgColorField() {
        if (packedFgColorFieldChecked) {
            return packedFgColorField;
        }
        packedFgColorFieldChecked = true;
        try {
            packedFgColorField = AbstractWidget.class.getDeclaredField("packedFGColor");
            packedFgColorField.setAccessible(true);
        } catch (NoSuchFieldException | RuntimeException ignored) {
            packedFgColorField = null;
        }
        return packedFgColorField;
    }

    private record WidgetSnapshot(int x, int y, int width, boolean active, boolean visible, float alpha, int fgColor, int packedFgColor, Component message) {
        private static WidgetSnapshot capture(AbstractWidget widget) {
            return new WidgetSnapshot(widget.getX(), widget.getY(), widget.getWidth(), widget.active, widget.visible, 1.0F, widget.getFGColor(), GuiTextureCorruptionManager.packedFgColor(widget), widget.getMessage());
        }

        private void restore(AbstractWidget widget) {
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(width);
            widget.active = active;
            widget.visible = visible;
            widget.setAlpha(alpha);
            restoreFgColor(widget);
            widget.setMessage(message);
        }

        private void restoreFgColor(AbstractWidget widget) {
            if (packedFgColor == AbstractWidget.UNSET_FG_COLOR) {
                widget.clearFGColor();
            } else {
                widget.setFGColor(packedFgColor);
            }
        }
    }

}
