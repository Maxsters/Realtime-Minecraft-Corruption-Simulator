package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.TextureSheetParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.particles.ParticleFieldAccess;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureSheetParticle.class)
public abstract class TextureSheetParticleAccessorMixin implements TextureSheetParticleAccessor {
    @Override
    public TextureAtlasSprite rmc$getSprite() {
        return ParticleFieldAccess.textureSheetSprite(rmc$self());
    }

    @Override
    public void rmc$setSprite(TextureAtlasSprite sprite) {
        ParticleFieldAccess.setTextureSheetSprite(rmc$self(), sprite);
    }

    @Override
    public void rmc$invokeSetSprite(TextureAtlasSprite sprite) {
        ParticleFieldAccess.setTextureSheetSprite(rmc$self(), sprite);
    }

    private TextureSheetParticle rmc$self() {
        return (TextureSheetParticle) (Object) this;
    }
}
