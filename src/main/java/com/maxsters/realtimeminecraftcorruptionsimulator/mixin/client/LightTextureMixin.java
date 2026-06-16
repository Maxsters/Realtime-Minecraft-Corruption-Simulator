package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
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

    private static Field rmc$findUpdateLightTextureField() {
        for (String name : new String[]{"updateLightTexture", "f_109873_"}) {
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
