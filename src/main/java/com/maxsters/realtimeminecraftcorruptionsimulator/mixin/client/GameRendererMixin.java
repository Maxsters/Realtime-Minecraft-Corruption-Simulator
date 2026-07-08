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
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#bobView.")
    private void rmc$corruptViewBob(PoseStack poseStack, float partialTick, CallbackInfo callback) {
        CameraRenderCorruptionHooks.mutateViewBob(poseStack, partialTick, false);
    }

    @Inject(
            method = {
                    "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    "m_109117_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for GameRenderer#bobHurt.")
    private void rmc$corruptHurtBob(PoseStack poseStack, float partialTick, CallbackInfo callback) {
        CameraRenderCorruptionHooks.mutateViewBob(poseStack, partialTick, true);
    }
}
