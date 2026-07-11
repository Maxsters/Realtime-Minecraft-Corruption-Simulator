package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.CameraRenderCorruptionHooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
@SuppressWarnings("target")
public abstract class CameraMixin {
    @Shadow(remap = false, aliases = "m_90581_")
    protected abstract void setPosition(Vec3 position);

    @Redirect(
            method = {
                    "tick()V",
                    "m_90565_()V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Corrupts Camera's real eye-height input, including the crouch transition.")
    private float rmc$corruptCameraEyeHeight(Entity entity) {
        Camera camera = (Camera) (Object) this;
        return CameraRenderCorruptionHooks.mutateCameraEyeHeight(camera, entity, entity.getEyeHeight());
    }

    @Redirect(
            method = {
                    "tick()V",
                    "m_90565_()V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20192_()F", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Runtime SRG alias for Entity#getEyeHeight in Camera#tick.")
    private float rmc$corruptCameraEyeHeightSrg(Entity entity) {
        Camera camera = (Camera) (Object) this;
        return CameraRenderCorruptionHooks.mutateCameraEyeHeight(camera, entity, entity.getEyeHeight());
    }

    @Inject(
            method = {
                    "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
                    "m_90575_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Applies a bounded deterministic fault to Camera's final world-space position.")
    private void rmc$corruptCameraPosition(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        Vec3 original = camera.getPosition();
        Vec3 mutated = CameraRenderCorruptionHooks.mutateCameraPosition(camera, entity, original);
        if (mutated != null && !mutated.equals(original)) {
            setPosition(mutated);
        }
    }
}
