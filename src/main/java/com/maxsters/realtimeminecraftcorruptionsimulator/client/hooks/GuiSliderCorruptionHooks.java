package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuiSliderCorruptionHooks {
    private static final ThreadLocal<Boolean> APPLYING_IMPOSSIBLE_VALUE = ThreadLocal.withInitial(() -> false);
    private static final Set<AbstractSliderButton> CORRUPTED_SLIDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Class<?>, List<Field>> NESTED_SLIDER_FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Field> NAMED_NESTED_SLIDER_FIELDS = new ConcurrentHashMap<>();

    private GuiSliderCorruptionHooks() {
    }

    public static boolean shouldCorruptSlider(AbstractSliderButton button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(minecraft.screen)) {
            return false;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return false;
        }

        String targetId = targetId(button);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.72F);
        return intensity > 0.04F
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, sliderSalt(button) ^ 0x534C4944)
                < Mth.clamp(0.10F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.96F);
    }

    public static double corruptSliderValue(AbstractSliderButton button, double clampedValue) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(button);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.72F);
        long hash = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, sliderSalt(button) ^ 0x534C4952);
        int mode = Math.floorMod((int) (hash >>> 30), 5);
        double span = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 5.0D : 1.3D + intensity * 3.0D;
        double corrupted = switch (mode) {
            case 0 -> -span * (0.15D + unit(hash ^ 0x4E4547L) * 0.85D);
            case 1 -> 1.0D + span * (0.10D + unit(hash ^ 0x504F53L) * 0.90D);
            case 2 -> clampedValue + (unit(hash ^ 0x4F4646L) * 2.0D - 1.0D) * span;
            case 3 -> Math.rint((clampedValue - span * 0.5D) * (2.0D + intensity * 10.0D)) / (2.0D + intensity * 10.0D);
            default -> unit(hash ^ 0x455854L) < 0.5D ? -1.0D - span : 2.0D + span;
        };
        return Mth.clamp(corrupted, -3.0D, 4.0D);
    }

    public static void markSliderCorrupted(AbstractSliderButton button) {
        if (button != null) {
            CORRUPTED_SLIDERS.add(button);
        }
    }

    public static boolean consumeSliderRepair(AbstractSliderButton button, double currentValue) {
        if (button == null) {
            return false;
        }
        boolean wasCorrupted = CORRUPTED_SLIDERS.remove(button);
        return wasCorrupted || currentValue < 0.0D || currentValue > 1.0D || Double.isNaN(currentValue);
    }

    public static void beginImpossibleSliderValue() {
        APPLYING_IMPOSSIBLE_VALUE.set(true);
    }

    public static void endImpossibleSliderValue() {
        APPLYING_IMPOSSIBLE_VALUE.set(false);
    }

    public static boolean isApplyingImpossibleSliderValue() {
        return APPLYING_IMPOSSIBLE_VALUE.get();
    }

    public static void corruptNestedSliders(Screen screen, String action) {
        if (screen == null || screen.getClass().getName().equals("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen")) {
            return;
        }
        for (Field field : nestedSliderFields(screen.getClass())) {
            try {
                Class<?> type = field.getType();
                if (type == Float.TYPE) {
                    float value = field.getFloat(screen);
                    field.setFloat(screen, corruptNestedSliderValue(screen, field.getName(), value, action.hashCode()));
                } else if (type == Double.TYPE) {
                    double value = field.getDouble(screen);
                    field.setDouble(screen, corruptNestedSliderValue(screen, field.getName(), value, action.hashCode()));
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
    }

    public static float corruptNestedSliderValue(Object owner, String sliderId, float clampedValue, int salt) {
        return (float) corruptNestedSliderValue(owner, sliderId, (double) clampedValue, salt);
    }

    public static void corruptNestedSliderField(Object owner, String mappedName, String srgName, String sliderId, int salt) {
        if (owner == null) {
            return;
        }
        Field field = namedSliderField(owner.getClass(), mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            if (field.getType() == Float.TYPE) {
                field.setFloat(owner, corruptNestedSliderValue(owner, sliderId, field.getFloat(owner), salt));
            } else if (field.getType() == Double.TYPE) {
                field.setDouble(owner, corruptNestedSliderValue(owner, sliderId, field.getDouble(owner), salt));
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    public static double corruptNestedSliderValue(Object owner, String sliderId, double clampedValue, int salt) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft == null ? null : minecraft.screen;
        if (minecraft == null
                || ClientCorruptionProtection.isModScreen(screen)
                || ClientCorruptionProtection.isDeathScreen(screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(screen)
                || !Double.isFinite(clampedValue)) {
            return Mth.clamp(clampedValue, 0.0D, 1.0D);
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return Mth.clamp(clampedValue, 0.0D, 1.0D);
        }

        String ownerName = owner == null ? "unknown" : owner.getClass().getName();
        String targetId = "gui_nested_slider:" + ownerName + ":" + sliderId;
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.74F);
        if (intensity <= 0.04F) {
            return clampedValue;
        }

        int stableSalt = stableString(sliderId) ^ salt ^ 0x4E534C44;
        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.96F
                : Mth.clamp(0.10F + intensity * 0.74F + stack.instability() * 0.10F, 0.0F, 0.92F);
        if (stack.unit(CorruptionSurface.GUI_SURFACE, targetId, stableSalt) > chance) {
            return clampedValue;
        }

        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, stableSalt ^ 0x5343524C);
        int mode = Math.floorMod((int) (seed >>> 30), 6);
        double span = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.45D : 0.28D + intensity * 1.10D;
        double corrupted = switch (mode) {
            case 0 -> -span * (0.05D + unit(seed ^ 0x4E4547L) * 0.70D);
            case 1 -> 1.0D + span * (0.05D + unit(seed ^ 0x504F53L) * 0.70D);
            case 2 -> clampedValue + (unit(seed ^ 0x4F4646L) * 2.0D - 1.0D) * span;
            case 3 -> unit(seed ^ 0x53545543L) < 0.5D ? 0.0D : 1.0D;
            case 4 -> Math.rint((clampedValue - span * 0.35D) * (2.0D + intensity * 12.0D)) / (2.0D + intensity * 12.0D);
            default -> 1.0D - clampedValue + (unit(seed ^ 0x464C4950L) * 2.0D - 1.0D) * span * 0.35D;
        };
        return Mth.clamp(corrupted, -0.75D, 1.75D);
    }

    private static String targetId(AbstractSliderButton button) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft == null || minecraft.screen == null ? "no_screen" : minecraft.screen.getClass().getName();
        return "gui_slider:" + screen + ":" + (button == null ? "unknown" : button.getClass().getName());
    }

    private static int sliderSalt(AbstractSliderButton button) {
        if (button == null) {
            return 0;
        }
        int hash = button.getClass().getName().hashCode();
        hash = 31 * hash + button.getX();
        hash = 31 * hash + button.getY();
        hash = 31 * hash + button.getWidth();
        hash = 31 * hash + button.getHeight();
        hash = 31 * hash + button.getMessage().getString().hashCode();
        return hash;
    }

    private static List<Field> nestedSliderFields(Class<?> type) {
        return NESTED_SLIDER_FIELDS.computeIfAbsent(type, GuiSliderCorruptionHooks::scanNestedSliderFields);
    }

    private static List<Field> scanNestedSliderFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType != Float.TYPE && fieldType != Double.TYPE) {
                    continue;
                }
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("scroll") && !name.contains("slider") && !name.contains("thumb")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return List.copyOf(fields);
    }

    private static Field namedSliderField(Class<?> ownerClass, String mappedName, String srgName) {
        String key = ownerClass.getName() + ":" + mappedName + ":" + srgName;
        Field cached = NAMED_NESTED_SLIDER_FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> cursor = ownerClass; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            Field field = declaredField(cursor, mappedName);
            if (field == null) {
                field = declaredField(cursor, srgName);
            }
            if (field != null && (field.getType() == Float.TYPE || field.getType() == Double.TYPE)) {
                try {
                    field.setAccessible(true);
                    NAMED_NESTED_SLIDER_FIELDS.put(key, field);
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

    private static int stableString(String value) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static double unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }
}
