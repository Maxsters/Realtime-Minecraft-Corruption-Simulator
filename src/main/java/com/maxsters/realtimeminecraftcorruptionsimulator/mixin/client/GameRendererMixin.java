package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.CameraRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.InteractionCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@SuppressWarnings("target")
public abstract class GameRendererMixin {
    @Inject(
            method = {
                    "pick(F)V",
                    "m_109087_(F)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#pick.")
    private void rmc$corruptPickRay(float partialTick, CallbackInfo callback) {
        if (InteractionCorruptionHooks.pickWithCorruptedRay(Minecraft.getInstance(), partialTick)) {
            callback.cancel();
        }
    }

    @Inject(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#bobView.")
    private void rmc$beginCorruptViewBob(PoseStack poseStack, float partialTick, CallbackInfo callback) {
        CameraRenderCorruptionHooks.beginViewBob();
    }

    @Inject(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#bobView.")
    private void rmc$endCorruptViewBob(PoseStack poseStack, float partialTick, CallbackInfo callback) {
        CameraRenderCorruptionHooks.endViewBob();
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Replaces the interpolated vanilla bob amplitude with a deterministic corrupted amplitude.")
    private float rmc$corruptViewBobAmplitude(float partialTick, float previousBob, float currentBob) {
        return CameraRenderCorruptionHooks.mutateBobAmplitude(partialTick, previousBob, currentBob);
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14179_(FFF)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Runtime SRG alias for Mth#lerp in GameRenderer#bobView.")
    private float rmc$corruptViewBobAmplitudeSrg(float partialTick, float previousBob, float currentBob) {
        return CameraRenderCorruptionHooks.mutateBobAmplitude(partialTick, previousBob, currentBob);
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts the sine phase used by vanilla walk bobbing.")
    private float rmc$corruptViewBobSine(float radians) {
        return CameraRenderCorruptionHooks.mutateBobSine(radians);
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14031_(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Runtime SRG alias for Mth#sin in GameRenderer#bobView.")
    private float rmc$corruptViewBobSineSrg(float radians) {
        return CameraRenderCorruptionHooks.mutateBobSine(radians);
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;cos(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts the cosine phase used by vanilla walk bobbing.")
    private float rmc$corruptViewBobCosine(float radians) {
        return CameraRenderCorruptionHooks.mutateBobCosine(radians);
    }

    @Redirect(
            method = {
                    "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14089_(F)F"),
            remap = false,
            require = 0
    )
    @Dynamic("Runtime SRG alias for Mth#cos in GameRenderer#bobView.")
    private float rmc$corruptViewBobCosineSrg(float radians) {
        return CameraRenderCorruptionHooks.mutateBobCosine(radians);
    }
}
