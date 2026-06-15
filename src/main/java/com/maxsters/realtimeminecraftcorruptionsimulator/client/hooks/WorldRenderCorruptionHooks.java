package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WorldRenderCorruptionHooks {
    private WorldRenderCorruptionHooks() {
    }

    public static boolean shouldSkipChunkLayer(RenderType renderType) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)
                && !stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                && !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String layer = renderType == null ? "unknown" : renderType.toString();
        String targetId = "chunk_layer_failure:" + dimension + ":" + layer;
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                Math.max(
                        (stack.extreme(CorruptionSurface.TEXTURE_MEMORY) ? 1.0F : stack.intensity(CorruptionSurface.TEXTURE_MEMORY)) * 0.58F,
                        (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 1.0F : stack.intensity(CorruptionSurface.MODEL_GEOMETRY)) * 0.48F
                )
        ), 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return false;
        }

        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, 0x43484E4B);
        boolean globalChunkBlank = stack.level() >= 88
                && unit(hash ^ 0x414C4C4CL) < 0.14F + intensity * 0.48F;
        float chance = globalChunkBlank
                ? 0.98F
                : Mth.clamp(0.02F + intensity * 0.46F + stack.instability() * 0.08F, 0.0F, 0.78F);
        return unit(hash ^ 0x534B4950L) < chance;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }
}
