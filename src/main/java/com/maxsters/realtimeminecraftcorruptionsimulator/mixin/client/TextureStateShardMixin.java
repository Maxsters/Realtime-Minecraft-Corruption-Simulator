package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.DirectTextureCorruptionHooks;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderStateShard.TextureStateShard.class)
public abstract class TextureStateShardMixin extends RenderStateShard.EmptyTextureStateShard {
    private TextureStateShardMixin() {
        super(() -> {
        }, () -> {
        });
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    @Dynamic("Replaces the captured setup lambda for RenderStateShard.TextureStateShard.")
    private void rmc$wrapTextureState(ResourceLocation texture, boolean blur, boolean mipmap, CallbackInfo callback) {
        this.setupState = () -> {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            ResourceLocation replacement = DirectTextureCorruptionHooks.bindTexture(texture, "render_type_direct_texture", texture == null ? 0x52545950 : texture.hashCode(), false);
            textureManager.getTexture(replacement).setFilter(blur, mipmap);
            RenderSystem.setShaderTexture(0, replacement);
        };
    }
}
