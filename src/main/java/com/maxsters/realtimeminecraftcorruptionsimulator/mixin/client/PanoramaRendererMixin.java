package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.MenuPanoramaCorruptionHooks;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PanoramaRenderer.class)
@SuppressWarnings("target")
public abstract class PanoramaRendererMixin {
    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for PanoramaRenderer#render.")
    private float rmc$corruptPanoramaPitch(float pitch) {
        return MenuPanoramaCorruptionHooks.pitch(pitch);
    }

    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for PanoramaRenderer#render.")
    private float rmc$corruptPanoramaYaw(float yaw) {
        return MenuPanoramaCorruptionHooks.yaw(yaw);
    }

    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for PanoramaRenderer#render.")
    private float rmc$corruptPanoramaAlpha(float alpha) {
        return MenuPanoramaCorruptionHooks.alpha(alpha);
    }

    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;m_108849_(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 1,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of CubeMap#render from PanoramaRenderer#render.")
    private float rmc$corruptPanoramaPitchSrg(float pitch) {
        return MenuPanoramaCorruptionHooks.pitch(pitch);
    }

    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;m_108849_(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of CubeMap#render from PanoramaRenderer#render.")
    private float rmc$corruptPanoramaYawSrg(float yaw) {
        return MenuPanoramaCorruptionHooks.yaw(yaw);
    }

    @ModifyArg(
            method = {
                    "render(FF)V",
                    "m_110003_(FF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;m_108849_(Lnet/minecraft/client/Minecraft;FFF)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of CubeMap#render from PanoramaRenderer#render.")
    private float rmc$corruptPanoramaAlphaSrg(float alpha) {
        return MenuPanoramaCorruptionHooks.alpha(alpha);
    }
}
