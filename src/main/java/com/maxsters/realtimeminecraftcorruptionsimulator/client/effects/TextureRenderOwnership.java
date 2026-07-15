package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class TextureRenderOwnership {
    private static final Set<ResourceLocation> GUI_RENDERED_TEXTURES = ConcurrentHashMap.newKeySet();

    private TextureRenderOwnership() {
    }

    public static boolean rememberGuiRendered(ResourceLocation texture) {
        return texture != null && GUI_RENDERED_TEXTURES.add(texture);
    }

    public static boolean isGuiOwned(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        String path = texture.getPath();
        return (path != null && path.startsWith("textures/gui/"))
                || GUI_RENDERED_TEXTURES.contains(texture);
    }
}
