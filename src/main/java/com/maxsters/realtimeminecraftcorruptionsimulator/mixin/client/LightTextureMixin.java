package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Inject(
            method = {
                    "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
                    "m_234316_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void rmc$corruptLightBrightnessCurve(DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(LightingCorruptionHooks.mutateBrightnessCurve(lightLevel, callback.getReturnValue()));
    }

    @Redirect(
            method = {
                    "updateLightTexture(F)V",
                    "m_109881_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V", remap = false),
            remap = false,
            require = 0
    )
    private void rmc$corruptLightmapPixel(NativeImage image, int x, int y, int color) {
        image.setPixelRGBA(x, y, LightingCorruptionHooks.mutateLightmapPixel(x, y, color));
    }

    @Redirect(
            method = {
                    "updateLightTexture(F)V",
                    "m_109881_"
            },
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;m_84988_(III)V", remap = false),
            remap = false,
            require = 0
    )
    private void rmc$corruptLightmapPixelSrg(NativeImage image, int x, int y, int color) {
        image.setPixelRGBA(x, y, LightingCorruptionHooks.mutateLightmapPixel(x, y, color));
    }
}
