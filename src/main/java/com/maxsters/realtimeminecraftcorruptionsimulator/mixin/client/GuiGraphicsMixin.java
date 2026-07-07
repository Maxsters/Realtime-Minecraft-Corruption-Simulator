package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.GuiAtlasSpriteCorruptionHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
@SuppressWarnings("target")
public abstract class GuiGraphicsMixin {
    @Inject(
            method = {
                    "blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
                    "m_280159_(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GuiGraphics#blit with atlas sprites.")
    private void rmc$corruptGuiAtlasSprite(int x, int y, int z, int width, int height, TextureAtlasSprite sprite, CallbackInfo callback) {
        GuiAtlasSpriteCorruptionHooks.SpriteDraw draw = GuiAtlasSpriteCorruptionHooks.corruptDraw(sprite, 0x475549, 1.0F, 1.0F, 1.0F, 1.0F);
        if (draw == null) {
            return;
        }
        innerBlit(draw.atlasLocation(), x, x + width, y, y + height, z, draw.u0(), draw.u1(), draw.v0(), draw.v1(), draw.red(), draw.green(), draw.blue(), draw.alpha());
        callback.cancel();
    }

    @Inject(
            method = {
                    "blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFF)V",
                    "m_280565_(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for tinted GuiGraphics#blit with atlas sprites.")
    private void rmc$corruptTintedGuiAtlasSprite(int x, int y, int z, int width, int height, TextureAtlasSprite sprite, float red, float green, float blue, float alpha, CallbackInfo callback) {
        GuiAtlasSpriteCorruptionHooks.SpriteDraw draw = GuiAtlasSpriteCorruptionHooks.corruptDraw(sprite, 0x54494E54, red, green, blue, alpha);
        if (draw == null) {
            return;
        }
        innerBlit(draw.atlasLocation(), x, x + width, y, y + height, z, draw.u0(), draw.u1(), draw.v0(), draw.v1(), draw.red(), draw.green(), draw.blue(), draw.alpha());
        callback.cancel();
    }

    @Shadow(remap = false, aliases = "m_280479_")
    abstract void innerBlit(ResourceLocation atlasLocation, int minX, int maxX, int minY, int maxY, int z, float minU, float maxU, float minV, float maxV, float red, float green, float blue, float alpha);
}
