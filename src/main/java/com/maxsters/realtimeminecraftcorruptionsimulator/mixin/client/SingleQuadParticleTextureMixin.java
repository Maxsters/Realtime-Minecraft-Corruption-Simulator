package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.particles.ParticleTextureCorruptionHooks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleTextureMixin {
    @Inject(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$beginParticleTextureCorruption(VertexConsumer consumer, Camera camera, float partialTick, CallbackInfo callback) {
        ParticleTextureCorruptionHooks.beginParticleRender(rmc$particle());
    }

    @Inject(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void rmc$endParticleTextureCorruption(VertexConsumer consumer, Camera camera, float partialTick, CallbackInfo callback) {
        ParticleTextureCorruptionHooks.endParticleRender();
    }

    @Redirect(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;uv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptParticleUv(VertexConsumer consumer, float u, float v) {
        return ParticleTextureCorruptionHooks.uv(rmc$particle(), consumer, u, v);
    }

    @Redirect(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_7421_(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptParticleUvSrg(VertexConsumer consumer, float u, float v) {
        return ParticleTextureCorruptionHooks.uv(rmc$particle(), consumer, u, v);
    }

    @Redirect(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptParticleColor(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        return ParticleTextureCorruptionHooks.color(rmc$particle(), consumer, red, green, blue, alpha);
    }

    @Redirect(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
                    "m_5744_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_85950_(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false),
            remap = false,
            require = 0
    )
    private VertexConsumer rmc$corruptParticleColorSrg(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        return ParticleTextureCorruptionHooks.color(rmc$particle(), consumer, red, green, blue, alpha);
    }

    @Unique
    private Particle rmc$particle() {
        return (Particle) (Object) this;
    }
}
