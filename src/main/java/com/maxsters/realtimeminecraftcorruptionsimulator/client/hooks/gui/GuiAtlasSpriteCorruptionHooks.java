package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuiAtlasSpriteCorruptionHooks {
    private static final List<TextureAtlasSprite> SPRITE_POOL = new ArrayList<>();
    private static final Set<ResourceLocation> SPRITE_POOL_IDS = ConcurrentHashMap.newKeySet();

    private GuiAtlasSpriteCorruptionHooks() {
    }

    public static void rememberAtlasSprite(TextureAtlasSprite sprite) {
        ResourceLocation id = spriteId(sprite);
        if (id == null) {
            return;
        }
        if (SPRITE_POOL_IDS.contains(id)) {
            return;
        }
        synchronized (SPRITE_POOL) {
            if (SPRITE_POOL_IDS.add(id)) {
                SPRITE_POOL.add(spriteInsertionIndex(id), sprite);
            }
        }
    }

    public static TextureAtlasSprite corruptSprite(TextureAtlasSprite original, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (original == null || !stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return original;
        }

        rememberAtlasSprite(original);
        ResourceLocation originalId = spriteId(original);
        String targetId = "gui_atlas_sprite:" + (originalId == null ? "unknown" : originalId);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.78F
                ) + stack.instability() * 0.06F, 0.0F, 1.0F);
        if (intensity <= 0.025F) {
            return original;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.98F
                : Mth.clamp(0.12F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.92F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return original;
        }

        synchronized (SPRITE_POOL) {
            if (SPRITE_POOL.size() < 2) {
                return original;
            }
            int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.GUI_SURFACE, targetId, salt, SPRITE_POOL.size());
            TextureAtlasSprite replacement = SPRITE_POOL.get(index);
            if (sameSprite(original, replacement)) {
                replacement = SPRITE_POOL.get((index + 1) % SPRITE_POOL.size());
            }
            return replacement;
        }
    }

    public static SpriteDraw corruptDraw(TextureAtlasSprite original, int salt, float red, float green, float blue, float alpha) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (original == null || !stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return null;
        }

        rememberAtlasSprite(original);
        ResourceLocation originalId = spriteId(original);
        String targetId = "gui_atlas_sprite_draw:" + (originalId == null ? "unknown" : originalId);
        float intensity = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.82F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.025F) {
            return null;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.98F
                : Mth.clamp(0.14F + intensity * 0.78F + stack.instability() * 0.08F, 0.0F, 0.93F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return null;
        }

        TextureAtlasSprite sprite = selectReplacementSprite(stack, original, targetId, salt);
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, salt ^ 0x44524157);
        boolean extreme = stack.extreme(CorruptionSurface.GUI_SURFACE);
        float spanScale = extreme
                ? 0.10F + unit(seed ^ 0x5350414EL) * 9.5F
                : 0.38F + unit(seed ^ 0x5350414EL) * (1.35F + intensity * 5.2F);
        float spanScaleV = extreme
                ? 0.10F + unit(seed ^ 0x53504156L) * 9.5F
                : 0.38F + unit(seed ^ 0x53504156L) * (1.35F + intensity * 5.2F);
        float offsetSpan = extreme ? 96.0F : 4.0F + intensity * 42.0F;
        float u0Local = signed(seed ^ 0x554F4646L, offsetSpan);
        float v0Local = signed(seed ^ 0x564F4646L, offsetSpan);
        float u1Local = u0Local + 16.0F * spanScale;
        float v1Local = v0Local + 16.0F * spanScaleV;

        if (unit(seed ^ 0x464C4955L) < 0.25F + intensity * 0.42F) {
            float swap = u0Local;
            u0Local = u1Local;
            u1Local = swap;
        }
        if (unit(seed ^ 0x464C4956L) < 0.25F + intensity * 0.42F) {
            float swap = v0Local;
            v0Local = v1Local;
            v1Local = swap;
        }
        if (unit(seed ^ 0x5155414EL) < intensity * 0.36F) {
            float step = 0.5F + unit(seed ^ 0x53544550L) * (1.0F + intensity * 7.0F);
            u0Local = Math.round(u0Local / step) * step;
            v0Local = Math.round(v0Local / step) * step;
            u1Local = Math.round(u1Local / step) * step;
            v1Local = Math.round(v1Local / step) * step;
        }

        float tintSpan = extreme ? 1.8F : 0.35F + intensity * 1.25F;
        float corruptedRed = Mth.clamp(red * (1.0F + signed(seed ^ 0x524544L, tintSpan)), 0.0F, 2.5F);
        float corruptedGreen = Mth.clamp(green * (1.0F + signed(seed ^ 0x475245454EL, tintSpan)), 0.0F, 2.5F);
        float corruptedBlue = Mth.clamp(blue * (1.0F + signed(seed ^ 0x424C5545L, tintSpan)), 0.0F, 2.5F);
        float corruptedAlpha = Mth.clamp(alpha * (0.45F + unit(seed ^ 0x414C5048L) * (0.95F + intensity * 0.85F)), 0.05F, 1.0F);
        if (unit(seed ^ 0x50414C45L) < 0.07F + intensity * 0.16F) {
            int palette = Math.floorMod((int) (seed >>> 52), 5);
            if (palette == 0) {
                corruptedRed = 1.0F;
                corruptedGreen = 0.0F;
                corruptedBlue = 1.0F;
            } else if (palette == 1) {
                corruptedRed = 0.0F;
                corruptedGreen = 1.0F;
                corruptedBlue = 1.0F;
            } else if (palette == 2) {
                corruptedRed = 1.0F;
                corruptedGreen = 1.0F;
                corruptedBlue = 1.0F;
            } else if (palette == 3) {
                corruptedRed = 0.0F;
                corruptedGreen = 0.0F;
                corruptedBlue = 0.0F;
            }
        }

        return new SpriteDraw(
                sprite.atlasLocation(),
                sprite.getU(u0Local),
                sprite.getU(u1Local),
                sprite.getV(v0Local),
                sprite.getV(v1Local),
                corruptedRed,
                corruptedGreen,
                corruptedBlue,
                corruptedAlpha
        );
    }

    private static TextureAtlasSprite selectReplacementSprite(CorruptionEffectStack stack, TextureAtlasSprite original, String targetId, int salt) {
        synchronized (SPRITE_POOL) {
            if (SPRITE_POOL.size() < 2) {
                return original;
            }
            int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.GUI_SURFACE, targetId, salt, SPRITE_POOL.size());
            TextureAtlasSprite replacement = SPRITE_POOL.get(index);
            if (sameSprite(original, replacement)) {
                replacement = SPRITE_POOL.get((index + 1) % SPRITE_POOL.size());
            }
            return replacement;
        }
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

    private static ResourceLocation spriteId(TextureAtlasSprite sprite) {
        return sprite == null || sprite.contents() == null ? null : sprite.contents().name();
    }

    private static boolean sameSprite(TextureAtlasSprite first, TextureAtlasSprite second) {
        ResourceLocation firstId = spriteId(first);
        ResourceLocation secondId = spriteId(second);
        return first == second || (firstId != null && firstId.equals(secondId));
    }

    private static int spriteInsertionIndex(ResourceLocation id) {
        String key = id.toString();
        int low = 0;
        int high = SPRITE_POOL.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            ResourceLocation existing = spriteId(SPRITE_POOL.get(mid));
            String existingKey = existing == null ? "" : existing.toString();
            if (existingKey.compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public record SpriteDraw(ResourceLocation atlasLocation, float u0, float u1, float v0, float v1, float red, float green, float blue, float alpha) {
    }
}
