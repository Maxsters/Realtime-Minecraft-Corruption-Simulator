package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class AdvancementGuiCorruptionHooks {
    private static final WeakHashMap<Object, TreeState> TREE_STATES = new WeakHashMap<>();
    private static final Map<Class<?>, Optional<Method>> TITLE_METHODS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> TREE_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final TreeTransform NONE = new TreeTransform(false, 0.0F, 0.0F, 1.0F, 1.0F);

    private AdvancementGuiCorruptionHooks() {
    }

    public static void beginTreeRender(Object tab, GuiGraphics graphics, int viewportX, int viewportY) {
        if (graphics == null) {
            return;
        }
        TreeTransform transform = treeTransform(tab, graphics.guiWidth(), graphics.guiHeight(), viewportX, viewportY);
        if (!transform.active()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(viewportX, viewportY, 0.0F);
        graphics.pose().translate(transform.dx(), transform.dy(), 0.0F);
        graphics.pose().scale(transform.scaleX(), transform.scaleY(), 1.0F);
        graphics.pose().translate(-viewportX, -viewportY, 0.0F);
        TREE_RENDER_DEPTH.set(TREE_RENDER_DEPTH.get() + 1);
    }

    public static void endTreeRender(GuiGraphics graphics) {
        if (graphics == null) {
            return;
        }
        int depth = TREE_RENDER_DEPTH.get();
        if (depth <= 0) {
            return;
        }
        graphics.pose().popPose();
        TREE_RENDER_DEPTH.set(depth - 1);
    }

    public static double corruptDragDelta(Object tab, double delta, int axis) {
        if (!Double.isFinite(delta) || Math.abs(delta) <= 0.0001D || protectedScreen()) {
            return delta;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "gui_advancements_drag:" + tabTitle(tab) + ":" + axis;
        float intensity = functionalityIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return delta;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.95F
                : Mth.clamp(0.10F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.90F);
        if (stack.unit(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x41445644 ^ axis) > chance) {
            return delta;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x44524147 ^ axis);
        int mode = Math.floorMod((int) (seed >>> 29), 6);
        double scale = 0.25D + unit(seed ^ 0x5343414CL) * (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 6.0D : 1.0D + intensity * 4.0D);
        double mutated = switch (mode) {
            case 0 -> 0.0D;
            case 1 -> -delta * scale;
            case 2 -> delta * scale;
            case 3 -> Math.copySign(Math.max(0.0D, Math.abs(delta) - (2.0D + intensity * 18.0D)), delta);
            case 4 -> Math.copySign(2.0D + unit(seed ^ 0x53544550L) * (18.0D + intensity * 90.0D), delta);
            default -> delta + signed(seed ^ 0x4F464653L, 4.0D + intensity * 80.0D);
        };
        return Mth.clamp(mutated, -240.0D, 240.0D);
    }

    private static TreeTransform treeTransform(Object tab, int screenWidth, int screenHeight, int viewportX, int viewportY) {
        if (tab == null || protectedScreen()) {
            forget(tab);
            return NONE;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            forget(tab);
            return NONE;
        }

        String title = tabTitle(tab);
        String targetId = "gui_advancements_tree:" + title;
        float intensity = surfaceIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            forget(tab);
            return NONE;
        }

        TreeState state = TREE_STATES.computeIfAbsent(tab, ignored -> new TreeState());
        long signature = signature(stack, title, screenWidth, screenHeight, viewportX, viewportY);
        if (state.signature == signature && state.transform != null) {
            return state.transform;
        }

        state.signature = signature;
        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.96F
                : Mth.clamp(0.10F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.92F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, 0x41445654) > chance) {
            state.transform = NONE;
            return NONE;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, 0x54524545);
        float dx = signed(seed ^ 0x584F4646L, 10.0F + screenWidth * (0.02F + intensity * 0.12F));
        float dy = signed(seed ^ 0x594F4646L, 8.0F + screenHeight * (0.02F + intensity * 0.10F));
        float scaleX = 1.0F;
        float scaleY = 1.0F;

        int mode = Math.floorMod((int) (seed >>> 30), 6);
        if (mode == 1 || mode == 4 || stack.extreme(CorruptionSurface.GUI_SURFACE)) {
            scaleX = Mth.clamp(1.0F + signed(seed ^ 0x53584F46L, 0.10F + intensity * 0.55F), 0.35F, 2.20F);
        }
        if (mode == 2 || mode == 4 || stack.extreme(CorruptionSurface.GUI_SURFACE)) {
            scaleY = Mth.clamp(1.0F + signed(seed ^ 0x53594F46L, 0.10F + intensity * 0.55F), 0.35F, 2.20F);
        }
        if (mode == 3) {
            dx = Math.round(dx / 8.0F) * 8.0F;
            dy = Math.round(dy / 8.0F) * 8.0F;
        } else if (mode == 5) {
            scaleX = Mth.clamp(scaleX * -1.0F, -2.20F, -0.35F);
        }

        state.transform = new TreeTransform(true, dx, dy, scaleX, scaleY);
        return state.transform;
    }

    private static void forget(Object tab) {
        if (tab != null) {
            TREE_STATES.remove(tab);
        }
    }

    private static boolean protectedScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null
                || minecraft.screen == null
                || ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isDeathScreen(minecraft.screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(minecraft.screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(minecraft.screen);
    }

    private static float surfaceIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.0F : stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.74F,
                stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId)
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static float functionalityIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_FUNCTIONALITY)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 1.0F : stack.intensity(CorruptionSurface.GUI_FUNCTIONALITY) * 0.78F,
                stack.targetIntensity(CorruptionSurface.GUI_FUNCTIONALITY, targetId)
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String tabTitle(Object tab) {
        if (tab == null) {
            return "unknown";
        }
        Optional<Method> method = TITLE_METHODS.computeIfAbsent(tab.getClass(), AdvancementGuiCorruptionHooks::titleMethod);
        if (method.isEmpty()) {
            return tab.getClass().getName();
        }
        try {
            Object value = method.get().invoke(tab);
            if (value instanceof Component component) {
                return component.getString();
            }
            return value == null ? tab.getClass().getName() : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return tab.getClass().getName();
        }
    }

    private static Optional<Method> titleMethod(Class<?> type) {
        String[] names = {"getTitle", "m_97189_"};
        for (String name : names) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException | RuntimeException ignored) {
            }
        }
        return Optional.empty();
    }

    private static long signature(CorruptionEffectStack stack, String title, int screenWidth, int screenHeight, int viewportX, int viewportY) {
        long value = stack.fixedSeed();
        value ^= (long) stack.level() << 48;
        value ^= (long) stack.enabledTargetsMask() << 24;
        value ^= (long) stack.layerCount() << 8;
        value ^= (long) title.hashCode() * 0x9E3779B97F4A7C15L;
        value ^= (long) screenWidth * 0xBF58476D1CE4E5B9L;
        value ^= (long) screenHeight * 0x94D049BB133111EBL;
        value ^= viewportX * 31L + viewportY * 17L;
        return mix(value);
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
    }

    private static double signed(long seed, double amplitude) {
        return (unit(seed) * 2.0D - 1.0D) * amplitude;
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

    private static final class TreeState {
        private TreeTransform transform;
        private long signature = Long.MIN_VALUE;
    }

    private record TreeTransform(boolean active, float dx, float dy, float scaleX, float scaleY) {
    }
}
