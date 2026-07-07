package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.BreakingTextureCorruptionHooks;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SheetedDecalTextureGenerator.class)
@SuppressWarnings("target")
public abstract class SheetedDecalTextureGeneratorMixin {
    @Redirect(
            method = {
                    "endVertex()V",
                    "m_5752_()V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SheetedDecalTextureGenerator#endVertex.")
    private VertexConsumer rmc$corruptDestroyUv(VertexConsumer consumer, float u, float v) {
        return BreakingTextureCorruptionHooks.uv(consumer, u, v);
    }

    @Redirect(
            method = {
                    "endVertex()V",
                    "m_5752_()V"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SheetedDecalTextureGenerator#endVertex.")
    private VertexConsumer rmc$corruptDestroyColor(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        return BreakingTextureCorruptionHooks.color(consumer, red, green, blue, alpha);
    }
}
