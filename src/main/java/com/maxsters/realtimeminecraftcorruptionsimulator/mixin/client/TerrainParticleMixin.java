package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(TerrainParticle.class)
public abstract class TerrainParticleMixin {
    private static Field rmc$spriteField;
    private static Method rmc$setSpriteMethod;
    private static boolean rmc$reflectionChecked;

    @Inject(
            method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void rmc$corruptConstructedTerrainParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, BlockState state, BlockPos pos, CallbackInfo callback) {
        rmc$corruptTerrainSprite(state, pos);
    }

    @Inject(
            method = "updateSprite",
            at = @At("RETURN"),
            remap = false
    )
    private void rmc$corruptUpdatedTerrainParticle(BlockState state, BlockPos pos, CallbackInfoReturnable<Particle> callback) {
        rmc$corruptTerrainSprite(state, pos);
    }

    private void rmc$corruptTerrainSprite(BlockState state, BlockPos pos) {
        TextureAtlasSprite original = rmc$getSprite();
        TextureAtlasSprite corrupted = ItemTextureCorruptionManager.corruptParticleSprite(original, rmc$targetId(state, pos), pos == null ? 0x50415254 : pos.hashCode());
        if (corrupted != null && corrupted != original) {
            rmc$setSprite(corrupted);
        }
    }

    private TextureAtlasSprite rmc$getSprite() {
        rmc$ensureReflection();
        if (rmc$spriteField == null) {
            return null;
        }
        try {
            return (TextureAtlasSprite) rmc$spriteField.get(this);
        } catch (IllegalAccessException | ClassCastException ignored) {
            return null;
        }
    }

    private void rmc$setSprite(TextureAtlasSprite sprite) {
        rmc$ensureReflection();
        if (rmc$setSpriteMethod == null) {
            return;
        }
        try {
            rmc$setSpriteMethod.invoke(this, sprite);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void rmc$ensureReflection() {
        if (rmc$reflectionChecked) {
            return;
        }
        rmc$reflectionChecked = true;
        for (String name : new String[]{"sprite", "f_108321_"}) {
            try {
                Field field = TextureSheetParticle.class.getDeclaredField(name);
                field.setAccessible(true);
                rmc$spriteField = field;
                break;
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (String name : new String[]{"setSprite", "m_108337_"}) {
            try {
                Method method = TextureSheetParticle.class.getDeclaredMethod(name, TextureAtlasSprite.class);
                method.setAccessible(true);
                rmc$setSpriteMethod = method;
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private static String rmc$targetId(BlockState state, BlockPos pos) {
        ResourceLocation id = state == null ? null : ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return "terrain_particle:" + (id == null ? "unknown" : id) + ":" + (pos == null ? "none" : pos.asLong());
    }
}
