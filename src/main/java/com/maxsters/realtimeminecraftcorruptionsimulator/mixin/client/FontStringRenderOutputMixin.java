package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.FontRenderCorruptionHooks;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {
    @Unique
    private static Field rmc$dropShadowField;
    @Unique
    private static boolean rmc$dropShadowFieldChecked;

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
        FontRenderCorruptionHooks.renderGlyph(glyph, bold, italic, boldOffset, x, y, matrix, consumer, red, green, blue, alpha, light, rmc$dropShadow());
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

    @Unique
    private boolean rmc$dropShadow() {
        Field field = rmc$dropShadowField((Object) this);
        if (field == null) {
            return false;
        }
        try {
            return field.getBoolean(this);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static Field rmc$dropShadowField(Object owner) {
        if (owner == null) {
            return null;
        }
        if (rmc$dropShadowField != null) {
            return rmc$dropShadowField;
        }
        if (rmc$dropShadowFieldChecked) {
            return null;
        }
        rmc$dropShadowFieldChecked = true;
        Class<?> type = owner.getClass();
        for (String name : new String[]{"dropShadow", "f_92939_"}) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                rmc$dropShadowField = field;
                return field;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        return null;
    }
}
