package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.FontRenderCorruptionHooks;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {
    @Shadow(remap = false, aliases = "f_92939_")
    @Final
    private boolean dropShadow;

    @Redirect(
            method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void rmc$renderCorruptedFontCharOfficial(Font font,
                                                     BakedGlyph glyph,
                                                     boolean bold,
                                                     boolean italic,
                                                     float boldOffset,
                                                     float x,
                                                     float y,
                                                     Matrix4f matrix,
                                                     VertexConsumer consumer,
                                                     float red,
                                                     float green,
                                                     float blue,
                                                     float alpha,
                                                     int light) {
        rmc$renderCorruptedFontChar(glyph, bold, italic, boldOffset, x, y, matrix, consumer, red, green, blue, alpha, light);
    }

    @Redirect(
            method = "m_6411_(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;m_253238_(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void rmc$renderCorruptedFontCharSrg(Font font,
                                                BakedGlyph glyph,
                                                boolean bold,
                                                boolean italic,
                                                float boldOffset,
                                                float x,
                                                float y,
                                                Matrix4f matrix,
                                                VertexConsumer consumer,
                                                float red,
                                                float green,
                                                float blue,
                                                float alpha,
                                                int light) {
        rmc$renderCorruptedFontChar(glyph, bold, italic, boldOffset, x, y, matrix, consumer, red, green, blue, alpha, light);
    }

    private void rmc$renderCorruptedFontChar(BakedGlyph glyph,
                                             boolean bold,
                                             boolean italic,
                                             float boldOffset,
                                             float x,
                                             float y,
                                             Matrix4f matrix,
                                             VertexConsumer consumer,
                                             float red,
                                             float green,
                                             float blue,
                                             float alpha,
                                             int light) {
        FontRenderCorruptionHooks.renderGlyph(glyph, bold, italic, boldOffset, x, y, matrix, consumer, red, green, blue, alpha, light, dropShadow);
    }

    @Redirect(
            method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/font/GlyphInfo;getAdvance(Z)F",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private float rmc$corruptCharacterAdvanceOfficial(GlyphInfo glyph, boolean bold) {
        return rmc$corruptCharacterAdvance(glyph, bold);
    }

    @Redirect(
            method = "m_6411_(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/font/GlyphInfo;m_83827_(Z)F",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private float rmc$corruptCharacterAdvanceSrg(GlyphInfo glyph, boolean bold) {
        return rmc$corruptCharacterAdvance(glyph, bold);
    }

    private float rmc$corruptCharacterAdvance(GlyphInfo glyph, boolean bold) {
        return FontRenderCorruptionHooks.mutateAdvance(glyph.getAdvance(bold));
    }
}
