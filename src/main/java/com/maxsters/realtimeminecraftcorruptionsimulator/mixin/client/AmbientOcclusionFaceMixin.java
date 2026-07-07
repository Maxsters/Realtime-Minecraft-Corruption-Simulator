package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.LightingCorruptionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.BitSet;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class AmbientOcclusionFaceMixin {
    @Unique
    private static volatile Field rmc$brightnessField;
    @Unique
    private static volatile Field rmc$lightmapField;
    @Unique
    private static volatile boolean rmc$fieldsChecked;

    @Inject(
            method = {
                    "calculate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;[FLjava/util/BitSet;Z)V",
                    "m_111167_"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$corruptSmoothLighting(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction face, float[] shape, BitSet flags, boolean shade, CallbackInfo callback) {
        float[] brightness = rmc$brightness(this);
        int[] lightmap = rmc$lightmap(this);
        LightingCorruptionHooks.mutateAmbientOcclusion(level, state, pos, face, brightness, lightmap);
    }

    @Unique
    private static float[] rmc$brightness(Object owner) {
        Field field = rmc$field(owner, true);
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(owner);
            return value instanceof float[] values ? values : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Unique
    private static int[] rmc$lightmap(Object owner) {
        Field field = rmc$field(owner, false);
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(owner);
            return value instanceof int[] values ? values : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Unique
    private static Field rmc$field(Object owner, boolean brightness) {
        if (!rmc$fieldsChecked) {
            rmc$resolveFields(owner.getClass());
        }
        return brightness ? rmc$brightnessField : rmc$lightmapField;
    }

    @Unique
    private static synchronized void rmc$resolveFields(Class<?> owner) {
        if (rmc$fieldsChecked) {
            return;
        }
        rmc$brightnessField = rmc$findField(owner, "brightness", "f_111149_");
        rmc$lightmapField = rmc$findField(owner, "lightmap", "f_111150_");
        rmc$fieldsChecked = true;
    }

    @Unique
    private static Field rmc$findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
