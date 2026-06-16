package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.particle.Particle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnimationSpeedCorruptionHooks {
    private static Field guiTickCountField;
    private static Field guiOverlayMessageTimeField;
    private static Field guiTitleTimeField;
    private static boolean guiTimerFieldsChecked;

    private AnimationSpeedCorruptionHooks() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.isPaused()) {
            return;
        }
        mutateCurrentGuiTimers(minecraft.gui);
    }

    public static float mutateDroppedItemPartial(ItemEntity entity, float partialTick) {
        if (entity == null) {
            return partialTick;
        }
        ItemStack stack = entity.getItem();
        ResourceLocation itemId = stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return mutateRenderPartial(partialTick, "dropped_item_spin:" + (itemId == null ? "empty" : itemId), entity.getId() ^ entity.getAge());
    }

    public static float mutateScreenPartial(float partialTick, String screenId) {
        return mutateRenderPartial(partialTick, "gui_animation:" + screenId, 0x475549);
    }

    public static int mutateParticleAge(Particle particle, int age, int lifetime) {
        if (particle == null || lifetime <= 1 || age < 0) {
            return age;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = "particle_animation:" + particle.getClass().getName();
        float intensity = animationIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return age;
        }

        long clock = animationClock(stack, targetId, System.identityHashCode(particle) ^ age);
        if (!stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                && stack.unit(CorruptionSurface.ANIMATION_TIMING, targetId + ":age_gate", age ^ lifetime) > 0.10F + intensity * 0.64F) {
            return age;
        }

        int mode = Math.floorMod((int) (clock >>> 27), 7);
        int extra = switch (mode) {
            case 0 -> 0;
            case 1 -> Math.round(1.0F + intensity * 7.0F + unit(clock ^ 0x46415354L) * intensity * 9.0F);
            case 2 -> -Math.round(unit(clock ^ 0x524556L) * intensity * 5.0F);
            case 3 -> unit(clock ^ 0x53544F50L) < 0.30F + intensity * 0.38F ? -1 : Math.round(intensity * 4.0F);
            case 4 -> Math.round(signed(clock ^ 0x4A495454L, intensity * 12.0F));
            case 5 -> Math.round((float) Math.sin((gameTime() + age) * (0.18F + intensity * 0.90F)) * intensity * 10.0F);
            default -> (int) Math.round(Math.rint(signed(clock ^ 0x5155414EL, intensity * 6.0F)));
        };
        return Mth.clamp(age + extra, 0, Math.max(0, lifetime - 1));
    }

    public static GuiTickMutation mutateGuiTimers(int tickCount, int overlayMessageTime, int titleTime) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "gui_tick_animation";
        float intensity = animationIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return new GuiTickMutation(tickCount, overlayMessageTime, titleTime);
        }

        long clock = animationClock(stack, targetId, tickCount);
        int mode = Math.floorMod((int) (clock >>> 30), 8);
        int tickDelta = switch (mode) {
            case 0 -> 0;
            case 1 -> Math.round(1.0F + intensity * 10.0F);
            case 2 -> -Math.round(intensity * 5.0F);
            case 3 -> unit(clock ^ (tickCount / 8)) < 0.38F + intensity * 0.34F ? -1 : Math.round(intensity * 4.0F);
            case 4 -> Math.round(signed(clock ^ 0x47554954L, intensity * 18.0F));
            case 5 -> Math.round((float) Math.sin(gameTime() * (0.12F + intensity * 1.20F)) * intensity * 12.0F);
            default -> (int) Math.round(Math.rint(signed(clock ^ 0x5155414EL, intensity * 8.0F)));
        };
        int timerDelta = switch (mode) {
            case 1 -> Math.round(1.0F + intensity * 6.0F);
            case 2 -> -Math.round(intensity * 4.0F);
            case 3 -> unit(clock ^ 0x53544F50L) < 0.50F ? -1 : Math.round(intensity * 5.0F);
            case 5 -> Math.round((float) Math.sin(gameTime() * (0.16F + intensity * 1.10F)) * intensity * 8.0F);
            default -> Math.round(signed(clock ^ 0x54494D45L, intensity * 6.0F));
        };
        return new GuiTickMutation(
                Mth.clamp(tickCount + tickDelta, 0, Integer.MAX_VALUE - 1024),
                overlayMessageTime > 0 ? Mth.clamp(overlayMessageTime - timerDelta, 0, 72000) : overlayMessageTime,
                titleTime > 0 ? Mth.clamp(titleTime - timerDelta, 0, 72000) : titleTime
        );
    }

    private static void mutateCurrentGuiTimers(Gui gui) {
        resolveGuiTimerFields(gui.getClass());
        if (guiTickCountField == null || guiOverlayMessageTimeField == null || guiTitleTimeField == null) {
            return;
        }
        Integer tickCount = readGuiInt(gui, guiTickCountField);
        Integer overlayMessageTime = readGuiInt(gui, guiOverlayMessageTimeField);
        Integer titleTime = readGuiInt(gui, guiTitleTimeField);
        if (tickCount == null || overlayMessageTime == null || titleTime == null) {
            return;
        }

        GuiTickMutation mutation = mutateGuiTimers(tickCount, overlayMessageTime, titleTime);
        writeGuiInt(gui, guiTickCountField, mutation.tickCount());
        writeGuiInt(gui, guiOverlayMessageTimeField, mutation.overlayMessageTime());
        writeGuiInt(gui, guiTitleTimeField, mutation.titleTime());
    }

    private static void resolveGuiTimerFields(Class<?> guiClass) {
        if (guiTimerFieldsChecked) {
            return;
        }
        guiTimerFieldsChecked = true;
        guiTickCountField = findField(guiClass, "tickCount", "f_92989_", "w");
        guiOverlayMessageTimeField = findField(guiClass, "overlayMessageTime", "f_92991_", "y");
        guiTitleTimeField = findField(guiClass, "titleTime", "f_93000_", "I");
    }

    private static Field findField(Class<?> owner, String... names) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Try the next mapping namespace.
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Integer readGuiInt(Gui gui, Field field) {
        try {
            return field.getInt(gui);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void writeGuiInt(Gui gui, Field field, int value) {
        try {
            field.setInt(gui, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static float mutateRenderPartial(float partialTick, String targetId, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        float intensity = animationIntensity(stack, targetId);
        if (intensity <= 0.01F) {
            return partialTick;
        }

        long clock = animationClock(stack, targetId, salt ^ Float.floatToIntBits(partialTick));
        float chance = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 0.96F
                : Mth.clamp(0.08F + intensity * 0.72F + stack.instability() * 0.08F, 0.0F, 0.90F);
        if (stack.unit(CorruptionSurface.ANIMATION_TIMING, targetId + ":partial_gate", salt) > chance) {
            return partialTick;
        }

        int mode = Math.floorMod((int) (clock >>> 29), 8);
        double multiplier = switch (mode) {
            case 0 -> 0.0D;
            case 1 -> Math.pow(2.0D, 7.0D * intensity * (0.25D + unit(clock >>> 7) * 0.75D));
            case 2 -> Math.pow(2.0D, -5.0D * intensity * (0.35D + unit(clock >>> 17) * 0.65D));
            case 3 -> -Math.pow(2.0D, 4.0D * intensity * (0.25D + unit(clock >>> 27) * 0.75D));
            case 4 -> Math.rint(Math.pow(2.0D, signed(clock >>> 37, intensity * 5.0F)));
            case 5 -> Math.sin(gameTime() * (0.10D + intensity * 0.75D)) * (1.0D + intensity * 18.0D);
            default -> Math.pow(2.0D, signed(clock >>> 47, intensity * 6.0F));
        };
        double offset = signed(clock ^ 0x4F464653L, intensity * (mode == 1 ? 48.0F : 12.0F));
        double mutated = partialTick * multiplier + offset;
        return (float) Mth.clamp(mutated, -96.0D, 192.0D);
    }

    private static float animationIntensity(CorruptionEffectStack stack, String targetId) {
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING),
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId)
        ), 0.0F, 1.0F);
    }

    private static long animationClock(CorruptionEffectStack stack, String targetId, int salt) {
        return stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, salt ^ 0x414E494D)
                ^ (gameTime() << 18);
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    public record GuiTickMutation(int tickCount, int overlayMessageTime, int titleTime) {
    }
}
