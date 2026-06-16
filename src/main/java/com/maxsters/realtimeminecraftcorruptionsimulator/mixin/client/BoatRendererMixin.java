package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ModelRenderCorruptionHooks;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BoatRenderer.class)
public abstract class BoatRendererMixin {
    @Redirect(
            method = {
                    "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ListModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", remap = false),
            remap = false,
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rmc$corruptBoatAnimation(ListModel model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity instanceof Boat boat) {
            float[] mutated = ModelRenderCorruptionHooks.mutateBoatSetupAnimArgs(boat, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.setupAnim(boat, mutated[0], mutated[1], mutated[2], mutated[3], mutated[4]);
        } else {
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
