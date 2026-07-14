package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class DirectTextureCorruptionHooks {
    private static final List<ResourceLocation> TEXTURE_POOL = new ArrayList<>();
    private static final Set<ResourceLocation> TEXTURE_POOL_IDS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<RawTextureState> RAW_TEXTURE = new ThreadLocal<>();

    private DirectTextureCorruptionHooks() {
    }

    public static void updateTexturePool(Collection<ResourceLocation> textureIds) {
        synchronized (TEXTURE_POOL) {
            TEXTURE_POOL.clear();
            TEXTURE_POOL_IDS.clear();
            if (textureIds == null) {
                return;
            }
            List<ResourceLocation> sorted = new ArrayList<>(textureIds);
            sorted.sort(DirectTextureCorruptionHooks::compareTexturePriority);
            for (ResourceLocation textureId : sorted) {
                rememberLocked(textureId);
            }
        }
    }

    public static ResourceLocation bindTexture(ResourceLocation texture, String context, int salt, boolean trackUv) {
        if (trackUv) {
            RAW_TEXTURE.remove();
        }
        if (!isDirectTextureCandidate(texture)) {
            return texture;
        }
        remember(texture);

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return texture;
        }

        String targetId = targetId(context, texture);
        float intensity = directTextureIntensity(stack, targetId);
        if (intensity <= 0.025F) {
            return texture;
        }

        float chance = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 0.99F
                : Mth.clamp(0.10F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, salt ^ 0x44525458) > chance) {
            return texture;
        }

        ResourceLocation replacement = replacementTexture(stack, texture, targetId, salt);
        if (trackUv) {
            RAW_TEXTURE.set(state(stack, replacement, targetId, salt ^ 0x55564C4B, intensity));
        }
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
        if (!isDirectTextureCandidate(texture)) {
            return;
        }
        if (TEXTURE_POOL_IDS.contains(texture)) {
            return;
        }
        synchronized (TEXTURE_POOL) {
            rememberLocked(texture);
        }
    }

    private static void rememberLocked(ResourceLocation texture) {
        if (!isDirectTextureCandidate(texture) || !TEXTURE_POOL_IDS.add(texture)) {
            return;
        }
        TEXTURE_POOL.add(texture);
    }

    private static ResourceLocation replacementTexture(CorruptionEffectStack stack, ResourceLocation original, String targetId, int salt) {
        synchronized (TEXTURE_POOL) {
            if (TEXTURE_POOL.size() < 2) {
                return original;
            }
            int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.TEXTURE_MEMORY, targetId, salt, TEXTURE_POOL.size());
            ResourceLocation replacement = TEXTURE_POOL.get(index);
            if (replacement.equals(original)) {
                replacement = TEXTURE_POOL.get((index + 1) % TEXTURE_POOL.size());
            }
            return replacement;
        }
    }

    private static RawTextureState state(CorruptionEffectStack stack, ResourceLocation texture, String targetId, int salt, float intensity) {
        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, salt);
        boolean extreme = stack.extreme(CorruptionSurface.TEXTURE_MEMORY);
        float uScale = extreme
                ? 0.08F + unit(seed ^ 0x5553434CL) * 10.0F
                : 0.40F + unit(seed ^ 0x5553434CL) * (1.25F + intensity * 5.4F);
        float vScale = extreme
                ? 0.08F + unit(seed ^ 0x5653434CL) * 10.0F
                : 0.40F + unit(seed ^ 0x5653434CL) * (1.25F + intensity * 5.4F);
        float leak = extreme ? 6.5F : 0.08F + intensity * 3.2F;
        float uOffset = signed(seed ^ 0x554F4646L, leak);
        float vOffset = signed(seed ^ 0x564F4646L, leak);
        boolean flipU = unit(seed ^ 0x464C4955L) < 0.18F + intensity * 0.38F;
        boolean flipV = unit(seed ^ 0x464C4956L) < 0.18F + intensity * 0.38F;
        return new RawTextureState(texture, uOffset, vOffset, uScale, vScale, flipU, flipV);
    }

    private static float directTextureIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return 1.0F;
        }
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.84F
        ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String targetId(String context, ResourceLocation texture) {
        return context + ":" + texture;
    }

    private static boolean isDirectTextureCandidate(ResourceLocation texture) {
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return false;
        }
        String path = texture.getPath();
        if (path == null || path.isBlank() || !path.startsWith("textures/") || !path.endsWith(".png")) {
            return false;
        }
        if (path.startsWith("textures/gui/") || path.startsWith("textures/font/") || path.startsWith("textures/atlas/")) {
            return false;
        }
        if (isDestroyStageTexture(path)) {
            return true;
        }
        return !path.startsWith("textures/block/")
                && !path.startsWith("textures/item/")
                && !path.startsWith("textures/particle/");
    }

    private static int compareTexturePriority(ResourceLocation first, ResourceLocation second) {
        int priority = Integer.compare(texturePriority(first), texturePriority(second));
        return priority != 0 ? priority : first.toString().compareTo(second.toString());
    }

    private static int texturePriority(ResourceLocation texture) {
        String path = texture == null ? "" : texture.getPath();
        if (path.startsWith("textures/environment/")) {
            return 0;
        }
        if (isDestroyStageTexture(path)) {
            return 1;
        }
        if (path.startsWith("textures/entity/")) {
            return 2;
        }
        if (path.startsWith("textures/models/") || path.startsWith("textures/effect/") || path.startsWith("textures/misc/")) {
            return 3;
        }
        return 4;
    }

    private static boolean isDestroyStageTexture(String path) {
        return path != null
                && path.startsWith("textures/block/destroy_stage_")
                && path.endsWith(".png");
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

    public record Uv(float u, float v) {
    }

    private record RawTextureState(ResourceLocation texture, float uOffset, float vOffset, float uScale, float vScale, boolean flipU, boolean flipV) {
        private Uv rawUv(float u, float v) {
            float localU = flipU ? 1.0F - u : u;
            float localV = flipV ? 1.0F - v : v;
            return new Uv(localU * uScale + uOffset, localV * vScale + vOffset);
        }
    }
}
