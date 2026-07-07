package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.GuiEntityPreviewCorruptionHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionf;

@Mixin(InventoryScreen.class)
@SuppressWarnings("target")
public abstract class InventoryScreenMixin {
    @Unique
    private static LivingEntity rmc$previewEntity;
    @Unique
    private static int rmc$previewX;
    @Unique
    private static int rmc$previewY;
    @Unique
    private static int rmc$previewScale;

    @Inject(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void rmc$corruptPreviewVisibility(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity, CallbackInfo callback) {
        if (GuiEntityPreviewCorruptionHooks.shouldSkip(entity, x, y, scale, mouseX, mouseY)) {
            callback.cancel();
        }
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private static int rmc$corruptPreviewX(int value, GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.x(entity, value, x, y, scale, mouseX, mouseY);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 0
    )
    private static int rmc$corruptPreviewY(int value, GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.y(entity, value, x, y, scale, mouseX, mouseY);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            remap = false,
            require = 0
    )
    private static int rmc$corruptPreviewScale(int value, GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.scale(entity, value, x, y, scale, mouseX, mouseY);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewMouseX(float value, GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.mouseX(entity, value, x, y, scale, mouseX, mouseY);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewMouseY(float value, GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.mouseY(entity, value, x, y, scale, mouseX, mouseY);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsAngle(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274525_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewAngleX(float value, GuiGraphics graphics, int x, int y, int scale, float angleX, float angleY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.angleX(entity, value, angleX, angleY, x, y, scale);
    }

    @ModifyVariable(
            method = {
                    "renderEntityInInventoryFollowsAngle(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    "m_274525_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewAngleY(float value, GuiGraphics graphics, int x, int y, int scale, float angleX, float angleY, LivingEntity entity) {
        return GuiEntityPreviewCorruptionHooks.angleY(entity, value, angleX, angleY, x, y, scale);
    }

    @Inject(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void rmc$beginPreviewRenderContext(GuiGraphics graphics, int x, int y, int scale, Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity, CallbackInfo callback) {
        rmc$previewEntity = entity;
        rmc$previewX = x;
        rmc$previewY = y;
        rmc$previewScale = scale;
    }

    @Inject(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void rmc$clearPreviewRenderContext(GuiGraphics graphics, int x, int y, int scale, Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity, CallbackInfo callback) {
        rmc$previewEntity = null;
        rmc$previewX = 0;
        rmc$previewY = 0;
        rmc$previewScale = 0;
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewRenderYaw(float yaw) {
        return GuiEntityPreviewCorruptionHooks.renderYaw(rmc$previewEntity, yaw, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114384_(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewRenderYawSrg(float yaw) {
        return GuiEntityPreviewCorruptionHooks.renderYaw(rmc$previewEntity, yaw, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 5,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewPartialTick(float partialTick) {
        return GuiEntityPreviewCorruptionHooks.partialTick(rmc$previewEntity, partialTick, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114384_(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 5,
            remap = false,
            require = 0
    )
    private static float rmc$corruptPreviewPartialTickSrg(float partialTick) {
        return GuiEntityPreviewCorruptionHooks.partialTick(rmc$previewEntity, partialTick, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 9,
            remap = false,
            require = 0
    )
    private static int rmc$corruptPreviewPackedLight(int packedLight) {
        return GuiEntityPreviewCorruptionHooks.packedLight(rmc$previewEntity, packedLight, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114384_(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
            index = 9,
            remap = false,
            require = 0
    )
    private static int rmc$corruptPreviewPackedLightSrg(int packedLight) {
        return GuiEntityPreviewCorruptionHooks.packedLight(rmc$previewEntity, packedLight, rmc$previewX, rmc$previewY, rmc$previewScale);
    }

    @Redirect(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;setRenderShadow(Z)V", remap = false),
            remap = false,
            require = 0
    )
    private static void rmc$corruptPreviewShadow(EntityRenderDispatcher dispatcher, boolean shadow) {
        dispatcher.setRenderShadow(GuiEntityPreviewCorruptionHooks.renderShadow(rmc$previewEntity, shadow, rmc$previewX, rmc$previewY, rmc$previewScale));
    }

    @Redirect(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114468_(Z)V", remap = false),
            remap = false,
            require = 0
    )
    private static void rmc$corruptPreviewShadowSrg(EntityRenderDispatcher dispatcher, boolean shadow) {
        dispatcher.setRenderShadow(GuiEntityPreviewCorruptionHooks.renderShadow(rmc$previewEntity, shadow, rmc$previewX, rmc$previewY, rmc$previewScale));
    }
}
