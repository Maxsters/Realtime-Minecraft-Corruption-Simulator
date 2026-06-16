package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.BiomeBlendCorruptionHooks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
@SuppressWarnings("target")
public abstract class BiomeColorsMixin {
    @Inject(
            method = {
                    "getAverageGrassColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                    "m_108793_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BiomeColors#getAverageGrassColor.")
    private static void rmc$corruptGrassBlend(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(BiomeBlendCorruptionHooks.mutateBiomeColor("grass", level, pos, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getAverageFoliageColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                    "m_108804_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BiomeColors#getAverageFoliageColor.")
    private static void rmc$corruptFoliageBlend(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(BiomeBlendCorruptionHooks.mutateBiomeColor("foliage", level, pos, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "getAverageWaterColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                    "m_108811_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BiomeColors#getAverageWaterColor.")
    private static void rmc$corruptWaterBlend(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(BiomeBlendCorruptionHooks.mutateBiomeColor("water", level, pos, callback.getReturnValue()));
    }
}
