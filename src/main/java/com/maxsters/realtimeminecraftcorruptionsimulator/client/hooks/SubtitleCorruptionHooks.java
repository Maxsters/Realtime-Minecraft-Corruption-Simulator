package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class SubtitleCorruptionHooks {
    private static final int MAX_TRACKED_SUBTITLES = 14;
    private static final ThreadLocal<Integer> RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<Class<?>, Optional<Field>> SUBTITLE_LIST_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, SubtitleFields> SUBTITLE_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SUBTITLE_METHODS = new ConcurrentHashMap<>();

    private SubtitleCorruptionHooks() {
    }

    public static boolean beforeSound(Object overlay, SoundInstance sound, WeighedSoundEvents events) {
        Component subtitle = subtitle(events);
        if (subtitle == null || sound == null || protectedScreen()) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(sound, subtitle);
        float intensity = subtitleIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return false;
        }

        long seed = stack.stableLong(CorruptionSurface.SOUND_STREAM, targetId + ":capture", subtitle.getString().hashCode());
        float dropChance = stack.extreme(CorruptionSurface.SOUND_STREAM)
                ? 0.36F
                : Mth.clamp(0.02F + intensity * 0.24F + stack.instability() * 0.07F, 0.0F, 0.30F);
        if (unit(seed ^ 0x44524F50L) < dropChance) {
            return true;
        }

        List<?> subtitles = subtitleList(overlay);
        if (subtitles != null && !subtitles.isEmpty()) {
            float clearChance = stack.extreme(CorruptionSurface.SOUND_STREAM)
                    ? 0.14F
                    : Mth.clamp(intensity * 0.08F + stack.instability() * 0.025F, 0.0F, 0.10F);
            if (unit(seed ^ 0x434C4541L) < clearChance) {
                subtitles.clear();
            }
        }
        return false;
    }

    public static void afterSound(Object overlay, SoundInstance sound, WeighedSoundEvents events) {
        Component subtitle = subtitle(events);
        if (subtitle == null || sound == null || protectedScreen()) {
            return;
        }

        List<?> subtitles = subtitleList(overlay);
        if (subtitles == null || subtitles.isEmpty()) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = targetId(sound, subtitle);
        float intensity = subtitleIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.SOUND_STREAM, targetId + ":queue", subtitles.size());
        float chance = stack.extreme(CorruptionSurface.SOUND_STREAM)
                ? 0.94F
                : Mth.clamp(0.08F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (unit(seed ^ 0x51554555L) > chance) {
            trim(subtitles);
            return;
        }

        Object entry = subtitles.get(Math.floorMod((int) (seed >>> 25), subtitles.size()));
        Vec3 base = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null && unit(seed ^ 0x504C4159L) < 0.30F + intensity * 0.34F) {
            base = new Vec3(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
        }

        double radius = 2.0D + intensity * 42.0D + (stack.extreme(CorruptionSurface.SOUND_STREAM) ? 40.0D : 0.0D);
        Vec3 corrupted = new Vec3(
                base.x + signed(seed ^ 0x584F4646L, radius),
                base.y + signed(seed ^ 0x594F4646L, radius * 0.45D),
                base.z + signed(seed ^ 0x5A4F4646L, radius)
        );
        setLocationAndMaybeTime(entry, corrupted, seed, intensity);

        if (subtitles.size() > 1 && unit(seed ^ 0x53574150L) < 0.12F + intensity * 0.32F) {
            int other = Math.floorMod((int) (seed >>> 33), subtitles.size());
            Collections.swap((List<?>) subtitles, subtitles.indexOf(entry), other);
        }
        trim(subtitles);
    }

    public static void beginRender(Object overlay, GuiGraphics graphics) {
        if (graphics == null || protectedScreen()) {
            return;
        }

        List<?> subtitles = subtitleList(overlay);
        if (subtitles == null || subtitles.isEmpty()) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "subtitle_overlay:" + subtitles.size();
        float intensity = subtitleIntensity(stack, targetId);
        if (intensity <= 0.035F) {
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.SOUND_STREAM, targetId, 0x53554252);
        float chance = stack.extreme(CorruptionSurface.SOUND_STREAM)
                ? 0.86F
                : Mth.clamp(0.08F + intensity * 0.56F + stack.instability() * 0.08F, 0.0F, 0.74F);
        if (unit(seed ^ 0x52454E44L) > chance) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float dx = signed(seed ^ 0x584F4646L, width * (0.02F + intensity * 0.18F));
        float dy = signed(seed ^ 0x594F4646L, height * (0.02F + intensity * 0.16F));
        float scale = Mth.clamp(1.0F + signed(seed ^ 0x5343414CL, 0.08F + intensity * 0.42F), 0.45F, 1.90F);

        graphics.pose().pushPose();
        graphics.pose().translate(dx, dy, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        RENDER_DEPTH.set(RENDER_DEPTH.get() + 1);
    }

    public static void endRender(GuiGraphics graphics) {
        if (graphics == null) {
            return;
        }
        int depth = RENDER_DEPTH.get();
        if (depth <= 0) {
            return;
        }
        graphics.pose().popPose();
        RENDER_DEPTH.set(depth - 1);
    }

    private static void setLocationAndMaybeTime(Object subtitle, Vec3 location, long seed, float intensity) {
        SubtitleFields fields = fieldsFor(subtitle.getClass());
        if (fields.location() == null) {
            return;
        }
        try {
            fields.location().set(subtitle, location);
            if (fields.time() != null && unit(seed ^ 0x54494D45L) < 0.20F + intensity * 0.36F) {
                long now = Util.getMillis();
                long offset = Math.round(signed(seed ^ 0x54494D4FL, 1600.0D + intensity * 5200.0D));
                fields.time().setLong(subtitle, Math.max(0L, now + offset));
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void trim(List<?> subtitles) {
        while (subtitles.size() > MAX_TRACKED_SUBTITLES) {
            subtitles.remove(0);
        }
    }

    private static List<?> subtitleList(Object overlay) {
        if (overlay == null) {
            return null;
        }
        Optional<Field> field = SUBTITLE_LIST_FIELDS.computeIfAbsent(overlay.getClass(), SubtitleCorruptionHooks::subtitleListField);
        if (field.isEmpty()) {
            return null;
        }
        try {
            Object value = field.get().get(overlay);
            return value instanceof List<?> list ? list : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Optional<Field> subtitleListField(Class<?> type) {
        return field(type, "subtitles", "f_94638_");
    }

    private static SubtitleFields fieldsFor(Class<?> type) {
        return SUBTITLE_FIELDS.computeIfAbsent(type, ignored -> new SubtitleFields(
                field(type, "text", "f_94648_").orElse(null),
                field(type, "time", "f_94649_").orElse(null),
                field(type, "location", "f_94650_").orElse(null)
        ));
    }

    private static Optional<Field> field(Class<?> type, String mappedName, String srgName) {
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            for (String name : new String[]{mappedName, srgName}) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (NoSuchFieldException | RuntimeException ignored) {
                }
            }
        }
        return Optional.empty();
    }

    private static Component subtitle(WeighedSoundEvents events) {
        if (events == null) {
            return null;
        }
        Optional<Method> method = SUBTITLE_METHODS.computeIfAbsent(events.getClass(), SubtitleCorruptionHooks::subtitleMethod);
        if (method.isEmpty()) {
            return null;
        }
        try {
            Object value = method.get().invoke(events);
            return value instanceof Component component ? component : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Optional<Method> subtitleMethod(Class<?> type) {
        for (String name : new String[]{"getSubtitle", "m_120453_"}) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException | RuntimeException ignored) {
            }
        }
        return Optional.empty();
    }

    private static boolean protectedScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null
                || ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isDeathScreen(minecraft.screen)
                || ClientCorruptionProtection.isSaveCriticalScreen(minecraft.screen)
                || ClientCorruptionProtection.isLifecycleAccessScreen(minecraft.screen);
    }

    private static float subtitleIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.SOUND_STREAM)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.SOUND_STREAM) ? 1.0F : stack.intensity(CorruptionSurface.SOUND_STREAM) * 0.76F,
                stack.targetIntensity(CorruptionSurface.SOUND_STREAM, targetId)
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String targetId(SoundInstance sound, Component subtitle) {
        String location = sound == null || sound.getLocation() == null ? "unknown" : sound.getLocation().toString();
        String text = subtitle == null ? "" : subtitle.getString();
        return "subtitle:" + location + ":" + text;
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
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

    private record SubtitleFields(Field text, Field time, Field location) {
    }
}
