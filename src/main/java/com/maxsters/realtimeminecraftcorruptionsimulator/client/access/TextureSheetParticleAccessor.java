package com.maxsters.realtimeminecraftcorruptionsimulator.client.access;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface TextureSheetParticleAccessor {
    TextureAtlasSprite rmc$getSprite();

    void rmc$setSprite(TextureAtlasSprite sprite);

    void rmc$invokeSetSprite(TextureAtlasSprite sprite);
}
