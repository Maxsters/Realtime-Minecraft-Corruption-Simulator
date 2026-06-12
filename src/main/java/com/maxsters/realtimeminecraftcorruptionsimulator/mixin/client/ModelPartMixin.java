package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ModelRenderCorruptionHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @Inject(
            method = {
                    "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    "m_104299_"
            },
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void rmc$corruptModelPartTransform(PoseStack poseStack, CallbackInfo callback) {
        ModelRenderCorruptionHooks.mutateModelPartTransform((ModelPart) (Object) this, poseStack);
    }
}
