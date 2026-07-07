package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class GuiDirectTextureCorruptionHooks {
    private static final List<ResourceLocation> TEXTURE_POOL = new ArrayList<>();
    private static final Set<ResourceLocation> TEXTURE_POOL_IDS = new HashSet<>();
    private static final ThreadLocal<RawTextureState> RAW_TEXTURE = new ThreadLocal<>();

    private GuiDirectTextureCorruptionHooks() {
    }

    public static DirectDraw corruptGuiBlit(ResourceLocation texture, int minX, int maxX, int minY, int maxY, int z, int sourceWidth, int sourceHeight, float sourceU, float sourceV, int textureWidth, int textureHeight) {
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return null;
        }
        remember(texture);

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return null;
        }

        String targetId = targetId("gui_direct_texture", texture);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return null;
        }

        int salt = texture.hashCode();
        salt = salt * 31 + sourceWidth;
        salt = salt * 31 + sourceHeight;
        salt = salt * 31 + Math.round(sourceU * 16.0F);
        salt = salt * 31 + Math.round(sourceV * 16.0F);
        salt = salt * 31 + Math.max(1, maxX - minX);
        salt = salt * 31 + Math.max(1, maxY - minY);
        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.99F
                : Mth.clamp(0.12F + intensity * 0.78F + stack.instability() * 0.08F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return null;
        }

        ResourceLocation replacement = replacementTexture(stack, texture, targetId, salt);
        RawTextureState state = state(stack, replacement, targetId, salt ^ 0x44495254, intensity, textureWidth, textureHeight);
        Uv uv0 = state.uv(sourceU, sourceV);
        Uv uv1 = state.uv(sourceU + Math.max(1, sourceWidth), sourceV + Math.max(1, sourceHeight));
        return new DirectDraw(replacement, uv0.u(), uv1.u(), uv0.v(), uv1.v());
    }

    public static ResourceLocation bindRawTexture(ResourceLocation texture, String context, int salt) {
        RAW_TEXTURE.remove();
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return texture;
        }
        remember(texture);

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return texture;
        }

        String targetId = targetId(context, texture);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return texture;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.99F
                : Mth.clamp(0.12F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return texture;
        }

        ResourceLocation replacement = replacementTexture(stack, texture, targetId, salt);
        RAW_TEXTURE.set(state(stack, replacement, targetId, salt ^ 0x524157, intensity, 1, 1));
        return replacement;
    }

    public static Uv rawUv(float u, float v) {
        RawTextureState state = RAW_TEXTURE.get();
        return state == null ? new Uv(u, v) : state.rawUv(u, v);
    }

    public static void clearRawTexture() {
        RAW_TEXTURE.remove();
    }

    private static void remember(ResourceLocation texture) {
        if (texture == null || texture.getPath().isEmpty() || ClientCorruptionProtection.isProtectedResource(texture)) {
            return;
        }
        synchronized (TEXTURE_POOL) {
            if (TEXTURE_POOL_IDS.add(texture)) {
                TEXTURE_POOL.add(insertionIndex(texture), texture);
            }
        }
    }

    private static ResourceLocation replacementTexture(CorruptionEffectStack stack, ResourceLocation original, String targetId, int salt) {
        synchronized (TEXTURE_POOL) {
            if (TEXTURE_POOL.size() < 2) {
                return original;
            }
            int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.GUI_SURFACE, targetId, salt, TEXTURE_POOL.size());
            ResourceLocation replacement = TEXTURE_POOL.get(index);
            if (replacement.equals(original)) {
                replacement = TEXTURE_POOL.get((index + 1) % TEXTURE_POOL.size());
            }
            return replacement;
        }
    }

    private static RawTextureState state(CorruptionEffectStack stack, ResourceLocation texture, String targetId, int salt, float intensity, int textureWidth, int textureHeight) {
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, salt);
        boolean extreme = stack.extreme(CorruptionSurface.GUI_SURFACE);
        float uScale = extreme
                ? 0.10F + unit(seed ^ 0x5553434CL) * 8.5F
                : 0.45F + unit(seed ^ 0x5553434CL) * (1.15F + intensity * 4.8F);
        float vScale = extreme
                ? 0.10F + unit(seed ^ 0x5653434CL) * 8.5F
                : 0.45F + unit(seed ^ 0x5653434CL) * (1.15F + intensity * 4.8F);
        float leakU = extreme ? 5.5F : 0.08F + intensity * 2.8F;
        float leakV = extreme ? 5.5F : 0.08F + intensity * 2.8F;
        float uOffset = signed(seed ^ 0x554F4646L, leakU);
        float vOffset = signed(seed ^ 0x564F4646L, leakV);
        boolean flipU = unit(seed ^ 0x464C4955L) < 0.18F + intensity * 0.36F;
        boolean flipV = unit(seed ^ 0x464C4956L) < 0.18F + intensity * 0.36F;
        int safeWidth = Math.max(1, textureWidth);
        int safeHeight = Math.max(1, textureHeight);
        return new RawTextureState(texture, uOffset, vOffset, uScale, vScale, flipU, flipV, safeWidth, safeHeight);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.84F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String targetId(String context, ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft == null || minecraft.screen == null ? "no_screen" : minecraft.screen.getClass().getName();
        return context + ":" + screen + ":" + texture;
    }

    private static int insertionIndex(ResourceLocation id) {
        String key = id.toString();
        int low = 0;
        int high = TEXTURE_POOL.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            String existingKey = TEXTURE_POOL.get(mid).toString();
            if (existingKey.compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
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

    public record DirectDraw(ResourceLocation texture, float u0, float u1, float v0, float v1) {
    }

    public record Uv(float u, float v) {
    }

    private record RawTextureState(ResourceLocation texture, float uOffset, float vOffset, float uScale, float vScale, boolean flipU, boolean flipV, int textureWidth, int textureHeight) {
        private Uv uv(float sourceU, float sourceV) {
            float u = sourceU / (float) textureWidth;
            float v = sourceV / (float) textureHeight;
            return rawUv(u, v);
        }

        private Uv rawUv(float u, float v) {
            float localU = flipU ? 1.0F - u : u;
            float localV = flipV ? 1.0F - v : v;
            return new Uv(localU * uScale + uOffset, localV * vScale + vOffset);
        }
    }
}
