package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Dynamic;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    private static Field rmc$updateLightTextureField;
    private static Field rmc$lightTextureField;
    private static Field rmc$lightPixelsField;

    @Inject(
            method = {
                    "tick()V",
                    "m_109880_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LightTexture#tick.")
    private void rmc$markLightTextureDirtyFromTick(CallbackInfo callback) {
        if (LightingCorruptionHooks.consumeLightTextureResetRequest()) {
            rmc$restoreDefaultLightTexture();
        }
        if (LightingCorruptionHooks.consumeLightTextureRefreshRequest()) {
            rmc$markLightTextureDirty();
        }
    }

    @Inject(
            method = {
                    "updateLightTexture(F)V",
                    "m_109881_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LightTexture#updateLightTexture.")
    private void rmc$markLightTextureDirtyFromUpdate(float partialTick, CallbackInfo callback) {
        if (LightingCorruptionHooks.consumeLightTextureResetRequest()) {
            rmc$restoreDefaultLightTexture();
        }
        if (LightingCorruptionHooks.consumeLightTextureRefreshRequest()) {
            rmc$markLightTextureDirty();
        }
    }

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

    private void rmc$markLightTextureDirty() {
        Field field = rmc$updateLightTextureField;
        if (field == null) {
            field = rmc$findUpdateLightTextureField();
            rmc$updateLightTextureField = field;
        }
        if (field == null) {
            return;
        }
        try {
            field.setBoolean(this, true);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void rmc$restoreDefaultLightTexture() {
        NativeImage pixels = rmc$lightPixels();
        DynamicTexture texture = rmc$lightTexture();
        if (pixels == null || texture == null) {
            return;
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixelRGBA(x, y, -1);
            }
        }
        texture.upload();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private DynamicTexture rmc$lightTexture() {
        Field field = rmc$lightTextureField;
        if (field == null) {
            field = rmc$findField("lightTexture", "f_109870_");
            rmc$lightTextureField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(this);
            return value instanceof DynamicTexture texture ? texture : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private NativeImage rmc$lightPixels() {
        Field field = rmc$lightPixelsField;
        if (field == null) {
            field = rmc$findField("lightPixels", "f_109871_");
            rmc$lightPixelsField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(this);
            return value instanceof NativeImage pixels ? pixels : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field rmc$findUpdateLightTextureField() {
        return rmc$findField("updateLightTexture", "f_109873_");
    }

    private static Field rmc$findField(String... names) {
        for (String name : names) {
            try {
                Field field = LightTexture.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
